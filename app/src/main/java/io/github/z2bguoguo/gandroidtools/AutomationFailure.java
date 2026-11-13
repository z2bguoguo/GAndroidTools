// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** A busy platform singleton is different from a missing permission or dead service. */
final class AutomationFailure {
  private AutomationFailure() {}

  static String classify(Throwable error) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    boolean denied = false;
    for (Throwable item = error; item != null && seen.add(item); item = item.getCause()) {
      String message = String.valueOf(item.getMessage());
      if (message.contains("UiAutomationService") && message.contains("already registered"))
        return "BUSY";
      if (item instanceof SecurityException) denied = true;
    }
    return denied ? "PERMISSION_DENIED" : "UNAVAILABLE";
  }
}
