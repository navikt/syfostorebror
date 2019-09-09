package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.jackson.jackson
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import no.nav.syfo.Environment
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.service.soknad.aksessering.hentSoknadsData
import no.nav.syfo.service.sykmelding.akessering.hentSykmeldingData
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("no.nav.syfo.kafka")

fun Route.registerSoknadDataApi(databaseInterface: DatabaseInterface) {
    get("/soknad_data") {

        val fom: LocalDateTime = LocalDateTime.from(
                DateTimeFormatter.ISO_DATE_TIME.parse(call.request.header("fom"))) ?: run {
            call.respond(BadRequest, "Mangler header 'fom' med fom-dato")
            log.warn("Motatt kall til /soknad_data uten fom-dato")
            return@get
        }
        val tom: LocalDateTime = LocalDateTime.from(
                DateTimeFormatter.ISO_DATE_TIME.parse(call.request.header("tom"))) ?: run {
            call.respond(BadRequest, "Mangler header 'tom' med tom-dato")
            log.warn("Motatt kall til /soknad_data uten tom-dato")
            return@get
        }

        when (val soknadsdata = databaseInterface.hentSoknadsData(fom, tom)) {
            null -> call.respond(NotFound, "Ingen data for angitt periode")
            else -> call.respond(soknadsdata)
        }
    }
}

fun Route.registerSykmeldingDataApi(databaseInterface: DatabaseInterface) {
    get("/sykmelding_data") {

        val fom: LocalDateTime = LocalDateTime.from(
                DateTimeFormatter.ISO_DATE_TIME.parse(call.request.header("fom"))) ?: run {
            call.respond(BadRequest, "Mangler header 'fom' med fom-dato")
            log.warn("Motatt kall til /sykmelding_data uten fom-dato")
            return@get
        }
        val tom: LocalDateTime = LocalDateTime.from(
                DateTimeFormatter.ISO_DATE_TIME.parse(call.request.header("tom"))) ?: run {
            call.respond(BadRequest, "Mangler header 'tom' med tom-dato")
            log.warn("Motatt kall til /sykmelding_data uten tom-dato")
            return@get
        }

        when (val sykmeldingdata = databaseInterface.hentSykmeldingData(fom, tom)) {
            null -> call.respond(NotFound, "Ingen data for angitt periode")
            else -> call.respond(sykmeldingdata)
        }
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
            realm = "syfohelsenettproxy"
            validate { credentials ->
                val appid: String = credentials.payload.getClaim("appid").asString()
                log.info("authorization attempt for $appid")
                if (appid in authorizedUsers && credentials.payload.audience.contains(environment.clientId)) {
                    log.info("authorization ok")
                    return@validate JWTPrincipal(credentials.payload)
                }
                log.info("authorization failed")
                return@validate null
            }
        }
    }
}

fun Application.setupContentNegotiation(database: DatabaseInterface) {
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
        }
    }
}

fun Route.enforceCallId(navcallid: String) {
    intercept(ApplicationCallPipeline.Setup) {
        if (call.request.header(navcallid).isNullOrBlank()) {
            call.respond(BadRequest, "Mangler header `$navcallid`")
            no.nav.syfo.log.warn("Mottatt kall som mangler callId: ${call.request.local.uri}")
            return@intercept finish()
        }
    }
}
