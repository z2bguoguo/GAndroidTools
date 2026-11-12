// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;
import android.os.ParcelFileDescriptor;
interface IRemoteTools {
    String call(String request) = 1;
    ParcelFileDescriptor screenshot(int displayId) = 2;
    void destroy() = 16777114;
}
