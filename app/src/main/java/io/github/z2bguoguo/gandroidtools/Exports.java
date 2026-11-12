package io.github.z2bguoguo.gandroidtools;

import android.content.Context;
import android.content.Intent;
import androidx.core.content.FileProvider;
import java.io.File;

public final class Exports {
  private Exports() {}

  public static File directory() {
    File f = new File(ToolApp.app.getFilesDir(), "exports");
    f.mkdirs();
    return f;
  }

  public static File file(String name) {
    if (!name.matches("[a-zA-Z0-9_.-]+"))
      throw new IllegalArgumentException("Invalid export filename");
    return new File(directory(), name);
  }

  public static void share(Context context, File file) {
    android.net.Uri uri =
        FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".files", file);
    Intent i =
        new Intent(Intent.ACTION_SEND)
            .setType(
                file.getName().endsWith(".png")
                    ? "image/png"
                    : file.getName().endsWith(".jpg") ? "image/jpeg" : "application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    context.startActivity(
        Intent.createChooser(i, ToolApp.t("导出文件", "Export file"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }
}
