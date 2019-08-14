package no.nav.syfo.persistering

import java.sql.Connection



fun Connection.lagreSoknad(soknad : SoknadRecord){
    use { connection ->
        connection.prepareStatement(
                """
                    INSERT INTO soknader (soknad_id, soknad_status, soknad)
                    VALUES (?,?,to_jsonb(?));
                """.trimIndent()
        ).use {
            it.setString(1, soknad.soknadId)
            it.setString(2, soknad.soknadStatus)
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
                    SELECT soknad_id, soknad_status
                    FROM soknader
                    WHERE soknad_id=?
                    AND soknad_status=?
                """.trimIndent()
        ).use {
            it.setString(1,soknad.soknadId)
            it.setString(2,soknad.soknadStatus)
            it.executeQuery().next()
        }
    }
