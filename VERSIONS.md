# Breathy — Version History

All builds are signed with the official release keystore (alias `breathy`,
SHA-1 `FC:79:B6:70:D4:8F:CC:EA:D5:E1:85:9A:0C:1E:5C:B9:E3:1B:1B:E8`).
Downloadable artifacts are attached to each GitHub Release.

| Version | versionCode | Date       | Artifacts                        | Tag      |
|---------|-------------|------------|----------------------------------|----------|
| 1.0.2   | 3           | 2026-09-03 | release APK · debug APK · AAB    | `v1.0.2` |
| 1.0.1   | 2           | 2026-09-02 | release APK · debug APK · AAB    | `v1.0.1` |

---

## v1.0.2 (versionCode 3) — 2026-09-03 (final bug fix + UI/UX polish build)

**Signing:** same official release keystore as v1.0.1 (alias `breathy`, SHA-1
`FC:79:B6:70:D4:8F:CC:EA:D5:E1:85:9A:0C:1E:5C:B9:E3:1B:1B:E8`, restored from
`Breathy-Backup/builds/v5` and verified against the Firebase-registered cert).

### Fixes and changes in this build

- **Event page — canonical Coming Soon structure.** The featured event detail
  now falls back to the centralized canonical configuration when no live event
  document exists: artwork, name, description, rewards, RULES & TERMS and the
  500-Gold entry summary (fee + YOUR BALANCE) are always fully presented, with
  the JOIN button disabled ("Entry Opens Soon") until a real event opens. Gold
  can never be charged for a closed event.
- **Home featured card opens the EVENT PAGE directly** (canonical event id),
  and the Events-page hero banner is now tappable with the same destination —
  one canonical event across Home + Events + detail.
- **Non-cash event rewards.** Configured prizes are Gold / cosmetic rewards
  (Event avatar frame, Champion badge) — cash prize copy removed; consistent
  with the "No cash value" rules section until a compliant payout system ships.
- **Event leaderboard empty state** — polished "NO PARTICIPANTS YET" state
  instead of an endless spinner; only real participants ever appear.
- **Friends-only DM enforced.** Opening a chat with a non-friend no longer
  creates a chat document; sending is blocked with a clear notice; the Message
  button is removed from non-friend public profiles (Add Friend only).
  Private chat is exclusively a friends feature.
- **Chat in-conversation empty state** — friendly "NO MESSAGES YET" prompt.
- **Leaderboard — true global rank.** Users outside the top-50 page now get
  their REAL position via a server-side COUNT of higher-XP profiles instead of
  the page-size approximation. Non-functional Weekly/Monthly chips removed
  (single honest all-time ranking).
- **Premium lifecycle — REVOKED / PAUSED states.** The subscription mirror can
  now carry backend lifecycle state; revoked (refund/Play revocation) and
  paused (account hold) are surfaced distinctly while entitlement correctly
  remains OFF in both. Cancelled-but-still-entitled behavior unchanged.
- **Community feed avatars** — story and reply cards now render the author's
  REAL equipped avatar frame via a live profile observer (frame changes
  propagate everywhere without restart).
- **versionCode 3 / versionName 1.0.2** — required for a new Play upload after
  the v1.0.1 (versionCode 2) closed-testing release.

---

## v1.0.1 (versionCode 2) — 2026-09-02 (feature implementation build)

**Downloads:** https://github.com/RinKaZuTeStudio/RinKaZuTe-Breathy/releases/tag/v1.0.1

| Artifact | File | Size | SHA-256 |
|----------|------|------|---------|
| Release APK | `Breathy-release-v1.0.1-signed.apk` | 98.0 MB | see release page |
| Debug APK | `Breathy-debug-v1.0.1.apk` | 106.1 MB | see release page |
| Release AAB (Play Store) | `Breathy-release-v1.0.1.aab` | 51.3 MB | see release page |

### What's in this build

- **Profile picture persistence — fixed.** Firebase Storage fallback now
  uploads to a unique path per upload (no more same-URL cache poisoning),
  the ViewModel consumes the returned URL, and cache invalidation is
  deterministic. Avatar + frame propagate to users/{uid}, publicProfiles/{uid},
  stories, replies and eventParticipants.
- **Avatar frame system** — new Sage Nature frame set (Classic, Nature, Leaf,
  Bronze L3, Silver L5, Gold L8, Achievement, Event, Premium, Rank) with real
  unlock conditions (level/achievements/verified premium). Persisted on both
  user + public profile and rendered everywhere via `BreathyAvatar`.
- **Rank identity** — nature tiers (Seed → Sprout → Leaf → Plant → Tree →
  Forest → Evergreen) mapped from the existing XP/level system (presentation
  only, no math changed) with `RankBadge` on profile and leaderboard.
- **Age collection** — required onboarding step (5-step onboarding now), saved
  to `users.age`; existing accounts without age get a ONE-TIME completion
  screen (`ageCompletion` route) — never an infinite loop.
- **Leaderboard initial reset (safe, one-time)** — entries are filtered by
  `updatedAt >= 2026-09-01 UTC` cutoff baked into this release. Zero fake or
  stale test users until real users join and use the app. Nothing is ever
  deleted; post-reset activity re-includes profiles automatically.
- **Events** — demo auto-created pushup challenge removed. Polished
  "Exclusive events are coming soon" empty state. Event model now supports
  `status` (upcoming/active/completed) and `access` (free/premium) with
  premium-only gating on join (verified entitlement, server rules still
  admin-only for event writes).
- **Breathy Premium — REAL Google Play subscription** — product
  `breathy_premium_monthly`, base plan `monthly-premium`, `launch-offer`
  applied when available. Localized Play price, purchase/pending/cancel/
  restore/expiry handling, acknowledgment, signature verification with the
  Play licensing public key, entitlement re-checked at every app start and
  mirrored to Firestore. Replaces the old one-time "supporter" purchase.
- **Ads — fixed and activated** — MobileAds init, app-open + interstitial
  (production ad units in ALL variants — debug and release both use the real
  production units), load-retry with backoff, frequency capping, and a hard
  premium exemption:
  verified premium → ads are never loaded or shown; expiry → free behavior
  resumes automatically.
- **Sage Nature UI redesign** — full palette swap to white/warm white (60–70%),
  light sage (20–25%), deep forest + natural accents (5–10%); light-first
  theme; botanical components, gradients, borders and empty states across all
  screens; "Breathe Through a Craving" prominent CTA on Home.
- **Billing library 6.0.1 → 7.1.1.**

**Build info**
- Package: `breathy.com` · Firebase project: `breathy-healthy`
- Firebase app ID: `1:956462842979:android:0685845f210d9d34c8a456` (release cert `fc79b670...` registered)
- AGP 8.5.2 · Kotlin 2.0.21 · Gradle 8.7 · compileSdk 35 · minSdk 24 · targetSdk 35
- Google Sign-In Web Client ID: `956462842979-fl850utkk746te3mq3hi4qii36as5ne5.apps.googleusercontent.com`

**Notes**
- Debug builds are signed with the repo debug keystore (SHA-1 `7B:70:FA:EB:44:33:41:E1:7B:4B:06:4B:83:3B:63:FE:82:C8:4F:87`).
- Signing material lives in `Breathy-Backup/builds/v5` and the local master
  file — never commit `keystore.properties` or `release.keystore` here.
