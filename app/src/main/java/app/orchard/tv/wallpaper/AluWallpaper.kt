package app.orchard.tv.wallpaper

import android.content.Context
import java.io.File

/** Imports the approved private artwork once; subsequent choices and deletions belong to the owner. */
internal object AluWallpaper {
    fun seed(context: Context) {
        val prefs = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)
        if (prefs.getBoolean("alu_initialized", false)) return
        val edit = prefs.edit()
        if (!prefs.contains("selected")) {
            // The photo store uses .jpg names; BitmapFactory detects the original PNG bytes.
            // Keep those bytes unmodified, rather than applying another lossy encoding.
            val destination = File(context.filesDir, "photos/alu-original.jpg")
            destination.parentFile?.mkdirs()
            context.assets.open("alu-wallpaper.png").use { input ->
                destination.outputStream().use { input.copyTo(it) }
            }
            edit.putString("selected", "photo:alu-original")
        }
        edit.putBoolean("alu_initialized", true).apply()
    }
}
