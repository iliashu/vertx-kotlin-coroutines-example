package domain.models

import java.math.BigDecimal

data class Account(
        val id: Long,
        val balance: BigDecimal
)