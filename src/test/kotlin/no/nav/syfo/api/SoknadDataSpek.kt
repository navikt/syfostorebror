package no.nav.syfo.api

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.every
import io.mockk.mockk
import io.netty.handler.codec.http.HttpHeaders.addHeader
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.aksessering.db.hentSoknadsData
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.SoknadRecord
import no.nav.syfo.persistering.lagreSoknad
import no.nav.syfo.testutil.TestDB
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.File
import java.util.concurrent.TimeUnit


object SoknadDataSpek : Spek( {

//    val envMock = mockk<Environment>()
//    val credMock = mockk<VaultCredentialService>()
//    val database = Database(envMock, credMock)
//
//    every { database.hentSoknadsData(any(), any()) } returns
    val testDatabase = TestDB()
    val credentials = VaultSecrets("", "")
    val env = Environment(
            applicationPort = 0,
            applicationThreads = 1,
            kafkaBootstrapServers = "",
            mountPathVault = "vault.adeo.no",
            soknadTopic = "",
            syfostorebrorDBURL = "",
            databaseName = "",
            soknadConsumerGroup = "spek.integration-consumer"
    )


    val engine = TestApplicationEngine()
    engine.start(wait = false)
    engine.application.apply {
        routing {
            registerSoknadDataApi(testDatabase)
        }

    }

    afterGroup {
        engine.stop(0, 0, TimeUnit.SECONDS)
    }

    describe ("Endepunkt for søknadsdata") {
        val message : String = File("src/test/resources/arbeidstakersoknad.json").readText()
        val soknadRecord = SoknadRecord(
                "00000000-0000-0000-0000-000000000001|SENDT|null|2019-08-02T15:02:33.123",
                "00000000-0000-0000-0000-000000000001",
                objectMapper.readTree(message)
        )

        it ("Finner søknaden gitt riktig periode"){
            testDatabase.connection.lagreSoknad(soknadRecord)
            with(engine.handleRequest(HttpMethod.Get, "/soknad_data"){
                addHeader("tom", "2019-08-01T00:00:00.000")
                addHeader("fom","2019-08-03T00:00:00.000")
            }) {
                response.status()?.shouldEqual(HttpStatusCode.OK)
            }

        }




    }

})