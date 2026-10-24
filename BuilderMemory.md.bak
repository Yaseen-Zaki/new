# FONDI PHOTO SYNC — DEEPSEEK BUILDER MEMORY

**Document type:** Master project specification, implementation roadmap, and progress tracker  
**Project status:** Environment preparation in progress  
**Developer:** Beginner working with AI-assisted coding  
**IDE:** GitHub Codespaces  
**Repository:** `new`  
**Target device:** Fondi tablet running Android 4.4 (API 19)  
**Secondary device:** Samsung Galaxy A15  
**Cloud provider:** Cloudflare Workers and Cloudflare R2  
**Primary language:** Java for Android; JavaScript for Cloudflare Worker  
**Main objective:** Build a reliable photo-sync Android application for the old tablet.

---

# PART 1 — RULES FOR THE AI BUILDER

You are my technical guide and coding assistant for this project.

I am a beginner in Java, Android development, APIs, networking, and cloud development. I know basic Python and have some experience with C.

Follow these rules throughout the project.

## 1.1 Work incrementally

- Implement one small, clearly defined component at a time.
- Explain what the component does and why it is necessary.
- Give me the exact commands to execute in GitHub Codespaces.
- Identify exactly which files must be created or modified.
- Provide complete file contents when replacing a file is appropriate.
- Explain how to verify the result.
- Wait for me to report the result before treating the component as complete.
- Maintain this document's progress tracker after each successful milestone.

Do not generate the entire application in one enormous response.

## 1.2 Protect existing work

The GitHub repository is named `new`.

The expected workspace path is:

`/workspaces/new`

Before changing files:

1. Confirm the current working directory.
2. Inspect the existing project files.
3. Check Git status.
4. Identify the files that need to change.
5. Avoid overwriting existing work unnecessarily.

Never assume a command completed just because it was started.

If a terminal process terminates unexpectedly, diagnose the current state and resume from the last successful step. Do not blindly repeat an entire setup script.

## 1.3 Maintain accurate progress

Every checklist item must have exactly one of these statuses:

- `NOT STARTED`
- `IN PROGRESS`
- `BLOCKED`
- `COMPLETED`

A component can be marked `COMPLETED` only when its defined acceptance criteria have been satisfied.

Examples:

- Installing Java does not mean the Android project is complete.
- Creating a Worker does not mean the cloud connection works.
- Compiling an APK does not prove that it runs on Android 4.4.
- A successful upload does not prove that duplicate prevention or retries work.
- A feature that has not been tested must not be described as tested.

When updating the tracker, preserve all existing statuses unless new evidence justifies changing them.

Never reset completed items to `NOT STARTED` without a specific reason.

## 1.4 Keep the implementation appropriate for the device

The Fondi tablet runs Android 4.4, API 19.

The application must support API 19 and later.

Avoid relying on modern Android APIs without checking their availability on API 19. When necessary, implement version-specific alternatives.

Prefer Java and Android's native APIs over unnecessarily complicated frameworks.

Do not introduce Kotlin, Flutter, React Native, or other frameworks unless a demonstrated technical need justifies the change.

The application should be lightweight and suitable for an old, low-performance device.

## 1.5 Explain before making major architectural changes

The chosen architecture is described below.

Do not replace Cloudflare R2, introduce a different database, or change the application framework without explaining the reason and obtaining my agreement.

Avoid unnecessary dependencies and services.

The goal is a working, understandable application, not an unnecessarily complicated production platform.

## 1.6 Handle credentials securely

Never embed permanent Cloudflare API tokens, R2 secret keys, or other powerful cloud credentials in the Android APK.

Never publish these credentials in GitHub or commit them to the repository.

The Worker must control authorization for cloud operations.

Use temporary, narrowly scoped upload or download URLs when appropriate.

If a credential is accidentally exposed, explain how to revoke or rotate it.

## 1.7 Keep instructions efficient

Avoid extensive web research when established technical knowledge is sufficient.

Consult official documentation when verifying important compatibility requirements, APIs, security rules, or changing service limits and pricing.

Do not spend time researching unrelated technologies.

When an error occurs, diagnose the error before proposing unrelated changes.

