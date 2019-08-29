package no.nav.syfo.aksessering.db

import io.ktor.util.InternalAPI
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.SoknadRecord
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime

@InternalAPI
fun DatabaseInterface.hentSoknaderFraId(soknadid: String): List<SoknadRecord> =
        connection.use { connection ->
            connection.prepareStatement(
                    """
                        SELECT composit_key, soknad_id, soknad 
                        FROM soknader
                        WHERE soknad_id=?;
                    """.trimIndent()
            ).use {
                it.setString(1,soknadid)
                it.executeQuery().toList{ toSoknadRecord() }
            }
        }

@InternalAPI
fun DatabaseInterface.hentAntallRawSoknader(): Int =
        connection.use { connection ->
            connection.prepareStatement(
                    """
                        SELECT COUNT(*) antall FROM soknader_raw;
                    """.trimIndent()
            ).use {
                // Refactor opportunity: må man via en liste når man vet at spørringen alltid returnerer kun en rad?
                it.executeQuery().toList{getInt("antall")}.first()

            }
        }

fun DatabaseInterface.hentSoknadsData(fom: LocalDateTime, tom: LocalDateTime): List<SoknadData> =
        connection.use { connection ->
            connection.prepareStatement(
                    """
                        SELECT 1 as antall
                        FROM soknader
                        WHERE soknad->>'status' = 'SENDT'
                        AND soknad->>'opprettet' >= ?
                        AND soknad->>'opprettet' < ?
                    """.trimIndent()
            ).use {
                it.setTimestamp(1, Timestamp.valueOf(fom))
                it.setTimestamp(2, Timestamp.valueOf(tom))
                it.executeQuery().toList { toSoknadData() }
            }
        }

@InternalAPI
fun ResultSet.toSoknadRecord(): SoknadRecord =
        SoknadRecord(
                compositKey = getString("composit_key"),
                soknadId = getString("soknad_id"),
                soknad = objectMapper.readTree(getString("soknad"))
        )

fun ResultSet.toSoknadData(): SoknadData =
        SoknadData(
            antall = getInt("antall")
        )
