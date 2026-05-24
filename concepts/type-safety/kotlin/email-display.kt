@JvmInline
value class Email(val address: String) {
    init {
        require(address.contains("@")) { "Email must contain @" }
    }

    override fun toString(): String = address
}
