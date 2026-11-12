// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONObject;

public final class CaptureSession {
  private final ToolApp app;
  private final OverlayService overlay;
  private final ExecutorService serial = Executors.newSingleThreadExecutor();
  private final AtomicBoolean active = new AtomicBoolean();
  private final AtomicBoolean autoRunning = new AtomicBoolean();
  private volatile boolean cancelled;
  private final List<File> frames = new ArrayList<>();
  private final List<TreeNode> include = new ArrayList<>(), exclude = new ArrayList<>();
  private File directory;
  private JSONObject firstTree;

  public CaptureSession(OverlayService overlay) {
    this.overlay = overlay;
    app = ToolApp.app;
  }

  public boolean active() {
    return active.get();
  }

  public synchronized void rules(List<TreeNode> selected, boolean ignored) {
    List<TreeNode> target = ignored ? exclude : include;
    target.clear();
    target.addAll(selected);
  }

  public synchronized List<String> ruleKeys(boolean ignored) {
    List<String> out = new ArrayList<>();
    for (TreeNode n : ignored ? exclude : include) out.add(n.path);
    return out;
  }

  public void start() {
    if (active.get()) return;
    cancelled = false;
    active.set(true);
    serial.execute(
        () -> {
          try {
            directory = new File(app.getCacheDir(), "capture-" + System.currentTimeMillis());
            if (!directory.mkdirs())
              throw new IllegalStateException("Cannot create frame directory");
            frames.clear();
            firstTree = null;
            Ui.toast(app, ToolApp.t("截图会话已启动，使用工具栏采集", "Capture session started; use the toolbar"));
          } catch (Exception e) {
            active.set(false);
            app.log.error("capture", e);
          }
        });
  }

  public void frame() {
    if (!active()) start();
    serial.execute(
        () -> {
          try {
            capture();
          } catch (Exception e) {
            fail(e);
          }
        });
  }

  public void automatic() {
    if (!active()) start();
    if (!autoRunning.compareAndSet(false, true)) return;
    serial.execute(
        () -> {
          try {
            int[] previous = null;
            int repeated = 0;
            while (active() && !cancelled && frames.size() < app.config.number("maxFrames")) {
              int[] now = capture();
              if (previous != null && OverlapMatcher.same(previous, now)) repeated++;
              else repeated = 0;
              if (repeated >= 2) break;
              previous = now;
              String mode = app.config.text("captureMode", "page");
              if (mode.equals("manual")) break;
              scroll(mode);
              pause(app.config.number("settle"));
            }
            app.log.add(
                "INFO", "capture", "Automatic capture paused · " + frames.size() + " frames");
            Ui.toast(app, ToolApp.t("采集暂停，可继续或导出", "Capture paused; continue or export"));
          } catch (Exception e) {
            if (!cancelled) fail(e);
          } finally {
            autoRunning.set(false);
          }
        });
  }

  public void pause() {
    cancelled = true;
    serial.execute(() -> cancelled = false);
  }

  private void scroll(String mode) throws Exception {
    List<TreeNode> nodes = List.of();
    if (mode.equals("page"))
      try {
        nodes = TreeNode.parse(app.bridge.call("tree"));
      } catch (Exception error) {
        app.log.add(
            "WARN",
            "capture.scroll",
            "Control scroll unavailable; using a screen gesture: " + ErrorDetails.message(error));
      }
    if (mode.equals("page"))
      for (TreeNode n : nodes)
        if (n.scrollable
            && app.bridge
                .call("node", new JSONObject().put("key", n.key).put("operation", "scroll"))
                .optBoolean("performed")) return;
    android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
    int start = dm.heightPixels * 4 / 5;
    int end =
        mode.equals("smooth")
            ? dm.heightPixels * 2 / 5
            : dm.heightPixels * (app.config.number("overlap")) / 100;
    app.bridge.call(
        "swipe",
        new JSONObject()
            .put("x1", dm.widthPixels / 2)
            .put("y1", start)
            .put("x2", dm.widthPixels / 2)
            .put("y2", end)
            .put("duration", mode.equals("smooth") ? app.config.number("duration") : 100));
  }

