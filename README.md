# Namma-Kelsa 🔨

**"Dignity-First" Labour Marketplace for Small Towns**  
Android App Development using GenAI — Self-Employment Domain

> **PRD:** Project #53 · MindMatrix Internship · HKBK College of Engineering  
> **Author:** B. Balasubramani | USN: 1HK22CS026 | 8th Semester, Dept. of CSE  
> **Guides:** Prof. Khallikkunaisa (Internal) · Tirumal Mutalikdesai (External, MindMatrix)

---

## 📌 What is Namma-Kelsa?

Namma-Kelsa digitally connects **daily-wage skilled workers** (painters, tilers, plumbers, electricians, gardeners) directly with **local homeowners** — eliminating the middleman, reducing exploitation, and giving workers a professional digital identity.

**GenAI powers three features:**
- Auto-generate skill descriptions from gallery photos (Gemini Vision)
- Natural-language customer search query resolution (Gemini Text)
- Sub-skill badge suggestions for worker profiles (Gemini Text)

---

## 🏗️ Project Structure

```
NammaKelsa/
├── app/
│   ├── src/main/
│   │   ├── java/com/nammakelsa/
│   │   │   ├── NammaKelsaApplication.kt       ← Firebase offline persistence init
│   │   │   ├── ui/
│   │   │   │   ├── SplashActivity.kt           ← Entry point, auth routing
│   │   │   │   ├── LoginActivity.kt            ← Role select + Phone OTP + Email
│   │   │   │   ├── worker/
│   │   │   │   │   ├── WorkerDashboardActivity.kt      ← Availability toggle, stats
│   │   │   │   │   ├── WorkerProfileSetupActivity.kt   ← Profile + AI badge suggest
│   │   │   │   │   └── WorkGalleryActivity.kt          ← Gallery + AI auto-describe
│   │   │   │   └── customer/
│   │   │   │       ├── CustomerSearchActivity.kt        ← Skill chips + NL search
│   │   │   │       ├── WorkerListActivity.kt            ← RecyclerView + Adapter
│   │   │   │       └── WorkerDetailActivity.kt          ← Full profile + CALL button
│   │   │   ├── model/
│   │   │   │   └── Worker.kt                   ← Firestore data model
│   │   │   ├── repository/
│   │   │   │   └── FirebaseRepository.kt       ← All Firebase operations
│   │   │   └── utils/
│   │   │       ├── LocationUtils.kt            ← FusedLocationProvider + Haversine
│   │   │       └── GenAIHelper.kt              ← All Gemini API calls (F-08, F-09, F-10)
│   │   ├── AndroidManifest.xml
│   │   └── res/
│   ├── build.gradle                            ← All dependencies
│   └── google-services.json                   ← (YOU ADD THIS)
├── firebase/
│   └── firestore.rules                        ← Security rules (PRD Section 6.4)
├── build.gradle
└── README.md
```

---

## ⚙️ Technology Stack (PRD Section 8.1)

| Layer | Technology |
|---|---|
| Platform | Android API 24+ (Android 7.0 Nougat and above) |
| Language | Kotlin (primary) |
| IDE | Android Studio Hedgehog |
| Database | Firebase Firestore (NoSQL, real-time) |
| Storage | Firebase Storage (profile photos, gallery images) |
| Authentication | Firebase Authentication (phone OTP / email) |
| GenAI API | Google Gemini API (gemini-pro, gemini-pro-vision) |
| UI Framework | Android Material Design 3 (ChipGroup, CardView, BottomNav) |
| Location | Android FusedLocationProvider API |
| Version Control | Git + GitHub |

---

## 🚀 Setup Instructions

### Step 1 — Clone the Repository

```bash
git clone https://github.com/YOUR_USERNAME/namma-kelsa.git
cd namma-kelsa
```

### Step 2 — Create Firebase Project

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Create a new project: **"NammaKelsa"**
3. Add an Android app with package name: `com.nammakelsa`
4. Download `google-services.json`
5. Place it in: `app/google-services.json`

### Step 3 — Enable Firebase Services

In Firebase Console:

| Service | Steps |
|---|---|
| **Authentication** | Authentication → Sign-in methods → Enable Phone + Email/Password |
| **Firestore** | Firestore Database → Create database → Start in test mode |
| **Storage** | Storage → Get started → Default rules |

### Step 4 — Deploy Firestore Security Rules

```bash
# Install Firebase CLI if not already installed
npm install -g firebase-tools

# Login
firebase login

# Initialize (select Firestore)
firebase init firestore

# Deploy rules from firebase/firestore.rules
firebase deploy --only firestore:rules
```

### Step 5 — Add Gemini API Key

