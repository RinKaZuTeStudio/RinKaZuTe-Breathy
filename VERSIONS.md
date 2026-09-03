# Breathy — Version History

## v1.0.6 (versionCode 7) — guided craving workouts with 5-second countdown

### Craving Coping — real workouts (fixes "instant confetti" bug)
- ROOT CAUSE FIXED: tapping Pushups / Squats / Plank in the craving sheet
  previously logged success instantly and fired confetti without any actual
  exercise — no timer, no reps, no preparation. The three cards now launch a
  full-screen guided workout (new ExerciseWorkout composable).
- 5-SECOND "GET READY" COUNTDOWN: every workout starts with a big animated
  5 → 1 countdown ("Get Ready!" + positioning instruction), then "GO! 🔥"
  — the exercise only starts after the preparation count, as requested.
- Guided workouts:
  * Pushups — 10 reps, tap "+1 Rep" after each pushup, progress ring fills.
  * Squats — 15 reps, tap "+1 Rep" after each squat, progress ring fills.
  * Plank — 30-second hold with automatic countdown timer, pulsing seconds
    and progress ring.
- Completion shows "Awesome! 🎉" + confetti and auto-returns after 2 s,
  logging the craving defeat. "Give up 😓" logs an unsuccessful attempt.
  Closing with ✕ cancels without logging (consistent with breathing/game).
- Craving logging is now honest: EXERCISE success is only logged when the
  workout is actually completed.

## v1.0.5 (versionCode 6) — subscription UX, premium frame delivery, ads & rewards, follow errors

### Subscription / Premium
- PremiumRepository binding fix: when Google Play reports an ACTIVE
  `breathy_premium_monthly` purchase but no Firestore mirror document exists
  yet (mirror write failed at purchase time, or app reinstall), the CURRENT
  account now receives Premium and the binding is written — instead of being
  denied. Different-account isolation preserved (MISMATCH still denies).
- Subscription page: status headline is now "SUBSCRIBED ✓"; benefits expanded
  (ad-free, exclusive events, auto-equipped Premium frame, badge across the
  app, funds development). Subscribe CTA hidden for subscribers; Manage
  Subscription + renewal info shown.
- Premium avatar frame is AUTO-EQUIPPED the moment the entitlement first
  activates (false→true transition) — subscribers visibly receive it without
  digging through the collection. Manual frame choice afterwards is respected.
- Navigating to the subscription page no longer shows an interstitial first
  (Google Play policy: no ads interrupting purchase flows).

### Ads (free users)
- Interstitial frequency cap reduced 3 min → 90 s; tab switches and detail
  navigation already show LevelPlay interstitials (never on launch, never in
  purchase/registration/reward flows). Premium: zero ads, unchanged.
- Rewarded "Gold Ads" (+200 Gold per completed ad) is now LIMITLESS and
  available in TWO places: Home and Gold History. Reload after every show;
  grant only on verified completion; per-show dedup key prevents double
  credit. New shared composable ui/components/GoldAdsCard.kt.

### Follow
- Follow failures are now surfaced with an inline error banner on the profile
  (previously silent — looked like a dead button). Root cause of most
  failures was the missing `follows` Firestore rules (v5 ruleset delivered
  separately — publish it to make follow/unfollow work).

### Misc
- Premium popup remains strictly gated to non-premium users.

## v1.0.4 (versionCode 5) — daily-reward permissions fix, leaderboard highlight fix, light-only UI hardening

**Signing:** same official release keystore (alias `breathy`, SHA-1
`06:03:48:17:16:6B:F1:63:D7:15:D9:B9:56:E5:96:1D:B2:5F:2A:D4`, committed at
`app/release.keystore`).

### Daily Login Reward — permission_denied root cause fixed
- `firestore.rules`: `users/{uid}` and `publicProfiles/{uid}` owner updates no
  longer re-validate the FULL document state. Sparse pre-onboarding docs
  (Google sign-in creates them without `quitDate`) and blank-nickname profiles
  were denied EVERY owner update — the daily reward transaction failed with
  "Missing or insufficient permissions". Ownership + immutable identity
  anchors (email/createdAt) remain enforced; nothing is public.
