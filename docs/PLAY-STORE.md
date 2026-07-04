# OpenFiles — Play Store Launch Roadmap

The project's main goal is publishing on Google Play. This file tracks what stands between the current build (v0.1.1) and a live listing, in priority order.

## 1. Policy blockers — must be resolved or the app is rejected

### 1.1 `MANAGE_EXTERNAL_STORAGE` (All files access) — the big one

Play restricts this permission. It is only granted to apps whose **core purpose** requires broad file access — and *file managers are an explicitly allowed use case*, so OpenFiles qualifies, but approval is manual and rejections for weak declarations are common.

What to do at submission time:
- Play Console → **App content → Sensitive app permissions → All files access**: declare the file-manager core use case.
- Provide a short **demo video** (screen recording) showing why broad access is needed: browsing arbitrary folders, move/copy across the tree, recycle bin at volume roots. Record it on the reference device.
- Description and screenshots must present the app *as a file manager* (they do) — the declared use must match the listing.
- If rejected: appeal with a tighter justification video. Only as a last resort consider a SAF-only variant; **recommendation: don't** — the entire data layer (`java.io.File`, `.openfiles_trash`, raw-path MediaStore `DATA`) assumes All files access, and a SAF port is a rewrite.

### 1.2 `REQUEST_INSTALL_PACKAGES`

Also requires a Play Console declaration. Installing APKs from the file list is a standard file-manager feature, so it's defensible — but it's a second manual review surface. Decision to make before first submission:
- **Keep it** and declare it (recommended if APK-install is a feature you want to advertise), or
- **Drop it** to reduce review risk: `Open.file` fires `ACTION_VIEW` through FileProvider; without the permission the system may block the install prompt on API 26+, so test what the UX degrades to before choosing this path.

### 1.3 Impersonation / intellectual property

The app deliberately recreates One UI "My Files" styling. To stay on the right side of Play's impersonation policy and trademark law:
- Never use the words "Samsung", "One UI", or "My Files" in the **app name, icon, or store graphics**. Describing the style in the full description is a gray zone — prefer wording like "clean, familiar stock-style design".
- Keep the non-affiliation disclaimer (already in README) in the store listing's full description.
- Icon and screenshots must be original assets (they are — all drawables are hand-written vectors).
- The device screenshots will *look like* One UI because the app looks like One UI — that's acceptable; branding text is not.

### 1.4 Privacy policy + Data safety form

Required for every app, even with zero collection:
- Host a one-page privacy policy (GitHub Pages on the repo is free and appropriate): "OpenFiles has no network access and collects, stores, and transmits no personal data. All file operations happen locally on your device."
- Data safety form: **No data collected, no data shared**. This is verifiable — the app has no INTERNET permission, which is a strong trust signal; mention it in the listing.

## 2. Technical release checklist

- [ ] **Build an AAB** — Play only accepts App Bundles: `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` (signing config already applies).
- [ ] **Play App Signing** — mandatory for new apps. The current `release.jks` becomes the *upload key*; Google holds the app signing key. Existing sideload users can't cross-update to the Play build if signatures differ — decide whether GitHub releases keep the old signature (fine; two channels) and note it in the README.
- [ ] **targetSdk 35** — meets the 2026 target-API requirement. Keep raising it each year (policy deadline is typically August for new target levels).
- [ ] **Version discipline** — bump `versionCode`/`versionName` per upload; Play rejects reused codes.
- [ ] **Reconcile the theme claim** — README/feature list says "Light & dark theme" but `Theme.OpenFiles` is dark-only. Either ship a light theme or fix the claim before the listing repeats it (store listings must be accurate).
- [ ] **Test the minSdk floor** — minSdk 21: run at least once on an API 21–23 emulator (legacy permission path) and API 30+ (All-files-access path).
- [ ] **Pre-launch report** — Google's crawler can't grant All files access, so it will sit on `PermissionFragment`. Provide reviewer notes in Play Console ("app requires All files access; grant via Settings → …") and expect shallow crawl results.
- [ ] **`android:allowBackup="true"`** — currently backs up only SharedPreferences (harmless). Leave as-is or set false; just a conscious choice.
- [ ] **Predictive back** — `onBackPressed()` is deprecated; not a launch blocker at targetSdk 35, but plan the `OnBackPressedDispatcher` migration (see PITFALLS.md).

## 3. Store assets to prepare

- App icon 512×512 PNG (adaptive icon already exists in-app).
- Feature graphic 1024×500.
- ≥4 phone screenshots (Home, Browser list + grid, Category, Recycle bin, Manage storage, palette picker — pick the best 6–8; take on the reference device, but crop/clean any Samsung UI outside the app).
- Short description (80 chars), e.g.: "Fast, private, open-source file manager. No ads, no network, under 2 MB."
- Full description: features list from README + privacy stance (no INTERNET permission) + GPL-3.0 + non-affiliation disclaimer.

## 4. Rollout strategy

1. **Internal testing track** first — the All-files-access declaration is reviewed on first submission; internal track surfaces problems fastest.
2. **Closed testing**: note that since late 2023, *new personal developer accounts* must run a closed test with ≥12 testers for 14 days before production access — plan for this if the account is personal.
3. **Production** with staged rollout (20% → 100%).
4. **F-Droid in parallel** (recommended) — fits the FOSS philosophy, no permission gatekeeping, reaches the audience that most values "no network, GPL". Needs reproducible metadata (fastlane structure) but no code changes.

## 5. Suggestions (prioritized, post-blocker)

1. **Release CI job** — on git tag: build signed AAB + APK (keystore as base64 GitHub secret), attach APK to a GitHub Release. Removes the manual build step and keeps the sideload channel alive alongside Play.
2. **Crash visibility without analytics** — philosophy forbids telemetry; a `Thread.setDefaultUncaughtExceptionHandler` that writes the stack trace to a local file plus a "share crash log" row in Settings preserves privacy and still gets you bug reports.
3. **Unit tests for the pure logic** — there are currently zero tests. `Sorting`, `Format`, `FileKind`, `FileRepository.uniqueDestination`, and the Trash index round-trip are all plain-JVM testable; a small suite protects refactors before release pressure grows.
4. **Localization** — strings are fully centralized and English-only; each added language is just a `values-xx/strings.xml`. Good first-contributor bait once the repo is public on Play.
5. **Light theme** — resolves the README inconsistency and widens appeal; the imperative color system (`ThemeManager`) is the natural extension point.
6. **Atomic trash index writes** (temp file + rename) before the user base grows — losing a recycle-bin index is the kind of bug that earns 1-star reviews.
