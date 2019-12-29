package com.example.starter.domain.services

import com.example.starter.domain.model.Account
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.ext.sql.querySingleWithParamsAwait
import io.vertx.kotlin.ext.sql.updateAwait

class AccountService(private val dbClient: JDBCClient) {
    suspend fun createAccount(): Account {
        val updateResult = dbClient.updateAwait("INSERT INTO accounts (id) VALUES DEFAULT")
        return Account(updateResult.keys.getLong(0))
    }

    suspend fun getAccount(accountId: Long): Account? {
        val result = dbClient.querySingleWithParamsAwait("SELECT id FROM accounts WHERE id = ?", Json.array(
                accountId
        ))
        return if (result != null) {
            Account(result.getLong(0))
        } else {
            null
        }
    }

}
