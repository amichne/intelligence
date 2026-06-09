package intelligence.cli.command

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.output.PlaintextHelpFormatter

internal class IntelligenceHelpFormatter(
    private val context: Context,
) : HelpFormatter {
    override fun formatHelp(
        error: UsageError?,
        prolog: String,
        epilog: String,
        parameters: List<HelpFormatter.ParameterHelp>,
        programName: String,
    ): String {
        if (error != null) {
            return PlaintextHelpFormatter(context).formatHelp(error, prolog, epilog, parameters, programName)
        }

        return buildString {
            if (prolog.isNotBlank()) {
                appendLine(prolog.trimEnd())
                appendLine()
            }
            appendLine("Usage: ${renderUsage(programName, parameters)}")
            appendSections(parameters)
            if (epilog.isNotBlank()) {
                appendLine()
                appendLine(epilog.trimEnd())
            }
        }.trimEnd() + "\n"
    }

    private fun StringBuilder.appendSections(parameters: List<HelpFormatter.ParameterHelp>) {
        val commands = parameters.filterIsInstance<HelpFormatter.ParameterHelp.Subcommand>()
            .map { HelpRow(it.name, it.help) }
        appendSection("Commands", commands)

        val arguments = parameters.filterIsInstance<HelpFormatter.ParameterHelp.Argument>()
            .filter { it.help.isNotBlank() }
            .map { HelpRow(renderArgumentName(it.name), it.help) }
        appendSection("Arguments", arguments)

        val options = parameters.filterIsInstance<HelpFormatter.ParameterHelp.Option>()
            .map { HelpRow(renderOptionName(it), it.help) }
        appendSection("Options", options)
    }

    private fun StringBuilder.appendSection(title: String, rows: List<HelpRow>) {
        if (rows.isEmpty()) return
        appendLine()
        appendLine("$title:")
        val termWidth = rows.maxOf { it.term.length } + 2
        rows.forEach { row ->
            append("  ")
            append(row.term)
            if (row.help.isNotBlank()) {
                append(" ".repeat(termWidth - row.term.length))
                append(row.help)
            }
            appendLine()
        }
    }

    private fun renderUsage(
        programName: String,
        parameters: List<HelpFormatter.ParameterHelp>,
    ): String = buildList {
        add(programName)
        if (parameters.any { it is HelpFormatter.ParameterHelp.Option }) add("[OPTIONS]")
        parameters.filterIsInstance<HelpFormatter.ParameterHelp.Argument>().mapTo(this) { argument ->
            val rendered = renderArgumentName(argument.name)
            val optional = if (argument.required) rendered else "[$rendered]"
            if (argument.repeatable) "$optional..." else optional
        }
        if (parameters.any { it is HelpFormatter.ParameterHelp.Subcommand }) add("[COMMAND]")
    }.joinToString(" ")

    private fun renderOptionName(option: HelpFormatter.ParameterHelp.Option): String {
        val primary = option.names.sortedBy { it.startsWith("--") }.joinToString(", ")
        val secondary = option.secondaryNames.sortedBy { it.startsWith("--") }.joinToString(", ")
        val value = option.metavar?.let { " ${renderMetavar(it)}" }.orEmpty()
        return listOf(primary + value, secondary.takeIf { it.isNotBlank() })
            .filterNotNull()
            .joinToString(" / ")
    }

    private fun renderArgumentName(name: String): String =
        when {
            name.startsWith("<") && name.endsWith(">") -> name
            else -> "<$name>"
        }

    private fun renderMetavar(value: String): String {
        val normalized = value.trim().trim('<', '>', '[', ']')
        return "<${normalized.uppercase()}>"
    }

    private data class HelpRow(
        val term: String,
        val help: String,
    )
}
