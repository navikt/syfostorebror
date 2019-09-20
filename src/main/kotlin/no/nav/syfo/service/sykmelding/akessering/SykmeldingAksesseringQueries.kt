package no.nav.syfo.service.sykmelding.akessering

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import no.nav.syfo.service.sykmelding.SykmeldingData
import no.nav.syfo.service.sykmelding.SykmeldingId
import no.nav.syfo.service.sykmelding.SykmeldingRecord

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

fun DatabaseInterface.hentSykmeldingData(fom: LocalDateTime, tom: LocalDateTime): List<SykmeldingData> =
        connection.use { connection ->
            connection.prepareStatement(
                    """
                        SELECT 1 as antall
                        FROM sykmeldinger
                        WHERE sykmelding->>'mottattDato' >= ?
                        AND sykmelding->>'mottattDato' < ?
                    """.trimIndent()
            ).use {
                it.setTimestamp(1, Timestamp.valueOf(fom))
                it.setTimestamp(2, Timestamp.valueOf(tom))
                it.executeQuery().toList { toSykmeldingData() }
            }
        }

fun ResultSet.toSykmeldingData(): SykmeldingData =
        SykmeldingData(
                antall = getInt("antall")
        )

fun DatabaseInterface.hentSykmeldingerFraLege(fnr: String): List<SykmeldingId> =
        connection.use { connection ->
            connection.prepareStatement(
                    """
                        SELECT sykmelding_id
                        FROM sykmeldinger
                        WHERE sykmelding->>'personNrLege' = ?
                    """.trimIndent()
            ).use {
                it.setString(1, fnr)
                it.executeQuery().toList { toSykmeldingId() }
            }
        }

fun ResultSet.toSykmeldingId(): SykmeldingId =
        SykmeldingId( id = getString("sykmelding_id"))