package domain.services

import domain.models.Account
import java.lang.Exception
import java.math.BigDecimal
import net.example.vertx.kotlin.domain.models.Deposit
import net.example.vertx.kotlin.domain.models.Transfer
import net.example.vertx.kotlin.domain.services.AccountService
import net.example.vertx.kotlin.persistance.AccountRepository

private val MINIMAL_TRANSFER_AMOUNT: BigDecimal = BigDecimal.ZERO

abstract class UserInputException(val fieldName: String, message: String, cause: Exception? = null) :
        RuntimeException(message, cause)

class AccountCreationException(accountId: Long) : RuntimeException("Unable to find just created account with id: $accountId")
class AccountNotFoundException(accountId: Long, accountPurpose: String = "account") : UserInputException(accountPurpose, "Unable to find account with id $accountId")

class InsufficientBalanceException(accountId: Long, requiredAmount: BigDecimal, availableAmount: BigDecimal) : RuntimeException(
        "Insufficient balance to complete operation. Account id: $accountId, required amount: $requiredAmount, available: $availableAmount"
)

class InvalidTransferAmount(amount: BigDecimal) : UserInputException("amount", "Invalid transfer amount: ${amount.toPlainString()}")

/**
 * Account service represents business logic of operations with accounts in isolation from persistence layer
 */
class AccountServiceImpl(private val repository: AccountRepository) : AccountService {

    override suspend fun createAccount(): Account = repository.transaction("create account") { connection ->
        val accountId = connection.createAccountInternal()
        connection.getAccount(accountId) ?: throw AccountCreationException(accountId)
    }

    override suspend fun getAccountById(accountId: Long): Account? {
        return repository.getAccount(accountId)
    }

    override suspend fun deposit(accountId: Long, amount: BigDecimal): Deposit = repository.transaction("deposit") { connection ->

        val account = connection.getAccount(accountId) ?: throw AccountNotFoundException(accountId)
        val newBalance = account.balance + amount
        connection.updateAccountBalanceInternal(account.id, newBalance)
        Deposit(accountId, amount)
    }

    override suspend fun transfer(sourceAccountId: Long, destinationAccountId: Long, amount: BigDecimal): Transfer {
        return repository.transaction("transfer") { connection ->

            if (amount < MINIMAL_TRANSFER_AMOUNT) {
                throw InvalidTransferAmount(amount)
            }

            val sourceAccount = connection.getAccount(sourceAccountId)
                    ?: throw AccountNotFoundException(sourceAccountId, "source account")

            if (sourceAccount.balance < amount) {
                throw InsufficientBalanceException(sourceAccount.id, amount, sourceAccount.balance)
            }

            val sourceAccountNewBalance = sourceAccount.balance - amount
            connection.updateAccountBalanceInternal(sourceAccount.id, sourceAccountNewBalance)

            val destinationAccount = connection.getAccount(destinationAccountId)
                    ?: throw AccountNotFoundException(sourceAccountId, "destination account")

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
