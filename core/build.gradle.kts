plugins { `java-library` }

val gsonVersion = "2.11.0"

dependencies {
    // Fourni à l'exécution par le serveur Hytale.
    compileOnly("com.google.code.gson:gson:$gsonVersion")

    testImplementation("com.google.code.gson:gson:$gsonVersion")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

// Bytecode Java 21 : ArchUnit (ASM) ne lit pas encore de façon fiable le format de classe de Java 25.
// Le plugin (Java 25) charge sans problème des classes Java 21.
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }

tasks.test { useJUnitPlatform() }
