// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Bounded diagnostics that preserve the cause hidden by reflection. */
public final class ErrorDetails {
  private ErrorDetails() {}

  public static String message(Throwable error) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable cause = error;
    while (cause.getCause() != null && seen.add(cause.getCause())) cause = cause.getCause();
    return limit(cause.toString(), 1800);
  }

  public static String trace(Throwable error) {
    StringWriter text = new StringWriter();
    error.printStackTrace(new PrintWriter(text));
    return limit(text.toString(), 16_384);
  }

  private static String limit(String text, int length) {
    return text.length() <= length ? text : text.substring(0, length - 14) + "\n[truncated]\n";
  }
}
