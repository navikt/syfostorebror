package no.nav.syfo.kafka

import java.time.Duration
import java.util.Properties
import no.nav.syfo.VaultSecrets
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.config.SaslConfigs
import org.slf4j.LoggerFactory

class StreamResetter(private val kafkaBootstrapServers: String, private val topic: String, private val consumerGroupId: String, private val vaultSecrets: VaultSecrets) {

    private val log = LoggerFactory.getLogger("no.nav.syfo.kafka")

    fun run() {
        val prop = Properties()
        prop.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers)
        prop.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer"
        )
        prop.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer"
        )
        prop.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId)
        prop.put(ConsumerConfig.CLIENT_ID_CONFIG, "simple")
        prop.put("enable.auto.commit", "false")
        prop.put("security.protocol", "SASL_SSL")
        prop.put(SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${vaultSecrets.kafkaUsername}\" password=\"${vaultSecrets.kafkaPassword}\";")
        prop.put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        prop.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

        log.info("Creating consumer")
        val consumer = KafkaConsumer<String, String>(prop)
        log.info("Subscribing to topic $topic")
        consumer.subscribe(listOf(topic))

        // Need to call poll() in order for consumer to join group
        try {
            log.info("polling...")
            consumer.poll(Duration.ofSeconds(7))
        } catch (e: Exception) {
            log.warn("exception on poll ", e)
        }

        // Reset offset to beginning (not necessarily 0), depending on topic compaction
        log.info("seekToBeginning...")
        consumer.seekToBeginning(consumer.assignment())

        // seekToBeginning is lazily evaluated, invoked on poll() or position()
        log.info("checking offsets...")
        for (partition in consumer.assignment()) {
            var offset = consumer.position(partition)
            log.info("partition: $partition, offset: $offset")
        }

        // Commit offsets and gtfo
        log.info("calling commitSync...")
        val offsets = consumer.assignment().associateBy({ it }, { OffsetAndMetadata(0) })
        consumer.commitSync(offsets)
        log.info("closing consumer...")
        consumer.close()
        log.info("Done")
    }
}