  private int[] capture() throws Exception {
    if (cancelled || !active()) throw new InterruptedException("Capture cancelled");
    if (frames.size() >= app.config.number("maxFrames"))
      throw new IllegalStateException("Frame limit reached");
    overlay.hideForCapture(true);
    Bitmap raw = null, bitmap = null;
    try {
      pause(150);
      JSONObject tree = app.bridge.call("freeze");
      raw = app.bridge.screenshot(-tree.getInt("captureId"));
      if (!tree.optBoolean("treeAvailable", true)) {
        synchronized (this) {
          if (!include.isEmpty() || !exclude.isEmpty())
            throw new IllegalStateException(
                "Control-based capture rules require a valid control tree; capture stopped without"
                    + " exporting an unmasked frame");
        }
        if (firstTree == null) {
          app.log.add("WARN", "capture.controls", tree.optString("treeError"));
          Ui.toast(
              app,
              ToolApp.t(
                  "控件树暂不可用：使用整屏截图与手势滚动",
                  "Controls unavailable: using full-screen capture and gesture scrolling"));
        }
      }
      if (firstTree == null) firstTree = tree;
      app.log.add("DEBUG", "capture.backend", tree.optString("captureBackend"));
      List<TreeNode> nodes = TreeNode.parse(tree);
      Rect region = new Rect(0, 0, raw.getWidth(), raw.getHeight());
      synchronized (this) {
        if (!include.isEmpty()) {
          region.setEmpty();
          for (TreeNode rule : include)
            for (TreeNode n : nodes) if (matches(rule, n)) region.union(n.bounds);
          if (region.isEmpty())
            throw new IllegalStateException("Selected control is absent; refresh capture rules");
        }
      }
      if (!region.intersect(0, 0, raw.getWidth(), raw.getHeight()))
        throw new IllegalArgumentException("Selected region is outside the screenshot");
      region.top += app.config.number("topCrop");
      region.bottom -= app.config.number("bottomCrop");
      if (region.width() < 16 || region.height() < 32)
        throw new IllegalArgumentException("Capture crop is too small");
      bitmap = Bitmap.createBitmap(region.width(), region.height(), Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      canvas.drawColor(Color.WHITE);
      canvas.drawBitmap(raw, region, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()), null);
      android.graphics.Paint paint = new android.graphics.Paint();
      paint.setColor(Color.WHITE);
      synchronized (this) {
        for (TreeNode rule : exclude)
          for (TreeNode n : nodes)
            if (matches(rule, n)) {
              Rect r = new Rect(n.bounds);
              r.offset(-region.left, -region.top);
              canvas.drawRect(r, paint);
            }
      }
      File frame = new File(directory, "frame-" + frames.size() + ".png");
      try (FileOutputStream out = new FileOutputStream(frame)) {
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
          throw new IllegalStateException("Frame encoding failed");
      }
      frames.add(frame);
      int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
      bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
      app.log.add(
          "INFO",
          "capture",
          "Frame " + frames.size() + " · " + bitmap.getWidth() + " × " + bitmap.getHeight());
      return pixels;
    } finally {
      if (raw != null) raw.recycle();
      if (bitmap != null) bitmap.recycle();
      overlay.hideForCapture(false);
    }
  }

  private static boolean matches(TreeNode rule, TreeNode node) {
    return !rule.id.isEmpty()
        ? rule.id.equals(node.id) && rule.className.equals(node.className)
        : rule.path.equals(node.path) && rule.className.equals(node.className);
  }

  private void pause(int ms) throws InterruptedException {
    long end = android.os.SystemClock.elapsedRealtime() + ms;
    while (android.os.SystemClock.elapsedRealtime() < end) {
      if (cancelled || !active()) throw new InterruptedException("Capture cancelled");
      Thread.sleep(30);
    }
  }

  public void finish() {
    cancelled = true;
    serial.execute(
        () -> {
          cancelled = false;
          try {
            if (frames.isEmpty()) throw new IllegalStateException("Capture at least one frame");
            List<File> files = stitch();
            int saved = 0;
            boolean toGallery = app.config.flag("saveGallery", true);
            if (toGallery)
              for (File file : files)
                try {
                  GallerySaver.save(app, file);
                  saved++;
                  app.log.add("INFO", "gallery", "Long capture saved to Pictures/GAndroidTools");
                } catch (Exception error) {
                  app.log.error("gallery", error);
                }
            Ui.toast(
                app,
                toGallery
                    ? ToolApp.t("已导出 ", "Exported ")
                        + files.size()
                        + ToolApp.t(" 张，相册已保存 ", " image(s); gallery saved ")
                        + saved
                        + ToolApp.t(" 张。", " image(s).")
                    : ToolApp.t(
                        "截图已导出，可在首页导出文件中分享", "Capture exported; share from Home → Exports"));
            app.main.post(() -> overlay.showFiles(files));
          } catch (Exception e) {
            fail(e);
          } finally {
            active.set(false);
            cleanup();
          }
        });
  }

  public void cancel() {
    cancelled = true;
    active.set(false);
    serial.execute(this::cleanup);
  }

  public void close() {
    cancel();
    serial.shutdown();
  }

