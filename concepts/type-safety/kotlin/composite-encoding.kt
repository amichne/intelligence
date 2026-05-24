internal object CompositeEncoding {
    const val SEPARATOR = "::"

    fun encode(prefix: String, parts: List<String>): String {
        require(prefix.isNotBlank()) { "Prefix must not be blank" }
        parts.forEachIndexed { index, part ->
            require(SEPARATOR !in part) {
                "Part[$index] must not contain '$SEPARATOR': '$part'"
            }
        }
        return (listOf(prefix) + parts).joinToString(SEPARATOR)
    }

    fun split(encoded: String): List<String> = encoded.split(SEPARATOR)
}
