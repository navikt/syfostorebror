package no.nav.syfo

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.common.KafkaEnvironment
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.flywaydb.core.Flyway
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.File
import java.net.ServerSocket
import java.sql.Connection
import java.time.Duration
import java.util.*
import javax.sql.DataSource

object SoknadServiceSpek : Spek( {

    // Embedded Postgres
    val embeddedPostgres = EmbeddedPostgres.start()
    val testDatabase = TestDatabase(embeddedPostgres.postgresDatabase)

    //Flyway.configure().run {
    //    dataSource(embeddedPostgres.postgresDatabase).load().migrate()
    //}

    // Embedded Kafka
    fun getRandomPort() = ServerSocket(0).use{
        it.localPort
    }
    val topic = "aapen-test-topic"
    val embeddedKafkaEnvironment = KafkaEnvironment(
            autoStart = false,
            topicNames = listOf(topic)
    )
    val credentials = VaultSecrets("", "")
    val env = Environment(
            applicationPort = getRandomPort(),
            applicationThreads = 1,
            kafkaBootstrapServers = embeddedKafkaEnvironment.brokersURL,
            mountPathVault = "vault.adeo.no",
            soknadTopic = "topic1",
            syfostorebrorDBURL = "",
            databaseName = ""
    )
    fun Properties.overrideForTest(): Properties = apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    }
    val baseConfig = loadBaseConfig(env, credentials).overrideForTest()
    val producerProperties = baseConfig
            .toProducerConfig("spek.integration", valueSerializer = StringSerializer::class)
    val producer = KafkaProducer<String, String>(producerProperties)
    val consumerProperties = baseConfig
            .toConsumerConfig("spek.integration-consumer", valueDeserializer = StringDeserializer::class)
    val consumer = KafkaConsumer<String, String>(consumerProperties)

    consumer.subscribe(listOf(topic))

    beforeGroup { embeddedKafkaEnvironment.start() }



    describe("SoknadService") {
        val message = File("src/test/resources/arbeidstakersoknad.json").readText() // Hent fra json
        it("Can read a message from the kafka topic"){
            producer.send(ProducerRecord(topic,message))
            val messages = consumer.poll(Duration.ofMillis(5000)).toList()
            messages.size shouldEqual 1
            messages[0].value() shouldEqual message
        }
    }

    afterGroup {
        embeddedKafkaEnvironment.tearDown()
        embeddedPostgres.close()
    }

})

class TestDatabase(private val datasource: DataSource) : DatabaseInterface {
    override val connection: Connection
        get() = datasource.connection.apply { autoCommit = false }
}