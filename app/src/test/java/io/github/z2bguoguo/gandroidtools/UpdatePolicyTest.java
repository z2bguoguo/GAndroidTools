// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

public class UpdatePolicyTest {
  private JSONObject metadata() throws Exception {
    return new JSONObject()
        .put("versionCode", 8)
        .put("versionName", "0.2.0-pre.8")
        .put("url", UpdatePolicy.BASE + "GAndroidTools-0.2.0-pre.8.apk")
        .put("sha256", "a".repeat(64))
        .put("size", 100);
  }

  @Test
  public void rejectsUntrustedLocationsAndOversizedFiles() throws Exception {
    for (String url :
        new String[] {
          "http://z2bguoguo.github.io/GAndroidTools/downloads/GAndroidTools-x.apk",
          "https://evil.example/GAndroidTools/downloads/GAndroidTools-x.apk",
          "https://z2bguoguo.github.io/GAndroidTools/downloads/../GAndroidTools-x.apk",
          "https://z2bguoguo.github.io/GAndroidTools/downloads/GAndroidTools-x.apk?redirect=evil",
          "https://z2bguoguo.github.io:443/GAndroidTools/downloads/GAndroidTools-x.apk"
        }) {
      JSONObject json = metadata().put("url", url);
      assertThrows(IllegalArgumentException.class, () -> UpdatePolicy.parse(json));
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> UpdatePolicy.parse(metadata().put("size", UpdatePolicy.MAX_BYTES + 1)));
  }

  @Test
  public void signedIdentityMustMatchManifestAndUpgradeInstalledVersion() throws Exception {
    UpdatePolicy.Release release = UpdatePolicy.parse(metadata());
    String pkg = "io.github.z2bguoguo.gandroidtools", pin = "a".repeat(64);
    UpdatePolicy.validateIdentity(release, pkg, 8, release.versionName(), pin, pin, 7);
    assertThrows(
        SecurityException.class,
        () ->
            UpdatePolicy.validateIdentity(
                release, pkg, 8, release.versionName(), pin, "b".repeat(64), 7));
    assertThrows(
        SecurityException.class,
        () -> UpdatePolicy.validateIdentity(release, pkg, 8, release.versionName(), pin, pin, 8));
    assertThrows(
        SecurityException.class,
        () ->
            UpdatePolicy.validateIdentity(
                release, "another.app", 8, release.versionName(), pin, pin, 7));
    assertThrows(
        SecurityException.class,
        () -> UpdatePolicy.validateIdentity(release, pkg, 9, release.versionName(), pin, pin, 7));
  }
}
