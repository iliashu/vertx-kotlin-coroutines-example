package integration
import domain.services.AccountServiceImpl
import io.restassured.RestAssured
import io.vertx.core.Vertx
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.closeAwait
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.ext.sql.callAwait
import io.vertx.kotlin.ext.sql.closeAwait
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.example.vertx.kotlin.createDbClient
import net.example.vertx.kotlin.createRouter
import net.example.vertx.kotlin.initDB
import net.example.vertx.kotlin.persistance.AccountRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.Duration
import java.util.concurrent.TimeUnit

// In the real world, you should try to use real database for your integration tests
// Testcontainers library is a great tool that allows you to start a Docker container with DB from within the test
abstract class IntegrationTestBase {

    private lateinit var testContext: VertxTestContext

    private lateinit var vertx: Vertx

    protected lateinit var dbClient: JDBCClient
        private set

    @BeforeEach
    fun initVertx() {
        vertx = Vertx.vertx()
        dbClient = createDbClient(vertx)
        runBlocking { initDB(dbClient) }
        val accountService = AccountServiceImpl(AccountRepository.create(dbClient))
        val testedRouter = createRouter(vertx, accountService)

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

    fun vertxTestWithTimeout(timeout: Duration = Duration.ofSeconds(1), block: suspend () -> Unit) {
        runBlocking(vertx.dispatcher()) {
            withTimeout(timeout.toMillis()) {
                block()
            }
        }
    }
}