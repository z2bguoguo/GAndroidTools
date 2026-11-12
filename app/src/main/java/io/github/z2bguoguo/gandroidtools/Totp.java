// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class Totp {
  public record Account(String label, String secret, String algorithm, int digits, int period) {}

  private Totp() {}

  public static Account parse(String text) {
    URI uri = URI.create(text.trim());
    if (!"otpauth".equals(uri.getScheme()) || !"totp".equals(uri.getHost()))
      throw new IllegalArgumentException("Only otpauth://totp accounts are supported");
    Map<String, String> values = new HashMap<>();
    if (uri.getRawQuery() != null)
      for (String pair : uri.getRawQuery().split("&")) {
        String[] p = pair.split("=", 2);
        if (p.length == 2) values.put(decode(p[0]), decode(p[1]));
      }
    String secret = values.getOrDefault("secret", "").replace(" ", "").toUpperCase(Locale.ROOT);
    byte[] key = base32(secret);
    if (key.length < 10 || key.length > 128)
      throw new IllegalArgumentException("Invalid OTP secret length");
    String algo = values.getOrDefault("algorithm", "SHA1").toUpperCase(Locale.ROOT);
    if (!java.util.Set.of("SHA1", "SHA256", "SHA512").contains(algo))
      throw new IllegalArgumentException("Unsupported OTP algorithm");
    int digits = Integer.parseInt(values.getOrDefault("digits", "6")),
        period = Integer.parseInt(values.getOrDefault("period", "30"));
    if ((digits != 6 && digits != 8) || period < 5 || period > 120)
      throw new IllegalArgumentException("Invalid OTP digits or period");
    return new Account(decode(uri.getRawPath().substring(1)), secret, algo, digits, period);
  }

  private static String decode(String s) {
    try {
      return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
    } catch (java.io.UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  public static byte[] base32(String s) {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    int buffer = 0, bits = 0;
    for (char c : s.replace("=", "").toUpperCase(Locale.ROOT).toCharArray()) {
      int n = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
      if (n < 0) throw new IllegalArgumentException("Invalid base32 secret");
      buffer = (buffer << 5) | n;
      bits += 5;
      if (bits >= 8) {
        bits -= 8;
        out.write((buffer >> bits) & 255);
      }
    }
    return out.toByteArray();
  }

  public static String code(Account a, long epochSeconds) throws Exception {
    Mac mac = Mac.getInstance("Hmac" + a.algorithm);
    mac.init(new SecretKeySpec(base32(a.secret), "Hmac" + a.algorithm));
    byte[] bytes = mac.doFinal(ByteBuffer.allocate(8).putLong(epochSeconds / a.period).array());
    int offset = bytes[bytes.length - 1] & 15;
    int binary = ByteBuffer.wrap(bytes, offset, 4).getInt() & 0x7fffffff;
    int divisor = a.digits == 8 ? 100000000 : 1000000;
    return String.format(Locale.ROOT, "%0" + a.digits + "d", binary % divisor);
  }
}
