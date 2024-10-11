plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    id("com.gradleup.shadow") version "8.3.3"
    id("java")
}

group = "online.aruka.amountChecker"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.akuleshov7:ktoml-core:0.5.1")
    implementation("com.akuleshov7:ktoml-file:0.5.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "online.aruka.amountChecker.MainKt"
    }
    archiveBaseName = ""
}

tasks.shadowJar {
    archiveBaseName = "AmountChecker"
    archiveClassifier = ""
    archiveVersion = "v${version}"
}

kotlin {
    jvmToolchain(17)
}