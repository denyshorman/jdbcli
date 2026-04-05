plugins {
    java
    application
    id("com.gradleup.shadow") version "9.4.1"
}

group = "io.github.denyshorman.jdbcli"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("io.github.denyshorman.jdbcli.cli.MainCommand")
    mainModule.set("io.github.denyshorman.jdbcli")
}

dependencies {
    implementation("info.picocli:picocli:4.7.7")
    implementation("tools.jackson.core:jackson-databind:3.1.1")
    implementation("org.jspecify:jspecify:1.0.0")
    implementation("ch.qos.logback:logback-classic:1.5.32")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.testcontainers:testcontainers:2.0.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("org.testcontainers:mssqlserver:1.21.4")
    testImplementation("org.testcontainers:oracle-xe:1.21.4")
    testImplementation("org.testcontainers:mongodb:1.21.4")

    testImplementation("org.postgresql:postgresql:42.7.10")
    testImplementation("com.microsoft.sqlserver:mssql-jdbc:13.4.0.jre11")
    testImplementation("com.oracle.database.jdbc:ojdbc11:23.26.1.0.0")
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "io.github.denyshorman.jdbcli.cli.MainCommand"
    }

    archiveBaseName.set("jdbcli")
    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.test {
    useJUnitPlatform()
}
