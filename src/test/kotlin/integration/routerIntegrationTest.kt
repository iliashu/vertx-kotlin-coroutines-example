package integration

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import io.restassured.RestAssured
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.vertx.core.Vertx
import io.vertx.junit5.VertxTestContext
import net.example.vertx.kotlin.createRouter
import net.example.vertx.kotlin.domain.services.AccountService
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class RouterIntegrationTest {

    @Test
    fun `unhandled exception results in 500 status code`() {
        val accountService = mock<AccountService> {
            onBlocking { it.getAccountById(any()) } doThrow RuntimeException("Some random exception")
        }

        startRouter(accountService)

        When {
           get("/accounts/1")
        } Then {
            statusCode(500)
        }
    }

    private fun startRouter(accountService: AccountService) {
        val vertx = Vertx.vertx()
        val testedRouter = createRouter(vertx, accountService)

        val testContext = VertxTestContext()
        val server = vertx.createHttpServer()
                .requestHandler(testedRouter)
                .listen(0, testContext.completing())

        testContext.awaitCompletion(5, TimeUnit.SECONDS)

        RestAssured.port = server.actualPort()
    }
}