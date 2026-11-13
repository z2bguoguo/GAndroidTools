// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Evidence-based vertical alignment. Unknown motion never becomes an invented overlap. */
public final class ScrollAnalysis {
  public record Box(int left, int top, int right, int bottom) {
    public int width() {
      return Math.max(0, right - left);
    }

    public int height() {
      return Math.max(0, bottom - top);
    }

    public long area() {
      return (long) width() * height();
    }

    public boolean contains(int x, int y) {
      return x >= left && x < right && y >= top && y < bottom;
    }
  }

  public record Anchor(String identity, Box bounds, boolean container) {}

  public record Motion(int shift, int top, int bottom, double error, List<Box> fixed) {
    public boolean matched() {
      return shift > 0;
    }
  }

  private ScrollAnalysis() {}

  public static Motion compare(
      int width,
      int height,
      int[] previous,
      int[] next,
      List<Anchor> before,
      List<Anchor> after,
      boolean detectFixed) {
    if (width < 16
        || height < 32
        || previous.length != width * height
        || next.length != previous.length) return new Motion(-1, 0, 0, Double.MAX_VALUE, List.of());
    if (OverlapMatcher.same(previous, next)) return new Motion(-1, 0, 0, 0, List.of());
    int top = detectFixed ? band(width, height, previous, next, false) : 0;
    int bottom = detectFixed ? band(width, height, previous, next, true) : 0;
    List<Box> stationary = new ArrayList<>();
    if (detectFixed) {
      Map<String, Anchor> old = new HashMap<>();
      for (Anchor anchor : before) old.put(key(anchor), anchor);
      for (Anchor anchor : after) {
        Box box = anchor.bounds();
        if (anchor.identity().startsWith("scroll-viewport:")
            && old.containsKey(key(anchor))
            && box.width() >= width * 3 / 4
            && box.height() >= height * 2 / 5) {
          if (box.top() >= 0 && box.top() < height / 3) top = Math.max(top, box.top());
          if (box.bottom() <= height && box.bottom() > height * 2 / 3)
            bottom = Math.max(bottom, height - box.bottom());
          continue;
        }
        if (anchor.identity().isEmpty()
            || !old.containsKey(key(anchor))
            || box.area() < 9
            || box.area() > (long) width * height / 3
            || !sameArea(width, height, previous, next, box)) continue;
        if (box.width() >= width * 3 / 4 && box.top() <= 2 && box.bottom() < height / 3)
          top = Math.max(top, box.bottom());
        else if (box.width() >= width * 3 / 4
            && box.bottom() >= height - 2
            && box.height() < height / 3) bottom = Math.max(bottom, height - box.top());
        else if (!anchor.container() && box.area() <= (long) width * height / 5)
          stationary.add(box);
      }
    }
    int maxShift = height - top - bottom - Math.max(16, (height - top - bottom) / 12);
    double[] scores = new double[Math.max(1, maxShift + 1)];
    Arrays.fill(scores, Double.MAX_VALUE);
    int best = -1;
    double bestError = Double.MAX_VALUE;
    for (int shift = 1; shift <= maxShift; shift++) {
      double error = cost(width, height, previous, next, top, bottom, shift);
      scores[shift] = error;
      if (error < bestError) {
        bestError = error;
        best = shift;
      }
    }
    if (best < 0 || bestError >= 9) return new Motion(-1, top, bottom, bestError, List.of());
    int distance = Math.max(3, height / 150);
    for (int shift = 1; shift <= maxShift; shift++)
      if (Math.abs(shift - best) > distance && scores[shift] <= bestError + 0.35)
        return new Motion(-1, top, bottom, bestError, List.of());
    int t = top, b = bottom;
    stationary.removeIf(box -> box.top() < t || box.bottom() > height - b);
    return new Motion(best, top, bottom, bestError, List.copyOf(stationary));
  }

