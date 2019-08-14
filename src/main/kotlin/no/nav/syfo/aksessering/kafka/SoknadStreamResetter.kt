package no.nav.syfo.aksessering.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.config.SaslConfigs
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import java.time.Duration
import java.util.*


class SoknadStreamResetter(val env: Environment, private val topic: String, private val consumerGroupId: String) {

    private val log = LoggerFactory.getLogger("no.nav.syfo.aksessering.kafka")
    private val vaultSecrets =
            objectMapper.readValue<VaultSecrets>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())

    fun run() {
        val prop = Properties()
        prop.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, env.kafkaBootstrapServers)
        prop.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer"
        )
        prop.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer"
        )
        prop.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        prop.put(ConsumerConfig.CLIENT_ID_CONFIG, "simple")
        prop.put("enable.auto.commit", "false");
        prop.put("security.protocol", "SASL_SSL")
        prop.put(SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${vaultSecrets.kafkaUsername}\" password=\"${vaultSecrets.kafkaPassword}\";")
        prop.put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        prop.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

        log.info("Creating consumer")
        val consumer = KafkaConsumer<String, String>(prop)
        log.info("Subscribing to topic ${topic}")
        consumer.subscribe(listOf(topic))

        try {
            log.info("polling...")
            consumer.poll(Duration.ofSeconds(10))
        } catch (e:Exception) {
            log.warn("exception on poll ", e)
        }

        log.info("seekToBeginning...")
        consumer.seekToBeginning(consumer.assignment())

        log.info("checking offsets...")
        for (partition in consumer.assignment()) {
            var offset = consumer.position(partition)
            log.info("partition: ${partition}, offset: ${offset}")
        }

        log.info("calling commitSync...")
        val offsets = consumer.assignment().associateBy({ it }, { OffsetAndMetadata(0) })
        consumer.commitSync(offsets)
        log.info("closing consumer...")
        consumer.close()
        log.info("Done")

    }
}