- `appConfig` match blocks moved INSIDE the /databases/{db}/documents scope
  (they sat after the closing brace, so the catch-all denied them).
- Auth: guaranteed non-empty nickname (name → Google display name → email
  prefix → "Breathy User") so public profiles always validate.
- ViewModel in-flight guard: rapid Claim taps cannot fire concurrent claims.
  Idempotency: Firestore transaction + same-day `lastDailyClaim` check +
  `goldTransactions/daily_checkin_{date}` dedup key.

### Leaderboard current-user row (main + event)
- Identical layout/size/spacing/avatar/border/rank for every row; elevation 0.
- The ONLY difference: a slightly darker subtle sage background
  (`#EFF3ED`, `CurrentUserRowBackground`) — no shadow, glow, floating card,
  thick border or oversized outline. YOU chip kept, toned subtle.
- Identification is strictly by Firebase Auth UID.

### UI
- Dark-mode remnants removed: XML theme `Theme.Material3.Light`, status/nav
  bars light, colors.xml rewritten to the light Sage Nature palette (dark
  GitHub-palette values caused a dark startup flash).
- Delete Account button/dialog/state fully removed from Settings UI
  (logout untouched).
- Event rewards card: honest PAYOUT STATUS block ("PENDING PAYOUT") — never
  claims a prize was paid before delivery.
- Recovery Journey retained (stage-chaptered rail, 3 states) — no identical
  card stacking.

## v1.0.3 (versionCode 4) — LevelPlay ads + payout setup + billing sync hardening

**Released:** 2026-09
**Signing:** official release keystore (alias `breathy`, PKCS12). NOTE: the
original keystore file was unrecoverable from GitHub (never committed; CI
secrets were empty), so a NEW release keystore was generated with the same
alias/passwords/DN. Its SHA-1 is `06:03:48:17:16:6B:F1:63:D7:15:D9:B9:56:E5:96:1D:B2:5F:2A:D4`.
The keystore is now committed to the repo (`app/release.keystore`) and mirrored
in GitHub Actions secrets (`RELEASE_KEYSTORE_BASE64`) so it can always be
retrieved from GitHub going forward.

### Advertising — Unity LevelPlay (AdMob fully removed)
- Removed the Google Mobile Ads (AdMob) SDK, all AdMob ad unit IDs, and the
  `APPLICATION_ID` manifest meta-data. No AdMob code remains in the app.
- Integrated Unity LevelPlay (production identifiers, no test IDs):
  - App Key `27e9c42cd`
  - Rewarded `b0taewni29ftw711` — "Gold Ads": +200 Gold granted ONLY after the
    LevelPlay completion callback; idempotent via Gold-ledger dedup key.
  - Native `5o8vznxxsem6mv51` — Breathy-styled sponsored card on Home between
    content cards, clearly marked ("AD" pill + "Sponsored").
  - Interstitial `flcqa09gxs9k0qgl` — full-screen, frequency-capped (1 per
    3 minutes), never during purchases/event registration/reward collection.
- Premium subscribers: zero ads (nothing loaded or shown, cached ads released).
- App-open ads removed (no LevelPlay app-open format exists).

### Google Play Premium subscription
- Billing is now re-verified on EVERY foreground return (MainActivity.onResume)
  in addition to app start, purchases, and restore — the app can no longer keep
  showing "Subscribe" after Google Play says the user is subscribed.
- "Manage Subscription" button (premium state) opens the Play subscriptions
  page for `breathy_premium_monthly`.
- No local subscription timers exist anywhere; the "5 minutes" renewal seen in
  Play was a test-track behavior and is not hardcoded in the app.

### Events & Challenges
- Prize tiers now include PayPal gift cards:
  1st–3rd $50 + Gold + Event Avatar Frame (1st also Champion Badge);
  4th–5th $30 + 1,500 Gold; 6th–10th $15 + 1,000 Gold.
- Rewards and Rules views explain the gift-card payout and where to set the
  PayPal email.

### Payment / Payout Setup
- New Settings → Payment / Payout Setup screen: PayPal ONLY, collects ONLY the
  PayPal email (validated, saved to `users/{uid}.payoutEmail`).


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
