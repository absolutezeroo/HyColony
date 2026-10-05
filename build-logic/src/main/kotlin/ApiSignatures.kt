import java.io.File
import java.lang.reflect.AnnotatedType
import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URLClassLoader

/**
 * The public signatures of the compiled classes under [packageName] in [classesDirs], its sub-packages included:
 * each public type (its declaration, supertypes and permitted subtypes), then its public and protected members, sorted.
 * What carries the annotation [experimentalName] (each api has its own, api spec § 4.1), or sits in a type or package
 * that does, is left out. Classes are loaded with [classpath], never initialised; the Gradle daemon's JVM must read the
 * compiled class files. Not seen: constants' values, annotation elements' defaults and declaration annotations
 * (@Deprecated).
 */
fun apiSignatures(
    classesDirs: Set<File>,
    classpath: Set<File>,
    packageName: String,
    experimentalName: String,
): List<String> {
    val urls = (classesDirs + classpath).map { it.toURI().toURL() }.toTypedArray()
    return URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
        @Suppress("UNCHECKED_CAST")
        val experimental = loader.loadClass(experimentalName) as Class<out Annotation>
        classNames(classesDirs, packageName)
            .map { Class.forName(it, false, loader) }
            .filter { isStable(it, experimental) }
            .sortedBy { it.name }
            .flatMap { listOf(declaration(it)) + members(it, experimental).map { m -> "    $m" } }
    }
}

private fun classNames(classesDirs: Set<File>, packageName: String): List<String> {
    val path = packageName.replace('.', '/')
    return classesDirs.map { File(it, path) }.filter { it.isDirectory }.flatMap { dir ->
        dir.walk()
            .filter { it.isFile && it.name.endsWith(".class") && !it.name.startsWith("package-info") }
            .map { it.relativeTo(dir).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.') }
            .map { "$packageName.$it" }
            .toList()
    }
}

/** Named, public with every enclosing type, and neither it, an enclosing type nor its package is experimental. */
private fun isStable(type: Class<*>, experimental: Class<out Annotation>): Boolean {
    if (type.isAnonymousClass || type.isLocalClass || type.isSynthetic) return false
    val enclosing = generateSequence(type) { it.enclosingClass }.toList()
    return enclosing.all { Modifier.isPublic(it.modifiers) && !it.isAnnotationPresent(experimental) } &&
        !type.`package`.isAnnotationPresent(experimental)
}

private fun declaration(type: Class<*>): String {
    val parts = mutableListOf(type.toGenericString())
    type.genericSuperclass?.takeIf { it != Any::class.java }?.let { parts += "extends ${it.typeName}" }
    if (type.genericInterfaces.isNotEmpty()) {
        parts += "implements " + type.genericInterfaces.joinToString(", ") { it.typeName }
    }
    type.permittedSubclasses?.let { parts += "permits " + it.map { c -> c.name }.sorted().joinToString(", ") }
    return parts.joinToString(" ")
}

/**
 * Its public and protected constructors, methods and fields, with those it inherits from non-public superclasses
 * (javac shows them to callers through synthetic bridges), each followed by its types' annotations when it has any.
 */
private fun members(type: Class<*>, experimental: Class<out Annotation>): List<String> {
    fun visible(m: Member) = !m.isSynthetic && (Modifier.isPublic(m.modifiers) || Modifier.isProtected(m.modifiers))
    val hidden = generateSequence(type.superclass) { it.superclass }.takeWhile { !Modifier.isPublic(it.modifiers) }
    val constructors = type.declaredConstructors.filter { visible(it) }.map { it.toGenericString() + typeUse(it) }
    val methods = (sequenceOf(type) + hidden).flatMap { it.declaredMethods.asSequence() }
        .filter { visible(it) && !it.isBridge && !it.isAnnotationPresent(experimental) }
        .map { it.toGenericString() + typeUse(it) }
    val fields = (sequenceOf(type) + hidden).flatMap { it.declaredFields.asSequence() }
        .filter { visible(it) }
        .map { it.toGenericString() + typeUse(listOf(it.annotatedType)) }
    return (constructors + methods + fields).sorted()
}

/** Its return and parameter types with their annotations (jspecify's @Nullable), or "" when none has any. */
private fun typeUse(e: Executable): String =
    typeUse(listOfNotNull((e as? Method)?.annotatedReturnType) + e.annotatedParameterTypes)

private fun typeUse(types: List<AnnotatedType>): String {
    val shown = types.map { it.toString() }
    return if (shown.any { '@' in it }) " [" + shown.joinToString(", ") + "]" else ""
}
