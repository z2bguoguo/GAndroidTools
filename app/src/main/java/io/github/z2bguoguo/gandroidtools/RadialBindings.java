package io.github.z2bguoguo.gandroidtools;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Slots run from top to bottom on the inward-facing semicircle. */
public final class RadialBindings {
  public static final int COUNT = 5;
  public static final String DEFAULT = "screenshot,capture,find,info,tasks";

  private RadialBindings() {}

  public static List<String> parse(String value, Set<String> allowed) {
    String[] source = value.split(",", -1);
    List<String> out = new ArrayList<>();
    for (int i = 0; i < COUNT; i++) {
      String id = i < source.length ? source[i] : "none";
      out.add(allowed.contains(id) ? id : "none");
    }
    return out;
  }

  public static String assign(List<String> slots, int index, String action) {
    if (index < 0 || index >= COUNT) throw new IllegalArgumentException("Invalid semicircle slot");
    List<String> next = new ArrayList<>(slots);
    next.set(index, action);
    return String.join(",", next);
  }

  public static int direction(float dx, float dy, float radius, boolean left) {
    float inward = left ? dx : -dx;
    if (Math.hypot(dx, dy) < radius * .38f || inward < -radius * .18f) return -1;
    double angle = Math.toDegrees(Math.atan2(dy, Math.max(0, inward)));
    return Math.max(0, Math.min(COUNT - 1, (int) Math.round((angle + 90) / 45)));
  }
}
