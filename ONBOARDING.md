# Cradle-Mobile: New Team Onboarding Guide

**CRADLE VSA System - Android Application**  
A health-tech platform that reduces preventable maternal deaths in remote Ugandan villages by capturing vital signs and syncing patient data to a central backend even without internet connectivity.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [System Architecture](#2-system-architecture)
3. [Technology Stack](#3-technology-stack)
4. [Prerequisites](#4-prerequisites)
5. [Backend Setup (Cradle Platform)](#5-backend-setup-cradle-platform)
6. [Android Development Setup](#6-android-development-setup)
7. [Running the App](#7-running-the-app)
8. [Codebase Structure](#8-codebase-structure)
9. [Key Architecture Patterns](#9-key-architecture-patterns)
10. [Core Data Models](#10-core-data-models)
11. [Network Communication](#11-network-communication)
12. [Data Synchronization](#12-data-synchronization)
13. [Authentication & Session Management](#13-authentication--session-management)
14. [OCR Feature](#14-ocr-feature)
15. [SMS Relay System](#15-sms-relay-system)
16. [Testing](#16-testing)
17. [Code Quality & Git Workflow](#17-code-quality--git-workflow)

---

## 1. Project Overview

### What is CRADLE VSA?

**CRADLE VSA (Vital Signs Alert)** is a portable medical device that reads a patient's blood pressure and heart rate, primarily used to detect abnormalities during pregnancy. The device displays readings on a screen and uses a colour-coded traffic light system (green/yellow/red) to indicate severity.

### What does this app do?

The **Cradle-Mobile** Android app is the field companion to that device. Village Health Team (VHT) workers carry Android phones running this app into remote communities where there may be no internet. The app:

- Captures and stores patient demographics and vital sign readings
- Can **read the CRADLE VSA screen using OCR** (camera + TensorFlow Lite model) to auto-fill values
- Classifies readings and gives clinical advice on urgency
- Manages **patient referrals** to health facilities
- Supports **dynamic form-based data collection**
- **Syncs all data to the Cradle Platform backend** via HTTP when internet is available, or falls back to **encrypted SMS** when it is not
- Works fully **offline-first** i.e., health workers can keep taking readings even without connectivity

---------TODO: ADD MORE DETAIL AFTER CONSULTING Dr. Brian

### Who uses it?

| Role | Description |
|------|-------------|
| **VHT (Village Health Team)** | Field workers in remote villages; primary users of the app |
| **Health Worker** | Clinic-based staff who review referrals and assessments |
| **Admin** | System administrators with full access |


## 2. System Architecture

```
┌-----------------------------------------------------┐
│                 Cradle-Mobile (Android)             │
│                                                     │
│  ┌------------┐    ┌--------------┐    ┌----------┐ │
│  │ Activities │    │  ViewModels  │    │ Managers │ │
│  │ Fragments  │<-->│  (LiveData)  │<-->│ (Logic)  │ │
│  └------------┘    └--------------┘    └----------┘ │
│                                            │        │
│                         ┌------------------┤        │
│                         ▼                  ▼        │
│                    ┌---------┐       ┌----------┐   │
│                    │  Room   │       │  RestApi │   │
│                    │   DB    │       │  /OkHttp │   │
│                    └---------┘       └----------┘   │
└------------------------------┬----------------------┘
                               │
              ┌----------------┴-----------------┐
              │ HTTP (internet)                  │ SMS (no internet)
              ▼                                  ▼
 ┌----------------------┐          ┌--------------------------┐
 │   Cradle Platform    │          │     Cradle-SMSRelay      │
 │  (Flask + MySQL)     │<---------│  receives encrypted SMS, │
 │  :5000               │   HTTP   │  forwards to Platform    │
 └----------------------┘          └--------------------------┘
```
**Key design choices:**
- **MVVM** (Model-View-ViewModel) architectural pattern throughout
- **Offline-first**: Room (SQLite) is the single source of truth; sync happens in the background
- **Manager layer** sits between ViewModels and data sources (DB + Network)
- **Dagger Hilt** for dependency injection across the entire app
- **WorkManager** schedules background sync jobs that survive process death
- **Dual transport**: HTTP is the primary sync path; when there is no internet, the app falls back to **encrypted SMS** sent to the **Cradle-SMSRelay** server, which decrypts and forwards the data to Cradle Platform over HTTP

---
## 3. Technology Stack

---------- TODO: See the dependencies and stuff - will write it after completing my code and all so that I don't have to change it again and again

# Getting The App Running

## 4. Prerequisites

Before doing anything else, make sure the following are installed on your machine:

### For Backend (Cradle Platform)
- **Docker Desktop** - must be running when you develop/test
- **Python 3** + `pip` - for seeding scripts and virtual environment
- **Node.js & npm** - for the React js web frontend ( not necessary for android app itself)

### For Android Development
- **Android Studio** (latest stable release)
- **JDK: JetBrains Runtime version 17** ------------ TODO: Add some link or something here
- **Android SDK** with API 24 through API 34 installed

---

## 5. Backend Setup (Cradle Platform)

The mobile app is **useless without the backend running**. It will not log in or sync data. You must set up Cradle Platform first.

### Step 1: Clone the Backend

```bash
git clone https://github.sfu.ca/cradle-project/Cradle-Platform.git
cd Cradle-Platform
```

### Step 2: Create `.env` File

Create a `.env` file in the root of `Cradle-Platform/`:

```env
DB_USERNAME=cradle
DB_PASSWORD=password
LIMITER_DISABLED=True
```

### Step 3: Set Up AWS Cognito Credentials

Create `.env.cognito_secrets` with the credentials provided by your team lead (originally from Dr. Brian Fraser):

```bash
cat > .env.cognito_secrets << EOF
COGNITO_AWS_ACCESS_KEY_ID=<provided-key>
COGNITO_AWS_SECRET_ACCESS_KEY=<provided-secret>
EOF
```

### Step 4: Create Your Personal Cognito User Pool

```bash
# Create a Python virtual environment (required on macOS)
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate

# Install script dependencies
pip install -r scripts/requirements.txt

# Create your own Cognito user pool (replace <your-name>)
python scripts/create_user_pool.py <your-name>

# Copy your pool credentials to the main secrets file
cat .env.cognito_secrets.<your-name> > .env.cognito_secrets
```

> **Note:** Each developer gets their own Cognito user pool to avoid conflicts.

### Step 5: Start Docker

Make sure Docker Desktop is running, then:

```bash
docker compose up -d
```

This starts the **MySQL database** and **Flask backend** containers.

### Step 6: Run Database Migrations

```bash
docker exec cradle_flask flask db upgrade
```

### Step 7: Seed the Database

```bash
# Minimal seed - good for day-to-day development
docker exec cradle_flask python manage.py seed_minimal

# Test data - required for running unit tests
docker exec cradle_flask python manage.py seed_test_data

# Large dataset - lots of random patients/readings
docker exec cradle_flask python manage.py seed
```

### Step 8: (Optional) Start the Web Frontend

```bash
cd client
npm install           # or: npm install --legacy-peer-deps  (if dependency errors appear)
npm run start
```

### Verifying Backend is Running

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000/ |
| Backend API | http://localhost:5000/ |
| API Docs (Swagger) | http://localhost:5000/apidocs |

### Default Login Credentials

```
Email:    admin@email.com
Password: cradle-admin
Role:     Admin (full access)
```

### Shutting Down

```bash
# Stop frontend: Ctrl+C in the npm terminal

# Stop Docker containers
docker compose down

# Deactivate Python virtual environment
deactivate
```

### Quick Start (After Initial Setup)

```bash
cd Cradle-Platform
docker compose up -d

# In a second terminal:
cd Cradle-Platform/client
npm start
```

---

## 6. Android Development Setup

### Step 1: Clone the Mobile Repo

```bash
git clone https://github.sfu.ca/cradle-project/Cradle-Mobile.git
```

### Step 2: Open in Android Studio

Open the cloned folder in Android Studio. Let Gradle sync on first open.

### Step 3: Configure Gradle Versions

Go to **File -> Project Structure -> Project**:

| Setting | Value |
|---------|-------|
| Android Gradle Plugin Version | `8.2.1` |
| Gradle Version | `8.2` |

### Step 4: Configure JDK (Critical!)

Go to **File -> Settings -> Build, Execution, Deployment -> Build Tools -> Gradle**:

| Setting | Value |
|---------|-------|
| Gradle JDK | `JetBrains Runtime version 17` |

If it's not listed, click **"Download JDK"** and select JetBrains Runtime 17.

> **Why this matters:** An external JDK (e.g., Oracle JDK) installed on your system can conflict with the build. This is the most common setup failure. 


### Step 5: Set Up Git Pre-Push Hooks

These hooks run static analysis and unit tests automatically before every push.

**macOS/Linux:**
```bash
hooks/setup-hooks.sh
```

**Windows (Admin Command Prompt):**
```cmd
mklink .git\hooks\pre-push ..\..\hooks\pre-push.sh
```

**Windows (PowerShell as Admin):**
```powershell
New-Item -ItemType SymbolicLink -Path .\.git\hooks -Name pre-push -Value .\hooks\pre-push.sh
```

**Verify:** Run `git push` in a terminal. You should see Detekt and unit tests run before the push completes.

---

## 7. Running the App

### On an Emulator

1. In Android Studio, create or select a virtual device (choose one **with Play Store** for best compatibility)
2. Start the emulator and click the green **Run** button
3. After the app launches, tap the **gear icon** (top right of login screen)
4. Enter connection settings:

| Setting | Value |
|---------|-------|
| Hostname | `10.0.2.2` |
| Port | `5000` |
| Use HTTPS | OFF |

> `10.0.2.2` is Android Studio's special loopback address that forwards to your computer's localhost inside the emulator.

5. Log in with `admin@email.com` / `cradle-admin`

### On a Physical Android Device

1. Enable **Developer Settings** on the phone (tap Build Number 7 times in About Phone)
2. Enable **USB Debugging** in Developer Settings
3. Connect phone via USB - it should appear in Android Studio's device list
4. Find your computer's LAN IP address:
   - **macOS/Linux:** `ifconfig | grep "inet "` 
   - **Windows:** `ipconfig` (look for IPv4 Address, e.g., `192.168.x.x`)
5. Run the app (green arrow)
6. Configure connection settings (gear icon):

| Setting | Value |
|---------|-------|
| Hostname | Your computer's IP (e.g., `192.168.1.42`) |
| Port | `5000` |
| Use HTTPS | OFF |

7. Log in with `admin@email.com` / `cradle-admin`

> **Note:** Both your computer and phone must be on the same Wi-Fi network.

---

## 8. Codebase Structure

All app source code lives under:
```
app/src/main/java/com/cradleplatform/neptune/
```

Here is a breakdown of every major package:

```
neptune/
├── CradleApplication.kt          # App entry point; sets up Hilt, session timeout tracking
│
├── activities/                   # Android Activities (one screen = one Activity)
│   ├── authentication/           # LoginActivity, PinPassActivity
│   ├── dashboard/                # DashBoardActivity (main menu after login)
│   ├── patients/                 # PatientListActivity, PatientProfileActivity, EditPatientInfoActivity
│   ├── newPatient/               # ReadingActivity (orchestrates multi-step reading flow)
│   ├── forms/                    # FormSelectionActivity, FormRenderingActivity, SavedFormsActivity
│   ├── settings/                 # SettingsActivity, HealthFacilitiesActivity
│   ├── education/                # EducationActivity, VideoActivity, PosterActivity
│   ├── statistics/               # StatsActivity
│   └── introduction/             # SplashActivity, IntroActivity (first-time onboarding)
│
├── fragments/                    # Reusable UI fragments (hosted inside Activities)
│   ├── newPatient/               # Multi-step reading flow:
│   │   ├── PatientInfoFragment   #   Step 1: Select/create patient
│   │   ├── SymptomsFragment      #   Step 2: Choose symptoms
│   │   ├── VitalSignsFragment    #   Step 3: Enter BP, HR, urine test
│   │   ├── OcrFragment           #   Step 3b: Camera + OCR auto-fill
│   │   └── AdviceFragment        #   Step 4: Clinical advice based on reading
│   ├── patients/                 # Patient list, search, profile sub-fragments
│   ├── settings/                 # Settings sub-fragments
│   ├── statistics/               # Charts and analytics fragments
│   └── shared/                   # Reusable dialogs, loading indicators
│
├── viewmodel/                    # ViewModels - hold UI state, survive rotation
│   ├── LoginViewModel.kt
│   ├── DashboardViewModel.kt
│   ├── patients/
│   ├── forms/
│   ├── newPatient/
│   ├── settings/
│   └── statistics/
│
├── model/                        # Pure data classes & Room entities
│   ├── Patient.kt                # Patient demographics + custom Jackson serializer
│   ├── Reading.kt                # BP/HR reading with symptoms list
│   ├── Referral.kt               # Referral to a health facility
│   ├── Assessment.kt             # Doctor's follow-up assessment
│   ├── FormTemplate.kt           # Dynamic form schema from server
│   ├── FormResponse.kt           # User's answers to a form
│   ├── HealthFacility.kt         # Facility reference data
│   ├── UserRole.kt               # Enum: VHT, HEALTH_WORKER, ADMIN, etc.
│   └── Statistics.kt             # Analytics data model
│
├── database/                     # Room database layer
│   ├── CradleDatabase.kt         # @Database class, version 2
│   ├── Migrations.kt             # v1 -> v2 migration SQL
│   ├── daos/                     # Data Access Objects (one per entity)
│   │   ├── PatientDao
│   │   ├── ReadingDao
│   │   ├── ReferralDao
│   │   ├── AssessmentDao
│   │   ├── FormClassificationDao
│   │   ├── FormResponseDao
│   │   └── HealthFacilityDao
│   └── views/
│       └── LocalSearchPatient.kt # DB view for efficient patient search
│
├── http_sms_service/             # All external communication
│   ├── http/
│   │   ├── RestApi.kt            # Type-safe API method definitions
│   │   ├── Http.kt               # OkHttp client wrapper
│   │   ├── NetworkResult.kt      # Sealed class: Success / Failure / NetworkException
│   │   ├── OkHttpUtils.kt        # TLS certificate pinning setup
│   │   └── Json.kt               # JSON serialization helpers
│   └── sms/
│       ├── SMSSender.kt          # Sends SMS to relay number
│       ├── SMSReceiver.kt        # BroadcastReceiver for incoming SMS replies
│       ├── SMSFormatter.kt       # Formats patient data into SMS-safe chunks
│       ├── SmsStateReporter.kt   # Tracks and broadcasts SMS state
│       └── SmsErrorHandler.kt   # Handles transmission failures
│
├── sync/                         # Background data synchronization
│   ├── PeriodicSyncer.kt         # Schedules WorkManager periodic jobs
│   ├── workers/
│   │   └── SyncAllWorker.kt      # The actual WorkManager worker
│   └── views/
│       └── SyncActivity.kt       # Manual sync UI with progress display
│
├── manager/                      # Business logic layer (between ViewModels and data)
│   ├── LoginManager.kt           # Login/logout, token storage, session state
│   ├── PatientManager.kt         # Patient CRUD: DB + API
│   ├── ReadingManager.kt         # Reading CRUD: DB + API
│   ├── ReferralManager.kt        # Referral CRUD: DB + API
│   ├── AssessmentManager.kt      # Assessment CRUD: DB + API
│   ├── FormManager.kt            # Form template fetching and caching
│   ├── FormResponseManager.kt    # Form answer storage and upload
│   ├── HealthFacilityManager.kt  # Facility data management
│   ├── UrlManager.kt             # Builds API URLs from stored settings
│   ├── SmsKeyManager.kt          # Manages SMS encryption key lifecycle
│   └── UpdateManager.kt         # Checks for and prompts in-app updates
│
├── ocr/                          # Optical Character Recognition
│   ├── CradleScreenOcrDetector.kt  # Main OCR orchestrator
│   ├── OcrAnalyzer.kt              # CameraX ImageAnalysis use case
│   └── tflite/
│       ├── TFLiteObjectDetectionHelper.kt  # TFLite model runner
│       └── Classifier.kt                   # Result parsing
│
├── di/                           # Dagger Hilt modules
│   ├── DataModule.kt             # Provides DB, DAOs, managers
│   ├── NetworkStateManagerModule.kt
│   ├── SharedPreferencesModule.kt
│   ├── WorkManagerModule.kt
│   └── SmsModules.kt
│
├── adapters/                     # RecyclerView adapters for lists
│   ├── forms/
│   ├── patients/
│   ├── settings/
│   └── introduction/
│
└── utilities/                    # Pure utility classes
    ├── connectivity/             # NetworkStateManager - monitors WiFi/mobile data
    ├── AESEncryptor.kt           # Symmetric encryption for SMS payloads
    ├── GzipCompressor.kt         # Compress data before SMS transmission
    ├── DateUtil.java             # Legacy date formatting
    ├── jackson/                  # Custom Jackson serializer extensions
    └── functional/               # Functional programming helpers
```

## 9. Key Architecture Patterns

### MVVM (Model-View-ViewModel)

Every screen follows MVVM:

```
Activity/Fragment (UI only)  -- observes -->  ViewModel (LiveData) (business logic, survives rotation)  --calls-->  Manager --calls-->  DAO / RestApi                                 
```

- **Activities/Fragments**: Only handle UI events and observe `LiveData`. No business logic here.
- **ViewModels**: Own `LiveData` state. Call managers. Survive configuration changes (screen rotation).
- **Managers**: Coordinate between Room database and the REST API. Handle sync logic.
- **DAOs**: Typed database queries via Room annotations.

### Manager Pattern

Every major entity has a dedicated manager:

```kotlin
// Example: Creating a new reading
readingManager.saveReadingLocally(reading)       // saves to Room DB
readingManager.uploadReading(reading)            // posts to REST API
```

Managers are injected via Hilt wherever needed - ViewModels, Workers, etc.

### NetworkResult Sealed Class

All API calls return a `NetworkResult<T>`:

```kotlin
sealed class NetworkResult<T> {
    data class Success<T>(val value: T) : NetworkResult<T>()
    data class Failure<T>(val statusCode: Int, val body: T?) : NetworkResult<T>()
    data class NetworkException<T>(val cause: IOException) : NetworkResult<T>()
}

// Usage pattern in ViewModel:
when (val result = patientManager.uploadPatient(patient)) {
    is NetworkResult.Success -> { /* update UI */ }
    is NetworkResult.Failure -> { /* show error */ }
    is NetworkResult.NetworkException -> { /* show offline message */ }
}
```

### LiveData & State Enums

ViewModels expose state via typed enums and LiveData:

```kotlin
// LoginViewModel example
val loginState: LiveData<LoginState>

enum class LoginState {
    IDLE, LOADING, SUCCESS, INVALID_CREDENTIALS, NETWORK_ERROR
}
```

Fragments observe these and update the UI reactively - no direct ViewModel-to-Fragment callbacks.

### Dependency Injection with Hilt

Hilt modules are in the `di/` package. Every injectable class is annotated with `@Inject constructor(...)`. To add a new dependency:

1. Declare it in a `@Module` class in `di/`
2. Use `@Inject` in your class constructor
3. Hilt wires it up automatically

---
## 10. Core Data Models

### Patient

```kotlin
// model/Patient.kt
@Entity(tableName = "Patient")
data class Patient(
    @PrimaryKey val id: String,         // UUID
    val name: String,
    val dob: String?,                   // ISO 8601 date
    val isExactDob: Boolean,
    val sex: Sex,                       // Enum: MALE, FEMALE
    val villageNumber: String?,
    val zone: String?,
    val householdNumber: String?,
    val phoneNumber: String?,
    val isPregnant: Boolean,
    val gestationalAge: GestationalAge?,
    // ... upload tracking fields
)
```

### Reading

```kotlin
// model/Reading.kt
@Entity(tableName = "Reading", foreignKeys = [/* Patient FK */])
data class Reading(
    @PrimaryKey val id: String,
    val patientId: String,
    val dateTimeTaken: Long,            // Unix timestamp
    val bpSystolic: Int?,              // Systolic blood pressure
    val bpDiastolic: Int?,             // Diastolic blood pressure
    val heartRateBPM: Int?,            // Heart rate
    val symptoms: List<String>,        // e.g. ["HEADACHE", "BLURRED_VISION"]
    val trafficLightStatus: TrafficLight,  // GREEN / YELLOW_UP / RED_DOWN / etc.
    val urineTest: UrineTest?,
    val isFlaggedForFollowup: Boolean,
    // ... referral/upload tracking fields
)
```

### Referral

```kotlin
// model/Referral.kt
@Entity(tableName = "Referral")
data class Referral(
    @PrimaryKey val id: String,
    val patientId: String,
    val readingId: String,
    val referralHealthFacilityName: String,
    val dateReferred: Long,
    val comment: String?,
    val isAssessed: Boolean,
    // ... upload tracking
)
```

### FormTemplate + FormResponse

Dynamic forms are driven by `FormTemplate` objects downloaded from the server. Each template contains a JSON schema describing question types, validation rules, and ordering. A `FormResponse` stores the user's answers, keyed to the template.

---

## 11. Network Communication

### HTTP via RestApi

All server communication goes through `RestApi.kt` (in `http_sms_service/http/`). Every method is type-safe and returns `NetworkResult<T>`:

```kotlin
// Key endpoints:
suspend fun authenticate(email: String, password: String): NetworkResult<LoginResponse>
suspend fun getPatients(lastSyncTime: Long): NetworkResult<List<Patient>>
suspend fun postPatient(patient: Patient): NetworkResult<Patient>
suspend fun getReadings(lastSyncTime: Long): NetworkResult<List<Reading>>
suspend fun postReading(reading: Reading): NetworkResult<Reading>
suspend fun postReferral(referral: Referral): NetworkResult<Referral>
suspend fun getHealthFacilities(): NetworkResult<List<HealthFacility>>
suspend fun getFormTemplates(): NetworkResult<List<FormTemplate>>
suspend fun postFormResponse(response: FormResponse): NetworkResult<FormResponse>
suspend fun getStatistics(): NetworkResult<Statistics>
```

### Security: TLS Certificate Pinning

`OkHttpUtils.kt` configures `CertificatePinner` on the OkHttp client. This means the app will **only trust the specific server certificate(s)** it was built with. If the server rotates its TLS certificate, the pins in `OkHttpUtils.kt` must be updated.

To get new certificate pins:
```bash
# Using the provided script
scripts/x509-subject-pubkey-hash.sh <hostname>

# Or follow OkHttp's guide
# https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/#setting-up-certificate-pinning
```

### Authentication

The app uses **Bearer token authentication**:
1. `POST /user/authenticate` with email + password -> returns `LoginResponse` containing an access token
2. The token is stored in `EncryptedSharedPreferences`
3. All subsequent requests include `Authorization: Bearer <token>` header
4. `LoginManager` is the single place that reads/writes the token

---


## 12. Data Synchronization

### How Sync Works

Data flows in two directions:

**Download (Server -> App):** New/updated patients, readings, form templates, health facilities, and assessments are fetched and stored locally in Room.

**Upload (App -> Server):** Locally created patients, readings, referrals, assessments, and form responses that haven't been uploaded yet are sent to the server.

### Sync Trigger Points

| Trigger | When |
|---------|------|
| **Periodic** | WorkManager job runs every few hours (configurable) |
| **On Login** | Sync immediately after successful login |
| **Network Restored** | `NetworkStateManager` detects connectivity -> triggers sync |
| **Manual** | User taps "Sync" on the dashboard |

### SyncAllWorker (the core)

`sync/workers/SyncAllWorker.kt` runs as a `CoroutineWorker`. Its sequence:

1. Download patients from server (since last sync timestamp)
2. Upload locally-created patients
3. Download form templates
4. Upload unsent form responses
5. Upload unsent readings
6. Upload unsent referrals
7. Upload unsent assessments
8. Download server-side assessments

Each step is independent - a failure in step 3 doesn't abort steps 4+. The last sync timestamp is stored in `SharedPreferences` and updated only on complete success.

### Upload Tracking

Entities have an `isUploadedToServer: Boolean` flag (and similar fields). The sync worker queries `WHERE isUploadedToServer = 0` to find items needing upload.

---

## 13. Authentication & Session Management

### Login Flow

```
User enters email + password
        |
        ▼
LoginManager.login()
        |
        ▼
RestApi.authenticate() -> POST /user/authenticate
        |
        ▼
LoginResponse received:
  - access token
  - user info (id, email, role, phone numbers)
  - SMS encryption key
        |
        ▼
Store token in EncryptedSharedPreferences
Start PeriodicSyncer
Navigate to DashBoard
```

### PIN-Based Session Lock

After **30 minutes of inactivity**, the app requires the user's PIN:

1. `CradleApplication` registers an `ActivityLifecycleCallbacks` to track the last-active timestamp
2. On each Activity `onStart`, the timestamp is checked
3. If elapsed time > 30 minutes, `PinPassActivity` is started (it cannot be bypassed via back button)
4. The user must enter their PIN to resume
5. PIN is set during first login and stored in `EncryptedSharedPreferences`

### Logout

Logout does the following in order:
1. Cancels all pending WorkManager sync jobs
2. Clears the Room database (all local patient data)
3. Removes the auth token and user info from `SharedPreferences`
4. Navigates to `LoginActivity`

---

## 14. OCR Feature

One of the most unique features: the app can **photograph the CRADLE VSA device screen** and automatically extract the blood pressure and heart rate values.

### How It Works

```
Camera Preview (CameraX)
        |
        ▼
OcrAnalyzer (ImageAnalysis use case)
        |
        ▼
TFLiteObjectDetectionHelper
  (runs the TFLite model on each frame)
        |
        ▼
CradleScreenOcrDetector
  (post-processes detections: filters, validates, parses numbers)
        |
        ▼
Auto-fills BP and HR fields in VitalSignsFragment
```

The TFLite model file (`.tflite`) lives in `res/raw/` and is loaded at runtime. The model was trained to detect the specific digit regions on the CRADLE VSA screen.

### Where to Find It

- `ocr/CradleScreenOcrDetector.kt` - main logic
- `ocr/OcrAnalyzer.kt` - CameraX integration
- `ocr/tflite/TFLiteObjectDetectionHelper.kt` - model runner
- `fragments/newPatient/OcrFragment` - the UI screen

---

## 15. SMS Relay System

When there is **no internet**, the app can send patient data via **encrypted SMS** to a relay phone number, which then forwards it to the server.

### How It Works

```
Patient data to upload
        │
        ▼
SMSFormatter: serialize + compress (Gzip) + encrypt (AES)
        |
        ▼
SMSSender: split into SMS-sized chunks, send to relay number
        |
        ▼
[SMS Relay Server receives and forwards to Cradle Platform]
        |
        ▼
SMSReceiver (BroadcastReceiver): receives confirmation SMS reply
        |
        ▼
SmsStateReporter: updates UI with success/failure state
```

### Key Files

| File | Role |
|------|------|
| `SMSSender.kt` | Sends SMS chunks via Android's `SmsManager` |
| `SMSReceiver.kt` | `BroadcastReceiver` for incoming relay replies |
| `SMSFormatter.kt` | Serializes + chunks data into 160-char SMS segments |
| `SmsKeyManager.kt` | Manages the AES encryption key (obtained at login, periodically refreshed) |
| `AESEncryptor.kt` | AES symmetric encryption implementation |
| `GzipCompressor.kt` | Compresses payloads before encryption |
| `RelayRequestCounter` | Rate-limits to prevent flooding the relay number |

### Configuration

The relay phone number is configured in **Settings -> SMS Relay Number**. The encryption key is provided by the server at login and stored in `EncryptedSharedPreferences`.

---

## 16. Testing

### Unit Tests

Located in `app/src/test/`. Run without a device.

```bash
# Run all unit tests
./gradlew test

# Run with detailed output
./gradlew test --info
```

**Key test files:**

| File | What It Tests |
|------|--------------|
| `RestApiTest.kt` | HTTP API methods using `MockWebServer` |
| `SMSFormatterTest.kt` | SMS data serialization and chunking |
| `SmsSenderTests.kt` | SMS sending logic |
| `DatabaseTypeConvertersTests.kt` | Room type converters for custom types |
| `PatientReadingViewModelTests.kt` | ViewModel business logic |
| `MigrationTest.kt` | Room database schema migration (v1 -> v2) |

**Mocking stack:** `Mockk` is preferred (idiomatic Kotlin); legacy tests may use `Mockito`.

### Static Code Analysis (Detekt)

```bash
./gradlew detekt
```

Detekt enforces code style and catches common Kotlin pitfalls. It is likely to fail on first run - a second run with auto-corrections usually resolves it. If issues remain after the second run, manual fixes are needed.

Configuration: `detekt.gradle` in the root.

### Instrumented Tests (On-Device)

Located in `app/src/androidTest/`. Require a connected device or running emulator.

```bash
./gradlew connectedAndroidTest
```

These test database migrations and UI flows end-to-end. **Not run by CI**, but should be run before every release.

### UI Tests (Espresso)

**Required setup before running Espresso tests:**

1. On the test device, go to **Settings -> Developer Options** and disable all three:
   - Window animation scale
   - Transition animation scale
   - Animator duration scale
2. Uninstall or log out of the app (login tests fail if already logged in)

---

## 17. Code Quality & Git Workflow

### Pre-Push Hook (Mandatory)

The hook at `hooks/pre-push.sh` runs automatically on every `git push`:
1. Detekt static analysis
2. All unit tests

**Do not skip this** (`--no-verify` is not recommended). Fix failures before pushing.

### CI Pipeline

GitHub CI runs the same checks (Detekt + unit tests) on every pull request and every commit to `master`. A PR cannot be merged until CI passes.

### Branching Convention

- `main` - stable, always deployable
- Feature branches - `feature/<description>` or `fix/<description>`
- Open a pull request into `main` for every change; get at least one review before merging

### Coding Conventions

- **Kotlin** for all new code - no new Java files
- Follow Android Kotlin style guide
- Use `suspend fun` for all database and network operations (coroutines, not callbacks)
- Use `LiveData` for UI-observable state in ViewModels
- Inject dependencies via Hilt - do not create instances manually in Activities/Fragments
- Use `EncryptedSharedPreferences` for all sensitive data storage
- Handle all three `NetworkResult` cases (Success, Failure, NetworkException) - never ignore Failure/Exception

---

## 18. Troubleshooting

### Build Fails with JDK Error

**Symptom:** Build fails with class file version or JDK-related errors.

**Fix:** Uninstall any externally-installed JDK (Oracle, OpenJDK from external source). Delete the repo, re-clone, and use only the JetBrains Runtime 17 bundled with Android Studio.

### App Won't Connect to Backend

**Checklist:**
1. Is Docker running? (`docker ps` should show `cradle_flask` and the MySQL container)
2. Did you run database migrations? (`docker exec cradle_flask flask db upgrade`)
3. Are you using the correct hostname?
   - Emulator: `10.0.2.2`
   - Physical device: Your computer's LAN IP (`192.168.x.x`)
4. Is HTTPS turned **OFF** in the app login settings?
5. Are both your computer and phone on the same Wi-Fi? (for physical device)

### Emulator Won't Install App

**Symptom:** Install fails or app crashes immediately on emulator.

**Fix:** Choose an emulator that **includes Play Store** (Google Play System). Create a new AVD in Android Studio's Device Manager if needed.

### Quick Reference: Gradle Commands

```bash
./gradlew test                    # Run unit tests
./gradlew detekt                  # Static code analysis (run twice)
./gradlew connectedAndroidTest    # Run instrumented tests (needs device)
./gradlew assembleDebug           # Build debug APK
./gradlew assembleRelease         # Build release APK (requires signing config)
./gradlew clean                   # Clean build directory
```
