package no.nav.syfo.persistering

import com.fasterxml.jackson.databind.JsonNode

data class SoknadRecord(
        val soknadId: String,
        val topicOffset: Int,
        val soknad: JsonNode
)


fun toPGObject(json : JsonNode) = org.postgresql.util.PGobject().also {
    it.type = "jsonb"
    it.value = no.nav.syfo.objectMapper.writeValueAsString(json)
}

