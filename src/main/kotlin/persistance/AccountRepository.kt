package net.example.vertx.kotlin.persistance

import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.sql.SQLOperations
import io.vertx.ext.sql.TransactionIsolation

interface AccountRepository : AccountOperations {
    suspend fun <T> transaction(
        operationName: String,
        isolationLevel: TransactionIsolation = TransactionIsolation.READ_COMMITTED,
        block: suspend (operations: AccountOperations) -> T
    ): T

    companion object {
        fun create(
            dbClient: JDBCClient,
            operationsFactory: (SQLOperations) -> AccountOperations = AccountOperations.Companion::create
        ): AccountRepository = AccountRepositoryImpl(dbClient, operationsFactory)
    }
}

