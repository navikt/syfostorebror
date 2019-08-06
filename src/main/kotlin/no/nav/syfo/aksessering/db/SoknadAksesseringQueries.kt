package no.nav.syfo.aksessering.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.SoknadRecord
import java.sql.ResultSet

fun DatabaseInterface.hentSoknad(SoknadId: String): List<SoknadRecord> =
        connection.use { connection ->
            connection.prepareStatement(
                    """
                        SELECT soknad_id, innsendt_dato, soknad 
                        FROM soknader
                        WHERE soknad_id=?;
                    """.trimIndent()
            ).use {
                it.setString(1,SoknadId)
                it.executeQuery().toList{ toSoknadRecord() }
            }
        }

fun ResultSet.toSoknadRecord(): SoknadRecord =
        SoknadRecord(
                soknadId = getString("soknad_id"),
                innsendtDato = getTimestamp("innsendt_dato"),
                soknad = getString("soknad")
        )