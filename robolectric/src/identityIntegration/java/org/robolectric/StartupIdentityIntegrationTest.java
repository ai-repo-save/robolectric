package org.robolectric;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.Process;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.robolectric.annotation.Config;

@RunWith(Parameterized.class)
public final class StartupIdentityIntegrationTest {
  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> identities() {
    return Arrays.asList(
        new Object[] {"com.ss.android.ugc.tiktok.pro", "com.ss.android.ugc.tiktok.pro:upload", 10000},
        new Object[] {"com.google.android.gms", "com.google.android.gms", 10123});
  }

  private final String packageName;
  private final String processName;
  private final int uid;

  public StartupIdentityIntegrationTest(String packageName, String processName, int uid) {
    this.packageName = packageName;
    this.processName = processName;
    this.uid = uid;
  }

  @Test
  public void applicationStartupPersistsItsIdentity() throws IOException {
    Path directory = Files.createTempDirectory("robolectric-startup-identity-");
    Path receipt = directory.resolve("identity.txt");
    System.setProperty("robolectric.applicationPackageName", packageName);
    System.setProperty("robolectric.applicationProcessName", processName);
    System.setProperty("identity.integration.uid", Integer.toString(uid));
    System.setProperty("identity.integration.receipt", receipt.toString());
    try {
      Result result = JUnitCore.runClasses(Bootstrap.class);
      assertTrue(result.getFailures().toString(), result.wasSuccessful());
      assertEquals(1, result.getRunCount());
      assertEquals(
          String.join(
              "\n", packageName, packageName, packageName, processName, processName,
              Integer.toString(uid), Integer.toString(uid), Integer.toString(uid)),
          Files.readString(receipt));
    } finally {
      System.clearProperty("robolectric.applicationPackageName");
      System.clearProperty("robolectric.applicationProcessName");
      System.clearProperty("identity.integration.uid");
      System.clearProperty("identity.integration.receipt");
      Files.deleteIfExists(receipt);
      Files.delete(directory);
    }
  }

  @RunWith(RobolectricTestRunner.class)
  @Config(sdk = 30, manifest = Config.NONE, application = IdentityApplication.class)
  public static final class Bootstrap {
    @Test
    public void completeLifecycle() {}
  }

  public static final class IdentityApplication extends Application {
    @Override
    public void onCreate() {
      super.onCreate();
      try {
        ApplicationInfo installed = getPackageManager().getApplicationInfo(getPackageName(), 0);
        Files.writeString(
            Path.of(System.getProperty("identity.integration.receipt")),
            String.join(
                "\n",
                getPackageName(),
                getBaseContext().getPackageName(),
                installed.packageName,
                Application.getProcessName(),
                getApplicationInfo().processName,
                Integer.toString(Process.myUid()),
                Integer.toString(getApplicationInfo().uid),
                Integer.toString(installed.uid)));
      } catch (Exception e) {
        throw new IllegalStateException("Cannot persist application startup identity", e);
      }
    }
  }
}
