package net.example.vertx.kotlin

import domain.models.Account
import domain.services.AccountNotFoundException
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.example.vertx.kotlin.domain.services.AccountService
import java.math.BigDecimal

fun createRouter(vertx: Vertx, accountService: AccountService): Router {
    val logger = LoggerFactory.getLogger("router")
    return Router.router(vertx).apply {
        post("/accounts").coroutineHandler {
            val account = accountService.createAccount()
            it.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(encodeAccount(account))
        }

        get("/accounts/:accountId").coroutineHandler {
            val accountId = it.pathParam("accountId").toLong()
            val account = accountService.getAccountById(accountId)
            if (account != null) {
                it.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(encodeAccount(account))
            } else {
                it.response().setStatusCode(404).end()
            }
        }

        post("/accounts/:accountId/deposits").handler(BodyHandler.create()).coroutineHandler {
            val accountId = it.pathParam("accountId").toLong()
            val amountStr = it.bodyAsJson.get<String>("amount")
            val amount = BigDecimal(amountStr)

            val deposit = accountService.deposit(accountId, amount)
            it.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(Json.obj(
                    "amount" to deposit.amount.toPlainString()
                ).toBuffer())
        }

        post("/accounts/:accountId/transfers").handler(BodyHandler.create()).coroutineHandler {
            val sourceAccountId = it.pathParam("accountId").toLong()
            val amountStr = it.bodyAsJson.get<String?>("amount")
            val amount = BigDecimal(amountStr)
            val destinationAccountId = it.bodyAsJson.get<Long>("destinationAccountId")

            try {
                val deposit = accountService.transfer(sourceAccountId, destinationAccountId, amount)
                it.response()
                    .setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(Json.obj(
                        "destinationAccountId" to destinationAccountId,
                        "amount" to deposit.amount.toPlainString()
                    ).toBuffer())
            } catch (e: AccountNotFoundException) {
                it.response().setStatusCode(400).end()
            }
        }

        errorHandler(500) {
            logger.error("Error during request handling", it.failure())
            it.response().setStatusCode(500).end()
        }
    }
}

fun encodeAccount(account: Account): Buffer = Json.obj(
    "id" to account.id,
    "balance" to account.balance.toPlainString()
).toBuffer()

private fun Route.coroutineHandler(fn: suspend (RoutingContext) -> Unit) {
    handler { ctx ->
        GlobalScope.launch(ctx.vertx().dispatcher()) {
            try {
                fn(ctx)
            } catch (e: Exception) {
                ctx.fail(e)
            }
        }
    }
}
