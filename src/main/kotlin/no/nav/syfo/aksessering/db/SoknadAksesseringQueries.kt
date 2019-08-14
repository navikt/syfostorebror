package no.nav.syfo.aksessering.db

import io.ktor.util.InternalAPI
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.SoknadRecord
import java.sql.ResultSet


@InternalAPI
fun DatabaseInterface.hentSoknad(soknadid: String, status: String): List<SoknadRecord> =
        connection.use { connection ->
            connection.prepareStatement(
                    """
                        SELECT soknad_id, soknad_status, soknad 
                        FROM soknader
                        WHERE soknad_id=?
                        AND soknad_status=?;
                    """.trimIndent()
            ).use {
                it.setString(1,soknadid)
                it.setString(2,status)
                it.executeQuery().toList{ toSoknadRecord() }
            }
        }

@InternalAPI
fun ResultSet.toSoknadRecord(): SoknadRecord =
        SoknadRecord(
                soknadId = getString("soknad_id"),
                soknadStatus = getString("soknad_status"),
                soknad = objectMapper.readTree(getString("soknad"))
        )