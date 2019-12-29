import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.obj
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.number.BigDecimalCloseTo.closeTo
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AccountIntegrationTest: IntegrationTestBase() {

    @Test
    fun `valid account creation does not fail`()  {
        createTestAccount()
    }

    @Test
    fun `can retrieve valid account after creation`() {
        val accountId = createTestAccount()
        When {
            get("/accounts/$accountId")
        } Then {
            statusCode(200)
            body("id", notNullValue())
        }
    }

    @Test
    fun `successfully deposit money into an account`() {
        val accountId = createTestAccount()
        deposit(accountId, "10.00")
    }

    @Test
    fun `successfully deposited money affects account's balance`() {
        val accountId = createTestAccount()
        deposit(accountId, "10.00")

        val amount = When {
            get("/accounts/$accountId")
        } Then {
            statusCode(200)
            body("amount", isA(String::class.java))
        } Extract {
            path<String>("amount")
        }

        assertThat(BigDecimal(amount), closeTo(BigDecimal("10.00"), BigDecimal.ZERO))
    }

    private fun deposit(accountId: Int, amount: String) {
        val returnedAmount = Given {
            body(Json.obj (
                "amount" to amount
            ).toString())
        } When {
            post("/accounts/$accountId/deposits")
        } Then {
            statusCode(201)
            body("amount", isA(String::class.java))

        } Extract { path<String>("amount") }

        assertThat(BigDecimal(returnedAmount), closeTo(BigDecimal(amount), BigDecimal.ZERO))
    }

    private fun createTestAccount(): Int {
        return When {
            post("/accounts")
        } Then {
            statusCode(201)
            body("id", notNullValue())
        } Extract {
            path<Int>("id")
        }
    }
}