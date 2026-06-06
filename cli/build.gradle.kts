plugins {
    kotlin("jvm") version "2.0.21"
    application
    id("org.graalvm.buildtools.native") version "0.10.2"
}

application {
    mainClass.set("intelligence.cli.MainKt")
    applicationName = "intelligence"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<AbstractArchiveTask>().configureEach {
    archiveBaseName.set("intelligence")
    archiveVersion.set("")
}

tasks.named<Tar>("distTar") {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("intelligence")
            mainClass.set("intelligence.cli.MainKt")
            fallback.set(false)
        }
    }
}
