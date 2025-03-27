plugins {
    java
    application
    id("io.freefair.lombok") version "8.4"  // Альтернативный плагин для Lombok
}

group = "org.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    compileOnly("org.projectlombok:lombok:1.18.30")  // Минимальная версия для JDK 21
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--release", "21"))
}
application {
    mainClass.set("org.example.Main")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

