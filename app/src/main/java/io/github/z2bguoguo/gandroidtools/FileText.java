// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class FileText {
  private FileText() {}

  public static String read(Path path) throws IOException {
    return new String(java.nio.file.Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  public static void write(Path path, String value) throws IOException {
    java.nio.file.Files.write(path, value.getBytes(StandardCharsets.UTF_8));
  }
}
