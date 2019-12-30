package net.example.vertx.kotlin.domain.services

import domain.models.Account
import net.example.vertx.kotlin.domain.models.Deposit
import net.example.vertx.kotlin.domain.models.Transfer
import java.math.BigDecimal

interface AccountService {
    suspend fun createAccount(): Account

    suspend fun getAccountById(accountId: Long): Account?

    suspend fun deposit(accountId: Long, amount: BigDecimal): Deposit

    suspend fun transfer(sourceAccountId: Long, destinationAccountId: Long, amount: BigDecimal): Transfer
}