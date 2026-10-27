package app.orchard.tv.privacy

import android.content.Context
import android.os.Looper
import app.orchard.tv.BuildConfig
import app.orchard.tv.network.RevocableHttp
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.wallpaper.Aerial
import app.orchard.tv.wallpaper.AerialQuality
import app.orchard.tv.wallpaper.AerialStream
import app.orchard.tv.wallpaper.Wallpapers
import app.orchard.tv.weather.Weather
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** Exercises the production objects; only the final URLConnection boundary is replaced. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AluPrivacyTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val weatherPrefs get() = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val forecast = """{"current":{"temperature_2m":20,"weather_code":0,"is_day":1}}"""

    @Before fun resetWeather() {
        assumeTrue("Personal-build networking policy", BuildConfig.ALU_BUILD)
        field(Weather, "job")?.let { (it as Job).cancel() }
        setField(Weather, "started", false)
        setField(Weather, "lastAttempt", 0L)
        setField(AerialSettings, "started", false)
        Weather.personalHttp.setEnabled(false)
        weatherPrefs.edit().clear().commit()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun migrationToggleAndManualCitiesUseOnlyPermittedRequests() {
        val boundary = Boundary { forecast }
        Weather.personalHttp.openConnection = boundary::open
        val fetchedAt = System.currentTimeMillis()
        weatherPrefs.edit().putBoolean("chosen", false).putString("place", "上海")
            .putFloat("lat", 31.2f).putFloat("lon", 121.4f)
            .putLong("fetched_at", fetchedAt).putInt("celsius", 16).putString("kind", "Clear").commit()
        Weather.init(context)
        assertTrue(Weather.chosen)
        assertEquals(16, Weather.now!!.celsius)
        assertEquals(fetchedAt, weatherPrefs.getLong("fetched_at", 0))
        assertTrue(AerialSettings.showWeather)
        assertEquals(31.2222f, weatherPrefs.getFloat("lat", 0f), 0.00001f)
        assertEquals(121.4581f, weatherPrefs.getFloat("lon", 0f), 0.00001f)
        Weather.refreshIfStale()
        await { boundary.requests.size == 1 && Weather.now?.celsius == 20 }
        assertTrue(boundary.requests.single().contains("latitude=31.2222&longitude=121.4581"))
        assertFalse(boundary.requests.any { it.contains("ipinfo") })
        Weather.useNetworkLocation()
        drain()
        assertTrue(Weather.chosen)
        assertEquals(1, boundary.requests.size)

        AerialSettings.chooseShowWeather(false)
        Weather.refreshIfStale(true)
        Weather.useNetworkLocation()
        assertTrue(runBlocking { Weather.choose("北京") })
        assertFalse(runBlocking { Weather.choose("Unlisted-remote-city") })
        drain()
        assertEquals(1, boundary.requests.size)
        assertEquals("北京", Weather.place)
        assertTrue(Weather.chosen)

        AerialSettings.chooseShowWeather(true)
        await { boundary.requests.size == 2 }
        assertTrue(boundary.requests.last().contains("latitude=39."))
        AerialSettings.chooseShowWeather(false)
        boundary.reply = { address ->
            if (address.contains("geocoding-api")) """{"results":[{"latitude":35.68,"longitude":139.69,"name":"Tokyo","feature_code":"PPLC"}]}"""
            else forecast
        }
        AerialSettings.chooseShowWeather(true)
        assertTrue(runBlocking { Weather.choose("Tokyo") })
        await { boundary.requests.any { it.contains("latitude=35.68") } }
        assertEquals("Tokyo", Weather.place)
        assertTrue(boundary.requests.all { it.startsWith("https://") && !it.contains("ipinfo") })
        AerialSettings.chooseShowWeather(false)
    }

    @Test fun persistedHiddenWeatherAndExplicitCitySurviveInitialization() {
        val boundary = Boundary { forecast }
        Weather.personalHttp.openConnection = boundary::open
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("show_weather", false).commit()
        weatherPrefs.edit().putBoolean("chosen", true).putString("place", "Tokyo")
            .putFloat("lat", 35.68f).putFloat("lon", 139.69f).putBoolean("checked_against_cities", true).commit()
        Weather.init(context)
        Weather.refreshIfStale(true)
        drain()
        assertEquals("Tokyo", Weather.place)
        assertEquals(35.68f, weatherPrefs.getFloat("lat", 0f), 0f)
        assertTrue(boundary.requests.isEmpty())
        AerialSettings.chooseShowWeather(true)
        await { boundary.requests.size == 1 }
        assertTrue(boundary.requests.single().contains("latitude=35.68"))
        AerialSettings.chooseShowWeather(false)
    }

    @Test fun hidingWeatherDisconnectsActiveReadAndReenableStartsFreshHttpsRequest() {
        val boundary = Boundary { forecast }
        boundary.blockNext = true
        Weather.personalHttp.openConnection = boundary::open
        Weather.init(context)
        Weather.refreshIfStale()
        await { boundary.blocked != null }
        val blocked = boundary.blocked!!
        AerialSettings.chooseShowWeather(false)
        assertTrue(blocked.disconnected.await(2, TimeUnit.SECONDS))
        Weather.refreshIfStale(true)
        drain()
        assertEquals(1, boundary.requests.size)
        AerialSettings.chooseShowWeather(true)
        await { boundary.requests.size == 2 }
        assertTrue(boundary.requests.all { it.startsWith("https://api.open-meteo.com/") })
        AerialSettings.chooseShowWeather(false)
    }

    @Test fun weatherTlsFailureIsNeverRetriedOverHttp() {
        val boundary = Boundary { throw SSLException("test certificate failure") }
        Weather.personalHttp.openConnection = boundary::open
        Weather.init(context)
        Weather.refreshIfStale()
        await { boundary.requests.size == 1 && (field(Weather, "job") as Job).isCompleted }
        assertEquals(listOf("https"), boundary.requests.map { URL(it).protocol })
        AerialSettings.chooseShowWeather(false)
    }

    @Test fun dailyIsOptInAndSwitchingAwayRevokesImagesAndRetries() {
        val boundary = Boundary { """{"images":[]}""" }
        Wallpapers.personalDailyHttp.openConnection = boundary::open
        context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE).edit().clear()
            .putString("selected", "photo:test-local").commit()
        Wallpapers.init(context)
        Wallpapers.refreshDailyIfStale(true)
        runBlocking { Wallpapers.refreshDaily() }
        drain()
        assertTrue(boundary.requests.isEmpty())
        assertEquals("photo:test-local", Wallpapers.selectedId)
        // An empty cache is not a prerequisite for selection: explicit daily starts the fetch.
        Wallpapers.select(Wallpapers.LATEST_DAILY)
        await { boundary.requests.size == 1 && (field(Wallpapers, "dailyJob") as Job).isCompleted }
        assertTrue(boundary.requests.single().startsWith("https://cn.bing.com/HPImageArchive"))
        assertTrue((field(Wallpapers, "dailyRetry") as Job).isActive)
        Wallpapers.select(Wallpapers.AUTO)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(31))
        Wallpapers.refreshDailyIfStale(true)
        runBlocking { Wallpapers.refreshDaily() }
        drain()
        assertEquals(1, boundary.requests.size)

        boundary.reply = { address ->
            if (address.contains("HPImageArchive")) {
                boundary.blockNext = true
                """{"images":[{"startdate":"20990101","url":"/first.jpg"},{"startdate":"20990102","url":"/second.jpg"}]}"""
            } else "unused image bytes"
        }
        Wallpapers.select("daily:20990101")
        await { boundary.blocked != null }
        val blocked = boundary.blocked!!
        Wallpapers.select(Wallpapers.ROTATE_PHOTOS) // Its cached daily fallback is not consent.
        assertTrue(blocked.disconnected.await(2, TimeUnit.SECONDS))
        drain()
        Wallpapers.refreshDailyIfStale(true)
        runBlocking { Wallpapers.refreshDaily() }
        drain()
        assertEquals(3, boundary.requests.size) // Two indexes and only the first image.
        assertFalse(boundary.requests.any { it.endsWith("second.jpg") })
        assertNull(field(Wallpapers, "dailyRetry"))
        for (id in listOf("photo:test-local", "grad:aurora", Wallpapers.VIDEOS, Wallpapers.LIVE, Wallpapers.AUTO)) {
            Wallpapers.select(id)
            Wallpapers.refreshDailyIfStale(true)
            runBlocking { Wallpapers.refreshDaily() }
        }
        drain()
        assertEquals(3, boundary.requests.size)
    }

    @Test fun personalBoundaryRejectsCleartextAndRedirectsAndAppleItemsStayHttps() {
        val boundary = Boundary { "unused" }
        val http = RevocableHttp().apply { openConnection = boundary::open; setEnabled(true) }
        assertTrue(runCatching { http.read("http://example.invalid", 1, 1) }.isFailure)
        assertTrue(boundary.requests.isEmpty())
        boundary.code = 302
        assertTrue(runCatching { http.read("https://example.invalid", 1, 1) }.isFailure)
        assertEquals(1, boundary.requests.size)
        assertFalse(boundary.connections.single().instanceFollowRedirects)
        val film = Aerial("test", "test", "test", "https://sylvan.apple.com/hd", "https://sylvan.apple.com/uhd", "https://sylvan.apple.com/dv", emptyList())
        for (quality in AerialQuality.entries) {
            assertEquals("https", AerialStream.mediaItem(film, quality, overHttp = true).localConfiguration!!.uri.scheme)
        }
    }

    private fun drain() { repeat(20) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) } }
    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (!condition() && System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Timed out waiting for production work", condition())
    }
    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun setField(target: Any, name: String, value: Any) = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)

    private class Boundary(@Volatile var reply: (String) -> String) {
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val connections = Collections.synchronizedList(mutableListOf<Connection>())
        @Volatile var blockNext = false
        @Volatile var blocked: Connection? = null
        var code = 200
        fun open(url: URL): HttpURLConnection = Connection(url, this).also { connections.add(it) }
    }
    private class Connection(url: URL, private val boundary: Boundary) : HttpURLConnection(url) {
        val disconnected = CountDownLatch(1)
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected.countDown() }
        override fun getResponseCode(): Int { boundary.requests.add(url.toString()); return boundary.code }
        override fun getInputStream(): ByteArrayInputStream {
            if (boundary.blockNext) {
                boundary.blockNext = false
                boundary.blocked = this
                check(disconnected.await(5, TimeUnit.SECONDS)) { "Download was not revoked" }
                throw IOException("Disconnected")
            }
            return ByteArrayInputStream(boundary.reply(url.toString()).toByteArray())
        }
    }
}