---

# PART 2 — WHAT WE ARE BUILDING

## 2.1 Main purpose

Build an Android application that turns the old Fondi tablet into an automatic photo uploader.

The tablet will capture or receive photos in a local folder. The application will monitor the selected folder and upload new photos to cloud storage.

The Samsung A15 will access those photos over the internet.

The devices must not need to be connected to the same Wi-Fi network.

The original photos must remain on the Fondi tablet.

## 2.2 Essential requirements

The application must eventually provide the following functionality:

1. Let the user select a local photo folder.
2. Scan that folder for photos.
3. Identify photos that have not been uploaded successfully.
4. Upload new photos automatically.
5. Preserve the original local files.
6. Avoid uploading the same photo repeatedly.
7. Retry failed uploads.
8. Preserve upload progress across application restarts.
9. Provide Start, Pause, and Stop controls.
10. Display upload progress and useful status messages.
11. Report failures in a way that helps diagnose them.
12. Continue working with the screen locked as reliably as Android 4.4 permits.
13. Upload photos over HTTPS to the cloud backend.
14. Allow the Samsung A15 to access the uploaded photos over the internet.
15. Keep cloud credentials out of the APK.
16. Minimize unnecessary CPU, memory, network, and battery usage.
17. Work within a cost-conscious, preferably zero-cost development and operating budget.

The application must not delete local originals after uploading.

## 2.3 Photo volume

Planning assumptions:

- Average photo size: approximately 0.31 MB.
- Expected volume: at least 1,000 photos per day.
- Approximate new storage per day: 310 MB.
- Approximate new storage per 30 days: 9.3 GB.
- Approximate new storage per year: 113 GB.

These are estimates, not guarantees. Actual sizes and storage requirements may differ.

Cloud storage grows continuously if all photos are retained.

Design the system so that the upload process can scale to this volume, but do not assume that unlimited storage or bandwidth will remain free.

Check current Cloudflare limits, allowances, and pricing before deploying a high-volume service.

---

# PART 3 — SYSTEM ARCHITECTURE

Use the following architecture unless a specific technical issue requires a change.

    FONDI TABLET — ANDROID 4.4
    ┌───────────────────────────────┐
    │ Photo folder                  │
    │       ↓                       │
    │ Folder scanner                │
    │       ↓                       │
    │ Duplicate detection           │
    │       ↓                       │
    │ Local upload queue/database   │
    │       ↓                       │
    │ Upload manager and retries    │
    └───────────────┬───────────────┘
                    │
                    │ HTTPS
                    ▼
    ┌───────────────────────────────┐
    │ CLOUDFLARE WORKER             │
    │                               │
    │ Authentication/authorization │
    │ Upload authorization          │
    │ Request validation            │
    │ Optional photo-list API       │
    └───────────────┬───────────────┘
                    │
                    ▼
    ┌───────────────────────────────┐
    │ CLOUDFLARE R2                 │
    │                               │
    │ Private object storage        │
    │ Original uploaded photos      │
    └───────────────┬───────────────┘
                    │
                    │ Authorized access
                    ▼
    ┌───────────────────────────────┐
    │ SAMSUNG GALAXY A15            │
    │                               │
    │ Cloudflare dashboard initially│
    │ Optional custom gallery later │
    └───────────────────────────────┘

## 3.1 Android application

The Android app is responsible for:

- Selecting the photo folder.
- Scanning for photos.
- Maintaining the upload queue.
- Tracking upload status.
- Requesting authorization from the Worker.
- Uploading photo data.
- Retrying failed operations.
- Saving progress locally.
- Displaying controls and status information.

## 3.2 Cloudflare Worker

The Worker is the backend service.

It will:

- Receive requests from the Android application.
- Authenticate and authorize requests.
- Validate filenames, file sizes, and other required fields.
- Issue appropriately restricted temporary upload URLs or handle uploads through another secure design.
- Restrict access to the intended bucket and objects.
- Provide additional endpoints only when needed.
- Return understandable HTTP status codes and error responses.

Do not expose a powerful R2 credential to the Android app.

## 3.3 Cloudflare R2

R2 stores the uploaded photos as objects.

