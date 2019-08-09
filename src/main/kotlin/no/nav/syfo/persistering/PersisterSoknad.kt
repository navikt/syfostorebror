package no.nav.syfo.persistering

import com.fasterxml.jackson.databind.JsonNode
import java.sql.Connection
import java.sql.Timestamp
import java.util.*


fun Connection.lagreSoknad(soknad : SoknadRecord){
    use { connection ->
        connection.prepareStatement(
                """
                    INSERT INTO soknader (soknad_id, topic_offset, soknad)
                    VALUES (?,?,to_jsonb(?));
                """.trimIndent()
        ).use {
            it.setString(1, soknad.soknadId)
            it.setInt(2, soknad.topicOffset)
            it.setObject(3, toPGObject(soknad.soknad))
            it.executeUpdate()
        }

        connection.commit()
    }
}