package no.nav.syfo.service

import io.ktor.util.InternalAPI
import java.io.File
import java.net.ServerSocket
import java.time.Duration
import java.util.Properties
import no.nav.common.KafkaEnvironment
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.objectMapper
import no.nav.syfo.service.sykmelding.SykmeldingRecord
import no.nav.syfo.service.sykmelding.akessering.hentSykmeldingFraId
import no.nav.syfo.service.sykmelding.persistering.lagreRawSykmelding
import no.nav.syfo.service.sykmelding.persistering.lagreSykmelding
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

@InternalAPI
object SykmeldingServiceSpek : Spek({

    val testDatabase = TestDB()

    // Embedded Kafka
    fun getRandomPort() = ServerSocket(0).use {
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
            smTopics = listOf(topic),
            syfostorebrorDBURL = "",
            databaseName = "",
            consumerGroupId = "spek.integration-consumer"
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
            .toConsumerConfig(env.consumerGroupId, valueDeserializer = StringDeserializer::class)
    val consumer = KafkaConsumer<String, String>(consumerProperties)

    consumer.subscribe(env.smTopics)

    beforeGroup {
        embeddedKafkaEnvironment.start()
    }

    describe("Kan lese melding fra kafka og skrive den til postgres") {
        val message: String = File("src/test/resources/sykmelding.json").readText() // Hent fra json

        it("skal være kun en melding på topic, og det er den vi sendte") {
            producer.send(ProducerRecord(env.smTopics[0], message))
            val messages = consumer.poll(Duration.ofMillis(5000)).toList()
            messages.size shouldEqual 1
            messages.forEach {
                it.value() shouldEqual message
            }
        }

        it("sykmelding skal kunne skrives til postgres og skal være samme som på kafka") {
            producer.send(ProducerRecord(env.soknadTopic, message))
            val messages = consumer.poll(Duration.ofMillis(5000)).toList()
            messages.forEach {
                val sykmelding = objectMapper.readTree(it.value())
                val sykmeldingRecord = SykmeldingRecord(
                        sykmelding.get("sykmelding").get("id").textValue(),
                        sykmelding
                )
                testDatabase.connection.lagreSykmelding(sykmeldingRecord)
                val rowsFromPG =
                        testDatabase.hentSykmeldingFraId(sykmelding.get("sykmelding").get("id").textValue())
                rowsFromPG[0].sykmeldingId shouldEqual sykmelding.get("sykmelding")
                        .get("id").textValue() shouldEqual "aaaaaaaa-ffff-eeee-bbbb-000000000000"
                rowsFromPG.size shouldEqual 1
            }
        }

        it("sykmelding kan lagres i loggtabell") {
            testDatabase.connection.lagreRawSykmelding(objectMapper.readTree(message), "", "")
        }
    }

    afterGroup {
        embeddedKafkaEnvironment.tearDown()
        testDatabase.connection.dropData()
        testDatabase.stop()
    }
})
