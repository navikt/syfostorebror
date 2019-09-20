package no.nav.syfo.api

import com.fasterxml.jackson.databind.JsonNode
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
import no.nav.syfo.service.sykmelding.SykmeldingRecord
import no.nav.syfo.service.sykmelding.persistering.lagreSykmelding
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DataSpek : Spek({

    val testDatabase = TestDB()

    val engine = TestApplicationEngine()
    engine.start(wait = false)
    engine.application.apply {
        install(ContentNegotiation) {
            jackson {
            }
        }
        routing {
            registerSoknadDataApi(testDatabase)
            registerSykmeldingDataApi(testDatabase)
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
            with(engine.handleRequest(HttpMethod.Get, "/v1/soknad/data") {
                addHeader("fom", "2019-08-01T00:00:00.000")
                addHeader("tom", "2019-08-03T00:00:00.000")
            }) {
                response.status()?.shouldEqual(HttpStatusCode.OK)
                val soknaddata = objectMapper.readTree(response.content!!)
                soknaddata[0].get("antall").intValue() shouldEqual 1
            }
        }
    }

    describe("Endepunkt for sykmeldinger gitt lege") {
        val message: JsonNode = objectMapper.readTree(File("src/test/resources/sykmelding.json").readText())
        val sykmeldingRecord = SykmeldingRecord(
                message.get("sykmelding").get("id").textValue(),
                message
        )
        it("Finner sykmeldingen gitt riktig periode") {
            testDatabase.connection.lagreSykmelding(sykmeldingRecord)
            with(engine.handleRequest(HttpMethod.Get, "/v1/sykmeldinger/fra_lege") {
                addHeader("legefnr", "02020254321")
            }) {
                response.status()?.shouldEqual(HttpStatusCode.OK)
                val sykmeldingdata = objectMapper.readTree(response.content!!)
                sykmeldingdata[0].get("id").textValue() shouldEqual message.get("sykmelding").get("id").textValue()
            }
        }
    }
})
