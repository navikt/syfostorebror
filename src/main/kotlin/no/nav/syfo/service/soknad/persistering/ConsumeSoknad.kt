package no.nav.syfo.service.soknad.persistering

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.delay
import no.nav.syfo.ApplicationState
import no.nav.syfo.db.Database
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.service.soknad.SoknadRecord
import no.nav.syfo.service.soknad.soknadCompositKey
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration


suspend fun blockingApplicationLogicSoknad(
        applicationState: ApplicationState,
        kafkaConsumer: KafkaConsumer<String, String>,
        database: Database
){
    while (applicationState.running) {
        kafkaConsumer.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            val message : JsonNode = objectMapper.readTree(consumerRecord.value())
            val headers = consumerRecord.headers().toString()
            consumerRecord.headers().toString()
            val compositKey = soknadCompositKey(message)
            val soknadRecord = SoknadRecord(
                    compositKey,
                    message.get("id").textValue(),
                    message
            )
            database.connection.lagreRawSoknad(message, headers)
            if (database.connection.erSoknadLagret(soknadRecord)){
                log.error("Mulig duplikat - søknad er allerede lagret (pk: ${compositKey})")
            } else {
                database.connection.lagreSoknad(soknadRecord)
                log.info("Søknad lagret")
            }
        }
        delay(100)
    }
}

