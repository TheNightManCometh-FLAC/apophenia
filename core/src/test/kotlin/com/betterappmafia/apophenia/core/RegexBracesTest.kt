package com.betterappmafia.apophenia.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Desktop Java takes a bare `{` or `}` in a pattern as a literal; Android's ICU regex refuses it and throws. The
 * drum chart reader's `{meter: …}` pattern had one, every unit test passed, and every drum chart crashed the app on
 * the phone (emulator pass, 26 Oct 2026). These tests run on the JVM, so this reads the source instead: every
 * pattern in the app's code must escape its braces unless they are a quantifier ({2}, {1,3}) or a class (\p{L}).
 */
class RegexBracesTest {
    /** A pattern written in the code: a raw string (group 1) or a plain one (group 2, still in Kotlin escapes). */
    private val literal = Regex("(?:Regex|Pattern\\.compile)\\(\\s*(?:\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\]|\\\\.)*)\")")

    private fun bare(pattern: String): Boolean {
        val rest = pattern
            .replace(Regex("""\$\{[^}]*\}"""), "")        // Kotlin string templates
            .replace(Regex("""\\+[{}]"""), "")            // escaped braces
            .replace(Regex("""\\\\?p\{\w+\}"""), "")      // \p{L}, \p{Cntrl}
            .replace(Regex("""\{\d+(,\d*)?\}"""), "")      // quantifiers
        return '{' in rest || '}' in rest
    }

    @Test fun no_pattern_in_the_code_has_a_bare_brace() {
        val roots = listOf("src/main", "../app/src/main", "../data/src").map(::File).filter { it.isDirectory }
        assertTrue("found the sources", roots.isNotEmpty())
        val bad = roots.flatMap { r -> r.walkTopDown().filter { it.extension == "kt" }.toList() }.flatMap { f ->
            f.readLines().withIndex().flatMap { (n, line) ->
                literal.findAll(line).mapNotNull { m -> (m.groups[1] ?: m.groups[2])?.value?.takeIf(::bare)?.let { "${f.name}:${n + 1}: $it" } }.toList()
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun the_check_catches_the_drum_chart_pattern_as_it_was() {
        assertTrue(bare("""^\{\s*(\w+)\s*:\s*(.*?)\s*}$"""))
        assertTrue(!bare("""^\{\s*(\w+)\s*:\s*(.*?)\s*\}$"""))
        assertTrue(!bare("""^([A-Za-z0-9]{2})\s*\|(.*)$"""))
        assertTrue(!bare("[\\\\/:*?\"<>|\\p{Cntrl}]"))
    }
}
