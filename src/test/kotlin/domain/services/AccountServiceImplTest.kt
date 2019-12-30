package domain.services

import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verifyBlocking
import domain.models.Account
import io.vertx.ext.sql.TransactionIsolation
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import net.example.vertx.kotlin.persistance.AccountOperations
import net.example.vertx.kotlin.persistance.AccountRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatcher

internal class AccountServiceImplTest {

    @Test
    fun `exception is thrown when unable to retrieve just created account`() {

        val operationsMock = mock<AccountOperations> {
            onBlocking { it.createAccount() } doReturn 1
            onBlocking { it.getAccountById(1) } doReturn null
        }

        val service = AccountServiceImpl(DummyRepository(operationsMock))

        assertThrowsBlocking<AccountCreationException> {
            service.createAccount()
        }
    }

    @Test
    fun `transfer of the entire balance works successfully`() {

        val accountId = 1L
        val destinationAccountId = 2L
        val balance = BigDecimal("100.00")
        val operationsMock = mock<AccountOperations> {
            onBlocking { it.getAccountById(accountId) } doReturn Account(accountId, balance)
            onBlocking { it.getAccountById(destinationAccountId) } doReturn Account(destinationAccountId, BigDecimal.ZERO)
        }

        val service = AccountServiceImpl(DummyRepository(operationsMock))

        runBlocking {
            service.transfer(accountId, destinationAccountId, balance)
        }

        verifyBlocking(operationsMock, times(1)) {
            operationsMock.updateAccountBalance(eq(accountId), equalToDecimal(BigDecimal.ZERO))
        }

        verifyBlocking(operationsMock, times(1)) {
            operationsMock.updateAccountBalance(eq(destinationAccountId), equalToDecimal(balance))
        }
    }

    @Test
    fun `negative transfer amount is rejected`() {

        val accountId = 1L
        val destinationAccountId = 2L
        val balance = BigDecimal("100.00")
        val operationsMock = mock<AccountOperations> {
            onBlocking { it.getAccountById(accountId) } doReturn Account(accountId, balance)
            onBlocking { it.getAccountById(destinationAccountId) } doReturn Account(destinationAccountId, BigDecimal.ZERO)
        }

        val service = AccountServiceImpl(DummyRepository(operationsMock))

        assertThrowsBlocking<UserInputException> {
            service.transfer(accountId, destinationAccountId, -balance)
        }
    }

    @Test
    fun `negative deposit amount is rejected`() {

        val accountId = 1L
        val operationsMock = mock<AccountOperations> {
            onBlocking { it.getAccountById(accountId) } doReturn Account(accountId, BigDecimal.ZERO)
        }

        val service = AccountServiceImpl(DummyRepository(operationsMock))

        assertThrowsBlocking<UserInputException> {
            service.deposit(accountId, BigDecimal("-100"))
        }
    }

    // not really clear what the expected behaviour would be to zero amount transfer

    private inline fun <reified T : Throwable> assertThrowsBlocking(noinline block: suspend () -> Unit): T? {
        return assertThrows {
            runBlocking { block() }
        }
    }

    private fun equalToDecimal(decimal: BigDecimal): BigDecimal {
        return argThat(object : ArgumentMatcher<BigDecimal> {
            override fun matches(argument: BigDecimal?): Boolean {
                return argument?.compareTo(other = decimal)?.let { it == 0 } ?: false
            }

            override fun toString(): String {
                return decimal.toPlainString()
            }
        })
    }
}

class DummyRepository(private val operationsMock: AccountOperations) : AccountRepository, AccountOperations by operationsMock {
    override suspend fun <T> transaction(operationName: String, isolationLevel: TransactionIsolation, block: suspend (operations: AccountOperations) -> T): T {
        return block(operationsMock)
    }
}