The bucket should remain private.

Object keys must be generated and managed carefully to prevent unintended overwrites or collisions.

The design must consider how the application distinguishes photos with identical filenames.

## 3.4 Samsung access

Initially, I am willing to use the Cloudflare dashboard on the Samsung A15 to inspect and download objects.

A custom photo gallery is optional and should not delay the first working version.

If a gallery is eventually developed, it must access photos through an authorized mechanism rather than making the entire bucket public.

## 3.5 Network independence

The Fondi tablet and Samsung A15 do not need to share a local network.

The Fondi tablet uploads through an internet connection. The Samsung accesses the stored photos through its own internet connection.

The Fondi tablet currently works with a Samsung hotspot, while the home WE/TE Data router has caused connection timeouts.

Treat this as a separate network troubleshooting issue. Do not assume it is caused by the Android application or Cloudflare.

---

# PART 4 — DEVELOPMENT ENVIRONMENT

## 4.1 Current environment information

The project uses GitHub Codespaces.

Repository:

`new`

Expected repository path:

`/workspaces/new`

The latest confirmed diagnostic output reported:

- Java: OpenJDK 17.0.20.1.
- `JAVA_HOME`: `/usr/lib/jvm/java-17-openjdk-amd64`.
- Android SDK location: `/home/codespace/android-sdk`.
- Android platform: `android-35`.
- Installed Android Build Tools: `35.0.0`.
- System Gradle: `9.7.1`.
- Git branch: `main`.
- Git status at the time of the diagnostic: clean.

An attempt to create the Android project terminated with exit code 1.

The exact cause and the state of any partially created files have not been confirmed.

Do not assume the project setup succeeded.

## 4.2 Intended toolchain

The intended configuration is:

| Component | Intended version or setting | Status |
|---|---|---|
| JDK | 17 | `COMPLETED` — version confirmed |
| Android SDK platform | 35 | `COMPLETED` — directory confirmed |
| Android Build Tools 35.0.0 | Installed | `COMPLETED` — directory confirmed |
| Android Build Tools 34.0.0 | Required by the proposed AGP setup | `NOT STARTED` — installation not verified |
| Gradle Wrapper | 8.7 | `NOT STARTED` — existence not verified |
| Android Gradle Plugin | 8.6.1 | `NOT STARTED` |
| Android Lint | Plugin-provided tasks | `NOT STARTED` |
| Android project files | Minimal Java project | `NOT STARTED` — creation not verified |
| Successful debug build | `assembleDebug` | `NOT STARTED` |
| Successful Lint run | `lintDebug` | `NOT STARTED` |
| API 19 APK verification | Actual device | `NOT STARTED` |

These statuses refer to the last known evidence. Update them only after checking the current environment.

## 4.3 Compatibility requirements

Use JDK 17, Gradle 8.7, and Android Gradle Plugin 8.6.1 as the intended initial combination.

The system Gradle installation is not the version that should build the project. The project should use its checked-in Gradle Wrapper.

The Android app should initially use:

- `compileSdk 35`
- `minSdk 19`
- `targetSdk 28` as a provisional starting point, subject to a deliberate compatibility and security review.
- Java source and target compatibility 17 for compilation.

Important distinction: the Java source compatibility setting does not mean the Android 4.4 runtime supports every Java 17 API. Use Android-compatible APIs and appropriate compatibility libraries where necessary.

A successful compilation does not prove runtime compatibility.

Before making final decisions about `targetSdk`, examine the background execution, storage, permissions, and installation requirements of both Android 4.4 and modern Android devices.

## 4.4 Environment setup procedure

Before continuing:

1. Open a new Codespaces terminal.
2. Run `pwd`.
3. Confirm that `/workspaces/new` is the repository root.
4. Inspect existing files.
5. Run `git status`.
6. Verify Java and SDK paths.
7. Check whether the Gradle Wrapper exists.
8. Check whether the Android project files already exist.
9. Continue from the actual state instead of repeating the previous setup script.

Never overwrite the repository merely to reproduce an expected folder structure.

---

# PART 5 — IMPLEMENTATION ROADMAP

Implement the project in the following order.

