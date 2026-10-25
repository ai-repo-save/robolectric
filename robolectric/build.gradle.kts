import java.util.zip.ZipFile
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE

plugins {
  `java-library`
  `maven-publish`
}

java {
  toolchain.languageVersion = JavaLanguageVersion.of(21)
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
  withSourcesJar()
}

abstract class AarClasses : TransformAction<TransformParameters.None> {
  @get:InputArtifact abstract val input: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    ZipFile(input.get().asFile).use { archive ->
      archive.getInputStream(archive.getEntry("classes.jar")).use { source ->
        outputs.file("classes.jar").outputStream().use { target -> source.copyTo(target) }
      }
    }
  }
}

dependencies {
  registerTransform(AarClasses::class) {
    from.attribute(ARTIFACT_TYPE_ATTRIBUTE, "aar")
    to.attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar")
  }

  annotationProcessor(libs.auto.service)
  listOf(
      "annotations", "junit", "pluginapi", "resources", "sandbox", "utils", "utils-reflector",
      "plugins-maven-dependency-resolver", "shadows-framework",
    )
    .forEach { api("org.robolectric:$it:4.17") }
  api(libs.javax.inject)
  api(libs.javax.annotation.api)
  api(platform(libs.bc.jdk18on.bom))
  api(libs.bcprov.jdk18on)
  api(variantOf(libs.androidx.test.monitor) { artifactType("aar") })
  implementation(libs.conscrypt.openjdk.uber)
  implementation(libs.snakeyaml)
  implementation(variantOf(libs.androidx.test.espresso.idling.resource) { artifactType("aar") })
  implementation(variantOf(libs.test.parameter.injector) { artifactType("jar") })
  compileOnly(libs.auto.service.annotations)
  compileOnly(libs.findbugs.jsr305)
  compileOnly(libs.junit4)
  compileOnly(libs.androidx.annotation)
  compileOnly("org.robolectric:android-all:17-robolectric-15733970")

  testImplementation(libs.androidx.annotation)
  testImplementation(libs.junit4)
  testImplementation(libs.truth)
  testImplementation(libs.mockito)
  testImplementation(libs.mockito.subclass)
  testImplementation(libs.hamcrest)
  testImplementation(variantOf(libs.androidx.test.core) { artifactType("aar") })
  testImplementation(variantOf(libs.androidx.test.ext.junit) { artifactType("aar") })
  testImplementation(variantOf(libs.androidx.test.ext.truth) { artifactType("aar") })
  testImplementation(variantOf(libs.androidx.test.runner) { artifactType("aar") })
  testImplementation(libs.guava)
  testImplementation(libs.guava.testlib)
  testCompileOnly("org.robolectric:android-all:17-robolectric-15733970")
  testRuntimeOnly(files("${System.getenv("ANDROID_HOME")}/platforms/android-${libs.versions.androidstubs.get()}/android.jar"))
}

configurations.configureEach {
  if (name.endsWith("Classpath")) {
    attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar")
  }
}

listOf("apiElements", "runtimeElements").forEach { variant ->
  configurations.named(variant) {
    outgoing.capability("${project.group}:robolectric:${project.version}")
    outgoing.capability("org.robolectric:robolectric:4.17")
  }
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.withType<Jar>().configureEach {
  isPreserveFileTimestamps = false
  isReproducibleFileOrder = true
  manifest.attributes("Implementation-Version" to project.version, "Robolectric-Upstream-Version" to "4.17")
}

publishing {
  publications {
    create<MavenPublication>("runtimeCore") {
      from(components["java"])
      pom {
        name = "Robolectric Runtime Core"
        description = "Robolectric 4.17 core with public application startup identity configuration"
        url = "https://github.com/robolectric/robolectric"
        licenses {
          license {
            name = "MIT License"
            url = "https://opensource.org/licenses/MIT"
          }
        }
        scm {
          url = "https://github.com/robolectric/robolectric"
          tag = "0706415db828e1bdcf4f0c820784fa760d0185a2"
        }
      }
    }
  }
  repositories {
    maven {
      name = "runtimeLocal"
      url = rootProject.layout.buildDirectory.dir("repository").get().asFile.toURI()
    }
  }
}

val identityIntegration = sourceSets.create("identityIntegration") {
  compileClasspath += sourceSets.main.get().output
  runtimeClasspath += sourceSets.main.get().output
}
configurations[identityIntegration.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[identityIntegration.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())
dependencies {
  add(identityIntegration.implementationConfigurationName, libs.junit4)
  add(identityIntegration.implementationConfigurationName, "org.robolectric:android-all:11-robolectric-6757853")
}

val identityIntegrationTest by tasks.registering(Test::class) {
  description = "Explicit startup identity integration test; not part of check or test."
  group = "verification"
  testClassesDirs = identityIntegration.output.classesDirs
  classpath = identityIntegration.runtimeClasspath
  useJUnit()
  maxHeapSize = "2g"
  systemProperty("robolectric.offline", "true")
  systemProperty(
    "robolectric.dependency.dir",
    providers.gradleProperty("identitySdkDirectory").getOrElse(
      "${System.getProperty("user.home")}/.m2/repository/org/robolectric/android-all-instrumented/11-robolectric-6757853-i7"
    ),
  )
  jvmArgs(
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--add-opens=java.base/java.net=ALL-UNNAMED",
    "--add-opens=java.base/java.security=ALL-UNNAMED",
    "--add-opens=java.base/java.text=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
  )
  testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
