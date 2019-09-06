package no.nav.syfo.service.sykmelding.persistering

import com.fasterxml.jackson.databind.JsonNode
import java.sql.Connection
import no.nav.syfo.db.toPGObject
import no.nav.syfo.service.sykmelding.SykmeldingRecord

fun Connection.lagreRawSykmelding(sykmelding: JsonNode, headers: String, topic: String) {
    use { connection ->
        connection.prepareStatement(
                """
                    INSERT INTO sykmeldinger_raw (sykmelding, headers, topic)
                    VALUES (to_jsonb(?), ?, ?);
                """.trimIndent()
        ).use {
            it.setObject(1, toPGObject(sykmelding))
            it.setString(2, headers)
            it.setString(3, topic)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun Connection.slettSykmeldingerRawLog() {
    use { connection ->
        connection.prepareStatement(
                """
                    TRUNCATE sykmeldinger_raw
                """.trimIndent()
        ).use {
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun Connection.lagreSykmelding(sykmelding: SykmeldingRecord) {
    use { connection ->
        connection.prepareStatement(
                """
                    INSERT INTO sykmeldinger (sykmelding_id, sykmelding)
                    VALUES (?, to_jsonb(?));
                """.trimIndent()
        ).use {
            it.setString(1, sykmelding.sykmeldingId)
            it.setObject(2, toPGObject(sykmelding.sykmelding))
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun Connection.erSykmeldingLagret(sykmelding: SykmeldingRecord) =
        use { connection ->
            connection.prepareStatement(
                    """
                    SELECT sykmelding_id
                    FROM sykmeldinger
                    WHERE sykmelding_id=?
                """.trimIndent()
            ).use {
                it.setString(1, sykmelding.sykmeldingId)
                it.executeQuery().next()
            }
        }