The sequence is intentional: establish a reliable build before implementing application features, then establish secure cloud communication before implementing automatic uploads.

## PHASE 0 — ENVIRONMENT AND PROJECT INITIALIZATION

### 0.1 Inspect the existing Codespace

- [x] JDK 17 version verified.
- [x] Android SDK platform 35 directory verified.
- [x] Android Build Tools 35.0.0 directory verified.
- [x] Repository name confirmed as `new`.
- [ ] Existing repository files inspected after the interrupted setup.
- [ ] Current Git status checked again.
- [ ] Environment setup state reconciled.

### 0.2 Establish the build toolchain

- [ ] Install or verify Android Build Tools 34.0.0.
- [ ] Accept required Android SDK licences.
- [ ] Create and verify Gradle Wrapper 8.7.
- [ ] Configure Android Gradle Plugin 8.6.1.
- [ ] Configure the Android SDK paths.
- [ ] Verify the Gradle Wrapper uses JDK 17.
- [ ] Verify the Gradle and plugin versions.
- [ ] Confirm the dependency repositories resolve correctly.

### 0.3 Create the minimal Android project

- [ ] Create or repair `settings.gradle`.
- [ ] Create or repair root `build.gradle`.
- [ ] Create or repair `gradle.properties`.
- [ ] Create the Gradle Wrapper files.
- [ ] Create `app/build.gradle`.
- [ ] Create `AndroidManifest.xml`.
- [ ] Create a basic Java `MainActivity`.
- [ ] Configure namespace and application ID.
- [ ] Set `minSdk` to 19.
- [ ] Set the provisional target SDK.
- [ ] Confirm the project structure.

### 0.4 Verify the build

- [ ] Run `./gradlew --version`.
- [ ] Run `./gradlew clean`.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Run `./gradlew lintDebug`.
- [ ] Inspect any Lint errors and warnings.
- [ ] Confirm the APK exists.
- [ ] Inspect the APK metadata.
- [ ] Save the successful command output as evidence.
- [ ] Commit the verified baseline to Git.

**Phase 0 acceptance criteria:** The project builds consistently through the Gradle Wrapper, Lint runs, and a debug APK is generated.

Do not begin major feature implementation before this phase is stable.

---

## PHASE 1 — BASIC ANDROID APPLICATION

### 1.1 Main screen

Create a lightweight interface with:

- Application title.
- Folder selection control.
- Start button.
- Pause button.
- Stop button.
- Current status.
- Number of pending photos.
- Number of successful uploads.
- Number of failed uploads.
- Current upload progress.
- Last error information.

### 1.2 Application state

Define clear states, such as:

- Stopped.
- Scanning.
- Uploading.
- Paused.
- Waiting for network.
- Retrying.
- Error.

Avoid contradictory states and ensure that controls behave consistently.

### 1.3 Basic controls

Implement and test:

- Start begins the selected workflow.
- Pause prevents new uploads and safely handles any active upload.
- Stop stops scheduling new work and shuts down active work according to a defined policy.
- Restarting the application restores appropriate saved state.

**Phase 1 acceptance criteria:** The app opens on Android, displays its interface, and its controls work without cloud connectivity.

---

## PHASE 2 — PHOTO FOLDER SELECTION AND SCANNING

### 2.1 Folder selection

- [ ] Determine which folder-selection mechanism works on API 19.
- [ ] Allow the user to choose a folder.
- [ ] Save the selected folder reference or path appropriately.
- [ ] Handle permissions and unavailable folders.
- [ ] Explain how to reselect a folder if access is lost.

### 2.2 Photo discovery

- [ ] Scan the selected folder.
- [ ] Identify supported image files.
- [ ] Ignore directories and unsupported files.
- [ ] Avoid processing incomplete files that are still being written.
- [ ] Avoid rescanning excessively.
- [ ] Handle inaccessible files.
- [ ] Handle large folders without exhausting memory.

### 2.3 Scan scheduling

- [ ] Configure the scan interval.
- [ ] Avoid overlapping scans.
- [ ] Handle application pauses and restarts.
- [ ] Record useful scanning errors.
- [ ] Ensure repeated scans do not create duplicate queue entries.

