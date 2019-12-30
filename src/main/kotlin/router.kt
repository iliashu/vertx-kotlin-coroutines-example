package net.example.vertx.kotlin

import io.vertx.core.Vertx
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.example.vertx.kotlin.controllers.AccountController

fun createRouter(vertx: Vertx, controller: AccountController): Router {
    val logger = LoggerFactory.getLogger("router")

    return Router.router(vertx).apply {
        post("/accounts").coroutineHandler(controller::createAccount)

        get("/accounts/:accountId").coroutineHandler(controller::getAccount)

        post("/accounts/:accountId/deposits")
            .handler(BodyHandler.create())
            .coroutineHandler(controller::deposit)

        post("/accounts/:accountId/transfers")
            .handler(BodyHandler.create())
            .coroutineHandler(controller::transfer)

        errorHandler(500) {
            logger.error("Error during request handling", it.failure())
            it.response().setStatusCode(500).end()
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
