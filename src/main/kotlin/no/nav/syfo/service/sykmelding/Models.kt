package no.nav.syfo.service.sykmelding

import com.fasterxml.jackson.databind.JsonNode

data class SykmeldingData(
    val antall: Int?
)

data class SykmeldingRecord(
    val sykmeldingId: String,
    val sykmelding: JsonNode
)

data class SykmeldingId(
    val id: String?
)
