package net.example.vertx.kotlin.persistance

import domain.models.Account
import io.vertx.ext.sql.SQLOperations
import java.math.BigDecimal

/**
 * Abstraction of all possible operations with account.
 * This layer of abstraction is required for ability to test race conditions of database transactions in reliable way (
 * see AccountServiceTransactionIntegrationTest class)
 */
interface AccountOperations {
    suspend fun updateAccountBalance(accountId: Long, newBalance: BigDecimal)
    suspend fun getAccountById(accountId: Long): Account?
    suspend fun createAccount(): Long

    companion object {
        fun create(connection: SQLOperations) = AccountOperationsImpl(connection)
    }
}
