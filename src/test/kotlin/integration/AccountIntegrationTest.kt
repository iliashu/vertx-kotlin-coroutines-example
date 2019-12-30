package integration

import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.obj
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.*
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.number.BigDecimalCloseTo
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AccountIntegrationTest: IntegrationTestBase() {

    @Test
    fun `valid account creation does not fail`()  {
        createAccount()
    }

    @Test
    fun `can retrieve valid account after creation`() {
        val accountId = createAccount()
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
        val accountId = createAccount()
        deposit(accountId, "10.00")
    }

    @Test
    fun `successfully deposited money affects account's balance`() {
        val accountId = createAccount()
        deposit(accountId, "10.00")
        verifyAccountBalance(accountId, "10.00")
    }

    @Test
    fun `transfer succeeds when source account has enough money and destination account exists`() {
        val sourceAccountId = createAccount()
        val destinationAccountId = createAccount()

        deposit(sourceAccountId, "10.00")

        val transferAmount = "8.00"
        Given {
            contentType("application/json")
            body(Json.obj(
                    "destinationAccountId" to destinationAccountId,
                    "amount" to transferAmount
            ).toString())
        } When {
            post("/accounts/$sourceAccountId/transfers")
        } Then {
            statusCode(201)
            contentType("application/json")
            body("destinationAccountId", equalTo(destinationAccountId))
            body("amount", stringEqualToDecimal(transferAmount))
        }

        verifyAccountBalance(sourceAccountId, "2.00")

        verifyAccountBalance(destinationAccountId, transferAmount)
    }

    @Test
    fun `transfer fails with 4xx when source account does not exist`() {

        val fakeAccountId = Long.MAX_VALUE - 1
        val destinationAccountId = createAccount()

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

    @Test
    fun `no balance change after transfer to yourself`() {


        val accountId = createAccount()
        val initialBalance = "100"
        deposit(accountId, initialBalance)

        Given {
            contentType("application/json")
            body(Json.obj(
                    "destinationAccountId" to accountId,
                    "amount" to "10"
            ).toString())
        } When {
            post("/accounts/$accountId/transfers")
        } Then {
            // we don't really care about the response in this case, only that the balance did not change in the end
        }

        verifyAccountBalance(accountId, initialBalance)
    }

    @Test
    fun `transfer fails with 4xx when destination account does not exist`() {

        val fakeAccountId = Long.MAX_VALUE - 1
        val sourceAccountId = createAccount()
        deposit(sourceAccountId, "100")

        When {
            get("/accounts/$fakeAccountId")
        } Then {
            statusCode(404)
        }

        Given {
            contentType("application/json")
            body(Json.obj(
                    "destinationAccountId" to fakeAccountId,
                    "amount" to "80"
            ).toString())
        } When {
            post("/accounts/$sourceAccountId/transfers")
        } Then {
            statusCode(isUserError())
        }
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

    private fun createAccount(): Int {
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

    private fun verifyAccountBalance(destinationAccountId: Int, transferAmount: String) {
        When {
            get("/accounts/$destinationAccountId")
        } Then {
            statusCode(200)
            contentType("application/json")
            body("amount", stringEqualToDecimal(transferAmount))
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

private fun isUserError(): Matcher<Int> {
    return allOf(greaterThanOrEqualTo(400),lessThan(500))
}
