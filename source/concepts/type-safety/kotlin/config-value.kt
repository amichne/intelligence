sealed interface ConfigValue {
    sealed interface Primitive<V> : ConfigValue {
        val value: V
    }

    @JvmInline
    value class Text(override val value: String) : Primitive<String>

    @JvmInline
    value class Flag(override val value: Boolean) : Primitive<Boolean>

    @JvmInline
    value class Count(override val value: Int) : Primitive<Int>

    data class Sequence<E>(val items: List<E>) : ConfigValue
    data class Composite(val fields: Map<String, ConfigValue>) : ConfigValue
}

sealed interface JsonValue {
    data class Text(val value: String) : JsonValue
    data class Flag(val value: Boolean) : JsonValue
    data class Count(val value: Int) : JsonValue
    data class Sequence(val items: List<JsonValue>) : JsonValue
    data class Composite(val fields: Map<String, JsonValue>) : JsonValue
}

fun serialize(value: ConfigValue): JsonValue = when (value) {
    is ConfigValue.Text -> JsonValue.Text(value.value)
    is ConfigValue.Flag -> JsonValue.Flag(value.value)
    is ConfigValue.Count -> JsonValue.Count(value.value)
    is ConfigValue.Sequence<*> -> JsonValue.Sequence(value.items.map { JsonValue.Text(it.toString()) })
    is ConfigValue.Composite -> JsonValue.Composite(value.fields.mapValues { serialize(it.value) })
}
