package net.example.vertx.kotlin.domain.models

import java.math.BigDecimal

data class Deposit(val destinationAccountId: Long, val amount: BigDecimal)
