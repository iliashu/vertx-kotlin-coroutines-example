package net.example.vertx.kotlin.controllers

import domain.models.Account
import domain.services.AccountNotFoundException
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.core.json.obj
import net.example.vertx.kotlin.domain.services.AccountService
import java.math.BigDecimal

// ideally we would extract an interface to simplify testing, but in this case it's just not worth is.
// The only tests would currently use it is router test, and the router is very simple
class AccountController(private val accountService: AccountService) {

    suspend fun getAccount(context: RoutingContext) {
        val accountId = context.pathParam("accountId").toLong()
        val account = accountService.getAccountById(accountId)
        if (account != null) {
            context.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(encodeAccount(account))
        } else {
            context.response().setStatusCode(404).end()
        }
    }

    suspend fun createAccount(context: RoutingContext) {
        val account = accountService.createAccount()
        context.response()
            .setStatusCode(201)
            .putHeader("Content-Type", "application/json")
            .end(encodeAccount(account))
    }

    suspend fun deposit(context: RoutingContext) {
        val accountId = context.pathParam("accountId").toLong()
        val amountStr = context.bodyAsJson.get<String>("amount")
        val amount = BigDecimal(amountStr)

        val deposit = accountService.deposit(accountId, amount)

        context.response()
            .setStatusCode(201)
            .putHeader("Content-Type", "application/json")
            .end(Json.obj(
                "amount" to deposit.amount.toPlainString()
            ).toBuffer())
    }

    suspend fun transfer(context: RoutingContext) {
        val sourceAccountId = context.pathParam("accountId").toLong()
        val amountStr = context.bodyAsJson.get<String?>("amount")
        val amount = BigDecimal(amountStr)
        val destinationAccountId = context.bodyAsJson.get<Long>("destinationAccountId")

        try {
            val deposit = accountService.transfer(sourceAccountId, destinationAccountId, amount)
            context.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(Json.obj(
                    "destinationAccountId" to destinationAccountId,
                    "amount" to deposit.amount.toPlainString()
                ).toBuffer())
        } catch (e: AccountNotFoundException) {
            context.response().setStatusCode(400).end()
        }
    }
}

private fun encodeAccount(account: Account): Buffer = Json.obj(
    "id" to account.id,
    "balance" to account.balance.toPlainString()
).toBuffer()