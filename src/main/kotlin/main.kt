package com.example.starter

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.ext.sql.callAwait
import io.vertx.kotlin.ext.sql.getConnectionAwait
import io.vertx.kotlin.ext.sql.setAutoCommitAwait
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


private val logger = LoggerFactory.getLogger("main")

fun main() = runBlocking {
    val vertx = Vertx.vertx()

    val dbClient = createDbClient(vertx)

    initDB(dbClient)

    val router: Router = createRouter(vertx, dbClient)

    logger.info("Starting Vert.x")

    vertx.createHttpServer()
            .requestHandler(router)
            .listen(8080)

    logger.info("Shutting down")
}

fun createRouter(vertx: Vertx, dbClient: JDBCClient): Router {
    val routerLogger = LoggerFactory.getLogger("router")
    return Router.router(vertx).apply {
        get("/").coroutineHandler {
            dbClient.callAwait("SELECT 1 FROM test")
            it.response().end("Hello World!")
        }

        errorHandler(500) {
            routerLogger.error("Error during request handling", it.failure())
        }
    }
}

fun createDbClient(vertx: Vertx): JDBCClient {
    val config = JsonObject()
            .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
            .put("driver_class", "org.hsqldb.jdbcDriver")
            .put("max_pool_size", 30)

    return JDBCClient.createShared(vertx, config)
}

// in a real project, this would have been handled by some DB migration library (e.g. liquibase)
suspend fun initDB(dbClient: JDBCClient) {
    dbClient.callAwait("CREATE TABLE test (id int) ")
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
