package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class InteractionTest {
  @Test
  public void doubleTapConsumesPendingSingle() {
    TapGate gate = new TapGate();
    assertEquals(TapGate.Result.WAIT, gate.tap(1000, 280));
    assertEquals(TapGate.Result.DOUBLE, gate.tap(1180, 280));
    assertFalse(gate.consumeSingle(1280));
  }

  @Test
  public void singleFiresOnlyOnceAfterWindow() {
    TapGate gate = new TapGate();
    gate.tap(1000, 280);
    assertFalse(gate.consumeSingle(1279));
    assertTrue(gate.consumeSingle(1280));
    assertFalse(gate.consumeSingle(1500));
  }

  @Test
  public void longPressCancelsPendingClick() {
    TapGate gate = new TapGate();
    gate.tap(1000, 280);
    gate.cancel();
    assertFalse(gate.consumeSingle(1400));
  }

  @Test
  public void nestedVisibilityKeepsSiblingsHidden() {
    VisibilityState state = new VisibilityState(List.of("0", "0.0", "0.0.0", "0.1"));
    state.set(0, false);
    state.set(2, true);
    assertTrue(state.visible(0));
    assertTrue(state.visible(1));
    assertTrue(state.visible(2));
    assertFalse(state.visible(3));
  }

  @Test
  public void frozenPathsAreDetached() {
    java.util.ArrayList<String> paths = new java.util.ArrayList<>(List.of("0", "0.0", "0.1"));
    VisibilityState state = new VisibilityState(paths);
    paths.clear();
    state.set(0, false);
    assertFalse(state.visible(2));
    state.showAll();
    assertTrue(state.visible(2));
  }
}
