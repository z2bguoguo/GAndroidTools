// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;

public final class ControlProbeActivity extends androidx.fragment.app.FragmentActivity {
  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    LinearLayout root = Ui.column(this);
    root.setBackgroundColor(android.graphics.Color.WHITE);
    EditText publicInput = new EditText(this);
    publicInput.setId(android.R.id.edit);
    publicInput.setText("public fixture");
    root.addView(publicInput);
    EditText secret = new EditText(this);
    secret.setId(android.R.id.custom);
    secret.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    secret.setText("excluded-password-fixture");
    root.addView(secret);
    setContentView(root);
  }
}
