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
import java.math.BigDecimal

class AccountCreationException(accountId: Long) : RuntimeException("Unable to find just created account with id: $accountId")
class AccountNotFoundException(accountId: Long) : RuntimeException("Unable to find account with id $accountId")
class FailedToUpdateAccountBalanceException(accountId: Long, newBalance: BigDecimal) : RuntimeException(
        "Failed to update account balance. Account id: $accountId, new balance: ${newBalance.toPlainString()}"
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

    suspend fun deposit(accountId: Long, amount: BigDecimal): Deposit {
        val connection = dbClient.getConnectionAwait()
        try {
            connection.setAutoCommitAwait(false)
            connection.setTransactionIsolationAwait(TransactionIsolation.READ_COMMITTED)
            val account = getAccountInternal(connection, accountId) ?: throw AccountNotFoundException(accountId)

            val newBalance = account.balance + amount
            updateAccountBalanceInternal(connection, account.id, newBalance)

            connection.commitAwait()

            return Deposit(amount)
        } catch (e: Exception) {
            log.error("Error trying to deposit money to an account (account id: $accountId)", e)
            connection.rollbackAwait()
            throw e
        } finally {
            connection.closeAwait()
        }
    }

    suspend fun withdraw(accountId: Long, amount: BigDecimal): Deposit {
        val connection = dbClient.getConnectionAwait()
        try {
            connection.setAutoCommitAwait(false)
            connection.setTransactionIsolationAwait(TransactionIsolation.READ_COMMITTED)
            val account = getAccountInternal(connection, accountId) ?: throw AccountNotFoundException(accountId)

            val newBalance = account.balance + amount
            updateAccountBalanceInternal(connection, account.id, newBalance)

            connection.commitAwait()

            return Deposit(amount)
        } catch (e: Exception) {
            log.error("Error trying to deposit money to an account (account id: $accountId)", e)
            connection.rollbackAwait()
            throw e
        } finally {
            connection.closeAwait()
        }
    }

    private suspend fun updateAccountBalanceInternal(connection: SQLOperations, accountId: Long, newBalance: BigDecimal) {
        val update = connection.updateWithParamsAwait("UPDATE accounts SET balance = CAST(? as DECIMAL(100, 10)) WHERE id = ?", Json.array(
                newBalance.toPlainString(),
                accountId
        ))

        if (update.updated == 0) {
            throw FailedToUpdateAccountBalanceException(accountId, newBalance)
        }

        assert(update.updated == 1) {"More than one account updated"}
    }

}




