@JvmInline
value class CardLast4(val value: String) {
    init {
        require(value.matches(Regex("[0-9]{4}"))) { "CardLast4 must contain exactly four digits" }
    }

    override fun toString(): String = value
}

@JvmInline
value class Expiry(val value: String) {
    init {
        require(value.matches(Regex("[0-9]{2}/[0-9]{2}"))) { "Expiry must use MM/YY" }
    }

    override fun toString(): String = value
}

@JvmInline
value class Iban(val value: String) {
    init {
        require(value.isNotBlank()) { "Iban must not be blank" }
    }

    override fun toString(): String = value
}

sealed interface PaymentMethod {
    data class Card(val last4: CardLast4, val expiry: Expiry) : PaymentMethod
    data class BankTransfer(val iban: Iban) : PaymentMethod
    data object Cash : PaymentMethod
}

fun describe(method: PaymentMethod): String = when (method) {
    is PaymentMethod.Card -> "Card ending ${method.last4}"
    is PaymentMethod.BankTransfer -> "Bank transfer to ${method.iban}"
    PaymentMethod.Cash -> "Cash"
}
