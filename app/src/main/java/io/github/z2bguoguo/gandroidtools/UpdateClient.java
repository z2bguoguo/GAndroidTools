// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import androidx.core.content.FileProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Automatic checks, explicit download consent, pinned APK identity and Android's installer. */
public final class UpdateClient {
  private static final AtomicBoolean busy = new AtomicBoolean();
  private static volatile File pending;
  private static volatile UpdatePolicy.Release pendingRelease;

  private UpdateClient() {}

  public static void check(Activity activity, boolean manual) {
    ToolApp app = ToolApp.app;
    long last;
    try {
      last = Long.parseLong(app.config.text("updateLastCheck", "0"));
    } catch (NumberFormatException ignored) {
      last = 0;
    }
    if (!manual
        && (!app.config.flag("autoUpdate", true)
            || System.currentTimeMillis() - last < app.config.number("updateHours") * 3_600_000L))
      return;
    if (!busy.compareAndSet(false, true)) {
      if (manual) Ui.toast(activity, ToolApp.t("正在检查或下载更新", "An update operation is running"));
      return;
    }
    app.worker.execute(
        () -> {
          try {
            HttpURLConnection connection = open(UpdatePolicy.BASE + "update.json");
            byte[] bytes;
            try (InputStream input = connection.getInputStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
              byte[] buffer = new byte[4096];
              int count;
              while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 32_768)
                  throw new IllegalStateException("Oversized update metadata");
                output.write(buffer, 0, count);
              }
              bytes = output.toByteArray();
            } finally {
              connection.disconnect();
            }
            UpdatePolicy.Release release =
                UpdatePolicy.parse(
                    new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
            app.config.set("updateLastCheck", Long.toString(System.currentTimeMillis()));
            app.log.add("INFO", "update", "Checked published version " + release.versionName());
            app.main.post(
                () -> {
                  if (activity.isFinishing() || activity.isDestroyed()) return;
                  if (release.versionCode() <= BuildConfig.VERSION_CODE) {
                    if (manual) Ui.toast(activity, ToolApp.t("当前已是最新版本", "You are up to date"));
                  } else
                    new MaterialAlertDialogBuilder(activity)
                        .setTitle(ToolApp.t("发现新版本 ", "Update available: ") + release.versionName())
                        .setMessage(
                            ToolApp.t(
                                    "下载后会校验文件、版本和原签名，再交由安卓确认安装。大小：",
                                    "The file, version and original signing certificate are"
                                        + " verified before Android asks to install. Size: ")
                                + String.format(
                                    java.util.Locale.ROOT, "%.1f MB", release.size() / 1048576.0))
                        .setNegativeButton(ToolApp.t("稍后", "Later"), null)
                        .setPositiveButton(
                            ToolApp.t("下载更新", "Download update"),
                            (dialog, which) -> download(activity, release))
                        .show();
                });
          } catch (Exception error) {
            app.log.add("WARN", "update", ErrorDetails.message(error));
            if (manual)
              Ui.toast(
                  activity,
                  ToolApp.t(
                      "暂时无法获取更新，请查看日志或稍后重试", "Cannot check updates; inspect logs or retry later"));
          } finally {
            busy.set(false);
          }
        });
  }

  private static HttpURLConnection open(String url) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(15_000);
    connection.setReadTimeout(15_000);
    connection.setInstanceFollowRedirects(false);
    connection.setRequestProperty("Cache-Control", "no-cache");
    connection.setRequestProperty("User-Agent", "GAndroidTools/" + BuildConfig.VERSION_NAME);
    int status = connection.getResponseCode();
    if (status != 200) {
      connection.disconnect();
      throw new IllegalStateException("Update HTTP status " + status);
    }
    return connection;
  }

  private static void download(Activity activity, UpdatePolicy.Release release) {
    if (!busy.compareAndSet(false, true)) return;
    LinearLayout layout = Ui.column(activity);
    layout.setPadding(
        Ui.dp(activity, 24), Ui.dp(activity, 16), Ui.dp(activity, 24), Ui.dp(activity, 16));
    ProgressBar progress =
        new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100);
    layout.addView(progress);
    AtomicBoolean cancelled = new AtomicBoolean();
    androidx.appcompat.app.AlertDialog dialog =
        new MaterialAlertDialogBuilder(activity)
            .setTitle(ToolApp.t("正在下载并验证", "Downloading and verifying"))
            .setView(layout)
            .setNegativeButton(ToolApp.t("取消", "Cancel"), (d, w) -> cancelled.set(true))
            .create();
    dialog.setOnCancelListener(d -> cancelled.set(true));
    dialog.show();
    ToolApp.app.worker.execute(
        () -> {
          File directory = new File(activity.getCacheDir(), "updates");
          File file = new File(directory, "update.apk.part");
          boolean retained = false;
          try {
            if (!directory.isDirectory() && !directory.mkdirs())
              throw new IllegalStateException("Cannot create update cache");
            File[] old = directory.listFiles((dir, name) -> name.endsWith(".apk"));
            if (old != null) {
              java.util.Arrays.sort(
                  old, java.util.Comparator.comparingLong(File::lastModified).reversed());
              for (int i = 1; i < old.length; i++) old[i].delete();
            }
            HttpURLConnection connection = open(release.url());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0, deadline = android.os.SystemClock.elapsedRealtime() + 10 * 60_000L;
            try (InputStream input = connection.getInputStream();
                FileOutputStream output = new FileOutputStream(file)) {
              byte[] buffer = new byte[65_536];
              int count, previousPercent = -1;
              while ((count = input.read(buffer)) != -1) {
                if (cancelled.get() || android.os.SystemClock.elapsedRealtime() > deadline)
                  throw new InterruptedException("Update download cancelled or timed out");
                total += count;
                if (total > release.size() || total > UpdatePolicy.MAX_BYTES)
                  throw new SecurityException("Update exceeds its declared size");
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
                int percent = (int) (total * 100 / release.size());
                if (percent != previousPercent) {
                  previousPercent = percent;
                  ToolApp.app.main.post(() -> progress.setProgress(percent));
                }
              }
            } finally {
              connection.disconnect();
            }
            if (cancelled.get()) throw new InterruptedException("Update cancelled");
            if (total != release.size() || !hex(digest.digest()).equals(release.sha256()))
              throw new SecurityException("Update checksum mismatch");
            verify(activity, file, release);
            File ready = new File(directory, "update-" + release.versionCode() + ".apk");
            if (ready.exists() && !ready.delete())
              throw new IllegalStateException("Cannot replace cached update");
            if (!file.renameTo(ready)) throw new IllegalStateException("Cannot finalize update");
            pending = ready;
            pendingRelease = release;
            retained = true;
            ToolApp.app.main.post(
                () -> {
                  if (!activity.isFinishing() && !activity.isDestroyed()) installPending(activity);
                });
          } catch (Exception error) {
            ToolApp.app.log.add("WARN", "update.download", ErrorDetails.message(error));
            Ui.toast(
                activity,
                ToolApp.t("更新未安装：", "Update not installed: ") + ErrorDetails.message(error));
          } finally {
            if (!retained) file.delete();
            busy.set(false);
            ToolApp.app.main.post(
                () -> {
                  if (!activity.isDestroyed()) dialog.dismiss();
                });
          }
        });
  }

  private static void verify(Activity activity, File file, UpdatePolicy.Release release)
      throws Exception {
    PackageInfo info =
        activity
            .getPackageManager()
            .getPackageArchiveInfo(
                file.toString(),
                Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES);
    if (info == null) throw new SecurityException("Unreadable APK");
    Signature[] signatures =
        Build.VERSION.SDK_INT >= 28
            ? info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners()
            : info.signatures;
    if (signatures == null || signatures.length != 1)
      throw new SecurityException("Unexpected APK signers");
    String expected;
    try (InputStream input = activity.getAssets().open("signing-certificate.sha256")) {
      byte[] buffer = new byte[256];
      int count = input.read(buffer);
      expected =
          new String(buffer, 0, Math.max(0, count), java.nio.charset.StandardCharsets.UTF_8).trim();
    }
    UpdatePolicy.validateIdentity(
        release,
        info.packageName,
        Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode,
        info.versionName,
        hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())),
        expected,
        BuildConfig.VERSION_CODE);
  }

  public static void installPending(Activity activity) {
    File file = pending;
    UpdatePolicy.Release release = pendingRelease;
    if (file == null || release == null || activity.isFinishing() || activity.isDestroyed()) return;
    try {
      if (!activity.getPackageManager().canRequestPackageInstalls()) {
        new MaterialAlertDialogBuilder(activity)
            .setTitle(ToolApp.t("允许安装更新", "Allow update installation"))
            .setMessage(
                ToolApp.t(
                    "请在安卓设置中允许本应用安装更新；授权返回后会继续验证并打开安装器。",
                    "Allow this app to install updates in Android settings. Returning continues"
                        + " verification and opens the installer."))
            .setNegativeButton(
                ToolApp.t("稍后", "Later"),
                (d, w) -> {
                  pending = null;
                  pendingRelease = null;
                })
            .setPositiveButton(
                ToolApp.t("打开设置", "Open settings"),
                (d, w) ->
                    activity.startActivity(
                        new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()))))
            .show();
        return;
      }
      verify(activity, file, release);
      pending = null;
      pendingRelease = null;
      Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", file);
      activity.startActivity(
          new Intent(Intent.ACTION_VIEW)
              .setDataAndType(uri, "application/vnd.android.package-archive")
              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    } catch (Exception error) {
      pending = null;
      pendingRelease = null;
      ToolApp.app.log.error("update.install", error);
      Ui.toast(activity, error.getMessage());
    }
  }

  static String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder();
    for (byte value : bytes)
      result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
    return result.toString();
  }
}
