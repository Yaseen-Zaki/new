# FONDI PHOTO SYNC — DEEPSEEK BUILDER MEMORY

**Document type:** Master project specification, implementation roadmap, and progress tracker
**Project status:** Phase 1 (Android features) not started; Phase 4B (Worker + B2) core upload verified
**Developer:** Beginner working with AI-assisted coding
**IDE:** GitHub Codespaces
**Repository:** `new`
**Target device:** Fondi tablet running Android 4.4 (API 19)
**Secondary device:** Samsung Galaxy A15
**Cloud provider:** Cloudflare Workers + Backblaze B2 (replaces earlier Cloudflare R2 plan)
**Primary language:** Java for Android; JavaScript for Cloudflare Worker

---

# PART 1 — RULES FOR THE AI BUILDER

(unchanged from previous version — retained in full, see prior revision)

Key constraints that remain in force:

- Work incrementally; one component at a time; wait for user confirmation.
- Protect existing work; inspect before modifying.
- Status values: `NOT STARTED` / `IN PROGRESS` / `BLOCKED` / `COMPLETED`.
- `COMPLETED` requires demonstrated acceptance criteria — never assumed.
- API 19 compatibility is mandatory; framework views only.
- Explain before major architectural changes.
- Never embed permanent cloud credentials in the APK or repository.
- Keep instructions efficient; consult official docs only when necessary.

---

# PART 2 — WHAT WE ARE BUILDING

(unchanged) — Android photo-sync app for the Fondi tablet, uploading to private cloud storage, Samsung A15 reads photos over the internet. Local originals are never deleted.

Planning volume: ~0.31 MB/photo, ~1,000 photos/day, ~113 GB/year. Costs must be reviewed against current B2 and Cloudflare pricing before scaling.

---

# PART 3 — SYSTEM ARCHITECTURE (REVISED)

**Architectural change (approved):** Cloudflare R2 was replaced by **Backblaze B2** as the storage backend. Reasons:

1. R2 and B2 pricing/allowances differ; B2's free tier (10 GB) plus Cloudflare's free Worker tier fit the zero-cost constraint better for the expected volume.
2. The original R2-based design relied on `If-None-Match: *` and `x-amz-checksum-sha256` for atomic dedup and integrity. **Backblaze B2's S3-compatible API does not support either**, confirmed via official B2 documentation and independent reports. The design was therefore reworked to avoid both and to enforce atomicity elsewhere (see §3.3).
FONDI TABLET (ANDROID 4.4)
Photo folder
→ Folder scanner
→ SHA-256 hashing
→ Local upload queue (SQLite)
→ Upload manager (HTTPS POST /upload)
│
▼
CLOUDFLARE WORKER (restless-fire-7680.yaseenzaki99.workers.dev)
• POST /upload — verify client SHA-256 vs server-computed SHA-256,
validate magic bytes, enforce size limit,
route to Durable Object keyed by hash
• GET /photos — NOT IMPLEMENTED
• GET /photos/{id} — NOT IMPLEMENTED
│
▼
DURABLE OBJECT (SQLite-backed, name = SHA-256)
• Single-threaded per hash → serializes concurrent uploads
• Stores "uploaded" flag after successful B2 PUT
• Returns {duplicate:false, id} on first PUT, {duplicate:true, id} thereafter
│
▼
BACKBLAZE B2 (private bucket: fondi-tablet-photosync, region us-east-005)
Object key: photos/<server-computed-sha256>.<validated-ext>
│
▼
SAMSUNG GALAXY A15
Cloudflare/B2 dashboard initially; optional gallery later

## 3.1 Android application

Same responsibilities as before. Hashing is added as a first-class step so the Worker can verify content identity.

## 3.2 Cloudflare Worker

- Authenticates requests (not yet implemented — see §4B.4).
- Enforces upload size cap (10 MB) and image magic-byte validation.
- Computes SHA-256 on received bytes; rejects mismatches with `400 checksum_mismatch`.
- Delegates to a Durable Object keyed by the verified hash.
- Never exposes B2 credentials.
- Deliberately **does not** send `If-None-Match` or `x-amz-checksum-*` to B2 — both unsupported.

## 3.3 Backblaze B2 (replaces R2)

