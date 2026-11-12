// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

/** A completed tap arms a following drag; a long press always selects actions. */
public final class MoveGesture {
  private long until = -1;

  public void tap(long now) {
    until = now + 1200;
  }

  public boolean armed(long now) {
    return until >= now;
  }

  public void cancel() {
    until = -1;
  }
}
