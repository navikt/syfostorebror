package no.nav.syfo

import io.ktor.util.InternalAPI
import no.nav.common.KafkaEnvironment
import no.nav.syfo.aksessering.db.hentSoknad
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.persistering.SoknadRecord
import no.nav.syfo.persistering.erSoknadLagret
import no.nav.syfo.persistering.lagreSoknad
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldBe
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
import java.time.Duration
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

    beforeGroup {
        embeddedKafkaEnvironment.start()
    }


    describe("Kan lese melding fra kafka og skrive den til postgres") {
        val message : String = File("src/test/resources/arbeidstakersoknad.json").readText() // Hent fra json

        it ("skal være kun en melding på topic, og det er den vi sendte"){
            producer.send(ProducerRecord(topic,message))
            val messages = consumer.poll(Duration.ofMillis(5000)).toList()
            messages.size shouldEqual 1
            messages.forEach{
                it.value() shouldEqual message
            }
        }

        it ( "søknad skal kunne skrives til postgres og skal være samme som på kafka"){
            producer.send(ProducerRecord(topic,message))
            val messages = consumer.poll(Duration.ofMillis(5000)).toList()
            messages.forEach{
                val soknad = objectMapper.readTree(it.value())
                val soknadRecord = SoknadRecord(
                        soknad.get("id").textValue(),
                        soknad.get("status").textValue(),
                        soknad
                )
                testDatabase.connection.lagreSoknad(soknadRecord)
                val rowsFromPG = testDatabase.hentSoknad(soknad.get("id").textValue(), soknad.get("status").textValue())
                rowsFromPG[0].soknadId shouldEqual soknad.get("id").textValue() shouldEqual "00000000-0000-0000-0000-000000000000"
                rowsFromPG[0].soknadStatus shouldEqual soknad.get("status").textValue() shouldEqual "SENDT"
                rowsFromPG.size shouldEqual 1
            }
        }

        it ("lagret søknad skal finnes i databasen") {

                val soknadRecord = SoknadRecord(
                        "00000000-0000-0000-0000-000000000001",
                        "NY",
                        objectMapper.readTree("{}")
                )
                testDatabase.connection.lagreSoknad(soknadRecord)
                testDatabase.connection.erSoknadLagret(soknadRecord) shouldBe true

        }

    }

    afterGroup {
        embeddedKafkaEnvironment.tearDown()
        testDatabase.connection.dropData()
        testDatabase.stop()
    }

})