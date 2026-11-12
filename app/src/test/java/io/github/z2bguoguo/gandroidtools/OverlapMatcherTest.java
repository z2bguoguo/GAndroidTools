// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import java.util.Random;
import org.junit.Test;

public class OverlapMatcherTest {
  @Test
  public void findsExactOverlap() {
    int w = 96, h = 180, overlap = 67;
    int[] all = random(w * (2 * h - overlap), 17);
    int[] a = java.util.Arrays.copyOfRange(all, 0, w * h);
    int[] b = java.util.Arrays.copyOfRange(all, (h - overlap) * w, all.length);
    assertEquals(overlap, OverlapMatcher.find(w, h, a, b));
  }

  @Test
  public void rejectsUnrelatedFrames() {
    assertEquals(-1, OverlapMatcher.find(96, 180, random(96 * 180, 4), random(96 * 180, 9)));
  }

  @Test
  public void rejectsUniformPages() {
    int[] blank = new int[96 * 180];
    java.util.Arrays.fill(blank, 0xffffffff);
    assertEquals(-1, OverlapMatcher.find(96, 180, blank, blank));
    assertTrue(OverlapMatcher.same(blank, blank));
  }

  @Test
  public void detectsRepeatedFrame() {
    int[] a = random(96 * 180, 4);
    assertTrue(OverlapMatcher.same(a, a.clone()));
    assertFalse(OverlapMatcher.same(a, random(a.length, 5)));
  }

  private int[] random(int length, int seed) {
    Random r = new Random(seed);
    int[] out = new int[length];
    for (int i = 0; i < out.length; i++) out[i] = 0xff000000 | r.nextInt(0xffffff);
    return out;
  }
}
