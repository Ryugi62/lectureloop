package io.github.ryugi62.lectureloop.application

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Domain and application may not import frameworks, SDKs or I/O (SPEC §7, AC-16). */
class LayeringTest {
    private val forbidden = listOf("android.", "androidx.", "com.revenuecat", "okhttp3", "java.io.File", "kotlinx.serialization", "java.net.")

    @Test fun innerLayersImportNoFrameworkOrIo() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val offenders = listOf("domain/src/main", "application/src/main")
            .flatMap { File(root, it).walkTopDown().filter { f -> f.extension == "kt" }.toList() }
            .flatMap { file ->
                file.readLines().filter { it.startsWith("import ") }
                    .filter { line -> forbidden.any { line.removePrefix("import ").startsWith(it) } }
                    .map { "${file.name}: $it" }
            }
        assertEquals(emptyList(), offenders)
    }
}
