
import io.restassured.RestAssured
import io.vertx.core.Vertx
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.closeAwait
import io.vertx.kotlin.ext.sql.callAwait
import io.vertx.kotlin.ext.sql.closeAwait
import kotlinx.coroutines.runBlocking
import net.example.vertx.kotlin.createDbClient
import net.example.vertx.kotlin.createRouter
import net.example.vertx.kotlin.initDB
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.TimeUnit

abstract class IntegrationTestBase {

    private lateinit var testContext: VertxTestContext
    private lateinit var vertx: Vertx
    private lateinit var dbClient: JDBCClient

    @BeforeEach
    fun initVertx() {
        vertx = Vertx.vertx()
        dbClient = createDbClient(vertx)
        runBlocking { initDB(dbClient) }
        val testedRouter = createRouter(vertx, dbClient)

        testContext = VertxTestContext()
        val server = vertx.createHttpServer()
                .requestHandler(testedRouter)
                .listen(0, testContext.completing())

        testContext.awaitCompletion(5, TimeUnit.SECONDS)

        RestAssured.port = server.actualPort()
    }

    @AfterEach
    fun closeVertx() {

        if (testContext.failed()) {
            throw testContext.causeOfFailure()
        }

        runBlocking {
            dbClient.callAwait("SHUTDOWN")
            dbClient.closeAwait()
            vertx.closeAwait()
        }
    }
}