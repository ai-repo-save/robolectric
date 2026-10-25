# Package-only startup identity API gap

## Affected upstream

Robolectric 4.17, source commit `0706415db828e1bdcf4f0c820784fa760d0185a2`.

An embedded worker runs one complete API 30 Robolectric lifecycle per JVM using `JUnitCore`, `RobolectricTestRunner`, `@Config(manifest = Config.NONE)` and a custom Application. Its resolved application profile can contain package metadata without Android resources or an APK.

## Reproduction

1. Run the worker with package `com.ss.android.ugc.tiktok.pro`, UID `10000` and no build-system resource APK.
2. During Application attachment, install this PackageInfo through public ShadowPackageManager.installPackage and switch the base Context with createPackageContext.
3. Allow the official Application bootstrap to continue, including FakeMediaProvider installation.

Observed failure:

```text
SecurityException: Requesting code from com.ss.android.ugc.tiktok.pro (with uid 10000)
to be run in process org.robolectric.default (with uid 12939)
```

The randomly assigned process UID also appeared as `11331` in an independent session. An explicit GMS package-only profile has the same initialization boundary.

## Source evidence

Paths and line numbers below refer to the unmodified upstream commit.

- `annotations/src/main/java/org/robolectric/annotation/Config.java:26–113`: public configuration has no packageName option.
- `robolectric/src/main/java/org/robolectric/internal/DefaultManifestFactory.java:21–37`: build-system configuration accepts a resource APK.
- `robolectric/src/main/java/org/robolectric/android/internal/AndroidTestEnvironment.java:474–478`: without that APK, startup always constructs `Package("org.robolectric.default")`.
- The same file at `342–343` assigns the process UID; `387–389` binds the parsed package and ApplicationInfo before `419` attaches the Application.
- The same file at `450` installs FakeMediaProvider; `robolectric/src/main/java/org/robolectric/Robolectric.java:503–506` then requests the Application's LoadedApk with `CONTEXT_INCLUDE_CODE`.

The Context switch changes what the Application reports but does not replace its already-bound process ApplicationInfo. The framework security check exposes the identity mismatch.

## Public API comparison

- A build-system resource APK supplies the correct startup Package but requires an unnecessary APK-generation/build-tools pipeline for a metadata-only session.
- `android_custom_package` and a plain merged manifest do not replace the no-APK Package constructed by AndroidTestEnvironment.
- `ShadowPackageManager.installPackage` registers packages; it does not configure the startup process identity.
- `ShadowApplication.setProcessName` updates the process name, not its bound ApplicationInfo.
- `ShadowProcess.setUid`, invoked through the public setup lifecycle plugin before ApplicationInfo creation, already supplies the required UID behavior.

## Fork change

Add documented JVM properties for the package-only startup package and explicit process name at the existing Package / ApplicationInfo / BoundApplication initialization points. Keep the existing public UID mechanism. Publish only the changed core under a fork GAV while retaining upstream Java packages and 4.17 support modules.

The focused integration test runs the official API 30 lifecycle with TikTok and GMS package-only identities. Application.onCreate persists the observed base Context, PackageManager, process and ApplicationInfo identities; the outer test independently reads and compares the receipt. FakeMediaProvider remains enabled, exercising the original failure path.

Upstream issue submission requires the user's decision. No issue has been submitted.
