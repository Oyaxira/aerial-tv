package app.orchard.tv.wallpaper

import android.content.Context
import app.orchard.tv.BuildConfig
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AluWallpaperTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)
    private val photo get() = File(context.filesDir, "photos/alu-original.jpg")

    @Before fun clear() { prefs.edit().clear().commit(); photo.delete() }

    @Test fun importsOriginalBytesOnlyOnce() {
        assumeTrue("Private artwork is packaged only in personal builds", BuildConfig.ALU_BUILD)
        AluWallpaper.seed(context)
        assertEquals("photo:alu-original", prefs.getString("selected", null))
        assertArrayEquals(context.assets.open("alu-wallpaper.png").use { it.readBytes() }, photo.readBytes())
        prefs.edit().putString("selected", "grad:other").commit()
        photo.delete()
        AluWallpaper.seed(context)
        assertEquals("grad:other", prefs.getString("selected", null))
        assertFalse(photo.exists())
    }

    @Test fun doesNotOverwriteAnExistingSelectionEvenBeforeMarker() {
        prefs.edit().putString("selected", "photo:owners-choice").commit()
        AluWallpaper.seed(context)
        assertEquals("photo:owners-choice", prefs.getString("selected", null))
        assertFalse(photo.exists())
        assertTrue(prefs.getBoolean("alu_initialized", false))
    }
}
