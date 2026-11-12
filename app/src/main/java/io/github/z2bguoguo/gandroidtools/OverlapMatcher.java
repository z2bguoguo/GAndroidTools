// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

public final class OverlapMatcher {
  private OverlapMatcher() {}

  /** Finds a contiguous overlap; -1 means that stitching cannot be established safely. */
  public static int find(int width, int height, int[] previous, int[] next) {
    if (width < 16
        || height < 32
        || previous.length != width * height
        || next.length != previous.length) return -1;
    int best = -1;
    double bestError = Double.MAX_VALUE;
    int low = Math.max(16, height / 12), high = height * 9 / 10;
    for (int overlap = low; overlap <= high; overlap++) {
      double error = error(width, height, previous, next, overlap);
      if (error < bestError) {
        bestError = error;
        best = overlap;
      }
    }
    int initial = best;
    for (int overlap = Math.max(low, initial - 4);
        overlap <= Math.min(high, initial + 4);
        overlap++) {
      double error = error(width, height, previous, next, overlap);
      if (error < bestError) {
        bestError = error;
        best = overlap;
      }
    }
    return bestError < 10 ? best : -1;
  }

  private static double error(int w, int h, int[] a, int[] b, int overlap) {
    long sum = 0;
    int count = 0;
    int min = 255, max = 0;
    for (int y = 2; y < overlap - 2; y += Math.max(1, overlap / 36))
      for (int x = w / 12; x < w - w / 12; x += Math.max(1, w / 48)) {
        int av = a[(h - overlap + y) * w + x], bv = b[y * w + x];
        int r = (av >> 16) & 255, g = (av >> 8) & 255, blue = av & 255;
        int brightness = (r + g + blue) / 3;
        min = Math.min(min, brightness);
        max = Math.max(max, brightness);
        sum +=
            Math.abs(r - ((bv >> 16) & 255))
                + Math.abs(g - ((bv >> 8) & 255))
                + Math.abs(blue - (bv & 255));
        count += 3;
      }
    return count == 0 || max - min < 18 ? Double.MAX_VALUE : (double) sum / count;
  }

  public static boolean same(int[] a, int[] b) {
    if (a.length != b.length || a.length == 0) return false;
    long error = 0;
    int count = 0;
    for (int i = 0; i < a.length; i += Math.max(1, a.length / 4000)) {
      int x = a[i], y = b[i];
      error +=
          Math.abs(((x >> 16) & 255) - ((y >> 16) & 255))
              + Math.abs(((x >> 8) & 255) - ((y >> 8) & 255))
              + Math.abs((x & 255) - (y & 255));
      count += 3;
    }
    return (double) error / count < 1.5;
  }
}
