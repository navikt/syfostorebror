package no.nav.syfo

import io.ktor.util.InternalAPI
import no.nav.common.KafkaEnvironment
import no.nav.syfo.aksessering.db.hentAntallRawSoknader
import no.nav.syfo.aksessering.db.hentSoknadsData
import no.nav.syfo.aksessering.db.hentSoknaderFraId
import no.nav.syfo.aksessering.kafka.SoknadStreamResetter
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.persistering.*
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.File
import java.net.ServerSocket
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@InternalAPI
object SoknadServiceSpek : Spek( {

    val testDatabase = TestDB()
    val log = LoggerFactory.getLogger("no.nav.syfo.syfostorebror")

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
            soknadTopic = topic,
            syfostorebrorDBURL = "",
            databaseName = "",
            soknadConsumerGroup = "spek.integration-consumer"
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
            .toConsumerConfig(env.soknadConsumerGroup, valueDeserializer = StringDeserializer::class)
    val consumer = KafkaConsumer<String, String>(consumerProperties)

    consumer.subscribe(listOf(env.soknadTopic))

    beforeGroup {
        embeddedKafkaEnvironment.start()
    }


    describe("Kan lese melding fra kafka og skrive den til postgres") {
        val message : String = File("src/test/resources/arbeidstakersoknad.json").readText() // Hent fra json

        it ("skal være kun en melding på topic, og det er den vi sendte"){
            producer.send(ProducerRecord(env.soknadTopic,message))
            val messages = consumer.poll(Duration.ofMillis(5000)).toList()
            messages.size shouldEqual 1
            messages.forEach{
                it.value() shouldEqual message
            }
        }

        it ( "søknad skal kunne skrives til postgres og skal være samme som på kafka"){
            producer.send(ProducerRecord(env.soknadTopic,message))
            val messages = consumer.poll(Duration.ofMillis(5000)).toList()
            messages.forEach{
                val soknad = objectMapper.readTree(it.value())
                val soknadRecord = SoknadRecord(
            soknad.get("id").textValue() + "|" +
                        soknad.get("status").textValue() + "|" +
                        (soknad.get("sendtNav")?.textValue() ?: "null") + "|" +
                        (soknad.get("sendtArbeidsgiver")?.textValue() ?: "null"),
                        soknad.get("id").textValue(),
                        soknad
                )
                testDatabase.connection.lagreSoknad(soknadRecord)
                val rowsFromPG = testDatabase.hentSoknaderFraId(soknad.get("id").textValue())
                rowsFromPG[0].soknadId shouldEqual soknad.get("id").textValue() shouldEqual "00000000-0000-0000-0000-000000000000"
                rowsFromPG.size shouldEqual 1
            }
        }

        it ("lagret søknad skal finnes i databasen, og kun en gang") {

                val soknadRecord = SoknadRecord(
                        "00000000-0000-0000-0000-000000000001|SENDT|null|2019-08-02T15:02:33.123",
                        "00000000-0000-0000-0000-000000000001",
                        objectMapper.readTree("{}")
                )
                testDatabase.connection.lagreSoknad(soknadRecord)
                testDatabase.connection.erSoknadLagret(soknadRecord) shouldBe true
                testDatabase.hentSoknadsData(LocalDateTime.of(2019,8,2,0,0),
                        LocalDateTime.of(2019,8,3,0,0))[0].antall shouldEqual 1
                testDatabase.hentSoknadsData(LocalDateTime.of(2019,8,3,0,0),
                        LocalDateTime.of(2019,8,4,0,0)) shouldEqual emptyList()
        }

        it ("søknad kan lagres i loggtabell") {
            testDatabase.connection.lagreRawSoknad(objectMapper.readTree(message),"")
        }

    }

    describe ("Consumer group offset kan nullstilles og logtabell tømmes ved behov"){
        val message : String = File("src/test/resources/arbeidstakersoknad.json").readText()

        it ("consumer group offset kan nullstilles"){
            producer.send(ProducerRecord(env.soknadTopic, message))
            val soknadResetter = SoknadStreamResetter(env, env.soknadTopic, env.soknadConsumerGroup, credentials)
            soknadResetter.run()

            val resetConsumer = KafkaConsumer<String, String>(consumerProperties)
            resetConsumer.subscribe(listOf(env.soknadTopic))

            for (partition in resetConsumer.assignment()) {
                var offset = resetConsumer.position(partition)
                offset shouldEqual 0
            }

        }

        it ("loggtabell kan tømmes"){
            testDatabase.connection.lagreRawSoknad(objectMapper.readTree(message), "")
            testDatabase.connection.slettRawLog()
            testDatabase.hentAntallRawSoknader() shouldEqual 0
        }
    }


    afterGroup {
        embeddedKafkaEnvironment.tearDown()
        testDatabase.connection.dropData()
        testDatabase.stop()
    }

})