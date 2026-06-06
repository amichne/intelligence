package intelligence.cli

import intelligence.cli.command.IntelligenceCommand
import com.github.ajalt.clikt.core.main

fun main(args: Array<String>) {
    IntelligenceCommand().main(args)
}
