package domain.services

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.vertx.ext.sql.TransactionIsolation
import kotlinx.coroutines.runBlocking
import net.example.vertx.kotlin.persistance.AccountOperations
import net.example.vertx.kotlin.persistance.AccountRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows


internal class AccountServiceTest {

    @Test
    fun `exception is thrown when unable to retrieve just created account`() {

        val operationsMock = mock<AccountOperations> {
            onBlocking { it.createAccountInternal() } doReturn 1
            onBlocking { it.getAccount(1) } doReturn null
        }

        val service = AccountService(DummyRepository(operationsMock))

        assertThrows<AccountCreationException> {
            runBlocking { service.createAccount() }
        }
    }
}

class DummyRepository(private val operationsMock: AccountOperations) : AccountRepository, AccountOperations by operationsMock {
    override suspend fun <T> transaction(operationName: String, isolationLevel: TransactionIsolation, block: suspend (operations: AccountOperations) -> T): T {
        return block(operationsMock)
    }

}
