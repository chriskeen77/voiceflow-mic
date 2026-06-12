# VoiceFlow Floating Mic (Android companion)

A tiny native Android app that shows a **floating mic bubble whenever you tap into any
text field in any app**. Tap the bubble → it listens (as long as you want), cleans up
fillers/stutters, and types the result straight into the focused field. This is the part
a web app physically cannot do on Android — it needs an Accessibility Service, so it
ships as a sideloaded APK (no Play Store needed).

The dictation pad PWA (https://voiceflow-ck.netlify.app) is the cross-platform app;
this is the Android-only "dictate into anything" layer.

## Build it (one-time, ~15 min)

1. Install **Android Studio** (free, https://developer.android.com/studio) on the PC.
2. **New Project → No Activity**. Set:
   - Name: `VoiceFlow Mic`
   - Package name: `com.ck.voiceflow`
   - Minimum SDK: **API 26**
   - Language: Kotlin
3. Replace/add these files from this folder into the project:
   - `app/build.gradle.kts`  → replace the module gradle file
   - `app/src/main/AndroidManifest.xml` → replace
   - `app/src/main/java/com/ck/voiceflow/MainActivity.kt` → add
   - `app/src/main/java/com/ck/voiceflow/FloatingMicService.kt` → add
   - `app/src/main/res/xml/accessibility_service_config.xml` → add (create `res/xml`)
   - `app/src/main/res/values/strings.xml` → replace
4. **Build → Build App Bundles / APKs → Build APK(s)**, then copy
   `app/build/outputs/apk/debug/app-debug.apk` to the phone (email/Drive/USB) and open it
   to install (allow "install unknown apps" when prompted).

## Set up on the phone (one-time)

1. Open **VoiceFlow Mic** → tap **Grant microphone** → Allow.
2. Tap **Enable floating mic** → find **VoiceFlow Mic** in Accessibility → turn it ON
   (Android will warn about full control — that's what lets it see the focused text box
   and type into it; nothing leaves the phone except audio going to Google's recognizer).

## Use

- Tap into any text field (Messages, Gmail, EMR app, anything) → teal mic bubble appears.
- **Tap bubble** → turns red, listens until you **tap it again** (silence won't stop it).
- Stop → text is cleaned (umms, stutters, "scratch that") and typed into the field.
- **Drag** the bubble anywhere if it's in the way.

## Troubleshooting

- No bubble: re-toggle the service in Settings → Accessibility; some phones (Samsung)
  also need "Allow background activity" for the app.
- No text inserted: a few apps block accessibility text-setting (banking apps); the text
  is also placed on the clipboard as a fallback — just long-press → Paste.
- Recognition quality: this uses Android's on-device/Google recognizer. For Deepgram
  quality, use the PWA and paste.