**Phase 2 acceptance criteria:** The app can select a folder, find existing photos, and discover new photos without uploading anything yet.

---

## PHASE 3 — LOCAL DATABASE AND UPLOAD QUEUE

### 3.1 Local persistence

Choose an appropriate local storage mechanism after evaluating API 19 compatibility and project complexity.

SQLite is a suitable starting option.

Store only the information required to manage uploads.

Potential fields include:

- Local file path or stable file reference.
- Filename.
- File size.
- Modification timestamp.
- Optional content hash.
- Remote object key.
- Upload status.
- Attempt count.
- Last attempt time.
- Last error.
- Creation or discovery time.

### 3.2 Queue management

Implement queue states such as:

- Discovered.
- Pending.
- Uploading.
- Uploaded.
- Retry scheduled.
- Permanently failed.

Define valid state transitions.

### 3.3 Duplicate prevention

Prevent the same local photo from being added to the queue repeatedly.

Use a stable local identity and, where appropriate, a content hash.

Do not assume that filenames are unique.

The remote naming strategy must prevent unintended overwrites and handle two different photos with identical names.

### 3.4 Crash recovery

- [ ] Save queue changes persistently.
- [ ] Recover work after an application crash.
- [ ] Recover from interruption during an upload.
- [ ] Avoid treating an interrupted upload as successful.
- [ ] Verify remote upload success before recording completion.
- [ ] Ensure that retrying an operation does not create unintended duplicates.

**Phase 3 acceptance criteria:** Queue state survives restarts, repeated scans do not create duplicate jobs, and interrupted uploads can be recovered safely.

---

## PHASE 4 — CLOUDFLARE ACCOUNT AND STORAGE

### 4.1 Cloudflare setup

- [ ] Confirm the Cloudflare account is available.
- [ ] Create a private R2 bucket.
- [ ] Record the bucket name in private project configuration.
- [ ] Understand the bucket's storage and request limits.
- [ ] Review current pricing and free allowances.
- [ ] Avoid enabling public bucket access.

Do not store credentials in source files.

### 4.2 Worker setup

- [ ] Create the Worker.
- [ ] Configure the required R2 binding.
- [ ] Configure secret values through Cloudflare's supported secret mechanism.
- [ ] Verify that the Worker can access the intended bucket.
- [ ] Keep development and production settings distinguishable.
- [ ] Record the deployed Worker URL without recording secret values.

### 4.3 Secure authorization design

Determine how the Android app authenticates to the Worker.

The design must distinguish:

- Authentication: determining who is making a request.
- Authorization: determining what that requester is allowed to do.
- Temporary upload authorization: allowing a restricted operation for a limited period.

Choose a simple, defensible authentication design suitable for a single-device project.

Do not mistake an unguessable URL for complete user authentication.

### 4.4 API design

Document the request and response format before implementing the Android client.

Possible endpoints include:

- Health or connectivity check.
- Request upload authorization.
- Request a photo list, if needed.
- Request authorized photo access, if needed.

The exact endpoints are not yet implemented and must not be treated as existing.

For every endpoint, document:

- HTTP method.
- URL path.
- Required headers.
- Request body.
- Successful response.
- Error responses.
- Authentication requirements.
- Authorization rules.
- Expected status codes.

### 4.5 Secure upload flow

A possible flow is:

1. The app identifies a pending photo.
2. It requests upload authorization from the Worker.
3. The Worker authenticates the request and validates the proposed object key.
4. The Worker returns an appropriately restricted temporary upload URL.
5. The app uploads the photo bytes using HTTPS.
6. The app verifies the result.
7. The app records the upload as successful.

Choose the exact implementation based on the available Cloudflare APIs and security requirements.

The app must not contain permanent R2 credentials.

### 4.6 Cloud tests

- [ ] Test the Worker independently.
- [ ] Test authorized requests.
- [ ] Test rejected or malformed requests.
- [ ] Test a small image upload.
- [ ] Verify the resulting object in R2.
- [ ] Test expired or invalid authorization.
- [ ] Confirm that the bucket remains private.
- [ ] Confirm that permanent credentials are not exposed to the client.

