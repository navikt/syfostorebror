package no.nav.syfo.db

import com.fasterxml.jackson.databind.JsonNode

fun toPGObject(json: JsonNode) = org.postgresql.util.PGobject().also {
    it.type = "jsonb"
    it.value = no.nav.syfo.objectMapper.writeValueAsString(json)
}
