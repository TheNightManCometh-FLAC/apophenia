package com.betterappmafia.apophenia.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every UI file's top-level values (menus of meters, keys, tempo words…) are built when its screen first opens. One
 * that throws takes the whole screen down: METERS held a bare "5/8", which TimeSignature refuses, and every tab and
 * sheet-music editor crashed on opening (found in the emulator, 26 Oct 2026). This loads each file's class, which
 * builds those values, and fails on any that throw.
 */
class ScreenTablesTest {
    @Test fun every_screen_file_s_top_level_values_build() {
        val loader = javaClass.classLoader!!
        // The app's compiled classes (the package also exists among the test classes, so ask a class that's only in the app).
        val root = File(Class.forName("com.betterappmafia.apophenia.ui.ShellKt", false, loader).protectionDomain.codeSource.location.toURI())
        val pkg = "com/betterappmafia/apophenia/ui/"
        val all = if (root.isDirectory) File(root, pkg).listFiles()!!.map { it.name }
        else java.util.jar.JarFile(root).use { j -> j.entries().toList().map { it.name }.filter { it.startsWith(pkg) && '/' !in it.removePrefix(pkg) }.map { it.removePrefix(pkg) } }
        val files = all.filter { it.endsWith("Kt.class") && '$' !in it }
        assertTrue("found the screen files", files.size > 20)
        val failures = files.mapNotNull { f ->
            val name = "com.betterappmafia.apophenia.ui." + f.removeSuffix(".class")
            runCatching { Class.forName(name, true, loader) }.exceptionOrNull()?.let { e ->
                var cause: Throwable = e
                while (cause.cause != null) cause = cause.cause!!
                "$name: ${cause.javaClass.simpleName}: ${cause.message}"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun the_meter_menu_has_5_8_and_7_8_by_grouping() {
        assertTrue(METERS.map { it.toString() }.containsAll(listOf("2+3/8", "3+2/8", "2+2+3/8", "3+2+2/8")))
    }
}
