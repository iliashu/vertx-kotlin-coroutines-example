package net.example.vertx.kotlin.domain.models

import java.math.BigDecimal

data class Transfer(val sourceAccountId: Long, val destinationAccountId: Long, val amount: BigDecimal)
