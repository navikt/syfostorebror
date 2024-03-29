package no.nav.syfo.service.soknad

import com.fasterxml.jackson.databind.JsonNode

data class SoknadData(
    val antall: Int?
)

data class SoknadRecord(
    val compositKey: String,
    val soknadId: String,
    val soknad: JsonNode
)

fun soknadCompositKey(soknad: JsonNode): String {
    return soknad.get("id").textValue() + "|" +
            soknad.get("status").textValue() + "|" +
            (soknad.get("sendtNav")?.textValue() ?: "null") + "|" +
            (soknad.get("sendtArbeidsgiver")?.textValue() ?: "null")
}
