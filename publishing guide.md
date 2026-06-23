# Automated Mileage Tracker & Classifier

## Technical Specification

### 1. System Architecture Overview
The system follows a local-first architecture to minimize battery consumption and ensure offline functionality, with an optional cloud synchronization layer for user authentication and multi-device access.

```text
[Vehicle Start Event]
        │
        ▼ (Low Power Mode)
[CoreMotion / ActivityRecognition] ────► [Local Notification Trigger]
                                                 │
                                                 ▼ (User Interaction)
                                      [Select: Work / Private]
                                                 │
                                                 ▼
                                      [High-Accuracy GPS Active]
                                                 │
                                                 ▼ (Vehicle Stop Event)
                                      [Write to Local SQLite / Room]
                                                 │
                                                 ▼ (Async Sync)
                                      [GCP Firestore / Cloud Backend]
```

## 2. Core Modules & Technical Implementation

### 2.1 Activity Detection & Trip Lifecycle Module
**Purpose:** Passively detect when the user enters and starts driving a vehicle without continuously running high-accuracy GPS tracking.

#### iOS Implementation
- Utilize the `CMMotionActivityManager` to query background motion data.
- Monitor for the automotive state transition (`activity.automotive == true`).

#### Android Implementation
- Register a `PendingIntent` with the `ActivityRecognitionClient` using the Transition API (`ActivityTransitionRequest`).
- Listen for `DetectedActivity.IN_VEHICLE` with an `ACTIVITY_TRANSITION_ENTER` event.

#### Alternative Trigger (Hardware Bluetooth)
- Register a broadcast receiver/background listener for specific Bluetooth UUIDs or MAC addresses corresponding to the vehicle's head unit.
- This provides an immediate, definitive trigger the moment the ignition turns on.

### 2.2 Notification & Classification Interaction Module
**Purpose:** Prompt the user to classify the trip instantly upon detection, before or during the drive.

#### Mechanics
- Trigger a high-priority local notification immediately when the vehicle state is entered.
- Implement Actionable Notifications (iOS) or Notification Actions (Android) to embed interactive buttons directly on the lock screen.
- Action Buttons: `Work` and `Private`.
- Payload Handling: Tapping an action button invokes a background service callback passing the classification enum (`TRIP_TYPE_WORK` or `TRIP_TYPE_PRIVATE`) and the current timestamp, preventing the need to launch the main application UI.

### 2.3 Location Tracking & Geospatial Module
**Purpose:** Capture accurate distance data once a trip is classified.

#### Lifecycle
- **Activation:** Initiated only after the user selects a classification from the notification.
- **Execution:** Launches a Foreground Service (Android) with a persistent notification, or requests background location execution with the `showsBackgroundLocationIndicator` flag (iOS) to protect the process from OS termination.
- **Configuration:** Set distance filter to 10 meters and accuracy to high/fine (`kCLLocationAccuracyBest` / `PRIORITY_HIGH_ACCURACY`).
- **Distance Calculation:** Accumulate distance using the Haversine formula or native OS distance utilities (`CLLocation.distance(from:)` / `Location.distanceBetween()`) between sequential coordinate points to avoid straight-line distortion.
- **Deactivation:** Terminates when activity recognition detects `WALKING` or `STILL` for a sustained period (e.g., 3 minutes), or when the vehicle Bluetooth connection drops.

### 2.4 Vision (OCR) Odometer Backup Module
**Purpose:** Provide an alternative verification method via dashboard photography.

#### Implementation
- Integrate Google ML Kit Text Recognition API (on-device SDK) or Apple's Vision framework (`VNRecognizeTextRequest`).

#### Pipeline
1. Capture image via native camera interface.
2. Convert image to bitmap/pixel buffer and pass it to the on-device OCR engine.
3. Parse text blocks using a regular expression (for example, `\b\d{5,6}\b`) targeted at isolating sequential numerical strings matching standard odometer mileage lengths.
4. Exclude extraneous dashboard data (temperature, clock time) by checking bounding box proximity and string structure.

### 2.5 Storage & Synchronization Layer

#### Local Storage
- Implement a local relational database (SQLite via Room on Android, or SQLite/CoreData on iOS) to log trips immediately.
- Ensures full offline compliance; trips are safely cached locally.

#### Cloud Synchronization & User System (Optional Component)
- **Authentication:** Firebase Authentication (supporting Google Sign-In, Email/Password) to manage user identity securely.
- **NoSQL Database:** Google Cloud Firestore. Local database records append a `synced` boolean flag (default `false`). A background worker (e.g., WorkManager on Android, Background Tasks on iOS) executes an asynchronous synchronization routine to push unsynced records to a Firestore collection partitioned by `userId` when network connectivity is verified.
- **Web Integration:** A lightweight cloud backend (such as a Python/Flask microservice deployed to Google Cloud Run) can interface with the database to automatically format and append logs to a specific Google Sheet via the Google Sheets API, or compile monthly Excel files for download.

