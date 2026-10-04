package app.tide.launcher.data

import android.content.ComponentName
import app.tide.launcher.ui.theme.TideTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the search scorer, which is the one piece of real logic in the launcher
 * that has no Android dependency and can therefore be tested exhaustively.
 */
class FuzzyTest {

    private fun entry(label: String) = AppEntry(
        packageName = "test.${label.lowercase()}",
        className = "$label.Activity",
        label = label,
        component = ComponentName("test.${label.lowercase()}", "$label.Activity"),
    )

    // ── score ───────────────────────────────────────────────────────────────

    @Test
    fun `characters must appear in order`() {
        assertEquals(Fuzzy.NO_MATCH, Fuzzy.score("Gmail", "mlg"))
    }

    @Test
    fun `characters need not be adjacent`() {
        assertNotEquals(Fuzzy.NO_MATCH, Fuzzy.score("Gmail", "gm"))
    }

    @Test
    fun `consecutive run outranks a scattered match`() {
        val run = Fuzzy.score("Maps", "maps")
        val scattered = Fuzzy.score("Search Anything", "maps")
        assertTrue("'$run' should outscore '$scattered'", run > scattered)
    }

    @Test
    fun `first character matching at index zero earns no run bonus`() {
        // "am" over "Amaze" exercises every weight exactly once:
        //   a at index 0  → BASE(16) + BOUNDARY(30)          = 46
        //   m at index 1  → BASE(16) + EXACT_CASE(2) + CONSECUTIVE*1(20) = 38
        //   length penalty                                        −1
        //                                                        ----
        //                                                         83
        // If the first character also collected a run bonus this would be 103,
        // and since most labels start with the first character of the query,
        // that would flatten the signal across every candidate.
        assertEquals(83, Fuzzy.score("Amaze", "am"))
    }

    @Test
    fun `prefix of the label outranks a scattered match`() {
        val prefix = Fuzzy.score("Chrome", "ch")
        val scattered = Fuzzy.score("Character", "ca")
        assertTrue(prefix > scattered)
    }

    @Test
    fun `query longer than candidate cannot match`() {
        assertEquals(Fuzzy.NO_MATCH, Fuzzy.score("Maps", "mapsx"))
    }

    @Test
    fun `empty query matches everything`() {
        assertEquals(0, Fuzzy.score("Anything", ""))
        assertEquals(0, Fuzzy.score("Anything", "   "))
    }

    @Test
    fun `case is ignored`() {
        assertNotEquals(Fuzzy.NO_MATCH, Fuzzy.score("SETTINGS", "set"))
    }

    // ── rank ────────────────────────────────────────────────────────────────

    @Test
    fun `gm finds gmail`() {
        val results = Fuzzy.rank(
            listOf(entry("Settings"), entry("Gmail"), entry("Maps")),
            "gm",
        )
        assertEquals("Gmail", results.first().label)
    }

    @Test
    fun `exact label wins outright`() {
        val results = Fuzzy.rank(
            listOf(entry("Files"), entry("Files by Google")),
            "files",
        )
        assertEquals("Files", results.first().label)
    }

    @Test
    fun `prefix matches rank above scattered ones`() {
        val results = Fuzzy.rank(
            listOf(entry("Search Anything"), entry("Calendar"), entry("Camera")),
            "ca",
        )
        // Calendar and Camera both *start* with the query; "Search Anything"
        // only matches it as a gapped subsequence, so it must sink below both.
        assertEquals("Search Anything", results.last().label)
        assertTrue(results.indexOfFirst { it.label == "Calendar" } < results.lastIndex)
        assertTrue(results.indexOfFirst { it.label == "Camera" } < results.lastIndex)
    }

    @Test
    fun `blank query returns input unchanged`() {
        val apps = listOf(entry("One"), entry("Two"))
        assertEquals(apps, Fuzzy.rank(apps, "   "))
    }

    @Test
    fun `no match returns empty`() {
        val results = Fuzzy.rank(listOf(entry("Settings"), entry("Maps")), "zzz")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `candidates that do not match are excluded`() {
        val results = Fuzzy.rank(
            listOf(entry("Chrome"), entry("Calendar"), entry("Maps")),
            "mm",
        )
        assertTrue(results.none { it.label == "Calendar" })
    }

    @Test
    fun `ranking is stable across repeated calls`() {
        val apps = listOf(entry("Chrome"), entry("Calendar"), entry("Clock"))
        val first = Fuzzy.rank(apps, "c").map { it.label }
        val second = Fuzzy.rank(apps, "c").map { it.label }
        assertEquals(first, second)
    }

    // ── alphabet bucketing ──────────────────────────────────────────────────

    @Test
    fun `alphabet is the uppercased first letter`() {
        assertEquals('C', Fuzzy.alphabetOf("chrome"))
        assertEquals('G', Fuzzy.alphabetOf("Gmail"))
    }

    @Test
    fun `non-letter leading characters bucket under hash`() {
        assertEquals('#', Fuzzy.alphabetOf("1Password"))
        assertEquals('#', Fuzzy.alphabetOf(""))
    }
}

/** Guards the persisted-settings invariants that would otherwise fail silently. */
class SettingsTest {

    @Test
    fun `column count is clamped to the supported range`() {
        assertEquals(3, TideSettings.clampColumns(1))
        assertEquals(6, TideSettings.clampColumns(99))
        assertEquals(4, TideSettings.clampColumns(4))
    }

    @Test
    fun `unknown theme name falls back to the default`() {
        assertEquals(TideTheme.Default, TideTheme.fromName("NotATheme"))
        assertEquals(TideTheme.Default, TideTheme.fromName(null))
    }

    @Test
    fun `known theme name round-trips`() {
        TideTheme.entries.forEach { theme ->
            assertEquals(theme, TideTheme.fromName(theme.name))
        }
    }

    @Test
    fun `unknown icon shape falls back to squircle`() {
        assertEquals(IconShape.Squircle, IconShape.fromName("NotAShape"))
        assertEquals(IconShape.Squircle, IconShape.fromName(null))
    }
}