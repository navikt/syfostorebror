package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
        val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
        val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
        val smTopics: List<String> = getEnvVar("KAFKA_SM_TOPICS",
                "privat-syfo-smpapir-automatiskBehandling," +
                        "privat-syfo-sm2013-automatiskBehandling," +
                        "privat-syfo-sm2013-manuellBehandling," +
                        "privat-syfo-smpapir-manuellBehandling," +
                        "privat-syfo-sm2013-avvistBehandling").split(","),
        val soknadTopic: String = getEnvVar("KAFKA_SOKNAD_TOPIC", "syfo-soknad-v2"),
        val consumerGroupId: String = getEnvVar("KAFKA_SOKNAD_CONSUMER_GROUP", "syfostorebror-consumer"),
        override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
        val databaseName: String = getEnvVar("DATABASE_NAME", "syfostorebror"),
        val syfostorebrorDBURL: String = getEnvVar("SYFOSTOREBROR_DB_URL"),
        val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
        val resetStreamOnly : Boolean = getEnvVar("RESET_STREAM_ONLY", "false") == "true",
        val resetStreams : List<String> = getEnvVar("RESET_STREAMS", "").split(","),
        val vaultPath: String = "/var/run/secrets/nais.io/vault/credentials.json",
        val jwtIssuer: String = getEnvVar("JWT_ISSUER", ""),
        val jwkKeysUrl: String = getEnvVar("JWKKEYS_URL", "https://login.microsoftonline.com/common/discovery/keys"),
        val clientId: String = getEnvVar("CLIENT_ID", "")
        ) : KafkaConfig

data class VaultSecrets(
        val serviceuserUsername: String,
        val serviceuserPassword: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

