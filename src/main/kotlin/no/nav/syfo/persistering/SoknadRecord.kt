package no.nav.syfo.persistering

import com.fasterxml.jackson.databind.JsonNode
import java.util.*

data class SoknadRecord(
        val soknadId: String,
        val innsendtDato: Date,
        val soknad: JsonNode
)


fun toPGObject(json : JsonNode) = org.postgresql.util.PGobject().also {
        it.type = "jsonb"
    it.value = no.nav.syfo.objectMapper.writeValueAsString(json)
}

