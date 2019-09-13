package no.nav.syfo.api

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import java.net.ServerSocket
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import no.nav.syfo.Environment
import no.nav.syfo.setupAuth
import no.nav.syfo.testutil.fakeJWTApi
import no.nav.syfo.testutil.generateJWT
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DataApiWithSecuritySpek : Spek({

    val randomPort = ServerSocket(0).use { it.localPort }
    val env = Environment(
            kafkaBootstrapServers = "",
            syfostorebrorDBURL = "",
            mountPathVault = "",
            jwkKeysUrl = "http://localhost:$randomPort/fake.jwt",
            jwtIssuer = "https://sts.issuer.net/myid",
            clientId = "syfostorebror-clientId"
    )

    describe("Hent søknad med authentication") {
        val fakeApi = fakeJWTApi(randomPort)
        val engine = TestApplicationEngine()
        engine.start(wait = false)
        engine.application.apply {
            install(ContentNegotiation) {
                jackson {
                }
            }
            setupAuth(env, listOf("consumerClientId"))
            routing {
                authenticate { get("/test") { call.respond(HttpStatusCode.OK) } }
            }
        }

        afterGroup {
            fakeApi.stop(0L, 0L, TimeUnit.SECONDS)
            engine.stop(0L, 0L, TimeUnit.SECONDS)
        }

        it("Uten token gir 401 Unauthorized") {
            with(engine.handleRequest(HttpMethod.Get, "/test")) {
                response.status()?.shouldEqual(HttpStatusCode.Unauthorized)
            }
        }

        it("Feil audience gir 401") {
            with(engine.handleRequest(HttpMethod.Get, "/test") {
                addHeader("Authorization", "Bearer ${generateJWT(audience = "some other audience")}")
            }) {
                response.status()?.shouldEqual(HttpStatusCode.Unauthorized)
            }
        }

        it("consumerId som ikke er i authorized users gir 401") {
            with(engine.handleRequest(HttpMethod.Get, "/test") {
                addHeader("Authorization", "Bearer ${generateJWT(consumerClientId = "some other app")}")
            }) {
                response.status()?.shouldEqual(HttpStatusCode.Unauthorized)
            }
        }

        it("Utgått token gir 401") {
            with(engine.handleRequest(HttpMethod.Get, "/test") {
                addHeader("Authorization",
                        "Bearer ${generateJWT(expiry = LocalDateTime.now().minusMinutes(5))}")
            }) {
                response.status()?.shouldEqual(HttpStatusCode.Unauthorized)
            }
        }

        it("Med gyldig token gir 200 OK") {
            val jwt = generateJWT()
            log.info("jwt er: $jwt")
            with(engine.handleRequest(HttpMethod.Get, "/test") {
                addHeader("Authorization", "Bearer $jwt")
            }) {
                response.status()?.shouldEqual(HttpStatusCode.OK)
            }
        }
    }
})
