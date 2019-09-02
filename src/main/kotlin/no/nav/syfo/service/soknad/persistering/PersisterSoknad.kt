package no.nav.syfo.service.soknad.persistering

import com.fasterxml.jackson.databind.JsonNode
import no.nav.syfo.service.soknad.SoknadRecord
import no.nav.syfo.service.soknad.toPGObject
import java.sql.Connection


fun Connection.lagreSoknad(soknad : SoknadRecord){
    use { connection ->
        connection.prepareStatement(
                """
                    INSERT INTO soknader (composit_key, soknad_id, soknad)
                    VALUES (?,?,to_jsonb(?));
                """.trimIndent()
        ).use {
            it.setString(1, soknad.compositKey)
            it.setString(2, soknad.soknadId)
            it.setObject(3, toPGObject(soknad.soknad))
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun Connection.lagreRawSoknad(soknad: JsonNode, headers: String){
    use { connection ->
        connection.prepareStatement(
                """
                    INSERT INTO soknader_raw (soknad, headers)
                    VALUES (to_jsonb(?), ?);
                """.trimIndent()
        ).use {
            it.setObject(1, toPGObject(soknad))
            it.setString(2, headers)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun Connection.slettSoknaderRawLog(){
    use { connection ->
        connection.prepareStatement(
                """
                    TRUNCATE soknader_raw
                """.trimIndent()
        ).use {
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun Connection.erSoknadLagret(soknad: SoknadRecord) =
    use {connection ->
        connection.prepareStatement(
                """
                    SELECT composit_key
                    FROM soknader
                    WHERE composit_key=?
                """.trimIndent()
        ).use {
            it.setString(1,soknad.compositKey)
            it.executeQuery().next()
        }
    }
