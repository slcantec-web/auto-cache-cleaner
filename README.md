# Auto Cache Cleaner

Lightweight Android app that clears cache on demand, reminds you on a schedule you choose, shows its version, and has a **Check for update** button. Releases are built by GitHub Actions; the landing page and `version.json` are hosted on Cloudflare Pages.

```
app/                  Android app (Kotlin, WorkManager)
site/                 Landing page + version.json  -> Cloudflare Pages
.github/workflows/    ci.yml (debug build) and release.yml (tag -> APK + deploy)
```

## How updates work

1. You push a tag such as `v1.2.0`.
2. `release.yml` builds and signs the APK, creates a GitHub Release with `auto-cache-cleaner.apk`, writes `site/version.json`, and deploys `site/` to Cloudflare Pages.
3. The app fetches `https://<project>.pages.dev/version.json`:
   - when the user taps **Check for update**, and
   - whenever the reminder notification fires (the notification then says "Update available").
4. If `versionCode` in that file is higher than the installed build, the app offers the APK download.

The version code is derived from the tag: `vMAJOR.MINOR.PATCH` becomes `MAJOR*10000 + MINOR*100 + PATCH` (so `v1.2.3` is build `10203`). Always tag in increasing order.

## One-time setup

### 1. Create the repo
Push these files to a new GitHub repository (default branch `main`).

### 2. Create a signing key
```bash
keytool -genkeypair -v -keystore release.jks -alias release -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks > keystore.b64      # macOS: base64 -i release.jks -o keystore.b64
```
Keep `release.jks` safe and backed up. If you lose it, existing users can't update in place. Never commit it (it is in `.gitignore`).

### 3. Add GitHub secrets
Repo > Settings > Secrets and variables > Actions:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | contents of `keystore.b64` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `release` (or your alias) |
| `KEY_PASSWORD` | key password |
| `CLOUDFLARE_API_TOKEN` | token with *Cloudflare Pages: Edit* permission |
| `CLOUDFLARE_ACCOUNT_ID` | your Cloudflare account ID |

### 4. Create the Cloudflare Pages project
Cloudflare dashboard > Workers & Pages > Create > Pages > **Direct Upload**, and name it `auto-cache-cleaner`. If you pick another name, change `CF_PAGES_PROJECT` at the top of `.github/workflows/release.yml` and the fallback URL in `app/build.gradle.kts`.

### 5. Release
```bash
git tag -a v1.0.0 -m "First release"
git push origin v1.0.0
```
The tag message becomes the "notes" text shown in the app's update dialog.

## Notes
- The landing page only goes live (and `version.json` only updates) when you push a release tag. To change the page, tag a new release.
- Android shows an "install unknown apps" prompt when users sideload the APK. If you later publish on Google Play, use Play's in-app updates instead of the APK link.
- **Per-app cache scan:** **Scan all apps' cache** lists every launchable app with its cache size, largest first. It needs the user to grant *Usage access* once (the screen links straight to that setting). The scan is read-only: Android doesn't let regular apps clear other apps' caches, so the user ticks apps (or **Select all**) and taps **Clear selected**: the app then opens each selected app's system settings page one by one (it tries the Storage & cache page directly, and falls back to App info on phones that don't allow that), the user taps Clear cache and goes back, and the next app opens automatically. Tapping a single app opens just that one. The **Clear Cache** button on the main screen clears this app's own cache only. Keep your store listing and landing page wording consistent with that.
- **Google Play:** if you ever publish there, the `PACKAGE_USAGE_STATS` permission needs a declaration in Play Console. It's not an issue for sideloaded APKs.
- Android 13+ asks for notification permission on first launch; without it, reminders and update notices are silently skipped.
- Local build: install JDK 17 + Android SDK, then `gradle assembleDebug` (or open in Android Studio, which generates the Gradle wrapper for you).
