package de.crunchymate.ui.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteUrlTest {
    @Test
    fun httpsUrl_isOpenable() {
        assertTrue(isOpenableSiteUrl("https://anilist.co/anime/1"))
        assertTrue(isOpenableSiteUrl("HTTPS://anilist.co/anime/1"))
    }

    @Test
    fun otherSchemesAndBlank_areNotOpenable() {
        assertFalse(isOpenableSiteUrl("http://anilist.co/anime/1"))
        assertFalse(isOpenableSiteUrl("intent://x#Intent;end"))
        assertFalse(isOpenableSiteUrl("javascript:alert(1)"))
        assertFalse(isOpenableSiteUrl(""))
    }
}
