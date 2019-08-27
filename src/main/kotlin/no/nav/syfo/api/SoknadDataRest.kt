package no.nav.syfo.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.util.InternalAPI
import no.nav.syfo.aksessering.db.hentSoknadsData
import no.nav.syfo.db.DatabaseInterface
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val log = LoggerFactory.getLogger("no.nav.syfo.aksessering.kafka")

fun Routing.registerSoknadDataApi(databaseInterface: DatabaseInterface) {
    get("/soknad_data") {

        val fom : LocalDateTime = LocalDateTime.from(
                DateTimeFormatter.ISO_DATE_TIME.parse(call.request.header("fom"))) ?: run {
            call.respond(BadRequest, "Mangler header 'fom' med fom-dato")
            log.warn("Motatt kall uten fom-dato")
            return@get
        }
        val tom : LocalDateTime = LocalDateTime.from(
                DateTimeFormatter.ISO_DATE_TIME.parse(call.request.header("tom"))) ?: run {
            call.respond(BadRequest, "Mangler header 'tom' med tom-dato")
            log.warn("Motatt kall uten tom-dato")
            return@get
        }

        when (val soknadsdata = databaseInterface.hentSoknadsData(fom, tom)){
            null -> call.respond(NotFound, "Ingen data for angitt periode")
            else -> call.respond(soknadsdata)
        }

    }
}