**Phase 4 acceptance criteria:** A small test image can be uploaded securely to the private bucket, and unauthorized access is rejected.

---

## PHASE 5 — ANDROID NETWORKING AND UPLOAD MANAGER

### 5.1 HTTP client

Implement an Android-compatible HTTPS client.

It must support:

- Request timeouts.
- Response handling.
- Error reporting.
- Appropriate resource cleanup.
- Correct handling of file streams.
- TLS certificate verification.
- Safe handling of interrupted connections.

Do not disable certificate validation to solve connection problems.

### 5.2 Upload manager

- [ ] Request upload authorization.
- [ ] Upload a single photo.
- [ ] Handle success and failure.
- [ ] Update the local database.
- [ ] Prevent concurrent processing of the same queue item.
- [ ] Limit simultaneous uploads.
- [ ] Avoid loading entire images into memory unnecessarily.
- [ ] Handle HTTP status codes correctly.

### 5.3 Retry policy

Implement bounded or otherwise controlled retry behavior.

Consider:

- Temporary network failures.
- Timeouts.
- Rate limits.
- Server errors.
- Expired upload URLs.
- Authentication failures.
- Invalid files.
- Permanent client errors.

Use backoff for repeated transient failures.

Do not retry permanent authorization failures indefinitely.

### 5.4 Upload integrity

Where supported, use appropriate checks such as:

- HTTP response verification.
- Expected object identity.
- File size verification.
- Checksums when practical.

Do not mark an upload complete merely because the request started or the connection remained open.

**Phase 5 acceptance criteria:** A real photo can be uploaded from the Android app, its success is recorded, and failures are handled without corrupting queue state.

---

## PHASE 6 — AUTOMATIC OPERATION AND BACKGROUND RELIABILITY

### 6.1 Background execution

Research and implement an approach compatible with Android 4.4.

Evaluate an appropriate combination of:

- A foreground or background service where suitable.
- A persistent notification when required by the chosen design.
- Scheduled scanning.
- Wake locks only when genuinely necessary.
- Network availability checks.
- Persistent queue recovery.

Do not assume that modern Android scheduling APIs are available on API 19.

### 6.2 Locked-screen operation

- [ ] Test scanning with the screen on.
- [ ] Test scanning with the screen locked.
- [ ] Test uploads with the screen locked.
- [ ] Test a temporarily unavailable network.
- [ ] Test recovery when the network returns.
- [ ] Test application process termination.
- [ ] Test device reboot if automatic restart is required.

Android 4.4 and device firmware may impose restrictions. Do not promise perfect reliability without testing the actual tablet.

### 6.3 Resource management

- [ ] Avoid unnecessary polling.
- [ ] Avoid repeated full-folder scans when incremental scanning is feasible.
- [ ] Limit concurrent network operations.
- [ ] Avoid memory-heavy image processing.
- [ ] Handle low storage.
- [ ] Handle database errors.
- [ ] Provide a way to inspect failures.

### 6.4 User settings

Implement settings only after the core workflow works.

Potential settings:

- Scan interval.
- Maximum concurrent uploads.
- Retry behavior.
- Wi-Fi-only uploads.
- Background operation.
- Storage warnings.
- Notifications.

Each setting must have a clear default and predictable behavior.

**Phase 6 acceptance criteria:** The application continues its upload workflow under tested background and locked-screen conditions and recovers from interruptions.

---

## PHASE 7 — SAMSUNG ACCESS AND OPTIONAL GALLERY

### 7.1 Initial access

- [ ] Confirm uploaded objects appear in R2.
- [ ] Access the objects through the Cloudflare dashboard on the Samsung A15.
- [ ] Verify that a photo can be downloaded.
- [ ] Confirm that the Samsung can access photos over a different internet connection.

Do not assume that an R2 bucket is publicly accessible.

### 7.2 Optional gallery

Do not implement this until the essential photo uploader is stable.

If added later:

- [ ] Design a simple gallery interface.
- [ ] Add an authorized photo-list endpoint.
- [ ] Add authorized photo retrieval.
- [ ] Display thumbnails efficiently.
- [ ] Handle large collections.
- [ ] Avoid exposing permanent cloud credentials.
- [ ] Keep the bucket private.
- [ ] Restrict access appropriately.

