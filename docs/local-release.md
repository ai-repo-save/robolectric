# Local core release

The fork's Gradle project includes only the original `:robolectric` module. Its source files compile directly against the unmodified upstream 4.17 published modules. No support module, SDK or native artifact is rebuilt or republished.

The standalone core build uses Gradle's Java library and Maven publishing plugins. A standard Gradle artifact transform exposes `classes.jar` from the upstream AndroidX AAR dependencies to the JVM compiler. Published dependency coordinates and AAR types remain unchanged.

## Toolchain

- JDK 21: `/usr/lib/jvm/java-21-openjdk-amd64`.
- Gradle wrapper 9.5.0; the single-module Java build does not require upstream's Android Gradle plugins or JDK 17 build-logic compilation.
- HTTP proxy: `http://127.0.0.1:7891`, configurable with `--proxy-url` or `HTTP_PROXY`.
- `uv` with Python 3.12.
- Official test SDK: `org.robolectric:android-all-instrumented:11-robolectric-6757853-i7`, already available locally. The compiler also resolves upstream's compile-only `org.robolectric:android-all:17-robolectric-15733970`.

## Release

Commit the source changes before publication, then run from the repository root:

```sh
uv run --script scripts/release-local.py \
  --java-home /usr/lib/jvm/java-21-openjdk-amd64 \
  --proxy-url http://127.0.0.1:7891
```

`--help` displays all options without starting a build. `--sdk-directory` selects the cached API 30 instrumented SDK directory.

The script explicitly runs only `org.robolectric.StartupIdentityIntegrationTest`, then publishes `io.github.ai-repo-save.robolectric:robolectric:1.0.0` to:

- `build/repository/`, a Maven-layout file repository.
- `~/.m2/repository/`, through Gradle's Maven-local publication task.

The version directory contains the main JAR, sources JAR, POM, Gradle module metadata and a `robolectric-1.0.0.provenance.json` receipt. The POM SCM identifies the real local Git repository through a `scm:git:file:///` connection and the clean checkout's actual commit, resolved when generating publication metadata. The upstream repository, baseline commit and version are separate POM properties; they do not identify the fork source. No remote fork or remote release tag is declared.

The provenance receipt records the same local source repository and fork commit, the upstream baseline, toolchain, build command, artifact sizes and SHA-256 values. The script verifies the published POM SCM against the release source, verifies the Maven-local artifacts against the file repository and copies the provenance receipt and its checksum to both destinations.

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
