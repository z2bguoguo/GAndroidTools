package io.github.z2bguoguo.gandroidtools;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.crypto.Cipher;

public final class AuthActivity extends FragmentActivity {
  private record Request(Cipher cipher, Consumer<Cipher> success) {}

  private static final ConcurrentHashMap<String, Request> requests = new ConcurrentHashMap<>();

  public static void authorize(Context context, Cipher cipher, Consumer<Cipher> success) {
    String id = UUID.randomUUID().toString();
    requests.put(id, new Request(cipher, success));
    ToolApp.app.main.postDelayed(() -> requests.remove(id), 60000);
    context.startActivity(
        new Intent(context, AuthActivity.class)
            .putExtra("request", id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
    Request request = requests.remove(getIntent().getStringExtra("request"));
    if (request == null) {
      finish();
      return;
    }
    if (BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        != BiometricManager.BIOMETRIC_SUCCESS) {
      Ui.toast(
          this,
          ToolApp.t("请先录入系统强生物识别（指纹等）", "Enroll a strong biometric such as fingerprint first"));
      finish();
      return;
    }
    BiometricPrompt prompt =
        new BiometricPrompt(
            this,
            androidx.core.content.ContextCompat.getMainExecutor(this),
            new BiometricPrompt.AuthenticationCallback() {
              @Override
              public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                finish();
                if (result.getCryptoObject() != null)
                  request.success.accept(result.getCryptoObject().getCipher());
              }

              @Override
              public void onAuthenticationError(int code, CharSequence message) {
                Ui.toast(AuthActivity.this, message.toString());
                finish();
              }
            });
    prompt.authenticate(
        new BiometricPrompt.PromptInfo.Builder()
            .setTitle(ToolApp.t("验证后使用隐私信息", "Authenticate to use private information"))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(ToolApp.t("取消", "Cancel"))
            .build(),
        new BiometricPrompt.CryptoObject(request.cipher));
  }
}
