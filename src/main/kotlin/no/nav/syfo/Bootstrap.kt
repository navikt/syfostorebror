package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import java.net.URL
import java.nio.file.Paths
import java.util.Properties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.syfo.api.enforceCallId
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.api.registerSoknadDataApi
import no.nav.syfo.api.registerSykmeldingDataApi
import no.nav.syfo.api.setupContentNegotiation
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.kafka.StreamResetter
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.service.soknad.persistering.blockingApplicationLogicSoknad
import no.nav.syfo.service.soknad.persistering.slettSoknaderRawLog
import no.nav.syfo.service.sykmelding.persistering.blockingApplicationLogicSykmelding
import no.nav.syfo.service.sykmelding.persistering.slettSykmeldingerRawLog
import no.nav.syfo.vault.Vault
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

val log = LoggerFactory.getLogger("no.nav.syfo.syfostorebror")
const val NAV_CALLID = "Nav-CallId"

fun main() {
    val env = Environment()
    val vaultSecrets = objectMapper.readValue<VaultSecrets>(Paths.get(env.vaultPath).toFile())
    val applicationState = ApplicationState()

    val authorizedUsers: List<String> = listOf(
            env.clientId // Self
    )

    val vaultCredentialService = VaultCredentialService()
    val database = Database(env, vaultCredentialService)

    GlobalScope.launch() {
        try {
            Vault.renewVaultTokenTask(applicationState)
        } finally {
            applicationState.running = false
        }
    }

    GlobalScope.launch() {
        try {
            vaultCredentialService.runRenewCredentialsTask { applicationState.running }
        } finally {
            applicationState.running = false
        }
    }

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        setupMetrics()
        setupAuth(env, authorizedUsers)
        setupContentNegotiation(database)
        initRouting(applicationState, database)
    }.start(wait = false)

    if (env.resetStreamOnly) {
        resetStreams(env, database, vaultSecrets)
    } else {
        val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets).envOverrides()
        val consumerProperties = kafkaBaseConfig.toConsumerConfig(
                groupId = env.consumerGroupId,
                valueDeserializer = StringDeserializer::class
        )
        launchListeners(env, applicationState, consumerProperties, database)
    }

    applicationState.initialized = true
}

fun launchListeners(

    env: Environment,
    applicationState: ApplicationState,
    consumerProperties: Properties,
    database: Database
) {

    val kafkaConsumerSoknad = KafkaConsumer<String, String>(consumerProperties)
    kafkaConsumerSoknad.subscribe(listOf(env.soknadTopic))
    createListener(applicationState) {
        blockingApplicationLogicSoknad(applicationState, kafkaConsumerSoknad, database)
    }

    val kafkaConsumerSykmelding = KafkaConsumer<String, String>(consumerProperties)
    kafkaConsumerSykmelding.subscribe(env.smTopics)
    createListener(applicationState) {
        blockingApplicationLogicSykmelding(applicationState, kafkaConsumerSykmelding, database)
    }

    applicationState.initialized = true
}

private fun Application.setupMetrics() {
    install(MicrometerMetrics) {
        registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, CollectorRegistry.defaultRegistry, Clock.SYSTEM)
        meterBinders = listOf(
                ClassLoaderMetrics(),
                JvmMemoryMetrics(),
                JvmGcMetrics(),
                ProcessorMetrics(),
                JvmThreadMetrics(),
                LogbackMetrics()
        )
    }
}

fun Application.initRouting(applicationState: ApplicationState, database: Database) {
    routing {
        naisRouting(applicationState)
        route("/api") {
            enforceCallId(NAV_CALLID)
            // authenticate {
            registerSoknadDataApi(database)
            registerSykmeldingDataApi(database)
            // }
        }
    }
}

fun Application.naisRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
                readynessCheck = {
                    applicationState.initialized
                },
                livenessCheck = {
                    applicationState.running
                }
        )
    }
}

private fun resetStreams(env: Environment, database: Database, vaultSecrets: VaultSecrets) {
    for (topic: String in env.resetStreams) {
        log.info("Starter StreamResetter for topic '$topic'...")
        val soknadResetter = StreamResetter(env.kafkaBootstrapServers, topic, env.consumerGroupId, vaultSecrets)
        soknadResetter.run()
        log.info("StreamResetter kjørt for topic '$topic'.")
    }

    database.connection.slettSoknaderRawLog()
    log.info("Raw-logg slettet for søknadspersistering.")

    database.connection.slettSykmeldingerRawLog()
    log.info("Raw-logg slettet for sykmeldingspersistering.")
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch {
            try {
                action()
            } finally {
                applicationState.running = false
            }
        }

fun Application.setupAuth(environment: Environment, authorizedUsers: List<String>) {
    install(Authentication) {
        jwt {
            verifier(
                    JwkProviderBuilder(URL(environment.jwkKeysUrl))
                            .cached(10, 24, java.util.concurrent.TimeUnit.HOURS)
                            .rateLimited(10, 1, java.util.concurrent.TimeUnit.MINUTES)
                            .build(), environment.jwtIssuer
            )
            realm = "syfostorebror"
            validate { credentials ->
                val appid: String = credentials.payload.getClaim("appid").asString()
                no.nav.syfo.api.log.info("authorization attempt for $appid")
                if (appid in authorizedUsers && credentials.payload.audience.contains(environment.clientId)) {
                    no.nav.syfo.api.log.info("authorization ok")
                    return@validate JWTPrincipal(credentials.payload)
                }
                no.nav.syfo.api.log.info("authorization failed")
                return@validate null
            }
        }
    }
}
