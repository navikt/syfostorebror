package no.nav.syfo.service.sykmelding.persistering

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


suspend fun blockingApplicationLogicSykmelding(
        applicationState: ApplicationState,
        kafkaConsumer: KafkaConsumer<String, String>,
        database: Database
){
    while (applicationState.running) {
        kafkaConsumer.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            val message : JsonNode = objectMapper.readTree(consumerRecord.value())
            val headers = consumerRecord.headers().toString()
            consumerRecord.headers().toString()

            database.connection.lagreRawSykmelding(message, headers)
        }
        delay(100)
    }
}

