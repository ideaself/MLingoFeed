# AGENTS.md

Android web reader app: Kotlin + Jetpack Compose + Material 3, MVVM, single `:app` module, package `com.mlingofeed`. README is in Chinese; UI strings are hardcoded English in Composables (only `app_name` lives in `strings.xml`).

## Build & verify
- Windows: `.\gradlew.bat assembleDebug` (JDK 11+, compileSdk 35). `local.properties` needs `sdk.dir` or `ANDROID_HOME` set.
- No tests, no CI, no lint config. Verification = `.\gradlew.bat compileDebugKotlin` (fast) or `assembleDebug`.
- APK outputs are renamed to `MLingoFeed-<variant>.apk` via `applicationVariants` in `app/build.gradle.kts`.
- Build cache + configuration cache enabled in `gradle.properties` — first build after config changes stores a fresh entry.
- Dependencies: Aliyun mirrors listed first in `settings.gradle.kts` with `RepositoriesMode.FAIL_ON_PROJECT_REPOS`. Add versions/deps in `gradle/libs.versions.toml`; never add `repositories {}` inside a module.

## Architecture
- Manual DI, no Hilt/Koin: `WebReaderApp` (Application) owns all repositories + `SettingsManager`; screens get them via `(context.applicationContext as WebReaderApp)`. Add new singletons there.
- ViewModels: plain `ViewModel` classes taking `WebReaderApp` in the constructor, created via `AppViewModelFactory` and `viewModel(factory = remember { AppViewModelFactory(app) })`. Every screen with state has a VM registered in `AppViewModelFactory`'s `when`. Screens needing nav args (Reader, RssArticleDetail) use the `remember(arg) { vm.ensureInitialized(arg) }` pattern. Only view-layer state stays in screens (dialog text inputs, drag/swipe gesture state, snackbars).
- Room 2.6.1 via KSP: `AppDatabase` version 7, `exportSchema = false`, `fallbackToDestructiveMigration()`. Schema change = bump `version`; data is wiped, no migration files.
- Navigation: sealed class `Screen` in `WebReaderNavHost.kt`. URL/title args must be URL-encoded via `Screen.*.createRoute()` when navigating and decoded from nav args (see `Reader` / `RssArticles` routes).
- Reader mode is a WebView with injected JS extraction (`webview/` package) plus a JS↔Kotlin bridge (`WebAppInterface`). ReaderViewModel owns the tab list (ReaderTab holds the WebView); the screen's `AndroidView` factory creates/attaches WebViews.

## Runtime config gotchas
- AI features (translation, chat, difficulty, collocations) call an OpenAI-compatible chat-completions endpoint; base URL, API key, and model are user-entered in Settings (DataStore `settings` prefs) — defaults `https://api.deepseek.com/chat/completions` / `deepseek-v4-flash`. Retrofit uses `@Url` dynamic endpoints (`TranslationApi.kt`), so the hardcoded `baseUrl` is a placeholder. No keys in code.
- Cleartext HTTP is intentionally permitted app-wide (`network_security_config.xml` + `usesCleartextTraffic`) for http:// RSS feeds and dictionaries — don't remove.
- DataStore `settings` prefs (AI API key) are excluded from Android backup (`res/xml/backup_rules.xml` + `data_extraction_rules.xml`) — keep it that way.
- RSS background sync: `RssSyncWorker.schedule(context, intervalHours)` (unique periodic WorkManager work, hourly + 15-min flex); needs POST_NOTIFICATIONS on API 33+ (requested at runtime in `RssSettingsScreen`).
- `MainActivity` is `singleTask`; shared/open intents arrive via `onNewIntent` and are funneled to the home screen through a hoisted `sharedUrl` state — keep both in sync when touching intent handling.
- Release signing uses `app/release.jks` with passwords read from `local.properties` (`MLINGOFEED_STORE_PASSWORD` / `MLINGOFEED_KEY_PASSWORD` / `MLINGOFEED_KEY_ALIAS`). The keystore is gitignored (`*.jks`) and `local.properties` is gitignored — back up both; losing the keystore makes release updates un-signable. Without the credentials the release build fails (debug build is unaffected).
