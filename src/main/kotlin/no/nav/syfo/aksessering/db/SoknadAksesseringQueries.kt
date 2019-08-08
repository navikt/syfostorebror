package no.nav.syfo.aksessering.db

import io.ktor.util.InternalAPI
import io.ktor.util.toLocalDateTime
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.SoknadRecord
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@InternalAPI
fun DatabaseInterface.hentSoknad(soknadid: String): List<SoknadRecord> =
        connection.use { connection ->
            connection.prepareStatement(
                    """
                        SELECT soknad_id, innsendt_dato, soknad 
                        FROM soknader
                        WHERE soknad_id=?;
                    """.trimIndent()
            ).use {
                it.setString(1,soknadid)
                it.executeQuery().toList{ toSoknadRecord() }
            }
        }

@InternalAPI
fun ResultSet.toSoknadRecord(): SoknadRecord =
        SoknadRecord(
                soknadId = getString("soknad_id"),
//                innsendtDato = getObject("innsendt_dato").toLocalDateTime(),
                innsendtDato = LocalDateTime.from(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                        .parse(getString("innsendt_dato"))),
                soknad = objectMapper.readTree(getString("soknad"))
        )