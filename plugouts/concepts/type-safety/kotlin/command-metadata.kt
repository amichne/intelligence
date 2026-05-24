data class CommandMeta(
    val path: List<String>,
    val summary: String,
)

@JvmInline
value class PersonName(val value: String) {
    init {
        require(value.isNotBlank()) { "PersonName must not be blank" }
    }

    override fun toString(): String = value
}

sealed interface Command {
    val meta: CommandMeta

    data class Greet(val name: PersonName) : Command {
        override val meta = CommandMeta(
            path = listOf("greet"),
            summary = "Greet a person by name",
        )
    }

    data class Quit(val force: Boolean) : Command {
        override val meta = CommandMeta(
            path = listOf("quit"),
            summary = "Exit the application",
        )
    }
}
