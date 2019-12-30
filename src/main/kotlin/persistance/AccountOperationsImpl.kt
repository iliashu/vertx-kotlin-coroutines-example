package net.example.vertx.kotlin.persistance

import domain.models.Account
import io.vertx.ext.sql.SQLOperations
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.ext.sql.querySingleWithParamsAwait
import io.vertx.kotlin.ext.sql.updateAwait
import io.vertx.kotlin.ext.sql.updateWithParamsAwait
import java.math.BigDecimal

class AccountOperationsImpl(private val connection: SQLOperations) : AccountOperations {
    override suspend fun updateAccountBalance(accountId: Long, newBalance: BigDecimal) {
        val update = connection.updateWithParamsAwait("UPDATE accounts SET balance = CAST(? as DECIMAL(100, 10)) WHERE id = ?", Json.array(
            newBalance.toPlainString(),
            accountId
        ))

        assert(update.updated != 0) { "Account balance was not updated" }
        assert(update.updated == 1) { "More than one account was updated" }
    }

    override suspend fun getAccountById(accountId: Long): Account? {

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

    override suspend fun createAccount(): Long {
        val updateResult = connection.updateAwait("INSERT INTO accounts (id, balance) VALUES DEFAULT, 0")
        return updateResult.keys.getLong(0)
    }
}