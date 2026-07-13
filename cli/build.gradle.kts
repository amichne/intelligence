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
val defaultMarketplaceGitHub = providers.gradleProperty("intelligenceDefaultMarketplaceGitHub")
    .orElse("")
val defaultMarketplaceSnapshot = providers.gradleProperty("intelligenceDefaultMarketplaceSnapshot")
    .orElse("")
val defaultMarketplaceIndexSha256 = providers.gradleProperty("intelligenceDefaultMarketplaceIndexSha256")
    .orElse("")

val generatedBuildInfoDir = layout.buildDirectory.dir("generated/sources/build-info/kotlin")

val generateBuildInfo by tasks.registering {
    inputs.property("intelligenceVersion", intelligenceVersion)
    inputs.property("defaultMarketplaceGitHub", defaultMarketplaceGitHub)
    inputs.property("defaultMarketplaceSnapshot", defaultMarketplaceSnapshot)
    inputs.property("defaultMarketplaceIndexSha256", defaultMarketplaceIndexSha256)
    outputs.dir(generatedBuildInfoDir)

    doLast {
        val escapedVersion = intelligenceVersion.get()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val escapedDefaultGitHub = defaultMarketplaceGitHub.get().replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedDefaultSnapshot = defaultMarketplaceSnapshot.get().replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedDefaultDigest = defaultMarketplaceIndexSha256.get().replace("\\", "\\\\").replace("\"", "\\\"")
        val output = generatedBuildInfoDir.get()
            .file("intelligence/cli/BuildInfo.kt")
            .asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            package intelligence.cli

            internal object BuildInfo {
                const val VERSION: String = "$escapedVersion"
                const val DEFAULT_MARKETPLACE_GITHUB: String = "$escapedDefaultGitHub"
                const val DEFAULT_MARKETPLACE_SNAPSHOT: String = "$escapedDefaultSnapshot"
                const val DEFAULT_MARKETPLACE_INDEX_SHA256: String = "$escapedDefaultDigest"
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