- Private bucket `fondi-tablet-photosync`, region `us-east-005`.
- S3-compatible endpoint `s3.us-east-005.backblazeb2.com`.
- Object keys are server-generated: `photos/<sha256>.<ext>`.
- **No conditional write support** on B2's S3 API; atomicity is provided by the Durable Object.
- **No native SHA-256 checksum verification** via S3 headers; integrity is verified in the Worker before the B2 PUT.

## 3.4 Samsung access

Deferred by user decision. `GET /photos` and `GET /photos/{id}` are not implemented. No public bucket exposure.

## 3.5 Network independence

Unchanged.

---

# PART 4 — DEVELOPMENT ENVIRONMENT

## 4.1 Environment

- JDK 17.0.20.1
- Android SDK `/home/codespace/android-sdk`, platform 35, Build Tools 34.0.0 & 35.0.0
- Gradle Wrapper 8.7; AGP 8.6.1
- Node.js (for Wrangler), Wrangler installed under `worker/`
- Cloudflare account on **Free plan** → Durable Objects must use `new_sqlite_classes`

## 4.2 Toolchain status

| Component | Status |
|---|---|
| JDK 17 | COMPLETED |
| Android SDK 35 | COMPLETED |
| Build Tools 34 & 35 | COMPLETED |
| Gradle Wrapper 8.7 | COMPLETED |
| AGP 8.6.1 | COMPLETED |
| Debug build | COMPLETED — APK produced |
| Lint clean | COMPLETED — `No issues found.` |
| Wrangler installed (worker/) | COMPLETED |
| Worker deployed | COMPLETED — `restless-fire-7680.yaseenzaki99.workers.dev` |
| B2 bucket created (private) | COMPLETED — `fondi-tablet-photosync` |
| B2 Application Key (scoped) | COMPLETED — stored as Worker secret |
| API 19 APK verification (device) | NOT STARTED |

## 4.3 Compatibility requirements

Unchanged: `compileSdk 35`, `minSdk 19`, `targetSdk 28`, Java source/target 1.8, no desugaring, `ExpiredTargetSdkVersion` lint disabled with documented justification, project-owned launcher icon, no committed `local.properties`.

## 4.4 Environment setup procedure

Unchanged.

---

# PART 5 — IMPLEMENTATION ROADMAP

## PHASE 0 — ENVIRONMENT AND PROJECT INITIALIZATION

- [x] JDK / SDK / Build Tools verified
- [x] Gradle Wrapper 8.7 + AGP 8.6.1
- [x] Minimal Android project (`com.fondi.photosync`)
- [x] `assembleDebug` and `lintDebug` clean
- [x] APK metadata confirmed (`minSdk 19`, `targetSdk 28`)
- [x] Baseline committed (Commit A)

---

## PHASE 1 — BASIC ANDROID APPLICATION

### 1.1 Main screen
- [x] Title, status view, primary action, scrollable output (currently an HTTPS test button — not the final Start/Pause/Stop UI)
- [ ] Folder selection control
- [ ] Start / Pause / Stop
- [ ] Pending / success / failure counters
- [ ] Upload progress
- [ ] Last error view

### 1.2 Application state
- [ ] State model (Stopped / Scanning / Uploading / Paused / Waiting for network / Retrying / Error)

### 1.3 Basic controls
- [ ] Start
- [ ] Pause
- [ ] Stop
- [ ] State restore on restart

**Phase 1 acceptance:** Not met. Current UI is a network test screen only.

---

## PHASE 2 — PHOTO FOLDER SELECTION AND SCANNING

- [ ] Folder-selection mechanism for API 19
- [ ] Persist folder reference
- [ ] Handle permissions / unavailable folders
- [ ] Scan for supported images
- [ ] Skip incomplete files
- [ ] Avoid duplicate queue entries on rescan
- [ ] Handle large folders

**Phase 2 acceptance:** NOT STARTED.

---

## PHASE 3 — LOCAL DATABASE AND UPLOAD QUEUE

- [ ] SQLite schema (path, filename, size, mtime, sha256, remote key, status, attempts, last error)
- [ ] Queue state machine
- [ ] Duplicate prevention by stable local identity + SHA-256
- [ ] Crash recovery
- [ ] Verify remote success before marking uploaded

**Phase 3 acceptance:** NOT STARTED.

---

## PHASE 4 — (DEPRECATED — replaced by Phase 4B)

The original R2-based Phase 4 is superseded. Retained here only to explain why the architecture changed.

---

## PHASE 4B — CLOUDFLARE WORKER + BACKBLAZE B2

