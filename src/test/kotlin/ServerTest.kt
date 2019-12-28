import com.example.starter.createDbClient
import com.example.starter.createRouter
import com.example.starter.initDB
import io.restassured.RestAssured
import io.vertx.core.Vertx
import io.vertx.junit5.VertxTestContext
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class ServerTest {

    @Test
    fun test() = vertxTest { port ->
        RestAssured.`when`().get("http://localhost:$port")
                .then()
                .body(equalTo("Hello World!"))
    }

    private fun vertxTest(block: suspend (Int) -> Unit) {
        val testContext = VertxTestContext()

        val vertx = Vertx.vertx()

        val db = createDbClient(vertx)

        val testedRouter = createRouter(vertx, db)

        val server = vertx.createHttpServer()
                .requestHandler(testedRouter)
                .listen(0, testContext.completing())

        runBlocking {
            initDB(db)
            block(server.actualPort())
        }

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
        if (testContext.failed()) {
            throw testContext.causeOfFailure()
        }

    }
}