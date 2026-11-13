// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import java.net.URI;
import org.json.JSONObject;

/** Public update metadata is untrusted until the downloaded APK is checked. */
final class UpdatePolicy {
  static final String BASE = "https://z2bguoguo.github.io/GAndroidTools/downloads/";
  static final long MAX_BYTES = 128L * 1024 * 1024;

  record Release(int versionCode, String versionName, String url, String sha256, long size) {}

  private UpdatePolicy() {}

  static Release parse(JSONObject json) {
    int code = json.optInt("versionCode", -1);
    String name = json.optString("versionName"), hash = json.optString("sha256");
    String url = json.optString("url");
    long size = json.optLong("size", -1);
    URI uri = URI.create(url);
    if (code < 1
        || name.length() > 100
        || name.isBlank()
        || !hash.matches("[a-fA-F0-9]{64}")
        || size < 1
        || size > MAX_BYTES
        || !"https".equals(uri.getScheme())
        || !"z2bguoguo.github.io".equals(uri.getHost())
        || uri.getPort() != -1
        || uri.getRawUserInfo() != null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null
        || !uri.getRawPath()
            .matches("/GAndroidTools/downloads/GAndroidTools-[A-Za-z0-9._-]+\\.apk"))
      throw new IllegalArgumentException("Invalid update metadata or download location");
    return new Release(code, name, url, hash.toLowerCase(java.util.Locale.ROOT), size);
  }

  static void validateIdentity(
      Release release,
      String packageName,
      long code,
      String name,
      String certificate,
      String expectedCertificate,
      long installedCode) {
    if (!"io.github.z2bguoguo.gandroidtools".equals(packageName)
        || code != release.versionCode()
        || !release.versionName().equals(name)
        || code <= installedCode
        || !certificate.equalsIgnoreCase(expectedCertificate.trim()))
      throw new SecurityException("APK identity, version or signing certificate does not match");
  }
}
