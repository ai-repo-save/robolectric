package org.robolectric;

import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin;
import org.robolectric.shadows.ShadowProcess;

public final class IdentityLifecyclePlugin implements TestEnvironmentLifecyclePlugin {
  @Override
  public void onSetupApplicationState() {
    ShadowProcess.setUid(Integer.parseInt(System.getProperty("identity.integration.uid")));
  }
}
