package no.nav.syfo.syfostorebror

import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
        val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
        val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
        val soknadTopic: String = getEnvVar("KAFKA_SOKNAD_TOPIC", "syfo-soknad-v2"),
        override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
        val databaseName: String = getEnvVar("DATABASE_NAME", "syfostorebror"),
        val syfostorebrorDBURL: String = getEnvVar("SYFOSTOREBROR_DB_URL"),
        val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT")
) : no.nav.syfo.kafka.KafkaConfig

data class VaultSecrets(
        val serviceuserUsername: String,
        val serviceuserPassword: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
