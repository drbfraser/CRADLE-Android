# Cradle-Mobile: New Team Onboarding Guide

**CRADLE VSA System - Android Application**  
A health-tech platform that reduces preventable maternal deaths in remote Ugandan villages by capturing vital signs and syncing patient data to a central backend even without internet connectivity.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [System Architecture](#2-system-architecture)
3. [Technology Stack](#3-technology-stack)
4. [Prerequites](#4-prerequisites)

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

Go to **File → Project Structure → Project**:

| Setting | Value |
|---------|-------|
| Android Gradle Plugin Version | `8.2.1` |
| Gradle Version | `8.2` |

### Step 4: Configure JDK (Critical!)

Go to **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**:

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


### Quick Reference: Gradle Commands

```bash
./gradlew test                    # Run unit tests
./gradlew detekt                  # Static code analysis (run twice)
./gradlew connectedAndroidTest    # Run instrumented tests (needs device)
./gradlew assembleDebug           # Build debug APK
./gradlew assembleRelease         # Build release APK (requires signing config)
./gradlew clean                   # Clean build directory
```
