plugins {
    kotlin("jvm") version "2.4.0"
    application
}

application {
    mainClass.set("intelligence.cli.MainKt")
    applicationName = "intelligence"
}

val intelligenceVersion = providers.gradleProperty("intelligenceVersion")
    .orElse("dev")

val generatedBuildInfoDir = layout.buildDirectory.dir("generated/sources/build-info/kotlin")

val generateBuildInfo by tasks.registering {
    inputs.property("intelligenceVersion", intelligenceVersion)
    outputs.dir(generatedBuildInfoDir)

    doLast {
        val escapedVersion = intelligenceVersion.get()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val output = generatedBuildInfoDir.get()
            .file("intelligence/cli/BuildInfo.kt")
            .asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            package intelligence.cli

            internal object BuildInfo {
                const val VERSION: String = "$escapedVersion"
            }
            """.trimIndent() + "\n",
        )
    }
}

kotlin {
    jvmToolchain(21)
    sourceSets.named("main") {
        kotlin.srcDir(generatedBuildInfoDir)
    }
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.1.0") {
        exclude(group = "com.github.ajalt.mordant", module = "mordant")
    }
    implementation("com.github.ajalt.mordant:mordant-core:3.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.installDist)
    systemProperty(
        "intelligence.installDir",
        layout.buildDirectory.dir("install/intelligence").get().asFile.absolutePath,
    )
}

tasks.named("compileKotlin") {
    dependsOn(generateBuildInfo)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    archiveBaseName.set("intelligence")
    archiveVersion.set("")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.named<Tar>("distTar") {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}
