plugins {
    base
}

tasks.register<Sync>("installDevelopmentCli") {
    group = "application"
    description = "Update the repo-local Intelligence CLI from the Kotlin build output."

    dependsOn(":cli:installDist")

    from(project(":cli").layout.buildDirectory.dir("install/intelligence"))
    into(layout.projectDirectory.dir(".local/intelligence"))
}
