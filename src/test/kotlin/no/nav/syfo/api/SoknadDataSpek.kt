package no.nav.syfo.api

import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import java.io.File
import java.util.concurrent.TimeUnit
import no.nav.syfo.objectMapper
import no.nav.syfo.service.soknad.SoknadRecord
import no.nav.syfo.service.soknad.persistering.lagreSoknad
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldEqual
import org.slf4j.LoggerFactory
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SoknadDataSpek : Spek({

    val testDatabase = TestDB()
    val log = LoggerFactory.getLogger("no.nav.syfo.syfostorebror")

    val engine = TestApplicationEngine()
    engine.start(wait = false)
    engine.application.apply {
        install(ContentNegotiation) {
            jackson {
            }
        }
        routing {
            registerSoknadDataApi(testDatabase)
        }
    }

    afterGroup {
        engine.stop(0, 0, TimeUnit.SECONDS)
        testDatabase.connection.dropData()
        testDatabase.stop()
    }

    describe("Endepunkt for søknadsdata") {
        val message: String = File("src/test/resources/arbeidstakersoknad.json").readText()
        val soknadRecord = SoknadRecord(
                "00000000-0000-0000-0000-000000000001|SENDT|null|2019-08-02T15:02:33.123",
                "00000000-0000-0000-0000-000000000001",
                objectMapper.readTree(message)
        )

        it("Finner søknaden gitt riktig periode") {
            testDatabase.connection.lagreSoknad(soknadRecord)
            with(engine.handleRequest(HttpMethod.Get, "/soknad_data") {
                addHeader("tom", "2019-08-03T00:00:00.000")
                addHeader("fom", "2019-08-01T00:00:00.000")
            }) {
                response.status()?.shouldEqual(HttpStatusCode.OK)
                val soknaddata = objectMapper.readTree(response.content!!)
                soknaddata[0].get("antall").intValue() shouldEqual 1
            }
        }
    }
})
