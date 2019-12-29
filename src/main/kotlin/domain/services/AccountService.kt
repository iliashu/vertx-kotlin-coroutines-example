package domain.services

import domain.models.Account
import net.example.vertx.kotlin.domain.models.Deposit
import net.example.vertx.kotlin.domain.models.Transfer
import net.example.vertx.kotlin.persistance.AccountRepository
import java.math.BigDecimal

class AccountCreationException(accountId: Long) : RuntimeException("Unable to find just created account with id: $accountId")
class AccountNotFoundException(accountId: Long) : RuntimeException("Unable to find account with id $accountId")
class FailedToUpdateAccountBalanceException(accountId: Long, newBalance: BigDecimal) : RuntimeException(
        "Failed to update account balance. Account id: $accountId, new balance: ${newBalance.toPlainString()}"
)

class InsufficientBalanceException(accountId: Long, requiredAmount: BigDecimal, availableAmount: BigDecimal) : RuntimeException(
        "Insufficient balance to complete operation. Account id: $accountId, required amount: $requiredAmount, available: $availableAmount"
)

/**
 * Account service represents business logic of operations with accounts in isolation from persistence layer
 */
class AccountService(private val repository: AccountRepository) {

    suspend fun createAccount(): Account = repository.transaction("create account") { connection ->
        val accountId = connection.createAccountInternal()
        connection.getAccount(accountId) ?: throw AccountCreationException(accountId)
    }

    suspend fun getAccountById(accountId: Long): Account? {
        return repository.getAccount(accountId)
    }

    suspend fun deposit(accountId: Long, amount: BigDecimal): Deposit = repository.transaction("deposit") { connection ->

        val account = connection.getAccount(accountId) ?: throw AccountNotFoundException(accountId)
        val newBalance = account.balance + amount
        connection.updateAccountBalanceInternal(account.id, newBalance)
        Deposit(accountId, amount)
    }

    suspend fun transfer(sourceAccountId: Long, destinationAccountId: Long, amount: BigDecimal): Transfer {
        return repository.transaction("transfer") { connection ->
            val sourceAccount = connection.getAccount(sourceAccountId)
                    ?: throw AccountNotFoundException(sourceAccountId)

            val destinationAccount = connection.getAccount(destinationAccountId)
                    ?: throw AccountNotFoundException(sourceAccountId)

            if (sourceAccount.balance < amount) {
                throw InsufficientBalanceException(sourceAccount.id, amount, sourceAccount.balance)
            }

            val sourceAccountNewBalance = sourceAccount.balance - amount
            connection.updateAccountBalanceInternal(sourceAccount.id, sourceAccountNewBalance)

            val destinationAccountNewBalance = destinationAccount.balance + amount
            connection.updateAccountBalanceInternal(destinationAccount.id, destinationAccountNewBalance)

            Transfer(
                    sourceAccountId = sourceAccountId,
                    destinationAccountId = destinationAccountId,
                    amount = amount
            )
        }
    }


}

