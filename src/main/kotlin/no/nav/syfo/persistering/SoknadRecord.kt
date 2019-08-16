package no.nav.syfo.persistering

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime

data class SoknadRecord(
        val compositKey: String,
        val soknadId: String,
        val soknad: JsonNode
)


fun toPGObject(json : JsonNode) = org.postgresql.util.PGobject().also {
    it.type = "jsonb"
    it.value = no.nav.syfo.objectMapper.writeValueAsString(json)
}
