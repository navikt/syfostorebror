package no.nav.syfo.persistering

import java.sql.Connection
import java.sql.Timestamp


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
