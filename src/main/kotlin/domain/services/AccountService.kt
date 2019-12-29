package domain.services

import domain.models.Account
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.sql.SQLOperations
import io.vertx.ext.sql.TransactionIsolation
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.ext.sql.*
import net.example.vertx.kotlin.domain.models.Deposit
import net.example.vertx.kotlin.domain.models.Transfer
import java.math.BigDecimal
import java.util.*

class AccountCreationException(accountId: Long) : RuntimeException("Unable to find just created account with id: $accountId")
class AccountNotFoundException(accountId: Long) : RuntimeException("Unable to find account with id $accountId")
class FailedToUpdateAccountBalanceException(accountId: Long, newBalance: BigDecimal) : RuntimeException(
        "Failed to update account balance. Account id: $accountId, new balance: ${newBalance.toPlainString()}"
)

class InsufficientBalanceException(accountId: Long, requiredAmount: BigDecimal, availableAmount: BigDecimal) : RuntimeException(
        "Insufficient balance to complete operation. Account id: $accountId, required amount: $requiredAmount, available: $availableAmount"
)

private val log = LoggerFactory.getLogger(AccountService::class.java)

class AccountService(private val dbClient: JDBCClient) {

    suspend fun createAccount(): Account {
        val updateResult = dbClient.updateAwait("INSERT INTO accounts (id, balance) VALUES DEFAULT, 0")
        val accountId = updateResult.keys.getLong(0)
        return getAccountById(accountId) ?: throw AccountCreationException(accountId)
    }

    suspend fun getAccountById(accountId: Long): Account? {
        return getAccountInternal(dbClient, accountId)
    }

    private suspend fun getAccountInternal(connection: SQLOperations, accountId: Long): Account? {

        // we cast balance to string to avoid potential precision loss due to vert.x way of dealing with doubles:
        // vert.x sql just converts decimals to doubles, and doubles are not suited to handle money (see README file)
        val result = connection.querySingleWithParamsAwait(
                "SELECT id, CAST(balance as VARCHAR(110)) FROM accounts WHERE id = ?",
                Json.array(
                        accountId
                ))

        return if (result != null) {
            val balanceStr = result.get<String>(1)
            Account(result.getLong(0), BigDecimal(balanceStr))
        } else {
            null
        }
    }

    suspend fun deposit(accountId: Long, amount: BigDecimal): Deposit = transaction("deposit") { connection ->

        val account = getAccountInternal(connection, accountId) ?: throw AccountNotFoundException(accountId)

        val newBalance = account.balance + amount
        updateAccountBalanceInternal(connection, account.id, newBalance)

        Deposit(accountId, amount)
    }

    private suspend fun updateAccountBalanceInternal(connection: SQLOperations, accountId: Long, newBalance: BigDecimal) {
        val update = connection.updateWithParamsAwait("UPDATE accounts SET balance = CAST(? as DECIMAL(100, 10)) WHERE id = ?", Json.array(
                newBalance.toPlainString(),
                accountId
        ))

        if (update.updated == 0) {
            throw FailedToUpdateAccountBalanceException(accountId, newBalance)
        }

        assert(update.updated == 1) { "More than one account updated" }
    }

    suspend fun transfer(sourceAccountId: Long, destinationAccountId: Long, amount: BigDecimal): Transfer = transaction("transfer") { connection ->
        val sourceAccount = getAccountInternal(connection, sourceAccountId)
                ?: throw AccountNotFoundException(sourceAccountId)
        val destinationAccount = getAccountInternal(connection, destinationAccountId)
                ?: throw AccountNotFoundException(sourceAccountId)

        if (sourceAccount.balance < amount) {
            throw InsufficientBalanceException(sourceAccount.id, amount, sourceAccount.balance)
        }

        val sourceAccountNewBalance = sourceAccount.balance - amount
        updateAccountBalanceInternal(connection, sourceAccount.id, sourceAccountNewBalance)

        val destinationAccountNewBalance = destinationAccount.balance + amount
        updateAccountBalanceInternal(connection, destinationAccount.id, destinationAccountNewBalance)

        Transfer(
                sourceAccountId = sourceAccountId,
                destinationAccountId = destinationAccountId,
                amount = amount
        )
    }


    private suspend fun <T> transaction(
            operationName: String,
            isolationLevel: TransactionIsolation = TransactionIsolation.READ_COMMITTED,
            block: suspend (sqlClient: SQLOperations) -> T
    ): T {
        // currently this transaction ID is only used for logging
        val transactionId = UUID.randomUUID()

        val connection = dbClient.getConnectionAwait()
        try {
            log.debug("Starting transaction. Transaction id: {0}, operation name: {1}", transactionId, operationName)
            connection.setAutoCommitAwait(false)
            connection.setTransactionIsolationAwait(isolationLevel)
            val result = block(connection)
            connection.commitAwait()

            return result
        } catch (e: Exception) {
            log.error("Failure during transaction {0}, operation name: {1}. Rolling back", e, transactionId, operationName)
            connection.rollbackAwait()
            log.error("Rollback successful. Transaction id: {0}, operation name: {1}", e, transactionId, operationName)
            throw e
        } finally {
            connection.closeAwait()
            log.debug("Transaction finished. Transaction id: {0}, operation name: {1}", transactionId, operationName)
        }
    }

}

