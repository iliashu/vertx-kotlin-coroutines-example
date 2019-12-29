package net.example.vertx.kotlin

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.web.Router
import io.vertx.kotlin.ext.sql.callAwait
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val logger = LoggerFactory.getLogger("main")

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

fun createDbClient(vertx: Vertx): JDBCClient {
    val config = JsonObject()
            .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
            .put("driver_class", "org.hsqldb.jdbcDriver")
            .put("max_pool_size", 30)

    return JDBCClient.createShared(vertx, config)
}

// in a real project, this would have been handled by some DB migration library (e.g. liquibase)
suspend fun initDB(dbClient: JDBCClient) {

    // This is a simplification of how the money would be stored in real world
    // Money precision and arithmetic depends on currency (e.g. rounding rules are different for different currencies)
    // Ideally, we should store money as a big integer with precision fixed on per-currency basis
    // But this would be an overkill for such a small example
    dbClient.callAwait("""CREATE TABLE accounts (
       id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
       balance DECIMAL(100, 10) NOT NULL
    )""")
}
