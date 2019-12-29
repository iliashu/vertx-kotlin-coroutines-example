package net.example.vertx.kotlin.persistance

import domain.models.Account
import domain.services.FailedToUpdateAccountBalanceException
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.sql.SQLOperations
import io.vertx.ext.sql.TransactionIsolation
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.ext.sql.*
import java.lang.RuntimeException
import java.math.BigDecimal
import java.util.*

private val log = LoggerFactory.getLogger(AccountRepository::class.java)

class AccountRepository(
        private val dbClient: JDBCClient,
        private val operationsFactory: (SQLOperations) -> AccountOperations
): AccountOperations by operationsFactory(dbClient) {

    /**
     * Run specified {{block}} in a transaction.
     * Any exception during block's execution will result in transaction roll-back.
     *
     * Decision to group account operations into a transaction is a business decision in the sense that it influences
     * how concurrent operations affect each other, irrespective of the backing storage. Hence the inclusion on this
     * method into the repository.
     */
    suspend fun <T> transaction(
            operationName: String,
            isolationLevel: TransactionIsolation = TransactionIsolation.READ_COMMITTED,
            block: suspend (operations: AccountOperations) -> T
    ): T {
        // currently this transaction ID is only used for logging
        val transactionId = UUID.randomUUID()

        val connection = dbClient.getConnectionAwait()
        try {
            log.debug("Starting transaction. Transaction id: {0}, operation name: {1}", transactionId, operationName)
            connection.setAutoCommitAwait(false)
            connection.setTransactionIsolationAwait(isolationLevel)

            val wrappedTransaction = operationsFactory(connection)
            val result = block(wrappedTransaction)

            log.debug("Main operation of the transaction completed successfully, committing. Transaction id: {0}, operation name: {1}", transactionId, operationName)
            connection.commitAwait()

            return result
        } catch (e: Exception) {
            log.error("Failure during transaction {0}, operation name: {1}. Rolling back", e, transactionId, operationName)
            connection.rollbackAwait()
            log.error("Rollback successful. Transaction id: {0}, operation name: {1}", transactionId, operationName)
            throw e
        } finally {
            connection.closeAwait()
            log.debug("Transaction finished. Transaction id: {0}, operation name: {1}", transactionId, operationName)
        }
    }
}

/**
 * Abstraction of all possible operations with account.
 * This layer of abstraction is required for ability to test race conditions of database transactions in reliable way (
 * see AccountServiceTransactionIntegrationTest class)
 */
interface AccountOperations {
    suspend fun updateAccountBalanceInternal(accountId: Long, newBalance: BigDecimal)
    suspend fun getAccount(accountId: Long): Account?
    suspend fun createAccountInternal(): Long

    companion object {
        fun create(connection: SQLOperations): AccountOperations = object : AccountOperations {
            override suspend fun updateAccountBalanceInternal(accountId: Long, newBalance: BigDecimal) {
                val update = connection.updateWithParamsAwait("UPDATE accounts SET balance = CAST(? as DECIMAL(100, 10)) WHERE id = ?", Json.array(
                        newBalance.toPlainString(),
                        accountId
                ))

                if (update.updated == 0) {
                    throw FailedToUpdateAccountBalanceException(accountId, newBalance)
                }

                assert(update.updated == 1) { "More than one account updated" }
            }

            override suspend fun getAccount(accountId: Long): Account? {

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

            override suspend fun createAccountInternal(): Long {
                val updateResult = connection.updateAwait("INSERT INTO accounts (id, balance) VALUES DEFAULT, 0")
                return updateResult.keys.getLong(0)
            }
        }
    }
}
