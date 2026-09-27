plugins { `java-library` }

val gsonVersion = "2.11.0"

dependencies {
    // Fourni à l'exécution par le serveur Hytale.
    compileOnly("com.google.code.gson:gson:$gsonVersion")

    testImplementation("com.google.code.gson:gson:$gsonVersion")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // ArchUnit 1.4.1+ lit le format de classe de Java 25 (version 69) : le cœur suit la toolchain Java 25.
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
}

tasks.test { useJUnitPlatform() }
