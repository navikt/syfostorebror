package no.nav.syfo.service.sykmelding.persistering

import com.fasterxml.jackson.databind.JsonNode
import java.time.Duration
import kotlinx.coroutines.delay
import no.nav.syfo.ApplicationState
import no.nav.syfo.db.Database
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.service.sykmelding.SykmeldingRecord
import org.apache.kafka.clients.consumer.KafkaConsumer

suspend fun blockingApplicationLogicSykmelding(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    database: Database
) {
    while (applicationState.running) {
        kafkaConsumer.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            val message: JsonNode = objectMapper.readTree(consumerRecord.value())
            val headers = consumerRecord.headers().toString()
            consumerRecord.headers().toString()
            val sykmeldingId = message.get("sykmelding").get("id").textValue()
            val sykmeldingRecord = SykmeldingRecord(
                    sykmeldingId = sykmeldingId,
                    sykmelding = message
            )

            database.connection.lagreRawSykmelding(message, headers, consumerRecord.topic())
            if (database.connection.erSykmeldingLagret(sykmeldingRecord)) {
                log.error("Mulig duplikat - sykmelding er allerede lagret (id: ${sykmeldingId}")
            } else {
                database.connection.lagreSykmelding(sykmeldingRecord)
                log.info("Sykmelding lagret")
            }

        }
        delay(100)
    }
}