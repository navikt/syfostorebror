package no.nav.syfo.persistering

import com.fasterxml.jackson.databind.JsonNode
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

fun Connection.lagreRawSoknad(soknad: JsonNode, headers: JsonNode){
    use { connection ->
        connection.prepareStatement(
                """
                    INSERT INTO soknader_raw (soknad, headers)
                    VALUES (to_jsonb(?), to_jsonb(?));
                """.trimIndent()
        ).use {
            it.setObject(1, toPGObject(soknad))
            it.setObject(2, toPGObject(headers))
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun Connection.slettRawLog(){
    use { connection ->
        connection.prepareStatement(
                """
                    DELETE FROM soknader_raw
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
