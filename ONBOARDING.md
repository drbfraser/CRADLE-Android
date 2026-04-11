# Cradle-Mobile: New Team Onboarding Guide

**CRADLE VSA System - Android Application**  
A health-tech platform that reduces preventable maternal deaths in remote Ugandan villages by capturing vital signs and syncing patient data to a central backend even without internet connectivity.

---

## Table of Contents

1. [Project Overview](#1-project-overview)

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

# TODO: ADD MORE DETAIL AFTER CONSULTING Dr. Brian

### Who uses it?

| Role | Description |
|------|-------------|
| **VHT (Village Health Team)** | Field workers in remote villages; primary users of the app |
| **Health Worker** | Clinic-based staff who review referrals and assessments |
| **Admin** | System administrators with full access |

### Quick Reference: Gradle Commands

```bash
./gradlew test                    # Run unit tests
./gradlew detekt                  # Static code analysis (run twice)
./gradlew connectedAndroidTest    # Run instrumented tests (needs device)
./gradlew assembleDebug           # Build debug APK
./gradlew assembleRelease         # Build release APK (requires signing config)
./gradlew clean                   # Clean build directory
```