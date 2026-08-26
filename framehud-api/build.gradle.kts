import java.io.PrintWriter
import java.io.StringWriter
import java.util.spi.ToolProvider

plugins {
    alias(libs.plugins.framehud.android.library)
    alias(libs.plugins.framehud.publish)
}

dependencies {
    api(libs.androidx.compose.runtime.annotation)
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}

val checkModelDependencies by tasks.registering {
    group = "verification"
    description = "Fails when the compiled model types depend on anything beyond the JDK, Kotlin and two platform classes."

    val classes = files(
        tasks.named("compileDebugKotlin"),
        tasks.named("compileReleaseKotlin"),
    )
    inputs.files(classes)

    doLast {
        val allowedPackages = listOf("java.", "kotlin.", "com.timkrest.framehud.")
        val allowedPlatformClasses = setOf("android.os.Build", "android.util.Log")

        val jdeps = ToolProvider.findFirst("jdeps")
            .orElseThrow { GradleException("The JDK running Gradle ships no jdeps to read the class files with.") }
        val compiled = classes.files.map { it.path }
        if (compiled.isEmpty()) error("Nothing was compiled for framehud-api, so its dependencies cannot be read.")

        val report = StringWriter()
        val into = PrintWriter(report)
        val status = jdeps.run(into, into, "-verbose:class", *compiled.toTypedArray())
        into.flush()
        if (status != 0) error("jdeps could not read the compiled framehud-api:\n$report")

        val dependency = Regex("^\\s+\\S+\\s+->\\s+(\\S+)", RegexOption.MULTILINE)
        val depended = dependency.findAll(report.toString())
            .map { it.groupValues[1].substringBefore('$') }
            .toSet()
        val added = depended
            .filterNot { it in allowedPlatformClasses || allowedPackages.any(it::startsWith) }
            .sorted()
        val gone = (allowedPlatformClasses - depended).sorted()
        if (added.isNotEmpty() || gone.isNotEmpty()) {
            error(
                buildString {
                    appendLine("framehud-api depends on something other than what the model types are allowed.")
                    if (added.isNotEmpty()) appendLine("Depends now on: ${added.joinToString()}")
                    if (gone.isNotEmpty()) appendLine("No longer depends on: ${gone.joinToString()}")
                    appendLine("Keep it out of the model types, or amend the lists in this file.")
                },
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkModelDependencies)
}
