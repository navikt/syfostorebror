package no.nav.syfo.persistering

import com.fasterxml.jackson.databind.JsonNode
import java.sql.Connection
import java.sql.Timestamp
import java.util.*


fun Connection.lagreSoknad(soknad : SoknadRecord){
    use { connection ->
        connection.prepareStatement(
                """
                    INSERT INTO soknader (soknad_id, innsendt_dato, soknad)
                    VALUES (?,?,to_jsonb(?));
                """.trimIndent()
        ).use {
            it.setString(1, soknad.soknadId)
            it.setTimestamp(2, Timestamp.valueOf(soknad.innsendtDato))
            it.setObject(3, toPGObject(soknad.soknad))
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun Connection.erSoknadLagret(soknadId : String) {
    use { connection ->
        connection.prepareStatement(
                """
                   SELECT *
                   FROM soknader
                   WHERE id=?;
                """.trimIndent()
        ).use {
            it.setString(1,soknadId)
            it.executeQuery().next()
        }
    }
}

