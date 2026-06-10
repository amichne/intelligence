plugins {
    base
}

val tuiExecutableName = if (System.getProperty("os.name").startsWith("Windows")) {
    "intelligence-tui.exe"
} else {
    "intelligence-tui"
}

tasks.register<Exec>("buildDevelopmentTui") {
    group = "application"
    description = "Build the repo-local Ratatui marketplace browser."

    commandLine("cargo", "build", "--manifest-path", "tui/Cargo.toml")
}

tasks.register<Sync>("installDevelopmentCli") {
    group = "application"
    description = "Update the repo-local Intelligence CLI from the Kotlin build output."

    dependsOn(":cli:installDist")
    dependsOn("buildDevelopmentTui")

    from(project(":cli").layout.buildDirectory.dir("install/intelligence"))
    from(layout.projectDirectory.file("target/debug/$tuiExecutableName")) {
        into("bin")
    }
    into(layout.projectDirectory.dir(".local/intelligence"))
}
