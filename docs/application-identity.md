# Application startup identity

Artifact: `io.github.ai-repo-save.robolectric:robolectric:1.0.0`.
Upstream: Robolectric 4.17, commit `0706415db828e1bdcf4f0c820784fa760d0185a2`.
Java packages remain `org.robolectric`. Unmodified modules remain upstream 4.17 artifacts.

## Configuration

Set JVM system properties before running `JUnitCore` / `RobolectricTestRunner`.

| Property | Contract |
| --- | --- |
| `robolectric.applicationPackageName` | Nonblank Android package name. Creates the actual startup Package when no build-system resource APK is configured. Defaults to `org.robolectric.default`. A configured resource APK remains authoritative for its package name. |
| `robolectric.applicationProcessName` | Nonblank process name. Configures both startup ApplicationInfo.processName and BoundApplication.processName. If absent, upstream process-name behavior applies. |

For a package-only profile, pass its package name and main-process name explicitly. Keep `@Config(sdk = 30, manifest = Config.NONE, application = RuntimeApplication.class)` for the worker. The package does not require generated resource APKs or Android build tools.

## UID and lifecycle

Implement the existing public `org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin` and register it in `META-INF/services/org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin`.

In `onSetupApplicationState()`, read the session's resolved UID and call `org.robolectric.shadows.ShadowProcess.setUid(uid)`. This callback runs inside the Android sandbox before startup ApplicationInfo creation. Do not set the UID only in the host classloader or after Application attachment.

Robolectric then creates the package, assigns `ApplicationInfo.uid = Process.myUid()`, binds the process and registers the startup package before attaching the Application. Keep this base Context; apply other profile metadata without replacing startup identity. Do not install an unrelated package and switch the Application's base Context to simulate process identity.

This configures Robolectric's Android framework model. It does not execute APK Dex code or create an ART process.

## Dependency selection

Replace `org.robolectric:robolectric:4.17` with the fork core; do not add both. Preserve upstream `org.robolectric:*:4.17` support modules.

The fork's Gradle module metadata advertises the `org.robolectric:robolectric:4.17` capability so a graph containing both cores produces a capability conflict instead of silently loading duplicate classes. Maven consumers must exclude the upstream core from any transitive dependency that introduces it. Inspect the resolved runtime graph before deployment.
