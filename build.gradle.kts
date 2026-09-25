subprojects {
    apply(plugin = "java")

    group = rootProject.property("group").toString()
    version = rootProject.property("version").toString()

    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(rootProject.property("java_version").toString().toInt()))
    }

    tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
    tasks.withType<Javadoc>().configureEach {
        (options as org.gradle.external.javadoc.StandardJavadocDocletOptions).addStringOption("Xdoclint:-missing", "-quiet")
    }
}
