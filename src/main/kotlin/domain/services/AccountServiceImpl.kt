package domain.services

import domain.models.Account
import java.lang.Exception
import java.math.BigDecimal
import net.example.vertx.kotlin.domain.models.Deposit
import net.example.vertx.kotlin.domain.models.Transfer
import net.example.vertx.kotlin.domain.services.AccountService
import net.example.vertx.kotlin.persistance.AccountRepository

private val MINIMAL_TRANSFER_AMOUNT: BigDecimal = BigDecimal.ZERO
private val MINIMAL_DEPOSIT_AMOUNT: BigDecimal = BigDecimal.ZERO

abstract class UserInputException(message: String, cause: Exception? = null) :
        RuntimeException(message, cause)

class AccountCreationException(accountId: Long) : RuntimeException("Unable to find just created account with id: $accountId")
class AccountNotFoundException(accountId: Long) : UserInputException("Unable to find account with id $accountId")

class InsufficientBalanceException(accountId: Long, requiredAmount: BigDecimal, availableAmount: BigDecimal) : RuntimeException(
        "Insufficient balance to complete operation. Account id: $accountId, required amount: $requiredAmount, available: $availableAmount"
)

class InvalidTransferAmount(amount: BigDecimal) : UserInputException("Invalid transfer amount: ${amount.toPlainString()}")
class InvalidDepositAmount(amount: BigDecimal) : UserInputException("Invalid deposit amount: ${amount.toPlainString()}")

/**
 * Account service represents business logic of operations with accounts in isolation from persistence layer
 */
class AccountServiceImpl(private val repository: AccountRepository) : AccountService {

    override suspend fun createAccount(): Account = repository.transaction("create account") { connection ->
        val accountId = connection.createAccount()
        connection.getAccountById(accountId) ?: throw AccountCreationException(accountId)
    }

    override suspend fun getAccountById(accountId: Long): Account? {
        return repository.getAccountById(accountId)
    }

    override suspend fun deposit(accountId: Long, amount: BigDecimal): Deposit = repository.transaction("deposit") { connection ->

        if (amount < MINIMAL_DEPOSIT_AMOUNT) {
            throw InvalidDepositAmount(amount)
        }
        val account = connection.getAccountById(accountId) ?: throw AccountNotFoundException(accountId)
        val newBalance = account.balance + amount
        connection.updateAccountBalance(account.id, newBalance)
        Deposit(accountId, amount)
    }

    override suspend fun transfer(sourceAccountId: Long, destinationAccountId: Long, amount: BigDecimal): Transfer {
        return repository.transaction("transfer") { connection ->

            if (amount < MINIMAL_TRANSFER_AMOUNT) {
                throw InvalidTransferAmount(amount)
            }

            val sourceAccount = connection.getAccountById(sourceAccountId)
                    ?: throw AccountNotFoundException(sourceAccountId)

            if (sourceAccount.balance < amount) {
                throw InsufficientBalanceException(sourceAccount.id, amount, sourceAccount.balance)
            }

            val sourceAccountNewBalance = sourceAccount.balance - amount
            connection.updateAccountBalance(sourceAccount.id, sourceAccountNewBalance)

            val destinationAccount = connection.getAccountById(destinationAccountId)
                    ?: throw AccountNotFoundException(sourceAccountId)

            val destinationAccountNewBalance = destinationAccount.balance + amount
            connection.updateAccountBalance(destinationAccount.id, destinationAccountNewBalance)

            Transfer(
                    sourceAccountId = sourceAccountId,
                    destinationAccountId = destinationAccountId,
                    amount = amount
            )
        }
    }
}