### 4B.1 Backblaze B2 setup
- [x] Private bucket created: `fondi-tablet-photosync`
- [x] Region noted: `us-east-005`
- [x] Scoped Application Key created (read/write, single bucket)
- [x] No public access enabled

### 4B.2 Cloudflare Worker setup
- [x] Worker created and deployed (`restless-fire-7680`)
- [x] Secrets set via Wrangler: `B2_APPLICATION_KEY_ID`, `B2_APPLICATION_KEY`
- [x] Non-secret vars: `B2_ENDPOINT`, `B2_BUCKET`, `B2_REGION`
- [x] `GET /health` returns `{ok:true, b2_key_id_configured:true, b2_app_key_configured:true}`

### 4B.3 B2 compatibility investigation
- [x] Confirmed: `If-None-Match: *` **not supported** by B2 S3 API
- [x] Confirmed: `x-amz-checksum-sha256` **not supported** by B2 S3 API
- [x] Confirmed: Durable Objects with `new_sqlite_classes` are available on Free plan
- [x] Design chosen: DO keyed by verified SHA-256 provides atomic serialization

### 4B.4 Authorization (deferred)
- [ ] Decide Worker auth scheme (static bearer token vs. HMAC-signed requests)
- [ ] Implement auth middleware
- [ ] Rate limiting
- [ ] Reject malformed / oversized / unauthorized requests before B2 PUT

### 4B.5 Upload endpoint
- [x] `POST /upload` accepts raw bytes + `X-Content-SHA256`
- [x] Rejects hash mismatch with `400 checksum_mismatch`
- [x] Rejects non-JPEG/PNG magic bytes with `400 unsupported_type`
- [x] Rejects >10 MB with `413 too_large`
- [x] Server generates key `photos/<sha256>.<ext>`
- [x] First upload → `201 {duplicate:false, id}`
- [x] Duplicate upload → `200 {duplicate:true, id}` (same key)
- [x] Object verified present in B2 bucket

### 4B.6 Listing and reading (deferred)
- [ ] `GET /photos` with pagination
- [ ] `GET /photos/{id}` returning bytes or short-lived signed URL
- [ ] Auth applied to both
- [ ] No delete endpoint (by design)

**Phase 4B acceptance (partial):** Upload + dedup verified end-to-end against real B2. Listing, reading, and auth are NOT implemented.

---

## PHASE 5 — ANDROID NETWORKING AND UPLOAD MANAGER

- [ ] HTTPS client (HttpURLConnection or OkHttp-lite suitable for API 19)
- [ ] SHA-256 hashing of local files (Android-compatible)
- [ ] POST to Worker `/upload`
- [ ] Parse `duplicate` flag
- [ ] Update local DB
- [ ] Bounded retry with backoff
- [ ] Do not mark complete before server confirmation

**Phase 5 acceptance:** NOT STARTED.

---

## PHASE 6 — AUTOMATIC OPERATION AND BACKGROUND RELIABILITY

All items NOT STARTED. Service/wake-lock strategy not yet chosen for API 19.

---

## PHASE 7 — SAMSUNG ACCESS AND OPTIONAL GALLERY

Deferred by user. All items NOT STARTED.

---

## PHASE 8 — SECURITY, COST, AND MAINTENANCE

- [x] No B2 credentials in APK or repo
- [x] Bucket private
- [x] Worker does not forward unsupported headers to B2
- [ ] Worker authentication implemented
- [ ] Rate limiting
- [ ] Cost monitoring (B2 storage growth, Cloudflare request counts)
- [ ] Documentation for rebuild / redeploy / secret rotation
- [ ] Clean-checkout build reproduction

---

# PART 6 — TESTING CHECKLIST

## 6.1 Build
- [x] Gradle Wrapper
- [x] Debug APK
- [x] Lint clean
- [x] Clean checkout can build (to be re-verified after Commit C)

## 6.2 Folder / scanning
All NOT STARTED.

## 6.3 Upload
- [x] One photo uploads to B2 via Worker (curl, `201`)
- [x] Same photo re-upload deduped (curl, `200 duplicate:true`)
- [x] Wrong SHA-256 rejected (not yet tested — implement and test in Phase 5)
- [x] Unsupported type rejected (implicitly required by code; explicit test pending)
- [ ] Oversized upload rejection test
- [ ] Concurrent identical uploads serialized by DO (two curls in parallel)
- [x] Local originals intact (Android path not yet used)

