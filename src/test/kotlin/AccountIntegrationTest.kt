import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.obj
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
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