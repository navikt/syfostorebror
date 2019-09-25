package no.nav.syfo.api

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.jackson.jackson
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.service.soknad.aksessering.hentSoknadsData
import no.nav.syfo.service.sykmelding.akessering.hentSykmeldingerFraLege
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("no.nav.syfo.kafka")

fun Route.registerSoknadDataApi(databaseInterface: DatabaseInterface) {
    get("/v1/soknad/data") {

        val fom: LocalDateTime = LocalDateTime.from(
                DateTimeFormatter.ISO_DATE_TIME.parse(call.request.header("fom"))) ?: run {
            call.respond(BadRequest, "Mangler header 'fom' med fom-dato")
            log.warn("Motatt kall til /v1/soknad/data uten fom-dato")
            return@get
        }
        val tom: LocalDateTime = LocalDateTime.from(
                DateTimeFormatter.ISO_DATE_TIME.parse(call.request.header("tom"))) ?: run {
            call.respond(BadRequest, "Mangler header 'tom' med tom-dato")
            log.warn("Motatt kall til /v1/soknad/data uten tom-dato")
            return@get
        }

        when (val soknadsdata = databaseInterface.hentSoknadsData(fom, tom)) {
            null -> call.respond(NotFound, "Ingen data for angitt periode")
            else -> call.respond(soknadsdata)
        }
    }
}

fun Route.registerSykmeldingDataApi(databaseInterface: DatabaseInterface) {
    get("/v1/sykmeldinger/fra_lege") {
        val fnr: String = call.request.header("legefnr") ?: run {
            call.respond(BadRequest, "Mangler header 'legefnr' med leges f.nr")
            log.warn("Motatt kall til /v1/sykmeldinger/fra_lege uten leges f.nr.")
            return@get
        }
        when (val sykmeldinger = databaseInterface.hentSykmeldingerFraLege(fnr)) {
            null -> call.respond(NotFound, "Ingen data for angitt periode")
            else -> call.respond(sykmeldinger)
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
