plugins {
    kotlin("jvm") version "1.8.20"
    kotlin("plugin.serialization") version "1.8.20"
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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    implementation("org.ow2.asm:asm:9.4")
    implementation("org.ow2.asm:asm-util:9.4")
    testImplementation("org.slf4j:slf4j-simple:2.0.5")
    testImplementation(kotlin("test"))
}

task<Copy>("copyFabricTestMod") {
    dependsOn("fabric-mod-test:remapJar")
    from("fabric-mod-test/build/libs/fabric-mod-test-0.1.jar")
    into("run/mods")
}

tasks.test {
    dependsOn("copyFabricTestMod")
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}
