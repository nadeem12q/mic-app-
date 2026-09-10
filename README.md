# mic-app-

## Diagnostic prototype (v0.2)

The repository now contains **Mic Route Test**, a native Kotlin Android 13+ app for checking the routing limits described below. It is a feasibility tool, not a verified system-wide microphone controller.

- Select a test input, record up to 10 seconds, and compare the requested microphone with Android's actual route and silenced status.
- Use simple phone/earbuds test buttons; the earbuds test prepares a temporary communication route before recording. Advanced controls are initially hidden.
- Treat routes with no recent samples as waiting, and distinguish silent samples from a signal. Route identity alone is never reported as successful recording.
- Play the in-memory sample through a selected headset and observe its output route.
- Make a time-limited communication-output request, reset it, and record manual observations from other apps.
- Export a text report without audio. No Internet permission, analytics, background recording, root or Shizuku backend.

**Start here:** [OPPO phone test instructions](docs/PHONE_TEST.md). Build output: `app/build/outputs/apk/debug/app-debug.apk`; handoff copy: `artifacts/mic-route-test-debug.apk`.

### Build

Use JDK 17 or 21, Android SDK platform 35 and build-tools 34.0.0. Set `ANDROID_HOME` or put `sdk.dir=/your/sdk/path` in ignored `local.properties`. The wrapper pins Gradle 8.9; Android Gradle Plugin is 8.7.3 and Kotlin is 2.0.21.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

With an authorized local phone, install using `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Android Studio can also open the repository directly.

Unit tests cover the OPPO zero-sample regression, stale data after input switches, silent samples, missing routes and silenced recording. The first user report confirmed phone-route data but no Bluetooth samples in v0.1's unprepared test. Guided Bluetooth and cross-app tests still require the real OPPO; see the device matrix in the test guide. No physical-device pass is claimed by a successful build.

### Original idea


App Idea: Audio Input Controller

Problem Statement: Users frequently struggle with Android's default audio routing. When connected to external devices like wireless earbuds or headphones during calls or recordings, the system often defaults to the wrong microphone. This results in users speaking into their phone while their earbuds are ignored, or vice versa, with no native, easy way to manually switch input sources mid-session.

Proposed Solution: A user-friendly app that gives control back to the user:

• Manual Source Selection: An overlay or quick-settings toggle to choose between internal device microphone and connected external earbud microphones.

• Real-time Switching: Seamlessly swap the active input source without dropping the call or stopping the recording.

• Default Preferences: Set specific audio source rules for different apps (e.g., always use earbuds for Zoom, but prefer the phone mic for voice memos).

• Clear Status Indicators: Visual feedback so users know exactly which microphone is currently active.


ok yar is readme file ma mana apna basic plan idea likha hai ab mujay ya app ki taraf jana hai to ab ham planing kartay hai ap mujh sa questiong kar kah plan bn saktay hoo mera idea simple hai jaisay android ma agar  ham external devices connect kartay hai jin ma speaker ho to use rka pass option rehta hai kah wo choos ekar skatay hai kah jo sound play ho rhi hai wokhaa pa rsunay gi like earnuds ka speaker ma ya mobile ka builtin speakers ma but for mic use asa koi bhi option hhai hai hmaray pass kah ham mic us ek aduraan kon sa mic us ekarn chahtay hai agar ham earbuds connect kartay hai to auto us ka mic use hona start ho jaata hai or kuch budget earbuds ka mic achay nhai hotay hai or some time us eka lia azzas earbuds ma sunna hi zarori hota hai ow mobile ka speaker  par nhai sun skata or jab to earbuds connect karay hai hearing kalai or mic bhi usi ka use hoat hai jo kah aca nhai hai to mic to chnage karnay ka lai usay ear biuds disconect karnay partay jis wajah sa again whi probelm kah wo apna mobile ka speake rma wo sesotive sound nhai run kar skata  to ab ap planing karo web fecth kari is cheez ka baray ma dekhoo or mujhay btao mujh sa questiong karoo or ya ham app built karay ga
