// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import java.util.function.Supplier;

/** Executes privileged local calls as the service process, restoring the inbound identity. */
public final class CallIdentity {
  public interface Identity {
    long clear();

    void restore(long token);
  }

  private CallIdentity() {}

  public static <T> T run(Identity identity, Supplier<T> operation) {
    long token = identity.clear();
    try {
      return operation.get();
    } finally {
      identity.restore(token);
    }
  }
}
