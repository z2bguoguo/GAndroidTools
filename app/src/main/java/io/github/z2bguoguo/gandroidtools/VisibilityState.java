// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Visibility belongs to a frozen tree and never depends on the current live page. */
public final class VisibilityState {
  private final List<String> paths;
  private final boolean[] visible;

  public VisibilityState(List<String> paths) {
    this.paths = new ArrayList<>(paths);
    visible = new boolean[paths.size()];
    showAll();
  }

  public boolean visible(int index) {
    return visible[index];
  }

  public void showAll() {
    Arrays.fill(visible, true);
  }

  public void set(int index, boolean show) {
    String path = paths.get(index);
    visible[index] = show;
    for (int i = 0; i < paths.size(); i++) {
      if (paths.get(i).startsWith(path + ".")) visible[i] = show;
      if (show && path.startsWith(paths.get(i) + ".")) visible[i] = true;
    }
  }
}
