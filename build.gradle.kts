import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0-SNAPSHOT"

val coroutinesVersion = "1.2.1"
val kluentVersion = "1.39"
val ktorVersion = "1.2.1"
val logbackVersion = "1.2.3"
val prometheusVersion = "0.5.0"
val spekVersion = "2.0.4"
val kafkaVersion = "2.0.0"
val micrometerVersion = "1.1.4"
val kotlinxSerializationVersion = "0.9.0"
val logstashLogbackEncoderVersion = "5.2"
val kafkaEmbeddedVersion = "2.1.1"
val logstash_encoder_version = "5.1"
val kafka_version = "2.0.0"
val jackson_version = "2.9.7"
val smCommonVersion = "1.0.22"
val postgresVersion = "42.2.5"
val h2Version = "1.4.197"
val flywayVersion = "5.2.4"
val hikariVersion = "3.3.0"
val vaultJavaDriveVersion = "3.1.0"
val opentableVersion = "0.13.1"

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.31"
    id("com.diffplug.gradle.spotless") version "3.14.0"
    id("com.github.johnrengelman.shadow") version "4.0.4"
}



repositories {
    maven (url= "https://kotlin.bintray.com/kotlinx")
    maven (url= "https://dl.bintray.com/kotlin/ktor")
    maven (url= "https://dl.bintray.com/spekframework/spek-dev")
    maven (url= "http://packages.confluent.io/maven/")
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation ("io.ktor:ktor-metrics-micrometer:$ktorVersion")
    implementation ("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")
    implementation ("io.ktor:ktor-server-netty:$ktorVersion")
    implementation ("io.ktor:ktor-client-apache:$ktorVersion")
    implementation ("io.ktor:ktor-client-logging:$ktorVersion")
    implementation ("io.ktor:ktor-client-logging-jvm:$ktorVersion")

    implementation ("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version")
    implementation ("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jackson_version")

    implementation ("org.apache.kafka:kafka_2.12:$kafkaVersion")

    implementation ("ch.qos.logback:logback-classic:$logbackVersion")
    implementation ("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")

    // Kafka
    implementation ("no.nav.syfo.sm:syfosm-common-kafka:$smCommonVersion")

    // Database
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.h2database:h2:$h2Version")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("com.bettercloud:vault-java-driver:$vaultJavaDriveVersion")

    compile ("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$kotlinxSerializationVersion")
    compile ("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$kotlinxSerializationVersion")

    testImplementation ("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation ("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation ("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation ("no.nav:kafka-embedded-env:$kafkaEmbeddedVersion")
    testImplementation ("com.opentable.components:otj-pg-embedded:$opentableVersion")


    testRuntimeOnly ("org.spekframework.spek2:spek-runtime-jvm:$spekVersion")
    testRuntimeOnly ("org.spekframework.spek2:spek-runner-junit5:$spekVersion")

    api ("io.ktor:ktor-client-mock:$ktorVersion")
    api ("io.ktor:ktor-client-mock-jvm:$ktorVersion")


}


tasks {
    "run"(JavaExec::class) {
        environment("LC_ALL","en_US.UTF-8")
        environment("LC_CTYPE", "en_US.UTF-8")
    }
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
