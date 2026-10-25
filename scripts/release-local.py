#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12,<3.13"
# dependencies = []
# ///

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = "0706415db828e1bdcf4f0c820784fa760d0185a2"
SOURCE_REPOSITORY = "https://github.com/ai-repo-save/robolectric.git"
COORDINATES = "io.github.ai-repo-save.robolectric:robolectric:1.0.0"
ARTIFACT_PATH = Path("io/github/ai-repo-save/robolectric/robolectric/1.0.0")


def digest(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Test and publish only the fork core to build/repository and Maven local."
    )
    parser.add_argument(
        "--java-home",
        type=Path,
        default=Path(os.environ.get("JAVA_HOME", "/usr/lib/jvm/java-21-openjdk-amd64")),
    )
    network = parser.add_mutually_exclusive_group()
    network.add_argument(
        "--proxy-url",
        default=os.environ.get("HTTP_PROXY", "http://127.0.0.1:7891"),
        help="HTTP proxy URL used explicitly for Maven and Gradle downloads",
    )
    network.add_argument(
        "--no-proxy",
        action="store_true",
        help="Use environment networking without injecting Gradle proxy options",
    )
    parser.add_argument(
        "--sdk-directory",
        type=Path,
        default=Path.home()
        / ".m2/repository/org/robolectric/android-all-instrumented/11-robolectric-6757853-i7",
        help="Directory containing the official API 30 instrumented SDK JAR",
    )
    args = parser.parse_args()
    proxy_arguments = []
    if not args.no_proxy:
        proxy = urlparse(args.proxy_url)
        if proxy.scheme != "http" or proxy.hostname is None or proxy.port is None:
            parser.error("--proxy-url must be an http://host:port URL")
        proxy_arguments = [
            f"-Dhttp.proxyHost={proxy.hostname}",
            f"-Dhttp.proxyPort={proxy.port}",
            f"-Dhttps.proxyHost={proxy.hostname}",
            f"-Dhttps.proxyPort={proxy.port}",
        ]
    if not (args.java_home / "bin/java").is_file():
        parser.error("--java-home must contain bin/java")
    sdk = args.sdk_directory.resolve()
    if not (sdk / "android-all-instrumented-11-robolectric-6757853-i7.jar").is_file():
        parser.error(
            "--sdk-directory must contain the official API 30 instrumented SDK JAR"
        )
    status = subprocess.check_output(
        ["git", "status", "--porcelain"], cwd=ROOT, text=True
    )
    if status:
        parser.error("commit source changes before releasing")
    commit = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
    ).strip()
    environment = os.environ | {"JAVA_HOME": str(args.java_home.resolve())}
    command = [
        str(ROOT / "gradlew"),
        "--no-daemon",
        "--console=plain",
        "--max-workers=2",
        *proxy_arguments,
        f"-PidentitySdkDirectory={sdk}",
        ":robolectric:identityIntegrationTest",
        "--tests",
        "org.robolectric.StartupIdentityIntegrationTest",
        ":robolectric:publishRuntimeCorePublicationToRuntimeLocalRepository",
        ":robolectric:publishRuntimeCorePublicationToMavenLocal",
    ]
    print(
        "Building, testing and publishing the fork core...", file=sys.stderr, flush=True
    )
    result = subprocess.run(
        command, cwd=ROOT, env=environment, stdout=sys.stderr, check=False
    )
    if result.returncode:
        return result.returncode
    repository = ROOT / "build/repository"
    artifact_directory = repository / ARTIFACT_PATH
    installed_directory = Path.home() / ".m2/repository" / ARTIFACT_PATH
    artifacts = [
        artifact_directory / f"robolectric-1.0.0{suffix}"
        for suffix in (".jar", "-sources.jar", ".pom", ".module")
    ]
    records = []
    for artifact in artifacts:
        sha = digest(artifact)
        installed = installed_directory / artifact.name
        if digest(installed) != sha:
            raise RuntimeError(
                f"Maven local artifact differs from file repository: {artifact.name}"
            )
        records.append(
            {"file": artifact.name, "bytes": artifact.stat().st_size, "sha256": sha}
        )
    source_repository = SOURCE_REPOSITORY
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    pom = ET.parse(artifact_directory / "robolectric-1.0.0.pom").getroot()
    expected_scm = {
        "url": source_repository,
        "connection": f"scm:git:{source_repository}",
        "tag": commit,
    }
    for field, expected in expected_scm.items():
        actual = pom.findtext(f"m:scm/m:{field}", namespaces=namespace)
        if actual != expected:
            raise RuntimeError(f"Published POM SCM {field} differs from release source")
    provenance = {
        "coordinates": COORDINATES,
        "sourceRepository": source_repository,
        "upstreamRepository": "https://github.com/robolectric/robolectric.git",
        "upstreamCommit": UPSTREAM,
        "sourceCommit": commit,
        "gradleVersion": "9.5.0",
        "javaVersion": subprocess.check_output(
            [str(args.java_home / "bin/java"), "--version"], text=True
        ).strip(),
        "command": command,
        "artifacts": records,
    }
    provenance_path = artifact_directory / "robolectric-1.0.0.provenance.json"
    provenance_path.write_text(json.dumps(provenance, indent=2) + "\n")
    checksum_path = provenance_path.with_suffix(".json.sha256")
    checksum_path.write_text(f"{digest(provenance_path)}  {provenance_path.name}\n")
    for path in (provenance_path, checksum_path):
        shutil.copy2(path, installed_directory / path.name)
    print(
        json.dumps({"repository": str(repository), "provenance": str(provenance_path)})
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
