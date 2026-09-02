# Breathy — Version History

All builds are signed with the official release keystore (alias `breathy`,
SHA-1 `FC:79:B6:70:D4:8F:CC:EA:D5:E1:85:9A:0C:1E:5C:B9:E3:1B:1B:E8`).
Downloadable artifacts are attached to each GitHub Release.

| Version | versionCode | Date       | Artifacts                        | Tag      |
|---------|-------------|------------|----------------------------------|----------|
| 1.0.1   | 2           | 2026-09-02 | release APK · debug APK · AAB    | `v1.0.1` |

---

## v1.0.1 (versionCode 2) — 2026-09-02

**Downloads:** https://github.com/RinKaZuTeStudio/RinKaZuTe-Breathy/releases/tag/v1.0.1

| Artifact | File | Size | SHA-256 |
|----------|------|------|---------|
| Release APK | `Breathy-v1.0.1-release.apk` | 97.9 MB | `bea1288c46a58c8f21aef873d9ea20da1389657603d13b6e35684e6cf16b1fe6` |
| Debug APK | `Breathy-v1.0.1-debug.apk` | 106.0 MB | `89691970f32dfa6da1ade15a3883622c236b97eb54ec99c9deabcbb95594ab03` |
| Release AAB (Play Store) | `Breathy-v1.0.1-release.aab` | 51.2 MB | `cd24f13f8c3f0a3efbc9a6c29f9d6f33341b40a236a199273048d1faa683fe1d` |

**Build info**
- Package: `breathy.com` · Firebase project: `breathy-healthy`
- Firebase app ID: `1:956462842979:android:0685845f210d9d34c8a456` (release cert `fc79b670...` registered)
- AGP 8.5.2 · Kotlin 2.0.21 · Gradle 8.7 · compileSdk 35 · minSdk 24 · targetSdk 35
- Google Sign-In Web Client ID: `956462842979-fl850utkk746te3mq3hi4qii36as5ne5.apps.googleusercontent.com`

**Notes**
- Debug builds are signed with the repo debug keystore (SHA-1 `7B:70:FA:EB:44:33:41:E1:7B:4B:06:4B:83:3B:63:FE:82:C8:4F:87`).
  Register that SHA-1 in the Firebase console if you need Google Sign-In to work in debug builds.
- Keystore backups and full signing details live in the master keys file (kept private).

## Releasing a new version

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Add a new row to the table above and a new section below.
3. Build: `./gradlew assembleRelease assembleDebug bundleRelease`
4. Create a GitHub Release with tag `v<version>` and attach the three artifacts
   (APK files exceed GitHub's 100 MB git limit — always publish them as **release assets**, not in git).