## 3. Data Schema (JSON Representation)

```json
{
  "tripId": "UUID-v4-string",
  "userId": "auth-user-id-string",
  "status": "completed | active | pending_classification",
  "classification": "work | private",
  "startTimestamp": 1782294000,
  "endTimestamp": 1782297600,
  "startLocation": {
    "latitude": -26.1044,
    "longitude": 27.9944
  },
  "endLocation": {
    "latitude": -26.1432,
    "longitude": 27.9123
  },
  "gpsCalculatedDistanceKm": 14.25,
  "odometerBackup": {
    "imageLocallyStoredPath": "file://...",
    "detectedValue": 124550,
    "verifiedAtTimestamp": 1782297650
  },
  "synced": false
}
```

## 4. Critical Battery & OS Constraints
- **Doze Mode & Power Saving:** Background GPS tracking will be throttled heavily by mobile operating systems unless a foreground service with a visible status bar notification is active.
- **Geofencing Optimization:** To further optimize battery, replace persistent activity recognition with a low-power geofence circular boundary (for example, 50 meters) around the last known parking spot. When the user exits the geofence, spin up the activity recognition layer to verify if they are in a vehicle.

## 5. Backend Infrastructure & Security Rules
- **Authentication:** Google OAuth via Firebase Authentication assigns a unique `uid` to each user upon login. This `uid` is passed securely to Firestore with every database request.
- **Firestore Security Rules:** The database must strictly lock down read and write access to the authenticated user's own data.

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // 1. Secure the User Profile Collection
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    // 2. Secure the Trips Collection
    match /trips/{tripId} {
      allow create: if request.auth != null && request.auth.uid == request.resource.data.userId;
      allow read, update, delete: if request.auth != null && request.auth.uid == resource.data.userId;
    }
  }
}
```

## 6. Apple App Store & Google Play Publishing Requirements

### 6.1 Background Location Approval
**Google Play:**
- Requires a "Prominent Disclosure" screen explaining exactly why background location is needed, what data is collected, and how it is used, displayed immediately before the OS permission prompt.
- Requires uploading a short video during the app review process demonstrating the core tracking feature and the prominent disclosure.

**Apple App Store:**
- Requires detailed justifications in `Info.plist` for `NSLocationAlwaysAndWhenInUseUsageDescription` and `NSMotionUsageDescription`.
- Requires the app to remain functional if the user selects "Allow Once" or "While Using App" instead of "Always." 

### 6.2 Authentication Requirements
- **Sign in with Apple Mandate:** Apple Guideline 4.8 strictly dictates that if an app uses a third-party login service (Google OAuth), it must also offer "Sign in with Apple" as an equivalent option.
- **Account Deletion:** Both Google Play and the Apple App Store mandate an intuitive, in-app mechanism for users to delete their account and all associated personal data.

### 6.3 Privacy Policy & Data Handling
- **Hosted Privacy Policy:** A publicly accessible URL hosting a comprehensive Privacy Policy stating how location and camera data is handled.
- **Data Safety Questionnaires:** Requires completing the Google Play Data Safety form and the Apple App Privacy labels declaring data collection.

### 6.4 Developer Accounts & Testing Restrictions
- **Google Play 20-Tester Rule:** New personal developer accounts require the app to be tested by at least 20 opted-in users for 14 continuous days on the Closed Testing track before applying for production release.
- **Apple App Store:** Requires Xcode running on macOS for the final compilation and submission.

## 7. HUAWEI AppGallery Publishing Requirements

### 7.1 Technical Adaptations (HMS Integration)
Modern Huawei devices do not support Google Mobile Services (GMS). The codebase requires flavoring to support Huawei Mobile Services (HMS).

- **Authentication:** Swap Google OAuth for Huawei Account Kit or an email/password fallback.
- **Activity & Location Tracking:** Swap Google APIs for Huawei Location Kit (including `ActivityIdentificationService`).
- **OCR Module:** Swap Google ML Kit for Huawei ML Kit (Text Recognition).
- **Database & Cloud Sync:** Direct client-side Firestore integration requires GMS. Route database traffic through the Python/Flask microservice backend via standard HTTPS REST APIs for universal compatibility.

### 7.2 Publishing & Verification Requirements
- **Developer Account Verification:** Registration requires uploading a passport, driver's license, or national ID.
- **Background Location Review:** Requires a dedicated permission application form within AppGallery Connect and a video demonstration.
- **Privacy & Distribution:** Requires a prominent privacy policy URL. Distribution in mainland China mandates local data residency, an Internet Content Provider (ICP) license, and compliance with the Personal Information Protection Law (PIPL).
