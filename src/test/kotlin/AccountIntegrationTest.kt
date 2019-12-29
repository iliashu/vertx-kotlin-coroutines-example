import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.obj
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.number.BigDecimalCloseTo
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
            contentType("application/json")
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

        When {
            get("/accounts/$accountId")
        } Then {
            statusCode(200)
            contentType("application/json")
            body("amount", stringEqualToDecimal("10.00"))
        }
    }

    @Test
    fun `transfer succeeds when source account has enough money and destination account exists`() {
        val sourceAccountId = createTestAccount()
        val destinationAccountId = createTestAccount()

        deposit(sourceAccountId, "10.00")

        val transferAmount = "8.00"
        transfer(sourceAccountId, destinationAccountId, transferAmount)

        When {
            get("/accounts/$sourceAccountId")
        } Then {
            statusCode(200)
            contentType("application/json")
            body("amount", stringEqualToDecimal("2.00"))
        }

        When {
            get("/accounts/$destinationAccountId")
        } Then {
            statusCode(200)
            contentType("application/json")
            body("amount", stringEqualToDecimal(transferAmount))
        }
    }

    @Test
    fun `transfer fails with 4xx when source account does not exist`() {

        val fakeAccountId = Long.MAX_VALUE - 1
        val destinationAccountId = createTestAccount()

        When {
            get("/accounts/$fakeAccountId")
        } Then {
            statusCode(404)
        }

        Given {
            contentType("application/json")
            body(Json.obj(
                    "destinationAccountId" to destinationAccountId,
                    "amount" to "100"
            ).toString())
        } When {
            post("/accounts/$fakeAccountId/transfers")
        } Then {
            statusCode(400)
        }
    }

    private fun transfer(sourceAccountId: Int, destinationAccountId: Int, amount: String) {
        Given {
            contentType("application/json")
            body(Json.obj(
                    "destinationAccountId" to destinationAccountId,
                    "amount" to amount
            ).toString())
        } When {
            post("/accounts/$sourceAccountId/transfers")
        } Then {
            statusCode(201)
            contentType("application/json")
            body("destinationAccountId", equalTo(destinationAccountId))
            body("amount", stringEqualToDecimal(amount))
        }
    }

    @Test
    fun `transfer from an account to the same account fails with 4xx error`() {

    }

    private fun deposit(accountId: Int, amount: String) {
        Given {
            body(Json.obj (
                "amount" to amount
            ).toString())
        } When {
            post("/accounts/$accountId/deposits")
        } Then {
            statusCode(201)
            contentType("application/json")
            body("amount", stringEqualToDecimal(amount))
        }
    }

    private fun createTestAccount(): Int {
        return When {
            post("/accounts")
        } Then {
            statusCode(201)
            contentType("application/json")
            body("id", notNullValue())
        } Extract {
            path<Int>("id")
        }
    }
}

private fun stringEqualToDecimal(decimalString: String): Matcher<String> = stringEqualToDecimal(decimalString.toBigDecimal())

private fun stringEqualToDecimal(bigDecimal: BigDecimal): Matcher<String> {
    val matcher = BigDecimalCloseTo(bigDecimal, BigDecimal.ZERO)
    return object: TypeSafeMatcher<String>() {
        override fun describeTo(description: Description?) {
            matcher.describeTo(description)
        }

        override fun matchesSafely(item: String?): Boolean {
            return matcher.matchesSafely(item?.toBigDecimal())
        }

        override fun describeMismatchSafely(item: String?, mismatchDescription: Description?) {
            matcher.describeMismatchSafely(item?.toBigDecimal(), mismatchDescription)
        }

    }
}