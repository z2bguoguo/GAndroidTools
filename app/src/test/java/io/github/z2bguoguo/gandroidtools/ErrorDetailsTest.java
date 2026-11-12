// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import org.junit.Test;

public class ErrorDetailsTest {
  @Test
  public void reflectiveFailuresExposeTheirRootAndFullStack() {
    Throwable error = new InvocationTargetException(new SecurityException("connection denied"));
    assertEquals("java.lang.SecurityException: connection denied", ErrorDetails.message(error));
    assertTrue(ErrorDetails.trace(error).contains("Caused by: java.lang.SecurityException"));
    assertTrue(ErrorDetails.trace(error).contains("reflectiveFailuresExposeTheirRootAndFullStack"));
  }

  @Test
  public void malformedCauseCyclesCannotHangDiagnostics() {
    Throwable a = new Exception("a"), b = new Exception("b");
    a.initCause(b);
    b.initCause(a);
    assertFalse(ErrorDetails.message(a).isBlank());
    assertTrue(ErrorDetails.trace(a).contains("CIRCULAR REFERENCE"));
  }

  @Test
  public void diagnosticsAreBoundedForBinderAndLogs() {
    Throwable error = new Exception("x".repeat(50_000));
    assertTrue(ErrorDetails.message(error).length() <= 1800);
    assertTrue(ErrorDetails.trace(error).length() <= 16_384);
    assertTrue(ErrorDetails.trace(error).endsWith("[truncated]\n"));
  }
}
