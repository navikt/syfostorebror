package no.nav.syfo.service.soknad.aksessering

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import no.nav.syfo.service.sykmelding.SykmeldingRecord
import java.sql.ResultSet

fun DatabaseInterface.hentSykmeldingFraId(sykmeldingid: String): List<SykmeldingRecord> =
        connection.use { connection ->
            connection.prepareStatement(
                    """
                        SELECT sykmelding_id, sykmelding 
                        FROM sykmeldinger
                        WHERE sykmelding_id=?;
                    """.trimIndent()
            ).use {
                it.setString(1, sykmeldingid)
                it.executeQuery().toList { toSykmeldingRecord() }
            }
        }

fun ResultSet.toSykmeldingRecord(): SykmeldingRecord =
        SykmeldingRecord(
                sykmeldingId = getString("sykmelding_id"),
                sykmelding = objectMapper.readTree(getString("sykmelding"))
        )