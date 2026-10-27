# Alu personal build

This is an independent, unsigned variant of Aerial, not an update to the official app.
The normal build remains unchanged unless `-PaluBuild=true` is supplied. Lite is not configured in that mode because it has a separate signing setup.

## Build without signing

The approved private image must exist at `local/alu-assets/alu-wallpaper.png`. It is copied into generated build assets only; do not add it or other `local/` files to version control. The APK contains this private artwork, so do not publish it.

Set `JAVA_HOME` to an installed JDK 17 and `ANDROID_HOME` to an Android SDK containing platform 36 and Build Tools 36.0.0. Keep machine-specific paths outside version control. Use an isolated Gradle user home to avoid loading unrelated global properties or init scripts; an existing dependency cache can be reused without copying configuration:

```bash
ALU_GRADLE_HOME="$HOME/.cache/aerial-alu-gradle"
mkdir -p "$ALU_GRADLE_HOME"
# Optional: omit this block to let Gradle download its own dependency cache.
if [ -d "$HOME/.gradle/caches" ] && [ ! -e "$ALU_GRADLE_HOME/caches" ]; then
  ln -s "$HOME/.gradle/caches" "$ALU_GRADLE_HOME/caches"
fi

./gradlew --gradle-user-home "$ALU_GRADLE_HOME" --no-daemon --no-configuration-cache \
  -PaluBuild=true :app:assembleStandardRelease \
  :app:testStandardReleaseUnitTest \
  --tests '*AluHomeTest' --tests '*AluWallpaperTest' --tests '*AluCameraTest' --tests '*AluPrivacyTest'
node tools/test-alu-privacy-web.cjs
```

Output: `app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk`.
Release retains R8 optimization, resource shrinking and baseline profiles. There is no release or debug signing configuration in personal mode; it does not read `local/signing.properties` or use the debug keystore. Signing and installation are separate owner-controlled steps, not performed by this build.

## Isolation

- Package: `app.orchard.tv.alu`; label: `Alu · Aerial`; version suffix: `-alu`.
- Classes stay in the original namespace. The launch component is **`app.orchard.tv.alu/app.orchard.tv.MainActivity`**, not `app.orchard.tv.alu/.MainActivity`.
- CONFIG action: `app.orchard.tv.alu.CONFIG`; receiver class: `app.orchard.tv.guard.ConfigReceiver`. Preview action is also package-specific.
- Providers, sync account type, preferences and internal files belong to the personal package. Phone settings uses **8791**; the official app keeps 8790.
- No upstream update checks/download/install offer; the upstream setup-install QR is omitted. The personal guard does not classify the official app as a firmware HOME to take over.
- Installing a sibling does not by itself reconcile two enabled launcher guards. HOME assignment and device permissions remain the installer's responsibility. Do not disable/uninstall the official app or system apps as part of this build.

## Home behavior

The shelf shows the first five items in the saved arrangement plus All apps. Initial defaults put the four approved installed apps first and other system entries in System tools; nothing is uninstalled, disabled or automatically hidden. All apps (or Down) opens the full existing arrangement, including folders and the remaining apps. The personal grid uses one subdued frosted background from entry; its title/status stay fixed outside the clipped scrolling area. Rows scroll only when focus would leave that viewport, so two-row lists do not shift. Back returns to the shelf; HOME resets to its first target. Long press opens the existing menu; arranging uses the full grid. Names are always visible and app icons come from installed packages, without white-logo recipes or automatic online replacements. Custom artwork explicitly supplied later still takes precedence.

Personal-only lower-area shading is baked into the existing backdrop, with a treatment key that refreshes old generated backdrops without touching photos or preferences. The status pill and generic navigation icons have subdued dark surfaces; original app art is only clipped to a consistent rounded outline. TV personalization hides legacy layout, clock placement/size and focus-style rows that do not apply to the fixed personal design, while keeping effective settings. The phone page likewise hides its inapplicable focus-style selector in this build.

The supplied wallpaper is imported only before any wallpaper selection exists. Later choices and deletion are not overwritten. Photo wallpaper has a 100-second rigid pan/zoom cycle using the existing bitmap draw, with enough overscan to cover its edges. It pauses behind apps, menus, the full grid and screensavers. Reduced motion and the existing Lite-effects fallback keep it static. Photo-screensaver Ken Burns and screensaver text drift remain; the personal home clock and shelf fade away rather than duplicating the saver clock.

## Home heading (75 / 1.60-alu.3)

The personal home heading is **夜航 / Night Voyage**. It is a decorative label, not a room name inferred from the device or network. This update only changes that text and the personal version; saved layouts, wallpaper and network choices remain intact.

## Personal network choices (74 / 1.60-alu.2)

- Weather defaults/migrates from automatic location to Shanghai (31.2222, 121.4581); explicit city choices and the weather switch are preserved. No public-IP lookup or automatic-location button/API. Turning weather off cancels its job and disconnects active weather/geocoder requests. Local city choices still save offline; unknown cities need weather enabled for the remote lookup. Turning it back on refreshes.
- Bing is fetched only with an explicit `daily:latest` or dated daily wallpaper selection. Local startup, photo/clock fallback and switching away do not authorize downloads; switching away cancels daily work and retries without clearing caches. Empty-cache TV and phone pickers still offer Daily. Deleting the selected photo or last selected video falls back to a builtin rather than opting into Bing.
- Personal outbound clients use HTTPS, with no weather or Apple HTTP downgrade. The strict manifest configuration retains Apple's CA trust; the inbound phone server remains on 8791. Weather/Bing redirects fail closed. Older TVs with unsupported TLS may therefore show no fresh weather/video instead of silently downgrading.
- Ordinary builds and Lite keep their existing policies. The personal APK remains unsigned and private.

## Validation boundary

Privacy tests exercise the real Weather/Wallpapers objects with fake URLConnections at the request boundary (no live cloud traffic): migration, visibility cancellation/resume, city lookup, daily opt-in/cancellation/retries, TLS failure and cleartext/redirect rejection. The Node test runs the shipped phone renderers against a tiny DOM. These are not packet captures or real-TV/network verification.

Unit tests cover default placement, accessibility of every listed app, persistence after editing, temporary package loss, shelf projection, incomplete-row navigation, first-use wallpaper preservation, and camera edge coverage/seam continuity. Compilation and tests do not establish real-TV focus, readability or performance. Check those on the optimized, separately signed release before treating the experience as accepted.
