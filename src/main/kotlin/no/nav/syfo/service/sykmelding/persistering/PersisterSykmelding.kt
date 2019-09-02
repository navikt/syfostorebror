package no.nav.syfo.service.sykmelding.persistering

import com.fasterxml.jackson.databind.JsonNode
import no.nav.syfo.service.soknad.SoknadRecord
import no.nav.syfo.service.soknad.toPGObject
import java.sql.Connection

fun Connection.lagreRawSykmelding(sykmelding: JsonNode, headers: String){
    use { connection ->
        connection.prepareStatement(
                """
                    INSERT INTO sykmeldinger_raw (sykmelding, headers)
                    VALUES (to_jsonb(?), ?);
                """.trimIndent()
        ).use {
            it.setObject(1, toPGObject(sykmelding))
            it.setString(2, headers)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun Connection.slettSykmeldingerRawLog(){
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