A gallery is optional and must not delay the first functioning uploader.

---

## PHASE 8 — SECURITY, COST, AND MAINTENANCE

### 8.1 Security

- [ ] Keep permanent credentials outside the APK.
- [ ] Keep secrets out of Git.
- [ ] Keep the R2 bucket private.
- [ ] Validate incoming Worker requests.
- [ ] Restrict upload permissions.
- [ ] Prevent unintended object overwrites.
- [ ] Handle expired authorization.
- [ ] Protect sensitive diagnostic logs.
- [ ] Review authentication and authorization before deployment.

### 8.2 Storage and costs

- [ ] Estimate storage from actual upload sizes.
- [ ] Estimate retained storage over time.
- [ ] Estimate request and bandwidth usage.
- [ ] Review current service pricing.
- [ ] Check applicable quotas and restrictions.
- [ ] Plan how to detect unexpectedly high usage.
- [ ] Avoid promising unlimited free operation.

Do not delete local originals automatically.

### 8.3 Maintenance and recovery

- [ ] Document how to rebuild the APK.
- [ ] Document how to deploy the Worker.
- [ ] Document how to configure the R2 binding.
- [ ] Document how to update secret values safely.
- [ ] Document how to diagnose common failures.
- [ ] Document how to recover an interrupted upload queue.
- [ ] Keep source changes in Git.
- [ ] Verify that a clean checkout can reproduce the build.

---

# PART 6 — TESTING CHECKLIST

Do not declare the application complete until the relevant tests have been executed.

## 6.1 Build tests

- [ ] Gradle Wrapper works.
- [ ] Debug APK builds.
- [ ] Lint runs.
- [ ] No unresolved build errors remain.
- [ ] APK metadata confirms the intended minimum SDK.
- [ ] A clean checkout can build the application.

## 6.2 Folder tests

- [ ] Folder selection works.
- [ ] Empty folder is handled.
- [ ] Existing photos are discovered.
- [ ] New photos are discovered.
- [ ] Incomplete files are not uploaded prematurely.
- [ ] Repeated scans do not duplicate queue entries.
- [ ] Unavailable folders are handled.

## 6.3 Upload tests

- [ ] One photo uploads successfully.
- [ ] Several photos upload successfully.
- [ ] Failed requests are retried appropriately.
- [ ] Duplicate uploads are prevented.
- [ ] Identical filenames do not cause unintended overwrites.
- [ ] Interrupted uploads recover correctly.
- [ ] Local originals remain intact.
- [ ] Invalid authorization is rejected.

## 6.4 Reliability tests

- [ ] Application restart.
- [ ] Device restart.
- [ ] Screen locked.
- [ ] Network interruption.
- [ ] Network recovery.
- [ ] Server failure.
- [ ] Low local storage.
- [ ] Large queue.
- [ ] Repeated scanning.
- [ ] Long-running operation.

## 6.5 Real-device tests

Test on the actual Fondi Android 4.4 tablet.

Do not mark compatibility as complete based solely on an emulator or APK build.

---

# PART 7 — DEBUGGING PROCEDURE

Whenever an error occurs, follow this process.

1. Capture the complete error message.
2. Identify the command or action that produced it.
3. Determine whether the failure is in the IDE, build system, Android app, network, Worker, or R2.
4. Reproduce the issue with the smallest useful test.
5. Check relevant logs and configuration.
6. Change one likely cause at a time.
7. Repeat the test.
8. Record the result and the actual cause if known.
9. Update the progress tracker only when the relevant acceptance criteria are satisfied.

Do not change several unrelated files in an attempt to fix an unclear error.

## Common error categories

### Build errors

Inspect:

- Java version.
- Gradle Wrapper version.
- Android Gradle Plugin version.
- SDK installation.
- Dependency repositories.
- Build configuration.
- Compilation errors.

### Android runtime errors

Inspect:

- Logcat.
- Manifest declarations.
- Permissions.
- API availability.
- File access.
- Background execution behavior.
- Exceptions and stack traces.

### Network errors

