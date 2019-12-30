package net.example.vertx.kotlin.persistance

import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.sql.SQLOperations
import io.vertx.ext.sql.TransactionIsolation
import io.vertx.kotlin.ext.sql.closeAwait
import io.vertx.kotlin.ext.sql.commitAwait
import io.vertx.kotlin.ext.sql.getConnectionAwait
import io.vertx.kotlin.ext.sql.rollbackAwait
import io.vertx.kotlin.ext.sql.setAutoCommitAwait
import io.vertx.kotlin.ext.sql.setTransactionIsolationAwait
import java.util.UUID

private val log = LoggerFactory.getLogger(AccountRepositoryImpl::class.java)

class AccountRepositoryImpl(
    private val dbClient: JDBCClient,
    private val operationsFactory: (SQLOperations) -> AccountOperations
) : AccountRepository, AccountOperations by operationsFactory(dbClient) {

    /**
     * Run specified {{block}} in a transaction.
     * Any exception during block's execution will result in transaction roll-back.
     *
     * Decision to group account operations into a transaction is a business decision in the sense that it influences
     * how concurrent operations affect each other, irrespective of the backing storage. Hence the inclusion on this
     * method into the repository.
     */
    override suspend fun <T> transaction(
        operationName: String,
        isolationLevel: TransactionIsolation,
        block: suspend (operations: AccountOperations) -> T
    ): T {
        // currently this transaction ID is only used for logging
        val transactionId = UUID.randomUUID()

        val connection = dbClient.getConnectionAwait()
        try {
            log.debug("Starting transaction. Transaction id: {}, operation name: {}", transactionId, operationName)
            connection.setAutoCommitAwait(false)
            connection.setTransactionIsolationAwait(isolationLevel)

            val wrappedTransaction = operationsFactory(connection)
            val result = block(wrappedTransaction)

            log.debug("Main operation of the transaction completed successfully, committing. Transaction id: {}, operation name: {}", transactionId, operationName)
            connection.commitAwait()

            return result
        } catch (e: Exception) {
            log.error("Failure during transaction {}, operation name: {}. Rolling back", e, transactionId, operationName)
            connection.rollbackAwait()
            log.error("Rollback successful. Transaction id: {}, operation name: {}", transactionId, operationName)
            throw e
        } finally {
            connection.closeAwait()
            log.debug("Transaction finished. Transaction id: {}, operation name: {}", transactionId, operationName)
        }
    }
}