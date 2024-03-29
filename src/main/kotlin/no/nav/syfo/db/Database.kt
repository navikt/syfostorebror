package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.ResultSet
import no.nav.syfo.Environment
import org.flywaydb.core.Flyway

enum class Role {
    ADMIN, USER, READONLY;

    override fun toString() = name.toLowerCase()
}

class Database(private val env: Environment, private val vaultCredentialService: VaultCredentialService) : DatabaseInterface {
    private val dataSource: HikariDataSource

    override val connection: Connection
        get() = dataSource.connection

    init {
        runFlywayMigrations()

        val initialCredentials = vaultCredentialService.getNewCredentials(
            mountPath = env.mountPathVault,
            databaseName = env.databaseName,
            role = Role.USER
        )
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = env.syfostorebrorDBURL
            username = initialCredentials.username
            password = initialCredentials.password
            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        })

        vaultCredentialService.renewCredentialsTaskData = RenewCredentialsTaskData(
            dataSource = dataSource,
            mountPath = env.mountPathVault,
            databaseName = env.databaseName,
            role = Role.USER
        )
    }

    private fun runFlywayMigrations() = Flyway.configure().run {
        val credentials = vaultCredentialService.getNewCredentials(
            mountPath = env.mountPathVault,
            databaseName = env.databaseName,
            role = Role.ADMIN
        )
        dataSource(env.syfostorebrorDBURL, credentials.username, credentials.password)
        initSql("SET ROLE \"${env.databaseName}-${Role.ADMIN}\"") // required for assigning proper owners for the tables
        load().migrate()
    }
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}

interface DatabaseInterface {
    val connection: Connection
}
