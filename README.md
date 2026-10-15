# Piano Steps 🎹 (Android prototype)

A prototype Android app that helps a **5-year-old total beginner** learn her first piano notes on a real
acoustic piano or a basic digital keyboard. The app **listens through the microphone** (no MIDI cable
needed), tells whether the right key was played, and celebrates with balloons and fireworks.

Built with **Kotlin + Jetpack Compose (Material 3)**, minSdk 26, a single Gradle module, a version
catalog, and a **pure-Kotlin YIN pitch detector** (no native or heavy audio dependencies).

> **Status: prototype.** It compiles, the unit tests pass, and it has been checked for launch on an
> emulator (see [Honest status](#honest-status--known-limitations)), but it has **not been tested with a
> real piano, a real room, or a real child**. Expect to tune thresholds.

## What it demonstrates

| # | Requirement | Where / how |
| - | --- | --- |
| 1 | **Collapsible on-screen keyboard** | `ui/Keyboard.kt` + `lessons/KeyboardPolicy.kt`. FULL / RELEVANT keys only (lesson range ± 3 semitones) / COLLAPSED slim strip / HIDDEN, chosen automatically from window size (`BoxWithConstraints`), forced by the parent, and a show/hide chip for the child. Tap a key to hear it. |
| 2 | **Microphone pitch detection + calibration** | `audio/PitchDetector.kt` (YIN, octave-error correction, range biased to C3-C6), `audio/MicEngine.kt` (`AudioRecord`), `audio/Calibration.kt` + `ui/CalibrationScreen.kt`: parent plays C4, G4, C5 -> noise floor, per-note loudness thresholds, tuning offset in cents, stored in SharedPreferences. **Near-misses** (default: ≤ 1 semitone) count as hits and softly glow the correct key; nothing is punished. |
| 3 | **Rhythm lessons** | `RhythmRound` + `PulseCircle`: a ring shrinks onto a circle; playing *anything* within ±40 % of a beat (parent-adjustable) lands the beat. Onsets come from the mic's amplitude envelope. No metronome precision, no click track. |
| 4 | **No staff** | `DirectionLine`: dots higher/lower by pitch, green ↑ / orange ↓ arrows, finger number in each dot, plus a right-hand `FingerHand` icon highlighting the starting finger. |
| 5 | **Short repeatable lessons + rewards** | Balloons pop on each completed round; **fireworks** (Canvas particles) after 3 clean rounds in a row; **reward themes**: Balloons / Stars / Animals / Space (`ui/Effects.kt`). |
| 6 | **Demo button** | "🔊 Hear it" plays the target note with `AudioTrack` (soft additive piano-like tone). The mic pipeline is muted while it plays + 350 ms, so the app can't hear itself. |
| 7 | **Parent mode** | 🔒 corner icon: press-and-hold 1.5 s, then answer a multiplication question. Behind it: choose lesson, difficulty (Easy/Normal/Tricky, near-miss tolerance, any-octave, rhythm window), keyboard mode, reward theme, re-run calibration. None of this is on the child screen. |

Also: friendly microphone rationale screen before the system permission prompt, portrait + landscape,
tablet-friendly layouts, adaptive launcher icon (placeholder).

## Starter lessons

Lessons are plain data (`app/src/main/java/com/pianosteps/lessons/Lesson.kt`) - see [docs/DESIGN.md](docs/DESIGN.md).

| # | id | Title | What it teaches | Steps |
| - | --- | --- | --- | --- |
| 1 | `find-the-key` | Find the Key | Finding and playing one key (middle C) with the right thumb; first note-to-sound connection | C4 (finger 1) |
| 2 | `up-and-down` | Up and Down | The idea of *up* (higher) and *down* (lower): two notes, one step to the right | C4 (1) → D4 (2) |
| 3 | `three-steps-up` | Three Steps Up | A three-note pattern going up with fingers 1-2-3; right = higher | C4 (1) → D4 (2) → E4 (3) |
| 4 | `down-we-go` | Down We Go | Playing downward with fingers 3-2-1; left = lower | E4 (3) → D4 (2) → C4 (1) |
| 5 | `gentle-beat` | Gentle Beat | Feeling a slow steady beat: play any key (or clap) roughly on the pulse | 4 beats at 60 BPM after a 2-beat count-in; 3 of 4 on the beat completes it |

The parent picks the active lesson in grown-up settings; the child keeps repeating it (balloons each time,
fireworks every third in a row).

## Build

Requirements: **JDK 17** and the **Android SDK** (platform 34, build-tools 34.0.0). With Android Studio
installed you already have both; otherwise install the command-line tools and run
`sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`.

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=$HOME/android-sdk        # or put sdk.dir=... in local.properties
./gradlew testDebugUnitTest                  # unit tests
./gradlew assembleDebug                      # -> app/build/outputs/apk/debug/app-debug.apk
```

A prebuilt debug APK is attached to the
[GitHub release](https://github.com/wlaust3/piano-steps/releases/tag/v0.1.0-prototype) and CI
(`.github/workflows/build.yml`) uploads one as a build artifact on every push.

## Install on a phone or tablet over USB (no Play Store)

You need a computer with `adb` (part of Android "platform-tools") and a USB cable that carries data.

1. **Enable Developer options** on the device: *Settings → About phone/tablet* → tap **Build number** 7 times
   (on some devices *Settings → About → Software information*). Enter your screen PIN if asked.
2. **Turn on USB debugging**: *Settings → System → Developer options → USB debugging*.
3. **Connect** the device to the computer with the USB cable. Set the USB mode to *File transfer* if it asks.
4. **Accept the RSA prompt** on the device ("Allow USB debugging from this computer?") - tick "Always allow" and tap *Allow*.
5. Check the connection:
   ```bash
   adb devices
   ```
   You should see your device serial followed by `device` (not `unauthorized` or `offline`).
6. **Install** - either let Gradle build and install in one go:
   ```bash
   ./gradlew installDebug
   ```
   or install the APK directly (from a source build, or the one from the GitHub release):
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
7. Open **Piano Steps** from the app drawer. Tap **Allow the microphone** and accept the system prompt.
8. **Calibrate (a grown-up does this once per piano):** on first launch the app opens *Grown-up setup*.
   Put the device where it will sit during lessons (e.g. on the music stand), tap **Start**, keep the room quiet for
   2 seconds, then play and hold **C4 (middle C)**, **G4**, and **C5** when prompted. Tap **Save**. You can redo it any time:
   🔒 (press and hold, then answer the question) → *Re-run calibration*.
9. Pick a lesson in the same grown-up settings, tap **Done**, and hand the device to your child.

**Android Studio route:** *File → Open* the repo folder, let Gradle sync, plug in the device (steps 1-4 above),
choose it in the device dropdown and press **Run ▶**.

You can also sideload the APK without `adb`: copy `app-debug.apk` to the device, open it in a file manager and
allow "Install unknown apps" for that file manager when asked.

### Troubleshooting

- **`adb devices` shows `unauthorized`** - unlock the device and look for the RSA prompt; if missing, run
  `adb kill-server && adb start-server`, or in Developer options tap *Revoke USB debugging authorizations*, then re-plug.
- **Device not listed** - try another cable/port (some cables are charge-only), set USB mode to *File transfer*,
  on Linux add a udev rule for your vendor, on Windows install the OEM USB driver.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`** - an older build signed with a different key exists; `adb uninstall com.pianosteps`.
- **The app never hears the piano (the 😴 stays asleep)** - check *Settings → Apps → Piano Steps → Permissions → Microphone*
  is allowed; close other apps using the mic (video calls, recorders); some tablets/phones with a case or a
  hardware mic switch block the mic. Put the device 0.5-1 m from the piano, speaker side not facing the keys.
- **Hears wrong notes or octaves** - re-run calibration; turn *Any octave counts* on; make sure the piano is roughly
  in tune with A = 440 Hz (the tuning offset only corrects up to ±50 cents).
- **Noisy room (TV, fan, siblings)** - calibration measures the room noise and sets thresholds above it, so calibrate in the
  same noise level you'll practise in, or switch the TV off. A louder, closer piano helps most.
- **Rhythm round never counts hits** - the onset detector needs a clear attack; play a bit louder or move the device closer.

## Honest status & known limitations

**Verified**
- `./gradlew testDebugUnitTest` - 54 unit tests pass (YIN on synthesized sines C3-C6, piano-like harmonic spectra,
  weak-fundamental / strong-harmonic octave-error cases, noise/silence rejection, calibration math, note matching and
  near-miss logic, rhythm windows, streak logic, onset/note stabilisation, keyboard policy, parent gate, lesson data).
- `./gradlew assembleDebug` - builds a debug APK.
- Smoke test: the APK installed and launched on a software-emulated Android 11 (API 30, x86_64) emulator and drew the first-run
  calibration screen with no crash in logcat. That emulator was too slow to interact with (no KVM on the build box), so no other
  screen, touch interaction, mic capture or audio playback was exercised.

**Not verified**
- **Not tested on a real piano, real room or real child.** All pitch tests use synthesized signals. Real piano attack
  noise, inharmonicity, pedal resonance, tablet microphone AGC and room echo may need threshold tuning
  (`PitchDetector`, `OnsetDetector`, `NoteStabilizer` constants).
- The audio loop, microphone capture, `AudioTrack` demo tone and the timing feel of the rhythm lessons can only
  really be judged on hardware. The "app doesn't hear its own demo tone" logic (mute window) is reasoned, not measured.
- Layouts were written for phone/tablet portrait and landscape but only lightly checked on one emulator size.
- Phone built-in microphones often apply automatic gain control; the app uses `AudioSource.MIC`, which may not be the rawest source.

**Known limitations / not built**
- Pitch detection is monophonic (one note at a time) and limited to roughly B2-C6; chords are not recognised.
- "Learning the timbre" is limited to noise floor, per-note loudness and tuning offset; there is no spectral model.
- No sound for correct answers or the metronome pulse (silence keeps the mic clean); animation only.
- English only, no progress tracking/accounts, one lesson at a time chosen by the parent, no on-screen note names beyond "C".
- The parent gate is a child speed bump, not real security. The audio never leaves the device, but this has not been privacy-reviewed.

## Project layout

```
app/src/main/java/com/pianosteps/
  audio/    PitchDetector (YIN), FrameAnalyzer, Calibration, MicEngine, OnsetAndNotes, Tone (AudioTrack demo)
  lessons/  Lesson data, matching/rhythm/streak logic, KeyboardPolicy, Settings, ParentGate
  data/     SharedPreferences stores
  ui/       Compose screens: Lesson, Calibration, Parent, Permission; Keyboard, Visuals, Effects
app/src/test/   unit tests (pure JVM)
docs/DESIGN.md  pitch detection, calibration, lesson data format
```

## License

MIT
