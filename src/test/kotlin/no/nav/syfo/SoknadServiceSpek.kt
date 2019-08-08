package no.nav.syfo

import io.ktor.util.InternalAPI
import no.nav.common.KafkaEnvironment
import no.nav.syfo.aksessering.db.hentSoknad
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.persistering.SoknadRecord
import no.nav.syfo.persistering.lagreSoknad
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.File
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@InternalAPI
object SoknadServiceSpek : Spek( {

    val testDatabase = TestDB()

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



    describe("Persister søknad fra Kafka til Postgres") {
        val message : String = File("src/test/resources/arbeidstakersoknad.json").readText() // Hent fra json
        val jsonmessage = objectMapper.readTree(message)

        it ("Les melding fra Kafka"){
            producer.send(ProducerRecord(topic,message))
            val messages = consumer.poll(Duration.ofMillis(5000))
            messages.toList().size shouldEqual 1
            messages.forEach {consumerRecord ->
                consumerRecord.value() shouldEqual message
            }
        }

        it ( "Skriv søknad til postgres"){
            val soknadRecord = SoknadRecord(
                    jsonmessage.get("id").textValue(),
                    LocalDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse(jsonmessage.get("opprettet").textValue())),
                    jsonmessage)
            testDatabase.connection.lagreSoknad(soknadRecord)
        }

        it ("Les søknad fra postgres, skal være samme som innsendt"){
            testDatabase.hentSoknad(jsonmessage.get("id").textValue())[0]
                    .soknadId shouldEqual jsonmessage.get("id").textValue()
        }

        it ("ID for record skal være samme som ID i JSON-objektet") {
            val soknadRecord = testDatabase.hentSoknad(jsonmessage.get("id").textValue())
            soknadRecord[0].soknadId shouldEqual soknadRecord[0].soknad.get("id").textValue()
        }

    }

    afterGroup {
        embeddedKafkaEnvironment.tearDown()
        testDatabase.connection.dropData()
        testDatabase.stop()
    }

})