Inspect:

- Internet connectivity.
- DNS resolution.
- HTTPS certificate validation.
- Connection and read timeouts.
- HTTP response codes.
- Worker logs.
- R2 configuration.

### Upload errors

Inspect:

- Authorization expiry.
- Upload URL validity.
- HTTP method and headers.
- Object key.
- Request size.
- Server response.
- Database status transitions.
- Retry state.

Do not disable HTTPS verification or expose credentials to make a test pass.

---

# PART 8 — FINAL ACCEPTANCE CRITERIA

The project is not complete until all essential criteria have been demonstrated.

## Essential completion requirements

1. The project builds reproducibly in Codespaces.
2. The APK installs and runs on the Fondi Android 4.4 tablet.
3. The user can select a photo folder.
4. The application discovers new photos.
5. The application uploads them securely to R2.
6. The original photos remain on the tablet.
7. Successful uploads are recorded persistently.
8. Duplicate processing is prevented.
9. Failed uploads are retried appropriately.
10. The queue survives application interruption.
11. Start, Pause, and Stop work as designed.
12. Locked-screen operation has been tested.
13. The Samsung A15 can access uploaded photos over the internet.
14. Permanent cloud credentials are not embedded in the APK.
15. The bucket remains private.
16. The project has useful diagnostic logs and documentation.
17. Storage and operating costs have been evaluated.
18. The final build and relevant Lint checks pass.

A custom gallery is not required for the initial release.

---

# PART 9 — CURRENT PROGRESS SUMMARY

**Current overall phase:** Phase 0 — Environment and project initialization.

**Last confirmed facts:**

- JDK 17 is installed.
- Android SDK platform 35 is present.
- Android Build Tools 35.0.0 are present.
- System Gradle 9.7.1 is installed.
- The repository is `/workspaces/new`.
- A setup attempt terminated with exit code 1.
- The exact state of the partially created files has not yet been confirmed.

**No Android feature has been verified as completed.**

Do not claim that the project builds or that an APK exists until the evidence confirms it.

## Immediate next task

Inspect the current Codespace without overwriting files.

Run:

```bash
cd /workspaces/new

echo "=== CURRENT DIRECTORY ==="
pwd

echo "=== GIT STATUS ==="
git status --short --branch

echo "=== PROJECT FILES ==="
find . -maxdepth 4 -type f \
  -not -path './.git/*' | sort

echo "=== GRADLE WRAPPER ==="
ls -l gradlew gradle/wrapper/gradle-wrapper.properties 2>/dev/null || true

echo "=== JAVA ==="
java -version

echo "=== SDK ==="
echo "$ANDROID_HOME"
ls "$ANDROID_HOME/platforms" 2>/dev/null || true
ls "$ANDROID_HOME/build-tools" 2>/dev/null || true
```

Use the results to determine the exact next action.

Do not regenerate files that already exist without inspecting them.

---

# PART 10 — HOW TO UPDATE THIS MEMORY

Whenever a component is finished, update its status in this document.

For every completed milestone, record:

- Component name.
- Status.
- Files created or changed.
- Commands executed.
- Test performed.
- Actual result.
- Known limitations.
- Next component to implement.

Example:

**Component:** Basic Android build  
**Status:** COMPLETED  
**Files:** `app/build.gradle`, `app/src/main/AndroidManifest.xml`  
**Test:** `./gradlew assembleDebug`  
**Result:** `BUILD SUCCESSFUL`; APK generated at the expected path.  
**Limitations:** Not yet tested on the Fondi tablet.  
**Next step:** Run Lint and verify APK metadata.

Only use this example format when the stated result is actually true.

At the beginning of every new DeepSeek session:

1. Read this memory.
2. Review the progress tracker.
3. Confirm the current project state.
4. Identify the next incomplete component.
5. Work on that component only.
6. Verify it before updating its status.

At the end of every session:

1. Summarize the changes.
2. List tests and their actual results.
3. Identify unresolved errors.
4. Update the progress tracker.
5. Provide the complete revised memory document or the exact sections that changed.

**The goal is to build the application step by step, preserve progress across sessions, and never confuse planned functionality with working functionality.**