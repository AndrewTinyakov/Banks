package test.developer.banks.managers

import org.bukkit.configuration.file.FileConfiguration
import test.developer.banks.Banks
import java.sql.*
import java.time.Instant
import java.util.*

class DatabaseManager(private val plugin: Banks) {

    var connection: Connection? = null
        private set

    init {
        connect()
        createTables()
    }

    private fun connect() {
        val config: FileConfiguration = plugin.config
        val dbType = config.getString("database.type")

        try {
            connection = if (dbType.equals("mysql", ignoreCase = true)) {
                val host = config.getString("database.mysql.host")
                val port = config.getInt("database.mysql.port")
                val database = config.getString("database.mysql.database")
                val username = config.getString("database.mysql.username")
                val password = config.getString("database.mysql.password")
                DriverManager.getConnection("jdbc:mysql://$host:$port/$database", username, password)
            } else {
                DriverManager.getConnection("jdbc:sqlite:${plugin.dataFolder}/banks.db")
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Could not connect to the database: ${e.message}")
        }
    }

    private fun createTables() {
        val statement: Statement? = connection?.createStatement()
        statement?.execute("CREATE TABLE IF NOT EXISTS bank_accounts " +
                "(" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "balance DOUBLE NOT NULL," +
                "created_at TIMESTAMP NOT NULL" +
                ")")
        statement?.close()
    }

    fun close() {
        try {
            connection?.takeIf { !it.isClosed }?.close()
        } catch (e: SQLException) {
            plugin.logger.severe("Could not close the database connection: ${e.message}")
        }
    }

    fun getBalance(uuid: UUID): Double? {
        val statement = connection?.prepareStatement("SELECT balance FROM bank_accounts WHERE uuid = ?")
        statement?.setString(1, uuid.toString())
        val resultSet = statement?.executeQuery()
        val balance = if (resultSet?.next() == true) {
            resultSet.getDouble("balance")
        } else {
            null
        }
        resultSet?.close()
        statement?.close()
        return balance
    }

    fun setBalance(uuid: UUID, balance: Double) {
        val statement =
            connection?.prepareStatement("UPDATE bank_accounts SET balance = ? WHERE uuid = ?")
        statement?.setDouble(1, balance)
        statement?.setString(2, uuid.toString())
        statement?.executeUpdate()
        statement?.close()
    }

    fun createAccount(uuid: UUID) {
        val statement = connection?.prepareStatement(
            "INSERT INTO bank_accounts (uuid, balance, created_at) VALUES (?, ?, ?)"
        )
        statement?.setString(1, uuid.toString())
        statement?.setDouble(2, 0.0)
        statement?.setTimestamp(
            3,
            Timestamp.from(Instant.now())
        )
        statement?.executeUpdate()
        statement?.close()
    }

    fun getCreatedAt(uuid: UUID): Timestamp? {
        val statement = connection?.prepareStatement("SELECT created_at FROM bank_accounts WHERE uuid = ?")
        statement?.setString(1, uuid.toString())
        val resultSet = statement?.executeQuery()
        val balance = if (resultSet?.next() == true) {
            resultSet.getTimestamp("created_at")
        } else {
            null
        }
        resultSet?.close()
        statement?.close()
        return balance
    }

}