  private void cleanup() {
    if (directory != null) {
      File[] list = directory.listFiles();
      if (list != null) for (File f : list) f.delete();
      directory.delete();
    }
    frames.clear();
  }

  private List<File> stitch() throws Exception {
    List<File> out = new ArrayList<>();
    Bitmap first = BitmapFactory.decodeFile(frames.get(0).toString());
    if (first == null) throw new IllegalStateException("Cannot decode frame");
    int width = first.getWidth(), height = first.getHeight();
    int cap =
        Math.min(
            app.config.number("maxHeight"),
            Math.max(height, (int) (48L * 1024 * 1024 / 4 / width)));
    Bitmap sheet = Bitmap.createBitmap(width, cap, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(sheet);
    int filled = 0;
    int[] previous = null;
    try {
      for (File f : frames) {
        Bitmap b = f.equals(frames.get(0)) ? first : BitmapFactory.decodeFile(f.toString());
        if (b == null) throw new IllegalStateException("Cannot decode frame");
        try {
          if (b.getWidth() != width || b.getHeight() != height)
            throw new IllegalStateException("Screen size changed; start a new session");
          int[] pixels = new int[width * height];
          b.getPixels(pixels, 0, width, 0, 0, width, height);
          int overlap = previous == null ? 0 : OverlapMatcher.find(width, height, previous, pixels);
          if (previous != null && OverlapMatcher.same(previous, pixels)) continue;
          if (overlap < 0) {
            if (filled > 0) {
              out.add(saveSheet(sheet, filled, out.size()));
              filled = 0;
            }
            overlap = 0;
            app.log.add("WARN", "capture", "No reliable overlap; starting a separate image");
          }
          int source = overlap;
          while (source < height) {
            int amount = Math.min(height - source, cap - filled);
            canvas.drawBitmap(
                b,
                new Rect(0, source, width, source + amount),
                new Rect(0, filled, width, filled + amount),
                null);
            filled += amount;
            source += amount;
            if (filled == cap) {
              out.add(saveSheet(sheet, filled, out.size()));
              filled = 0;
            }
          }
          previous = pixels;
        } finally {
          b.recycle();
        }
      }
      if (filled > 0) out.add(saveSheet(sheet, filled, out.size()));
      return out;
    } finally {
      sheet.recycle();
      if (!first.isRecycled()) first.recycle();
    }
  }

  private File saveSheet(Bitmap sheet, int height, int index) throws Exception {
    Bitmap b = Bitmap.createBitmap(sheet, 0, 0, sheet.getWidth(), height);
    boolean jpeg = app.config.flag("jpeg", false);
    File f =
        Exports.file("long-" + System.currentTimeMillis() + "-" + index + (jpeg ? ".jpg" : ".png"));
    try (FileOutputStream stream = new FileOutputStream(f)) {
      if (!b.compress(
          jpeg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG,
          app.config.number("jpegQuality"),
          stream)) throw new IllegalStateException("Image export failed");
    } finally {
      if (b != sheet) b.recycle();
    }
    return f;
  }

  public void snapshot() {
    app.worker.execute(
        () -> {
          overlay.hideForCapture(true);
          Bitmap b = null;
          try {
            Thread.sleep(150);
            JSONObject tree =
                app.bridge.call(
                    "tree",
                    new JSONObject().put("allWindows", app.config.flag("allWindows", false)));
            b = app.bridge.screenshot(0);
            File file = Exports.file("page-" + System.currentTimeMillis() + ".gatpage");
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
              textEntry(
                  out,
                  "manifest.json",
                  new JSONObject()
                      .put("format", "gandroidtools.page")
                      .put("version", 1)
                      .put("created", java.time.Instant.now().toString())
                      .put("screenWidth", b.getWidth())
                      .put("screenHeight", b.getHeight())
                      .put("image", "screen.png")
                      .put("controls", "controls.json")
                      .put("passwordTextExcluded", true)
                      .toString(2));
              textEntry(out, "controls.json", tree.toString(2));
              out.putNextEntry(new ZipEntry("screen.png"));
              b.compress(Bitmap.CompressFormat.PNG, 100, out);
              out.closeEntry();
            }
            app.log.add("INFO", "snapshot", "Page archive saved");
            app.main.post(() -> overlay.showFiles(List.of(file)));
          } catch (Exception e) {
            fail(e);
          } finally {
            if (b != null) b.recycle();
            overlay.hideForCapture(false);
          }
        });
  }

  private static void textEntry(ZipOutputStream out, String path, String text) throws Exception {
    out.putNextEntry(new ZipEntry(path));
    out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    out.closeEntry();
  }

  private void fail(Exception e) {
    app.log.error("capture", e);
    Ui.toast(app, e.getMessage());
  }
}
