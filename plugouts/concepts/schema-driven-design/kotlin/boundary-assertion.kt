@JvmInline
value class TenantId(val value: String) {
    init {
        require(value.matches(Regex("[a-z0-9][a-z0-9-]*"))) { "Invalid tenant id: $value" }
    }

    override fun toString(): String = value
}

@JvmInline
value class InvoiceId(val value: String) {
    init {
        require(value.isNotBlank()) { "InvoiceId must not be blank" }
    }

    override fun toString(): String = value
}

sealed interface LifecycleEvent {
    val tenantId: TenantId
    val invoiceId: InvoiceId

    data class InvoiceCreated(
        override val tenantId: TenantId,
        override val invoiceId: InvoiceId,
    ) : LifecycleEvent

    data class InvoiceCancelled(
        override val tenantId: TenantId,
        override val invoiceId: InvoiceId,
        val reason: String,
    ) : LifecycleEvent {
        init {
            require(reason.isNotBlank()) { "Cancellation reason must not be blank" }
        }
    }
}

fun parseLifecycleEvent(payload: Map<String, String>): LifecycleEvent {
    val tenantId = TenantId(required(payload, "tenantId"))
    val invoiceId = InvoiceId(required(payload, "invoiceId"))
    return when (required(payload, "type")) {
        "INVOICE_CREATED" -> LifecycleEvent.InvoiceCreated(tenantId, invoiceId)
        "INVOICE_CANCELLED" -> LifecycleEvent.InvoiceCancelled(
            tenantId = tenantId,
            invoiceId = invoiceId,
            reason = required(payload, "reason"),
        )
        else -> error("Unknown lifecycle event type: ${payload["type"]}")
    }
}

private fun required(payload: Map<String, String>, key: String): String =
    payload[key] ?: error("Missing required field: $key")
