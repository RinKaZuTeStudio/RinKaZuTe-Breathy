# Breathy - Quit Smoking App

**Your all-in-one companion for quitting smoking and staying smoke-free.**

Breathy is a comprehensive Android application that helps smokers quit through health tracking, community support, gamification, and AI-powered coaching. Built with Kotlin, Jetpack Compose, and Firebase.

---

## App Identity

| Property | Value |
|----------|-------|
| **Name** | Breathy |
| **Package** | `com.breathy` |
| **Platform** | Android (min SDK 24, target SDK 34) |
| **Language** | Kotlin + Jetpack Compose (Material 3) |
| **Backend** | Firebase (Auth, Firestore, Storage, Functions) + OpenAI API |
| **Monetization** | $1 one-time "Support Me" + AdMob (open app + interstitial) |

---

## Features

- **Real-Time Health Tracking** - Watch your body recover with a detailed health timeline (20 min to 15 years)
- **Craving SOS** - Guided breathing (4-7-8), tap game, or AI coach when cravings hit
- **Community Support** - Share stories, like, reply, and connect with other quitters
- **Friends & Chat** - 1:1 real-time messaging with typing indicators and read receipts
- **Global Leaderboard** - Compete by XP with weekly/monthly/all-time filters
- **Challenge Events** - Push-up challenges with video check-ins and prizes
- **AI Coach** - 24/7 personalized guidance powered by GPT-4o-mini
- **Gamification** - 19 levels, 19 achievements, daily rewards, confetti celebrations
- **Money Saved Tracker** - See exactly how much you're saving by not smoking

---

## Tech Stack

### Android App
- **Kotlin** + **Jetpack Compose** (Material 3)
- **Compose BOM** 2025.02.00
- **Firebase BOM** 33.5.0 (Auth, Firestore, Storage, Functions, Analytics, Crashlytics, Messaging)
- **Coil** 3.0 (image loading)
- **Accompanist** (permissions, system UI)
- **Play Billing** 6.0 (in-app purchases)
- **AdMob** 23.0 (ads)
- **CameraX** (video check-ins)
- **CanHub Cropper** (image cropping)
- **Konfetti** (confetti celebrations)
- **Kotlin Coroutines** + **Flow** (async + reactive)
- **Timber** (logging)
- **kotlinx.serialization** (JSON parsing)

### Cloud Functions (Node.js 18)
- `updateDaysSmokeFree` - Scheduled daily at 2 AM UTC
- `onReplyCreated` / `onReplyDeleted` - Atomic reply count
- `sendChatNotification` - FCM push notifications
- `calculateEventRanks` - Scheduled hourly
- `openAIChat` - Callable, rate-limited AI proxy

---

## Project Structure

