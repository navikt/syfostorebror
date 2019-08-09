package no.nav.syfo.aksessering.db

import io.ktor.util.InternalAPI
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.SoknadRecord
import java.sql.ResultSet


@InternalAPI
fun DatabaseInterface.hentSoknad(soknadid: String, offset: Int): List<SoknadRecord> =
        connection.use { connection ->
            connection.prepareStatement(
                    """
                        SELECT soknad_id, topic_offset, soknad 
                        FROM soknader
                        WHERE soknad_id=?
                        AND topic_offset=?;
                    """.trimIndent()
            ).use {
                it.setString(1,soknadid)
                it.setInt(2,offset)
                it.executeQuery().toList{ toSoknadRecord() }
            }
        }

@InternalAPI
fun ResultSet.toSoknadRecord(): SoknadRecord =
        SoknadRecord(
                soknadId = getString("soknad_id"),
                topicOffset = getInt("topic_offset"),
                soknad = objectMapper.readTree(getString("soknad"))
        )