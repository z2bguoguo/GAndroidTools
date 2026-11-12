// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

/** Delays a single click until a possible double click has been resolved. */
public final class TapGate {
  public enum Result {
    WAIT,
    DOUBLE
  }

  private long deadline = -1;

  public Result tap(long now, int interval) {
    if (deadline >= now && deadline >= 0) {
      deadline = -1;
      return Result.DOUBLE;
    }
    deadline = now + interval;
    return Result.WAIT;
  }

  public boolean consumeSingle(long now) {
    if (deadline >= 0 && now >= deadline) {
      deadline = -1;
      return true;
    }
    return false;
  }

  public void cancel() {
    deadline = -1;
  }
}
