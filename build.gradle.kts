plugins {
    kotlin("jvm") version "1.8.20"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    mavenCentral()
}

dependencies {
    implementation("net.fabricmc:fabric-loom:0.4-SNAPSHOT")
    implementation("net.fabricmc:tiny-remapper:0.8.6")
    implementation("net.fabricmc:tiny-mappings-parser:0.3.0+build.17")
    implementation("org.slf4j:slf4j-api:2.0.5")
    testImplementation("org.slf4j:slf4j-simple:2.0.5")
    testImplementation(kotlin("test"))
}

tasks.test {
    // remap test jar.
    // we need to translate it so that it can be load in forge environment.
    dependsOn("fabric-mod-test:remapJar")

    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}
