// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Random;
import org.junit.Test;

public class ScrollAnalysisTest {
  private static final int W = 96, H = 180, T = 22, B = 18, SHIFT = 63;

  private int[] document() {
    Random random = new Random(719);
    int[] data = new int[W * H * 4];
    for (int i = 0; i < data.length; i++) data[i] = 0xff000000 | random.nextInt(0xffffff);
    return data;
  }

  private int[] viewport(int[] doc, int offset) {
    int[] out = new int[W * H];
    for (int y = 0; y < H; y++)
      for (int x = 0; x < W; x++)
        out[y * W + x] =
            y < T ? 0xff245c49 : y >= H - B ? 0xff154b69 : doc[(y - T + offset) * W + x];
    return out;
  }

  @Test
  public void stationaryBarsDoNotPreventReliableBodyAlignment() {
    int[] doc = document();
    ScrollAnalysis.Motion motion =
        ScrollAnalysis.compare(
            W, H, viewport(doc, 0), viewport(doc, SHIFT), List.of(), List.of(), true);
    assertTrue(motion.matched());
    assertEquals(SHIFT, motion.shift());
    assertEquals(T, motion.top());
    assertEquals(B, motion.bottom());
    assertEquals(H + SHIFT, T + (H - T - B) + motion.shift() + B);
  }

  @Test
  public void stationaryFloatingControlIsRecognizedWithoutTreatingThePageAsFixed() {
    int[] doc = document(), a = viewport(doc, 0), b = viewport(doc, SHIFT);
    ScrollAnalysis.Box box = new ScrollAnalysis.Box(66, 115, 90, 141);
    for (int y = box.top(); y < box.bottom(); y++)
      for (int x = box.left(); x < box.right(); x++) a[y * W + x] = b[y * W + x] = 0xffab8921;
    var control = new ScrollAnalysis.Anchor("floating-action", box, false);
    var motion = ScrollAnalysis.compare(W, H, a, b, List.of(control), List.of(control), true);
    assertEquals(SHIFT, motion.shift());
    assertEquals(List.of(box), motion.fixed());
    int restored = ScrollAnalysis.historyPixel(70, 125, 0, W, H, b, SHIFT, T, B, List.of(box));
    assertEquals(doc[(125 - T) * W + 70], restored);
    assertEquals(0, ScrollAnalysis.historyPixel(70, 40, 0, W, H, b, SHIFT, T, B, List.of(box)));
  }

  @Test
  public void repeatingPatternsDoNotInventAUniqueScrollDistance() {
    int[] doc = document();
    for (int y = 24; y < doc.length / W; y++) System.arraycopy(doc, (y % 24) * W, doc, y * W, W);
    assertFalse(
        ScrollAnalysis.compare(
                W, H, viewport(doc, 0), viewport(doc, 24), List.of(), List.of(), true)
            .matched());
  }

  @Test
  public void unrelatedUniformAndAmbiguousMotionAreRejected() {
    int[] a = new int[W * H], b = new int[W * H];
    java.util.Arrays.fill(a, -1);
    java.util.Arrays.fill(b, -1);
    assertFalse(ScrollAnalysis.compare(W, H, a, b, List.of(), List.of(), true).matched());
    int[] doc = document();
    a = viewport(doc, 0);
    b = viewport(doc, SHIFT);
    for (int i = 0; i < b.length; i++) b[i] ^= 0x00ffffff;
    assertFalse(ScrollAnalysis.compare(W, H, a, b, List.of(), List.of(), true).matched());
  }
}
