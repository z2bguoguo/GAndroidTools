// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;

public class GalleryTest {
  @Test
  public void publishesReadableImageAndKeepsInternalExport() throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    boolean originallyAllowed = GallerySaver.permitted(context);
    if (!originallyAllowed)
      try (ParcelFileDescriptor fd =
              InstrumentationRegistry.getInstrumentation()
                  .getUiAutomation()
                  .executeShellCommand(
                      "pm grant "
                          + context.getPackageName()
                          + " android.permission.WRITE_EXTERNAL_STORAGE");
          ParcelFileDescriptor.AutoCloseInputStream in =
              new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
        byte[] buffer = new byte[1024];
        while (in.read(buffer) != -1) {}
      }
    File source = new File(context.getCacheDir(), "gallery-test-" + System.nanoTime() + ".png");
    Bitmap bitmap = Bitmap.createBitmap(8, 12, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(0xff407f75);
    try (FileOutputStream out = new FileOutputStream(source)) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
    Uri saved = null;
    try {
      saved = GallerySaver.save(context, source);
      assertTrue(source.isFile());
      try (java.io.InputStream in = context.getContentResolver().openInputStream(saved)) {
        Bitmap result = BitmapFactory.decodeStream(in);
        assertNotNull(result);
        assertEquals(8, result.getWidth());
        assertEquals(12, result.getHeight());
        result.recycle();
      }
      if (android.os.Build.VERSION.SDK_INT >= 29)
        try (android.database.Cursor cursor =
            context
                .getContentResolver()
                .query(
                    saved,
                    new String[] {
                      android.provider.MediaStore.Images.Media.IS_PENDING,
                      android.provider.MediaStore.Images.Media.RELATIVE_PATH
                    },
                    null,
                    null,
                    null)) {
          assertNotNull(cursor);
          assertTrue(cursor.moveToFirst());
          assertEquals(0, cursor.getInt(0));
          assertEquals("Pictures/GAndroidTools/", cursor.getString(1));
        }
    } finally {
      source.delete();
      if (saved != null) {
        if ("content".equals(saved.getScheme()))
          context.getContentResolver().delete(saved, null, null);
        else {
          File target = new File(saved.getPath());
          target.delete();
          android.media.MediaScannerConnection.scanFile(
              context, new String[] {target.getAbsolutePath()}, null, null);
        }
      }
    }
  }
}
