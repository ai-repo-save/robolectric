# Local core release

The fork's Gradle project includes only the original `:robolectric` module. Its source files compile directly against the unmodified upstream 4.17 published modules. No support module, SDK or native artifact is rebuilt or republished.

The standalone core build uses Gradle's Java library and Maven publishing plugins. A standard Gradle artifact transform exposes `classes.jar` from the upstream AndroidX AAR dependencies to the JVM compiler. Published dependency coordinates and AAR types remain unchanged.

## Toolchain

- JDK 21: `/usr/lib/jvm/java-21-openjdk-amd64`.
- Gradle wrapper 9.5.0; the single-module Java build does not require upstream's Android Gradle plugins or JDK 17 build-logic compilation.
- HTTP proxy defaults to `HTTP_PROXY` or `http://127.0.0.1:7891`; `--proxy-url` selects an explicit proxy. `--no-proxy` uses normal environment networking without adding Gradle proxy options.
- `uv` with Python 3.12.
- Official test SDK: `org.robolectric:android-all-instrumented:11-robolectric-6757853-i7`, resolved through Gradle by default. The compiler also resolves upstream's compile-only `org.robolectric:android-all:17-robolectric-15733970`.

## Release

Commit the source changes, review them and push the approved commit to `https://github.com/ai-repo-save/robolectric.git` before publication. Run from that clean checkout:

```sh
uv run --script scripts/release-local.py \
  --java-home /usr/lib/jvm/java-21-openjdk-amd64 \
  --proxy-url http://127.0.0.1:7891
```

`--help` displays all options without starting a build. By default, the script delegates official API 30 SDK resolution to Gradle. The optional `--sdk-directory` validates an existing SDK directory and passes it as an explicit `identitySdkDirectory` override; the script does not require or create a local Maven SDK installation.

For environments without an explicit Gradle proxy, including GitHub Actions, run:

```sh
uv run --script scripts/release-local.py --no-proxy
```

`--no-proxy` and an explicitly supplied `--proxy-url` are mutually exclusive. `--no-proxy` bypasses the script's default proxy selection and adds no proxy properties or addresses to the Gradle command. It preserves the existing process environment.

The script explicitly runs only `org.robolectric.StartupIdentityIntegrationTest`, then publishes `io.github.ai-repo-save.robolectric:robolectric:1.0.0` to:

- `build/repository/`, a Maven-layout file repository.
- `~/.m2/repository/`, through Gradle's Maven-local publication task.

The version directory contains the main JAR, sources JAR, POM, Gradle module metadata and a `robolectric-1.0.0.provenance.json` receipt. The POM SCM identifies the public fork through `scm:git:https://github.com/ai-repo-save/robolectric.git` and the clean checkout's actual commit, resolved when generating publication metadata. The upstream repository, baseline commit and version are separate POM properties; they do not identify the fork source.

The provenance receipt records the same HTTPS source repository and fork commit, the upstream baseline, toolchain, build command, artifact sizes and SHA-256 values. Consumers check out that exact commit from the public fork. The script verifies the published POM SCM against the release source, verifies the Maven-local artifacts against the file repository and copies the provenance receipt and its checksum to both destinations.

The focused integration test is not a dependency of `test` or `check`. To run it independently:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew \
  --no-daemon --max-workers=2 \
  -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7891 \
  -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7891 \
  :robolectric:identityIntegrationTest \
  --tests org.robolectric.StartupIdentityIntegrationTest
```

The two parameterized inputs exercise package-only TikTok and GMS startup identities, including an explicit separate process name. They retain official FakeMediaProvider setup and independently read an Application.onCreate filesystem receipt.

## Public source validation

`.github/workflows/core-validation.yml` checks out the triggering immutable commit, sets up JDK 21 and uses the committed Gradle 9.5.0 wrapper. It runs only core compilation and the explicit `StartupIdentityIntegrationTest`, with normal Gradle dependency caching. The workflow does not publish packages, images or deployment artifacts and does not configure a local proxy.

Without an explicit `identitySdkDirectory`, the test task resolves the official API 30 instrumented SDK through Gradle and reads it directly from the dependency cache. No SDK copy or local Maven installation is required for CI.
