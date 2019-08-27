package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.InternalAPI
import io.ktor.util.KtorExperimentalAPI
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
import kotlinx.coroutines.*
import kotlinx.coroutines.slf4j.MDCContext
import no.nav.syfo.aksessering.kafka.SoknadStreamResetter
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.api.registerSoknadDataApi
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.persistering.*
import no.nav.syfo.vault.Vault
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

private val log = LoggerFactory.getLogger("no.nav.syfo.syfostorebror")

val backgroundTasksContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher() + MDCContext()

@InternalAPI
fun main() = runBlocking(Executors.newFixedThreadPool(2).asCoroutineDispatcher()) {
    val env = Environment()
    val vaultSecrets = objectMapper.readValue<VaultSecrets>(Paths.get(env.vaultPath).toFile())
    val applicationState = ApplicationState()

    val vaultCredentialService = VaultCredentialService()
    val database = Database(env, vaultCredentialService)

    launch(backgroundTasksContext) {
        try {
            Vault.renewVaultTokenTask(applicationState)
        } finally {
            applicationState.running = false
        }
    }

    launch(backgroundTasksContext) {
        try {
            vaultCredentialService.runRenewCredentialsTask { applicationState.running }
        } finally {
            applicationState.running = false
        }
    }

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
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
        initRouting(applicationState)
        dataRouting(database)
    }.start(wait = false)

    if (env.resetStreamOnly){
        log.info("Starter SoknadStreamResetter...")
        val soknadResetter = SoknadStreamResetter(env, env.soknadTopic, env.soknadConsumerGroup, vaultSecrets)
        soknadResetter.run()
        log.info("SoknadStreamResetter kjørt.")
        database.connection.slettRawLog()
        log.info("Raw-logg slettet.")
    } else {
        val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets)
                .envOverrides()
        val consumerProperties = kafkaBaseConfig.toConsumerConfig(
                /* Todo: Koble på syfosøknad */
                groupId = env.soknadConsumerGroup,
                valueDeserializer = StringDeserializer::class
        )
        launchListeners(env, applicationState, consumerProperties, database)
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })

    applicationState.initialized = true
}

@KtorExperimentalAPI
fun CoroutineScope.launchListeners(
        env: Environment,
        applicationState: ApplicationState,
        consumerProperties: Properties,
        database: Database
) {
    try {
        val listeners = (1..env.applicationThreads).map {
            launch {
                val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                kafkaconsumer.subscribe(listOf(env.soknadTopic))

                while (applicationState.running) {
                    kafkaconsumer.poll(Duration.ofMillis(0)).forEach {consumerRecord ->
                        val message : JsonNode = objectMapper.readTree(consumerRecord.value())
                        val headers = consumerRecord.headers().toString()
                        consumerRecord.headers().toString()
                        val compositKey: String = message.get("id").textValue() + "|" +
                                message.get("status").textValue() + "|" +
                                (message.get("sendtNav")?.textValue() ?: "null") + "|" +
                                (message.get("sendtArbeidsgiver")?.textValue() ?: "null")
                        val soknadRecord = SoknadRecord(
                                compositKey,
                                message.get("id").textValue(),
                                message
                        )
                        database.connection.lagreRawSoknad(message, headers)
                        if (database.connection.erSoknadLagret(soknadRecord)){
                            log.error("Mulig duplikat - søknad er allerede lagret (pk: ${compositKey})")
                        } else {
                            database.connection.lagreSoknad(soknadRecord)
                            log.info("Søknad lagret")
                        }
                    }
                    delay(100)
                }
            }
        }.toList()

        applicationState.initialized = true
        runBlocking { listeners.forEach { it.join() } }
    } finally {
        applicationState.running = false
    }
}

fun Application.initRouting(applicationState: ApplicationState) {
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

fun Application.dataRouting(database: DatabaseInterface) {
    routing { registerSoknadDataApi(database) }
}

