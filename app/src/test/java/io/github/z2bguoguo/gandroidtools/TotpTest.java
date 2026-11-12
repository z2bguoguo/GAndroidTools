// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import org.junit.Test;

public class TotpTest {
  @Test
  public void rfc6238Sha1Vectors() throws Exception {
    Totp.Account account =
        new Totp.Account("RFC", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "SHA1", 8, 30);
    long[] times = {59, 1111111109, 1111111111, 1234567890, 2000000000, 20000000000L};
    String[] expected = {"94287082", "07081804", "14050471", "89005924", "69279037", "65353130"};
    for (int i = 0; i < times.length; i++) assertEquals(expected[i], Totp.code(account, times[i]));
  }

  @Test
  public void parsesUriAndEncodedLabel() {
    Totp.Account a =
        Totp.parse(
            "otpauth://totp/Example%3Aalice?secret=JBSWY3DPEHPK3PXP&algorithm=SHA256&digits=8&period=45");
    assertEquals("Example:alice", a.label());
    assertEquals("SHA256", a.algorithm());
    assertEquals(45, a.period());
  }

  @Test
  public void rejectsHotp() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Totp.parse("otpauth://hotp/test?secret=JBSWY3DPEHPK3PXP"));
  }

  @Test
  public void rejectsUnknownAlgorithm() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Totp.parse("otpauth://totp/test?secret=JBSWY3DPEHPK3PXP&algorithm=MD5"));
  }

  @Test
  public void rejectsInvalidSecret() {
    assertThrows(
        IllegalArgumentException.class, () -> Totp.parse("otpauth://totp/test?secret=1234INVALID"));
  }

  @Test
  public void stableWithinPeriodAndChangesAfter() throws Exception {
    Totp.Account a = Totp.parse("otpauth://totp/test?secret=JBSWY3DPEHPK3PXP");
    assertEquals(Totp.code(a, 60), Totp.code(a, 89));
    assertNotEquals(Totp.code(a, 60), Totp.code(a, 90));
  }
}
