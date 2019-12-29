package com.example.starter

import com.example.starter.domain.services.AccountService
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun createRouter(vertx: Vertx, dbClient: JDBCClient): Router {
    val logger = LoggerFactory.getLogger("router")
    val accountService = AccountService(dbClient)
    return Router.router(vertx).apply {
        post("/accounts").coroutineHandler {
            val account = accountService.createAccount()
            it.response()
                    .setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(Json.encode(account))
        }

        get("/accounts/:accountId").coroutineHandler {
            val accountId = it.pathParam("accountId").toLong()
            val account = accountService.getAccount(accountId)
            it.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(Json.encode(account))
        }

        errorHandler(500) {
            logger.error("Error during request handling", it.failure())
        }
    }
}


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