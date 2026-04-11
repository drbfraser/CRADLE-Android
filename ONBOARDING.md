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


### Quick Reference: Gradle Commands

```bash
./gradlew test                    # Run unit tests
./gradlew detekt                  # Static code analysis (run twice)
./gradlew connectedAndroidTest    # Run instrumented tests (needs device)
./gradlew assembleDebug           # Build debug APK
./gradlew assembleRelease         # Build release APK (requires signing config)
./gradlew clean                   # Clean build directory
```