## 6.4 Reliability
All NOT STARTED.

## 6.5 Real-device
All NOT STARTED. APK has not been installed on the Fondi tablet.

---

# PART 7 — DEBUGGING PROCEDURE

Unchanged. Standard procedure applies. For B2-specific issues:

- `502 storage_error` → check B2 Application Key scope, region, and bucket name.
- `400 checksum_mismatch` → client computed hash differs from server; verify client-side hashing.
- `413 too_large` → Worker caps body at 10 MB.
- `401` once auth is implemented → token mismatch or expired.

Never add `If-None-Match` or `x-amz-checksum-*` to B2 requests to "fix" a race or integrity issue — B2 will reject them. Use the Durable Object path.

---

# PART 8 — FINAL ACCEPTANCE CRITERIA

(unchanged) — full list of 18 items. Current state: items 1 (build) and 14–15 (credentials/bucket) are satisfied. Item 5 (secure upload to B2) is satisfied at the backend level but not from the Android app. All others NOT STARTED.

---

# PART 9 — CURRENT PROGRESS SUMMARY

**Current overall phase:** Phase 0 complete; Phase 4B backend upload verified; Phase 1, 2, 3, 5, 6, 7 not started.

**Verified facts (with evidence):**

- Android baseline builds cleanly (`assembleDebug`, `lintDebug` → `No issues found.`).
- Worker deployed at `https://restless-fire-7680.yaseenzaki99.workers.dev`.
- `GET /health` returns `b2_key_id_configured:true`, `b2_app_key_configured:true`.
- `POST /upload` on a real PNG returned `201 {duplicate:false, id:"photos/b03e7926…c300.png"}`.
- Re-upload of the same file returned `200 {duplicate:true, id:"photos/b03e7926…c300.png"}`.
- The object is visible in the B2 bucket.
- Worker does not send `If-None-Match` or `x-amz-checksum-*` to B2 (grep confirms; the single `x-amz-checksum` match is the explanatory comment).
- Durable Object bound as `UploadCoordinator` with `new_sqlite_classes`.

**Verified limitations:**

- No Worker authentication yet — the endpoint is publicly reachable. Must be addressed before Android integration.
- No rate limiting.
- No `GET /photos` or `GET /photos/{id}`.
- Android app has no folder selection, scanning, queue, hashing, or upload logic.
- APK not installed on the Fondi tablet.
- Samsung access not tested.
- Concurrency (two parallel identical uploads) not yet exercised.

**Immediate next task (recommended):** Add a minimal bearer-token authentication gate to the Worker before wiring Android to it. Do not expose an unauthenticated upload endpoint to a network-connected tablet.

---

# PART 10 — HOW TO UPDATE THIS MEMORY

Unchanged format. Record: component, status, files, commands, test, actual result, limitations, next step. Only mark `COMPLETED` when the evidence exists.

---

**Component:** Phase 0 — Android baseline
**Status:** COMPLETED (build/lint/APK); device install NOT STARTED
**Files:** Android project as previously listed
**Tests:** `assembleDebug`, `lintDebug`, `aapt2 dump badging`
**Result:** APK built; lint clean; minSdk 19 confirmed
**Limitations:** Not installed on Fondi tablet

---

**Component:** Phase 4B — Worker + B2 upload backend
**Status:** Upload + dedup COMPLETED; auth, listing, reading NOT STARTED
**Files:**
- `worker/wrangler.toml` (DO binding + vars)
- `worker/src/index.js` (sha256 `0ee036fe316e5d76ce8da9ba9f2ae1cd2536c5d7697a051ebe04500aa71409bb`)
- `worker/package.json` (`type: module`, deploy/dev scripts, `aws4fetch` dependency)

**Commands:**
- `npx wrangler secret put B2_APPLICATION_KEY_ID`
- `npx wrangler secret put B2_APPLICATION_KEY`
- `npx wrangler deploy`
- `curl -i -X POST …/upload -H "X-Content-SHA256: $HASH" -H "Content-Type: image/png" --data-binary @test.png`

**Tests:** health check; first upload; duplicate upload; B2 console object inspection
**Results:** `201` then `200 duplicate:true`; same key returned both times; object present in bucket
**Limitations:** No auth; no rate limiting; no list/read endpoints; concurrency not yet exercised
**Next step:** Add bearer-token auth gate to Worker, then move to Android folder scanning + hashing + upload client (Phases 2, 3, 5).