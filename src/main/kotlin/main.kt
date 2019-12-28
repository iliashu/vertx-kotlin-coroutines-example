package com.example.starter

import io.vertx.core.Vertx
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

private val logger = LoggerFactory.getLogger("main")

fun main() {
    val vertx = Vertx.vertx()

    val router: Router = createRouter(vertx)

    logger.info("Starting Vert.x")

    vertx.createHttpServer()
            .requestHandler(router)
            .listen(8080)

    logger.info("Shutting down")
}

fun createRouter(vertx: Vertx): Router {
    val routerLogger = LoggerFactory.getLogger("router")

    return Router.router(vertx).apply {
        get("/").coroutineHandler {
            it.response().end("Hello World!")
        }

        errorHandler(500) {
            routerLogger.error("Error during request handling", it.failure())
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
