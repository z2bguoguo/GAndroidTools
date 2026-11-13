// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import org.junit.Test;

public class AutomationFailureTest {
  @Test
  public void actualRegistrationFailureSurvivesRemoteStackTrailer() {
    IllegalStateException occupied =
        new IllegalStateException("UiAutomationService client already registered!");
    occupied.initCause(new RuntimeException("Remote stack trace:\n server"));
    Throwable error = new InvocationTargetException(occupied);
    assertEquals("BUSY", AutomationFailure.classify(error));
    assertTrue(ErrorDetails.message(error).contains("already registered"));
  }

  @Test
  public void permissionDenialIsNotConfusedWithACompetingOwner() {
    assertEquals(
        "PERMISSION_DENIED",
        AutomationFailure.classify(new InvocationTargetException(new SecurityException("denied"))));
    assertEquals(
        "UNAVAILABLE", AutomationFailure.classify(new IllegalStateException("Already connected")));
  }

  @Test
  public void commonControlTypesHaveReadableChineseNames() {
    assertEquals("输入框", ControlLabels.type("android.widget.EditText", false));
    assertEquals("布局容器", ControlLabels.type("android.widget.FrameLayout", false));
    assertEquals("CustomControl", ControlLabels.type("sample.CustomControl", false));
  }
}
