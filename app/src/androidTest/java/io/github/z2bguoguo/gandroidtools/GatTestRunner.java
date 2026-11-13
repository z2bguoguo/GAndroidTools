// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

public final class GatTestRunner extends androidx.test.runner.AndroidJUnitRunner {
  @Override
  public void onStart() {
    new Config(getTargetContext()).set("autoUpdate", false);
    super.onStart();
  }
}
