plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
    id("application")
}

group = "ru.skokova.chatwithygpt"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Client
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")

    // JSON сериализация
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Логирование Ktor (опционально)
    implementation("io.ktor:ktor-client-logging:2.3.7")
    implementation("org.slf4j:slf4j-simple:2.0.9")

    // Тестирование
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("ru.skokova.chatwithygpt.MainKt")

    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "ru.skokova.chatwithygpt.MainKt"
    }
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