  private static String key(Anchor anchor) {
    return anchor.identity() + "@" + anchor.bounds();
  }

  private static int band(int width, int height, int[] a, int[] b, boolean bottom) {
    int stable = 0, misses = 0;
    for (int row = 0; row < height / 3; row++) {
      int y = bottom ? height - 1 - row : row;
      int changed = 0, count = 0;
      for (int x = width / 32; x < width - width / 32; x += Math.max(1, width / 80)) {
        if (difference(a[y * width + x], b[y * width + x]) > 9) changed++;
        count++;
      }
      if (changed <= Math.max(1, count / 20)) {
        stable = row + 1;
        misses = 0;
      } else if (++misses >= 3) break;
    }
    return stable;
  }

  private static double cost(int w, int h, int[] a, int[] b, int top, int bottom, int shift) {
    double[] rows = new double[48];
    int rowCount = 0;
    int end = h - bottom - shift;
    for (int y = top + 2;
        y < end - 2 && rowCount < rows.length;
        y += Math.max(1, (end - top) / 44)) {
      long sum = 0;
      int count = 0, min = 255, max = 0;
      for (int x = w / 16; x < w - w / 16; x += Math.max(1, w / 44)) {
        int av = a[(y + shift) * w + x], bv = b[y * w + x];
        if ((av >>> 24) < 220 || (bv >>> 24) < 220) continue;
        int value = ((bv >> 16 & 255) + (bv >> 8 & 255) + (bv & 255)) / 3;
        min = Math.min(min, value);
        max = Math.max(max, value);
        sum += difference(av, bv);
        count++;
      }
      if (count >= 8 && max - min >= 18) rows[rowCount++] = (double) sum / count;
    }
    if (rowCount < 6) return Double.MAX_VALUE;
    Arrays.sort(rows, 0, rowCount);
    int keep = Math.max(6, rowCount * 3 / 4);
    double sum = 0;
    for (int i = 0; i < keep; i++) sum += rows[i];
    return sum / keep;
  }

  private static boolean sameArea(int w, int h, int[] a, int[] b, Box box) {
    int same = 0, count = 0;
    for (int y = Math.max(1, box.top());
        y < Math.min(h - 1, box.bottom());
        y += Math.max(1, box.height() / 32))
      for (int x = Math.max(1, box.left());
          x < Math.min(w - 1, box.right());
          x += Math.max(1, box.width() / 48)) {
        int at = y * w + x;
        boolean edge =
            difference(a[at], a[at + 1]) > 8
                || difference(a[at], a[at - 1]) > 8
                || difference(a[at], a[at + w]) > 8
                || difference(a[at], a[at - w]) > 8
                || difference(b[at], b[at + 1]) > 8
                || difference(b[at], b[at + w]) > 8;
        if (!edge) continue;
        if (difference(a[at], b[at]) <= 9) same++;
        count++;
      }
    return count >= 6 && same >= count * 9 / 10;
  }

  private static int difference(int a, int b) {
    return (Math.abs((a >> 16 & 255) - (b >> 16 & 255))
            + Math.abs((a >> 8 & 255) - (b >> 8 & 255))
            + Math.abs((a & 255) - (b & 255)))
        / 3;
  }

  public static boolean masked(List<Box> boxes, int x, int y) {
    for (Box box : boxes) if (box.contains(x, y)) return true;
    return false;
  }

  /** Reuses a real pixel from the same aligned document coordinate; zero means unobserved. */
  public static int historyPixel(
      int x,
      int y,
      long targetOrigin,
      int width,
      int height,
      int[] source,
      long sourceOrigin,
      int top,
      int bottom,
      List<Box> sourceMasks) {
    long sy = y + targetOrigin - sourceOrigin;
    if (x < 0
        || x >= width
        || sy < top
        || sy >= height - bottom
        || masked(sourceMasks, x, (int) sy)) return 0;
    return source[(int) sy * width + x];
  }
}
