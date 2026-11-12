package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;
import org.junit.Test;

public class SettingsBehaviorTest {
  @Test
  public void everyNumericSettingHasDetailedBilingualHelp() {
    for (Config.Option option : Config.OPTIONS) {
      assertTrue(option.key(), SettingsHelp.has(option.key()));
      assertTrue(SettingsHelp.text(option.key(), false).contains("例："));
      assertTrue(SettingsHelp.text(option.key(), true).contains("Example:"));
    }
  }

  @Test
  public void nonNumericSettingsHaveHelp() {
    for (String key :
        List.of(
            "language",
            "radial",
            "applyLayout",
            "resetRadial",
            "jpeg",
            "saveGallery",
            "captureMode",
            "allWindows",
            "caseSensitive",
            "recordText",
            "logFollow",
            "overlayPermission",
            "accessibilityPermission")) {
      assertTrue(key, SettingsHelp.has(key));
      assertTrue(SettingsHelp.text(key, true).length() > 100);
    }
  }

  @Test
  public void positionAssignmentsPreserveDuplicatesAndEmptySlots() {
    List<String> slots =
        RadialBindings.parse("find,find,none,info,tasks", Set.of("find", "info", "tasks"));
    assertEquals(List.of("find", "find", "none", "info", "tasks"), slots);
    assertEquals("find,capture,none,info,tasks", RadialBindings.assign(slots, 1, "capture"));
  }

  @Test
  public void invalidStoredActionsDoNotExecute() {
    assertEquals(
        List.of("none", "find", "none", "none", "none"),
        RadialBindings.parse("unknown,find", Set.of("find")));
  }

  @Test
  public void semicircleMirrorsTowardTheScreen() {
    assertEquals(2, RadialBindings.direction(100, 0, 100, true));
    assertEquals(2, RadialBindings.direction(-100, 0, 100, false));
    assertEquals(-1, RadialBindings.direction(100, 0, 100, false));
    assertEquals(0, RadialBindings.direction(0, -100, 100, true));
    assertEquals(4, RadialBindings.direction(0, 100, 100, false));
    assertEquals(-1, RadialBindings.direction(5, 4, 100, true));
  }

  @Test
  public void moveRequiresCompletedTapAndExpires() {
    MoveGesture gesture = new MoveGesture();
    assertFalse(gesture.armed(100));
    gesture.tap(200);
    assertTrue(gesture.armed(500));
    assertFalse(gesture.armed(1401));
    gesture.tap(2000);
    gesture.cancel();
    assertFalse(gesture.armed(2001));
  }

  @Test
  public void shellIdentityIsRestoredAfterSuccessAndFailure() {
    class Identity implements CallIdentity.Identity {
      long uid = 10042;

      public long clear() {
        long before = uid;
        uid = 2000;
        return before;
      }

      public void restore(long value) {
        uid = value;
      }
    }
    Identity identity = new Identity();
    assertEquals(Long.valueOf(2000), CallIdentity.run(identity, () -> identity.uid));
    assertEquals(10042, identity.uid);
    assertThrows(
        IllegalStateException.class,
        () ->
            CallIdentity.run(
                identity,
                () -> {
                  assertEquals(2000, identity.uid);
                  throw new IllegalStateException("test");
                }));
    assertEquals(10042, identity.uid);
  }
}