```
breathy/
├── app/
│   ├── build.gradle.kts
│   ├── google-services.json
│   └── src/main/
│       ├── java/com/breathy/
│       │   ├── BreathyApplication.kt
│       │   ├── MainActivity.kt
│       │   ├── data/
│       │   │   ├── models/Models.kt
│       │   │   └── repository/
│       │   │       ├── AuthRepository.kt
│       │   │       ├── UserRepository.kt
│       │   │       ├── StoryRepository.kt
│       │   │       ├── FriendRepository.kt
│       │   │       ├── ChatRepository.kt
│       │   │       ├── EventRepository.kt
│       │   │       ├── RewardRepository.kt
│       │   │       └── CoachRepository.kt
│       │   ├── di/AppModule.kt
│       │   ├── ui/
│       │   │   ├── auth/ (AuthScreen, OnboardingScreen)
│       │   │   ├── home/ (HomeScreen, HealthTimeline, StatCard, CravingBottomSheet, BreathingExercise, TapGame)
│       │   │   ├── community/ (CommunityScreen, StoryCard, PostStoryScreen, StoryDetailScreen, PublicProfileScreen)
│       │   │   ├── friends/ (FriendsScreen, ChatScreen)
│       │   │   ├── leaderboard/ (LeaderboardScreen)
│       │   │   ├── events/ (EventsScreen, EventChallengeScreen, EventCheckinScreen, AdminReviewScreen)
│       │   │   ├── profile/ (ProfileScreen, AchievementsList)
│       │   │   ├── coach/ (AICoachScreen)
│       │   │   ├── subscription/ (SubscriptionScreen)
│       │   │   ├── navigation/ (NavGraph)
│       │   │   └── theme/ (Color, Theme, Type)
│       │   └── utils/ (ImageUploader, VideoUploader, NotificationHelper, AdManager, ConfettiHelper)
│       └── res/
│           ├── values/ (strings.xml, colors.xml, themes.xml)
│           └── drawable/
├── functions/
│   ├── index.js
│   └── package.json
├── docs/
│   └── Breathy_Complete_Documentation.pdf
├── .github/workflows/build-apk.yml
├── codemagic.yaml
├── firestore.rules
├── storage.rules
├── firestore.indexes.json
├── build.gradle.kts (project)
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

---

## Building APK from Tablet (No Computer Needed!)

This project includes **Codemagic** and **GitHub Actions** configurations for building the APK directly from your tablet browser.

### Quick Steps:
1. Create a **GitHub** account and upload this project
2. Sign up for **Codemagic** (free plan) at https://codemagic.io
3. Connect your GitHub repo → Add `GOOGLE_SERVICES_JSON` env var
4. Click **Start Build** → Wait 5-15 min → Download APK
5. Install APK on your tablet!

See `Breathy_Codemagic_Guide_Arabic.pdf` for detailed Arabic instructions.

---

## Getting Started

### Prerequisites

- **Android Studio** Hedgehog or later
- **JDK 17**
- **Node.js 18+** (for Cloud Functions)
- **Firebase CLI** (`npm install -g firebase-tools`)

### 1. Clone & Open

```bash
git clone https://github.com/RinKaZuTeStudio/Breathy.git
cd breathy
# Open in Android Studio
```

### 2. Firebase Setup

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Enable **Authentication** (Email/Password + Google)
3. Enable **Cloud Firestore** (start in test mode, then deploy rules)
4. Enable **Cloud Storage** (default bucket)
5. Enable **Cloud Functions** (requires Blaze billing plan)
6. Place your `google-services.json` in `app/` (already included with project credentials)

### 3. Build & Run

```bash
# Debug build
./gradlew assembleDebug

# Install on device
./gradlew installDebug
```

### 4. Deploy Cloud Functions

```bash
cd functions
npm install

# Set OpenAI API key
firebase functions:secrets:set OPENAI_API_KEY

# Deploy functions
firebase deploy --only functions

# Deploy Firestore rules + indexes
firebase deploy --only firestore:rules,firestore:indexes

# Deploy Storage rules
firebase deploy --only storage
```

---

## Firebase Credentials

| Property | Value |
|----------|-------|
| Project ID | `breathy-healthy` |
| Storage Bucket | `breathy-healthy.firebasestorage.app` |
| Project Number | `956462842979` |
| Mobile SDK App ID | `1:956462842979:android:55b5bb62f889f0a0c8a456` |
| API Key | `AIzaSyCJ1A4_pljiYjD9eMVvm9WAu7jYNWqpHyg` |
| OAuth Client ID | `956462842979-fl850utkk746te3mq3hi4qii36as5ne5.apps.googleusercontent.com` |

## AdMob Credentials

| Property | Value |
|----------|-------|
| App ID | `ca-app-pub-9434446627275871~1020887836` |
| Open App Ad | `ca-app-pub-9434446627275871/1257681230` |
| Interstitial Ad | `ca-app-pub-9434446627275871/6356974992` |
| Rewarded Ad 1 (Gold) | `ca-app-pub-9434446627275871/5304737452` |
| Rewarded Ad 2 (Picture) | `ca-app-pub-9434446627275871/1296220001` |

---

## Testing

### Unit Tests
```bash
./gradlew test
```

### Instrumented Tests
```bash
firebase emulators:start
./gradlew connectedAndroidTest
```

---

## Documentation

- **`docs/Breathy_Complete_Documentation.pdf`** - PRD, UI/UX specs, architecture, data model, testing, deployment, store listing, post-launch plan
- **`Breathy_Codemagic_Guide_Arabic.pdf`** - Arabic guide for building APK from tablet

---

## License

Proprietary - All rights reserved.
