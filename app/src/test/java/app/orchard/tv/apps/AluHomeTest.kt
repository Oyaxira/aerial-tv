package app.orchard.tv.apps

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.ColorDrawable
import app.orchard.tv.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AluHomeTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val art by lazy { TileArtFactory.originalIcon(ColorDrawable(android.graphics.Color.RED)) }
    private fun app(key: String, system: Boolean = false) = AppEntry(key, key, ComponentName(key, "$key.Main"), false, !system, art)
    private val installed get() = AluHome.favorites.reversed().map { app(it, true) } +
        listOf(app("other.video"), app("com.android.contacts", true), app("com.android.settings", true))
    private fun personalStore() = LayoutStore(context, AluHome.favorites, context.getString(R.string.alu_system_tools))
    private fun arrange(store: LayoutStore, apps: List<AppEntry> = installed) = store.arrange(apps) { folder, _ -> folderEntry(folder, art) }

    @Before fun clear() { context.getSharedPreferences("layout", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun firstRunSeedsFavoritesAndToolsWithoutHidingAnyApp() {
        val result = arrange(personalStore())
        assertEquals(AluHome.favorites, result.home.take(4).map { it.key })
        val folder = result.home[4].folder!!
        assertEquals(context.getString(R.string.alu_system_tools), folder.name)
        assertEquals(setOf("com.android.contacts", "com.android.settings"), result.folders[folder.id]!!.map { it.key }.toSet())
        assertTrue(result.hidden.isEmpty())
        val reachable = result.home.filter { it.folder == null } + result.folders.values.flatten()
        assertEquals(installed.map { it.key }.toSet(), reachable.map { it.key }.toSet())
    }

    @Test fun editedOrderAndFolderPersistAndNewAppsRemainReachable() {
        val store = personalStore()
        arrange(store)
        store.move("other.video", AluHome.favorites.first())
        store.renameFolder("initial-tools", "My tools")
        store.takeOutOfFolder("com.android.contacts")
        val before = arrange(store)
        val after = arrange(personalStore(), installed + app("new.video"))
        assertEquals(before.home.map { it.key }, after.home.dropLast(1).map { it.key })
        assertEquals("other.video", after.home.first().key)
        assertEquals("My tools", after.home.first { it.folder != null }.folder!!.name)
        assertTrue(after.home.any { it.key == "com.android.contacts" })
        assertEquals("new.video", after.home.last().key)
    }

    @Test fun emptyScanDoesNotConsumeInitialDefaults() {
        val store = personalStore()
        assertTrue(arrange(store, emptyList()).home.isEmpty())
        assertEquals(AluHome.favorites, arrange(store).home.take(4).map { it.key })
    }

    @Test fun temporarilyMissingAppReturnsToItsSavedPosition() {
        val store = personalStore()
        val before = arrange(store).home.map { it.key }
        arrange(store, installed.filterNot { it.key == AluHome.favorites[1] })
        assertEquals(before, arrange(personalStore()).home.map { it.key })
    }

    @Test fun omittedDefaultsKeepTheSharedStoreBehavior() {
        val result = arrange(LayoutStore(context), listOf(app("com.android.tv.settings", true),
            app("com.xiaomi.mitv.settings", true), app("com.ktcp.video")))
        assertEquals("com.ktcp.video", result.home.first().key)
        assertTrue(result.folders.isEmpty())
        assertEquals(listOf("com.android.tv.settings"), result.hidden.map { it.key })
    }

    @Test fun changedCallerDefaultsDoNotReplaceAnExistingArrangement() {
        val before = arrange(personalStore())
        val after = arrange(LayoutStore(context, listOf("other.video"), "Different tools"))
        assertEquals(before.home.map { it.key }, after.home.map { it.key })
        assertEquals(before.home.first { it.folder != null }.folder, after.home.first { it.folder != null }.folder)
    }

    @Test fun shelfProjectionDoesNotMutateOrLoseTheFullGrid() {
        val grid = (0..16).toList()
        assertEquals(listOf(0, 1, 2, 3, 4, -1), AluHome.shelf(grid, -1))
        assertEquals((0..16).toList(), grid)
        assertEquals(listOf(0, -1), AluHome.shelf(listOf(0), -1))
    }

    @Test fun verticalNavigationClampsIncompleteRows() {
        assertEquals(9, AluHome.verticalTarget(4, 10, 6, down = true))
        assertEquals(3, AluHome.verticalTarget(9, 10, 6, down = false))
        assertEquals(9, AluHome.verticalTarget(9, 10, 6, down = true))
        assertEquals(6, AluHome.verticalTarget(6, 10, 6, down = true))
        assertEquals(-1, AluHome.verticalTarget(0, 0, 6, down = true))
    }

    @Test fun twoVisibleRowsDoNotScroll() {
        assertEquals(0f, AluHome.scrollToReveal(0f, 120f, 220f, 100f, 500f, 0f), 0f)
        assertEquals(0f, AluHome.scrollToReveal(0f, 240f, 340f, 100f, 500f, 0f), 0f)
    }

    @Test fun overflowScrollsOnlyEnoughToRevealTheFocusedRow() {
        val down = AluHome.scrollToReveal(0f, 480f, 580f, 100f, 500f, 500f)
        assertEquals(80f, down, 0f)
        // A nearby row still fits, so changing focus must not move the page again.
        assertEquals(down, AluHome.scrollToReveal(down, 360f, 460f, 100f, 500f, 500f), 0f)
        val up = AluHome.scrollToReveal(down, 120f, 220f, 100f, 500f, 500f)
        assertEquals(100f, 120f - up, 0f)
    }

    @Test fun revealStaysWithinContentBounds() {
        assertEquals(0f, AluHome.scrollToReveal(70f, 90f, 190f, 100f, 500f, 300f), 0f)
        assertEquals(300f, AluHome.scrollToReveal(200f, 800f, 900f, 100f, 500f, 300f), 0f)
    }

}
