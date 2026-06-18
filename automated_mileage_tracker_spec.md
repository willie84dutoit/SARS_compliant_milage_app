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

---

### 2. Core Modules & Technical Implementation

#### 2.1. Activity Detection & Trip Lifecycle Module
* **Purpose:** Passively detect when the user enters and starts driving a vehicle without continuously running high-accuracy GPS tracking.
* **iOS Implementation:**
    * Utilize the `CMMotionActivityManager` to query background motion data.
    * Monitor for the `automotive` state transition (`activity.automotive == true`).
* **Android Implementation:**
    * Register a `PendingIntent` with the `ActivityRecognitionClient` using the Transition API (`ActivityTransitionRequest`).
    * Listen for `DetectedActivity.IN_VEHICLE` with an `ACTIVITY_TRANSITION_ENTER` event.
* **Alternative Trigger (Hardware Bluetooth):** Register a broadcast receiver/background listener for specific Bluetooth UUIDs or MAC addresses corresponding to the vehicle's head unit. This provides an immediate, definitive trigger the moment the ignition turns on.

#### 2.2. Notification & Classification Interaction Module
* **Purpose:** Prompt the user to classify the trip instantly upon detection, before or during the drive.
* **Mechanics:**
    * Trigger a high-priority local notification immediately when the vehicle state is entered.
    * Implement Actionable Notifications (iOS) or Notification Actions (Android) to embed interactive buttons directly on the lock screen widget.
    * **Action Buttons:** `[Work]` and `[Private]`.
* **Payload Handling:** Tapping an action button invokes a background service callback passing the classification enum (`TRIP_TYPE_WORK` or `TRIP_TYPE_PRIVATE`) and the current timestamp, preventing the need to launch the main application UI.

#### 2.3. Location Tracking & Geospatial Module
* **Purpose:** Capture accurate distance data once a trip is classified.
* **Lifecycle:**
    * **Activation:** Initiated only after the user selects a classification from the notification.
    * **Execution:** Launches a Foreground Service (Android) with a persistent notification, or requests background location execution with the `showsBackgroundLocationIndicator` flag (iOS) to protect the process from OS termination.
    * **Configuration:** Set distance filter to 10 meters and accuracy to high/fine (`kCLLocationAccuracyBest` / `PRIORITY_HIGH_ACCURACY`).
    * **Distance Calculation:** Accumulate distance using the Haversine formula or native OS distance utilities (`CLLocation.distance(from:)` / `Location.distanceBetween()`) between sequential coordinate points to avoid straight-line distortion.
    * **Deactivation:** Terminates when the activity recognition detects `WALKING` or `STILL` for a sustained period (e.g., 3 minutes), or when the vehicle Bluetooth connection drops.

#### 2.4. Vision (OCR) Odometer Backup Module
* **Purpose:** Provide an alternative verification method via dashboard photography.
* **Implementation:**
    * Integrate Google ML Kit Text Recognition API (on-device SDK) or Apple's Vision framework (`VNRecognizeTextRequest`).
    * **Pipeline:**
        1. Capture image via native camera interface.
        2. Convert image to bitmap/pixel buffer and pass to the on-device OCR engine.
        3. Parse text blocks using a regular expression (e.g., `\b\d{5,6}\b`) targeted at isolating sequential numerical strings matching standard odometer mileage lengths.
        4. Exclude extraneous dashboard data (temperature, clock time) by checking bounding box proximity and string structure.

#### 2.5. Storage & Synchronization Layer
* **Local Storage:**
    * Implement a local relational database (SQLite via Room on Android, or SQLite/CoreData on iOS) to log trips immediately.
    * Ensures full offline compliance; trips are safely cached locally.
* **Cloud Synchronization & User System (Optional Component):**
    * **Authentication:** Firebase Authentication (supporting Google Sign-In, Email/Password) to manage user identity securely.
    * **NoSQL Database:** Google Cloud Firestore. Local database records append a `synced` boolean flag (default `false`). A background worker (e.g., WorkManager on Android, Background Tasks on iOS) executes an asynchronous synchronization routine to push unsynced records to a Firestore collection partitioned by `userId` when network connectivity is verified.
    * **Web Integration:** A lightweight cloud backend (such as a Python/Flask microservice deployed to Google Cloud Run) can interface with the database to automatically format and append logs to a specific Google Sheet via the Google Sheets API, or compile monthly Excel files for download.

---

### 3. Data Schema (JSON Representation)

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

---

### 4. Critical Battery & OS Constraints

* **Doze Mode & Power Saving:** Background GPS tracking will be throttled heavily by mobile operating systems unless a foreground service with a visible status bar notification is active.
* **Geofencing Optimization:** To further optimize battery, replace persistent activity recognition with a low-power geofence circular boundary (e.g., 50 meters) around the last known parking spot. When the user exits the geofence, spin up the activity recognition layer to verify if they are in a vehicle.