1. Get API key from [aistudio.google.com](https://aistudio.google.com)
2. Open `local.properties` (in project root — never commit this file)
3. Add:
   ```
   GEMINI_API_KEY=AIzaSy...your_key_here
   ```

> ⚠️ `local.properties` is in `.gitignore` — the key is never committed to version control.  
> PRD Section 5.4: "API key stored securely — never hardcoded in source."

### Step 6 — Open in Android Studio

1. Open Android Studio Hedgehog
2. File → Open → Select `NammaKelsa/` folder
3. Wait for Gradle sync to complete
4. Run on device or emulator (API 24+)

---

## 🗄️ Firestore Data Model (PRD Section 8.2)

### Collection: `/workers/{userId}`

| Field | Type | Description |
|---|---|---|
| `userId` | String | Firebase Auth UID (document ID) |
| `name` | String | Full name of the worker |
| `skills` | Array | e.g. `["Painter", "Waterproofing"]` |
| `dailyRate` | Number | Daily wage in INR |
| `location` | String | Area name e.g. `"Koramangala, Bangalore"` |
| `phoneNumber` | String | Registered phone (shown only on CALL tap) |
| `profilePhotoUrl` | String | Firebase Storage URL |
| `galleryUrls` | Array | Up to 3 Firebase Storage URLs |
| `galleryDescriptions` | Array | AI-generated or manual descriptions |
| `available` | Boolean | True if available for work today |
| `lastUpdated` | Timestamp | Auto-set on save |

---

## 🤖 GenAI Features (PRD Section 5)

### F-08 — Skill Description Generator
**Where:** `WorkGalleryActivity.kt` → calls `GenAIHelper.generateSkillDescription()`  
**API:** Gemini Vision (`gemini-pro-vision`)  
**Flow:** Worker uploads photo → taps "✨ Auto-Describe" → Gemini analyses image → returns 1-2 sentence professional description → worker accepts or edits

**Example output:**
> "Professional interior wall painting with clean finish and sharp edges, suited for bedroom or living room settings."

### F-09 — Natural Language Customer Search
**Where:** `CustomerSearchActivity.kt` → calls `GenAIHelper.resolveSearchQuery()`  
**API:** Gemini Text (`gemini-pro`)  
**Flow:** Customer types free-text → Gemini extracts skill category → auto-applies chip filter

**Example:**
```
Input:  "someone to fix my leaking pipe"
Output: "Plumber"  ← chip selected automatically
```

### F-10 — Skill Badge Auto-Suggestion
**Where:** `WorkerProfileSetupActivity.kt` → calls `GenAIHelper.suggestSkillBadges()`  
**API:** Gemini Text (`gemini-pro`)  
**Flow:** Worker selects primary skill → taps "Suggest Badges" → Gemini suggests 2-3 sub-skills → worker accepts or dismisses each

**Example:**
```
Primary skill: Painter
Suggestions:  [Waterproofing] [Texture Painting] [Wall Putty]
```

---

## 📋 Feature Checklist (PRD Section 7)

| ID | Feature | Status |
|---|---|---|
| F-01 | Authentication (Phone OTP + Email) | ✅ |
| F-02 | Worker Profile (Name, Photo, Skills, Rate, Location) | ✅ |
| F-03 | Work Gallery (3 photos, Firebase Storage) | ✅ |
| F-04 | Availability Toggle (real-time Firestore sync) | ✅ |
| F-05 | Customer Search (skill filter + distance sort) | ✅ |
| F-06 | Worker Detail View (full profile + gallery) | ✅ |
| F-07 | Direct Call Button (native dialer) | ✅ |
| F-08 | GenAI Skill Description (Gemini Vision) | ✅ |
| F-09 | NL Customer Search (Gemini Text) | ✅ |
| F-10 | Badge Suggestion (Gemini Text) | ✅ |
| F-11 | Offline Support (Firestore persistence) | ✅ |
| F-12 | Midnight Toggle Reset (checked in onCreate) | ✅ |

---

## 🔐 Security (PRD Section 6.4)

- Firebase Security Rules prevent cross-user data access (`firestore.rules`)
- Worker phone number **not shown** in search list — only revealed when CALL button is tapped
- Gemini API key stored in `local.properties` — never in source code
- All Firebase Storage URLs require authenticated app context

---

## 🧪 Running the Demo

### Worker Flow (PRD Section 9.1)
1. Open app → Select **"Skilled Worker"**
2. Login with phone OTP (or email)
3. Fill profile: Name, Skills (multi-select chips), Daily Rate, Location
4. Tap **"✨ Suggest Badges"** — AI suggests sub-skills
5. Upload 3 gallery photos → tap **"✨ Auto-Describe"** for each
6. Dashboard → toggle **"Available for Work Today" → ON**
7. Worker now appears in customer search results

### Customer Flow (PRD Section 9.2)
1. Open app → Select **"Customer"**
2. Login with phone OTP (or email)
3. Search: tap skill chip (e.g. "Painter") **or** type free-text (AI resolves)
4. Browse list of available workers, sorted by distance
5. Tap a worker card → view full profile + gallery
6. Tap **CALL** → native phone dialer opens
7. Speak directly with worker — hire agreed upon

---

## 📦 Key Dependencies

```gradle
// Firebase
firebase-auth-ktx
firebase-firestore-ktx
firebase-storage-ktx

// Location
play-services-location:21.1.0

// UI
com.google.android.material:material:1.11.0

// Image Loading
com.github.bumptech.glide:glide:4.16.0

// Coroutines
kotlinx-coroutines-android:1.7.3
kotlinx-coroutines-play-services:1.7.3
```

---

## 📁 Important Files to Add (Not in Repo)

| File | Location | Why |
|---|---|---|
| `google-services.json` | `app/` | Firebase project config |
| `local.properties` | project root | Contains `GEMINI_API_KEY` |

---

## 📽️ Demo Video Checklist (PRD Section 11.2)

- [ ] Worker: login → profile setup → gallery upload → AI auto-describe
- [ ] Worker: availability toggle ON → verified in Firebase console
- [ ] Customer: login → skill chip filter → worker list appears
- [ ] Customer: NL search ("fix leaking pipe") → Plumber selected automatically  
- [ ] Customer: tap worker card → full profile → tap CALL → dialer opens
- [ ] Total video length: **under 5 minutes**

---

*Namma-Kelsa — "Our Work" in Kannada/Tamil. Built for the dignity of daily-wage workers.*
