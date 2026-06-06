private const val SEPARATOR = "::"

interface Validateable {
    fun validate(): Validateable
}

interface Named : Validateable {
    val value: String

    interface NonBlank : Named {
        override fun validate() = apply {
            require(value.isNotBlank()) {
                "${this::class.simpleName} must not be blank"
            }
        }
    }

    interface Composable : NonBlank {
        override fun validate() = apply {
            super<NonBlank>.validate()
            require(SEPARATOR !in value) {
                "${this::class.simpleName} must not contain '$SEPARATOR': '$value'"
            }
        }
    }
}

@JvmInline
value class TenantId(override val value: String) : Named.Composable {
    init {
        validate()
    }

    override fun toString(): String = value
}

@JvmInline
value class OrderId private constructor(val raw: String) {
    companion object {
        fun create(tenant: TenantId, sequence: Long): OrderId {
            require(sequence > 0) { "Sequence must be positive" }
            return OrderId("${tenant.value}$SEPARATOR$sequence")
        }

        fun parse(raw: String): OrderId {
            require(raw.split(SEPARATOR).size == 2) { "Malformed OrderId: $raw" }
            return OrderId(raw)
        }
    }

    override fun toString(): String = raw
}
