# Easy Download

A tiny Android app for one person (your dad). He taps **Share** in YouTube / Instagram / Facebook,
picks **Easy Download**, and a big popup asks **Video** or **Audio**. The file lands in his Gallery
(videos) or Music app (songs), in a folder called "Easy Download". No ads, no websites, no sketchy
installers, and everything runs on his phone.

## Build it

1. Install **Android Studio** (latest stable).
2. File > Open > select this `EasyDownload` folder. Let Gradle sync (first sync downloads a lot).
   Android Studio creates the Gradle wrapper itself, so there is no `gradlew` in this folder.
3. Plug in your phone (USB debugging on) or use an emulator, and press Run.
   To get an APK for dad: Build > Build APK(s), then send `app/build/outputs/apk/debug/app-debug.apk`.
4. On dad's phone: open the APK, allow "install unknown apps" for whatever app opened it, install.
   Then open Easy Download once and tap **Allow notifications**.

## Things to know

- **This code has never been compiled.** It was written without an Android SDK. Expect to fix a
  small compile error or two on first sync. Android Studio will point right at them.
- **Engine updates:** YouTube breaks yt-dlp every few weeks. The app checks for a newer yt-dlp at
  most every 12 hours in the background and retries once with a fresh one if a download fails.
  The "Update download engine" button on the main screen forces it.
  If a library version bump is ever needed, change `youtubedlAndroid` in `app/build.gradle.kts`.
- **Play Store:** don't bother, YouTube downloaders get rejected. This is a sideloaded family app.
  Keep it private. Downloading from YouTube is against its terms of service.
- **minSdk 29 (Android 10+).** That is what lets it save to Gallery/Music with no storage permission.
- **When something fails:** dad gets a plain-language message. The raw error is kept in the app;
  the main screen shows a **Send details** button that shares it to you over WhatsApp.

## Where things are

| File | Job |
|---|---|
| `ShareActivity.kt` + `sheet_share.xml` | The popup (bottom sheet) shown on Share |
| `DownloadService.kt` | Runs yt-dlp in the background with a progress notification, retries once after updating |
| `Engine.kt` | Starts yt-dlp/ffmpeg, updates yt-dlp |
| `MediaSaver.kt` | Copies the finished file into Gallery / Music |
| `Errors.kt` | Friendly error messages, remembers the last raw error |
| `MainActivity.kt` | Instructions + update / notifications / send-details buttons |

## Changing what the buttons do

The yt-dlp options for each button are in `DownloadService.runYtDlp()`. Video is capped at 720p
(H.264 + AAC so it plays anywhere), "smaller video" at 360p, audio is MP3.
