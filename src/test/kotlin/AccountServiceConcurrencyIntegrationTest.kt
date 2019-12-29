import domain.models.Account
import domain.services.AccountService
import io.vertx.ext.sql.SQLOperations
import kotlinx.coroutines.*
import net.example.vertx.kotlin.persistance.AccountRepository
import net.example.vertx.kotlin.persistance.AccountOperations
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.number.BigDecimalCloseTo.closeTo
import org.hamcrest.number.OrderingComparison.greaterThan
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration

// all of these tests are somewhat useless unless they are executed against a real DB, as
// collision and deadlock resolution strategies differ from DB to DB
class AccountServiceConcurrencyIntegrationTest : IntegrationTestBase() {

    @Test
    fun `concurrent deposits do not corrupt data`() = vertxTestWithTimeout {

        val controlledTransactions = TransactionFactoryWithDelayControl()
        val baseRepository = AccountRepository(dbClient, controlledTransactions)

        val service = AccountService(baseRepository)

        val account = service.createAccount()

        val (pause, job1) = controlledTransactions.pauseAfterBalanceUpdate {
            service.deposit(account.id, BigDecimal("100"))
        }

        val job2 = GlobalScope.launch {
            service.deposit(account.id, BigDecimal("200"))
        }
        pause.complete(Unit)

        joinAll(job1, job2)

        val finalBalance = service.getAccountById(account.id)!!.balance

        assertThat(finalBalance, closeTo(BigDecimal("300"), BigDecimal.ZERO))
    }

    @Test
    fun `concurrent transfers do not allow customers go into negative balance`() = vertxTestWithTimeout {

        val controlledTransactions = TransactionFactoryWithDelayControl()
        val baseRepository = AccountRepository(dbClient, controlledTransactions)

        val service = AccountService(baseRepository)

        val sourceAccount = service.createAccount()
        val destinationAccount = service.createAccount()
        val destinationAccount2 = service.createAccount()

        service.deposit(sourceAccount.id, BigDecimal("100"))

        val (pause, job1) = controlledTransactions.pauseAfterBalanceUpdate(sourceAccount.id) {
            service.transfer(
                    sourceAccountId = sourceAccount.id,
                    destinationAccountId = destinationAccount.id,
                    amount = BigDecimal("80")
            )
        }

        val job2 = GlobalScope.launch {
            service.transfer(
                    sourceAccountId = sourceAccount.id,
                    destinationAccountId = destinationAccount2.id,
                    amount = BigDecimal("80")
            )
        }
        pause.complete(Unit)

        joinAll(job1, job2)

        val finalSourceBalance = service.getAccountById(sourceAccount.id)!!.balance
        assertThat(finalSourceBalance, greaterThan(BigDecimal("0")))

        // we don't really care which transfer out of the two was successful
        val finalTotal = finalSourceBalance + service.getAccountById(destinationAccount.id)!!.balance + service.getAccountById(destinationAccount2.id)!!.balance
        assertThat(finalTotal, closeTo(BigDecimal("100"), BigDecimal.ZERO))
    }

    @Test
    @Disabled("HSQLDB actually hangs on this test when running in in-memory mode =(")
    fun `deadlock transfers are resolved by DB`() = vertxTestWithTimeout(Duration.ofSeconds(100)) {

        val controlledTransactions = TransactionFactoryWithDelayControl()
        val baseRepository = AccountRepository(dbClient, controlledTransactions)

        val service = AccountService(baseRepository)

        val account1 = service.createAccount()
        val account2 = service.createAccount()

        service.deposit(account1.id, BigDecimal("100"))
        service.deposit(account2.id, BigDecimal("100"))

        val (pause1, job1) = controlledTransactions.pauseAfterBalanceUpdate(account2.id) {
            service.transfer(
                    sourceAccountId = account1.id,
                    destinationAccountId = account2.id,
                    amount = BigDecimal("80")
            )
        }

        val (pause2, job2) = controlledTransactions.pauseAfterBalanceUpdate(account1.id) {
            service.transfer(
                    sourceAccountId = account2.id,
                    destinationAccountId = account1.id,
                    amount = BigDecimal("80")
            )
        }

        pause1.complete(Unit)
        pause2.complete(Unit)

        joinAll(job1, job2)
    }
}

/**
 * Class to introduce a controlled race-condition for account updates
 */
class TransactionFactoryWithDelayControl : (SQLOperations) -> AccountOperations {

    private var pauseAccountIdFilter: ((Long) -> Boolean)? = null
    private var pauseCompletedCompletionSource: CompletableDeferred<Unit>? = null
    private var accountUpdatedCompletionSource: CompletableDeferred<Unit>? = null

    override fun invoke(connection: SQLOperations): AccountOperations {
        val baseTransaction = AccountOperations.create(connection)
        return object : AccountOperations {
            override suspend fun updateAccountBalanceInternal(accountId: Long, newBalance: BigDecimal) {
                val result = baseTransaction.updateAccountBalanceInternal(accountId, newBalance)
                val pause = pauseCompletedCompletionSource;
                if (pause != null && pauseAccountIdFilter!!(accountId)) {
                    accountUpdatedCompletionSource!!.complete(Unit)
                    pause.await()

                }
                return result
            }

            override suspend fun getAccount(accountId: Long): Account? {
                return baseTransaction.getAccount(accountId)
            }

            override suspend fun createAccountInternal(): Long {
                return baseTransaction.createAccountInternal()
            }
        }
    }

    suspend fun pauseAfterBalanceUpdate(
            accountId: Long? = null,
            block: suspend () -> Unit
    ): Pair<CompletableDeferred<Unit>, Job> {

        val pause = CompletableDeferred<Unit>()
        val ready = CompletableDeferred<Unit>()
        pauseAccountIdFilter = { aId -> accountId == null || aId == accountId }
        pauseCompletedCompletionSource = pause
        accountUpdatedCompletionSource = ready
        val job = GlobalScope.launch(Dispatchers.Default) {
            block()
        }

        ready.await()
        pauseCompletedCompletionSource = null
        accountUpdatedCompletionSource = null
        pauseAccountIdFilter = null
        return Pair(pause, job)
    }

}
