package io.github.z2bguoguo.gandroidtools;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class GallerySaver {
  private GallerySaver() {}

  public static boolean permitted(Context context) {
    return Build.VERSION.SDK_INT >= 29
        || context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;
  }

  public static Uri save(Context context, File source) throws IOException {
    if (!source.isFile() || source.length() == 0) throw new IOException("Export image is missing");
    String mime = source.getName().endsWith(".jpg") ? "image/jpeg" : "image/png";
    if (Build.VERSION.SDK_INT >= 29) {
      ContentValues values = new ContentValues();
      values.put(MediaStore.Images.Media.DISPLAY_NAME, source.getName());
      values.put(MediaStore.Images.Media.MIME_TYPE, mime);
      values.put(
          MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GAndroidTools");
      values.put(MediaStore.Images.Media.IS_PENDING, 1);
      Uri uri =
          context
              .getContentResolver()
              .insert(
                  MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                  values);
      if (uri == null) throw new IOException("Gallery refused a new image");
      try {
        try (InputStream in = new FileInputStream(source);
            OutputStream out = context.getContentResolver().openOutputStream(uri)) {
          if (out == null) throw new IOException("Gallery output is unavailable");
          copy(in, out);
        }
        ContentValues ready = new ContentValues();
        ready.put(MediaStore.Images.Media.IS_PENDING, 0);
        if (context.getContentResolver().update(uri, ready, null, null) != 1)
          throw new IOException("Gallery could not publish the image");
        return uri;
      } catch (IOException | RuntimeException failure) {
        try {
          context.getContentResolver().delete(uri, null, null);
        } catch (RuntimeException cleanup) {
          failure.addSuppressed(cleanup);
        }
        throw failure;
      }
    }
    if (!permitted(context))
      throw new SecurityException(
          ToolApp.t(
              "Android 8/9 需要在设置中允许相册写入；应用内导出已保留",
              "Android 8/9 needs storage write permission; the internal export is retained"));
    File directory =
        new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "GAndroidTools");
    if (!directory.isDirectory() && !directory.mkdirs())
      throw new IOException("Cannot create gallery folder");
    File target = new File(directory, source.getName());
    if (target.exists()) throw new IOException("Gallery filename already exists");
    try (InputStream in = new FileInputStream(source);
        OutputStream out = new FileOutputStream(target)) {
      copy(in, out);
    } catch (IOException failure) {
      if (target.exists() && !target.delete())
        failure.addSuppressed(new IOException("Cannot remove partial gallery image"));
      throw failure;
    }
    android.media.MediaScannerConnection.scanFile(
        context, new String[] {target.getAbsolutePath()}, new String[] {mime}, null);
    return Uri.fromFile(target);
  }

  private static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[65536];
    int n;
    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    out.flush();
  }
}
