package com.betterappmafia.apophenia.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticesTest {
    private val assets = File("src/main/assets/licenses")

    @Test
    fun everyNoticeHasItsLicenceFileAndEveryLicenceFileIsListed() {
        val named = Notices.all.flatMap { it.files }.toSet()
        for (f in named) assertTrue("missing licence file $f", File(assets, f).isFile)
        assertEquals("licence files no notice points to", emptySet<String>(), assets.list()!!.toSet() - named)
        assertTrue(File(assets, "apophenia-GPL-3.0.txt").readText().contains("Version 3, 29 June 2007"))
        assertTrue(File(assets, "apache-2.0.txt").readText().contains("Version 2.0, January 2004"))
    }

    @Test
    fun theManifestHasNoInternetPermission() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse("the privacy text says there is no internet permission", manifest.contains("android.permission.INTERNET"))
    }

    @Test
    fun everyAssetModelAndFontHasANotice() {
        // Models in assets/models and fonts in res/font each need an entry.
        val text = Notices.all.joinToString(" ") { it.name + " " + it.use }.lowercase()
        for (m in File("src/main/assets/models").list()!!) assertTrue("no notice for model $m", listOf("basic-pitch" to "basic pitch", "moonshine" to "moonshine", "spleeter" to "spleeter").any { (k, v) -> m.startsWith(k) && v in text })
        for (f in File("src/main/res/font").list()!!) assertTrue("no notice for font $f", listOf("bravura", "jetbrains", "noto").any { f.startsWith(it) && it.replace("jetbrains", "jetbrains mono") in text })
    }
}
