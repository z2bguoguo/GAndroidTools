// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Two disk-backed passes: measure motion first, then repair and append only new document rows. */
final class LongComposer {
  record Result(List<File> images, File history, long repaired, long unknown) {}

  private static final class Plan {
    final File file, metadata;
    final List<ScrollAnalysis.Anchor> anchors;
    final List<ScrollAnalysis.Box> masks = new ArrayList<>();
    final List<ScrollAnalysis.Box> manualMasks = new ArrayList<>();
    final int left, top;
    long origin;
    int group, header, footer, shift;
    boolean duplicate;

    Plan(File file, JSONObject json) {
      this.file = file;
      metadata = new File(file.getParentFile(), file.getName() + ".json");
      JSONArray region = json.optJSONArray("region");
      left = region == null ? 0 : region.optInt(0);
      top = region == null ? 0 : region.optInt(1);
      anchors = new ArrayList<>();
      List<TreeNode> treeNodes = TreeNode.parse(json);
      TreeNode viewport = null;
      for (TreeNode n : treeNodes)
        if (n.scrollable
            && (viewport == null
                || (long) n.bounds.width() * n.bounds.height()
                    > (long) viewport.bounds.width() * viewport.bounds.height())) viewport = n;
      for (TreeNode node : treeNodes) {
        String identity =
            !node.id.isBlank()
                ? node.id + "|" + node.className
                : (node.text.isBlank() ? node.description : node.text) + "|" + node.className;
        if (node == viewport) identity = "scroll-viewport:" + node.className + "|" + node.id;
        Rect b = node.bounds;
        if (region != null
            && (b.right <= region.optInt(0)
                || b.bottom <= region.optInt(1)
                || b.left >= region.optInt(2)
                || b.top >= region.optInt(3))) continue;
        if (b.width() < 2 || b.height() < 2 || identity.equals("|" + node.className)) continue;
        boolean container =
            node.scrollable
                || node.className.contains("Layout")
                || node.className.endsWith("ViewGroup")
                || node.className.endsWith("WebView")
                || node.className.endsWith("RecyclerView");
        anchors.add(
            new ScrollAnalysis.Anchor(
                identity,
                new ScrollAnalysis.Box(b.left - left, b.top - top, b.right - left, b.bottom - top),
                container));
      }
      anchors.sort(
          Comparator.comparingInt(
                  (ScrollAnalysis.Anchor a) -> a.identity().startsWith("scroll-viewport:") ? 0 : 1)
              .thenComparingLong(a -> a.bounds().area()));
      if (anchors.size() > 256) anchors.subList(256, anchors.size()).clear();
      JSONArray excluded = json.optJSONArray("excludedBounds");
      if (excluded != null)
        for (int i = 0; i < excluded.length(); i++) {
          JSONArray b = excluded.optJSONArray(i);
          if (b != null)
            masks.add(new ScrollAnalysis.Box(b.optInt(0), b.optInt(1), b.optInt(2), b.optInt(3)));
        }
      manualMasks.addAll(masks);
    }
  }

  private final Config config;
  private final BooleanSupplier cancelled;
  private final List<Plan> plans = new ArrayList<>();
  private final List<File> images = new ArrayList<>();
  private final JSONArray reportFrames = new JSONArray();
  private int width, height, filled;
  private Bitmap sheet;
  private Canvas canvas;
  private final Paint replace = new Paint();
  private boolean transparent;
  private long repaired, unknown;
  private final String name = "long-" + System.currentTimeMillis();

  LongComposer(Config config, BooleanSupplier cancelled, boolean transparent) {
    this.config = config;
    this.cancelled = cancelled;
    this.transparent = transparent;
    replace.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
  }

  Result compose(List<File> frames) throws Exception {
    File archive = config.flag("longArchive", true) ? Exports.file(name + ".gatlong") : null;
    File partial = archive == null ? null : new File(archive + ".part");
    boolean success = false;
    try {
      analyze(frames);
      int capacity =
          Math.min(
              config.number("maxHeight"), Math.max(height, (int) (48L * 1024 * 1024 / 4 / width)));
      sheet = Bitmap.createBitmap(width, capacity, Bitmap.Config.ARGB_8888);
      canvas = new Canvas(sheet);
      try (ZipOutputStream zip =
          partial == null ? null : new ZipOutputStream(new FileOutputStream(partial))) {
        for (int i = 0; i < plans.size(); i++) {
          check();
          Plan plan = plans.get(i);
          int[] pixels = read(plan.file);
          long before = repaired, missingBefore = unknown;
          repair(i, pixels);
          Bitmap processed = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
          try {
            if (!plan.duplicate) {
              int source =
                  i == 0 || plans.get(i - 1).group != plan.group
                      ? 0
                      : height - plan.footer - plan.shift;
              append(processed, source, height - plan.footer);
            }
            boolean last = i == plans.size() - 1 || plans.get(i + 1).group != plan.group;
            if (last) {
              append(processed, height - plan.footer, height);
              flush();
            }
            JSONObject descriptor =
                new JSONObject()
                    .put("frame", i)
                    .put("group", plan.group)
                    .put("documentOffsetY", plan.origin)
                    .put("shift", plan.shift)
                    .put("duplicate", plan.duplicate)
                    .put("fixedTop", plan.header)
                    .put("fixedBottom", plan.footer)
                    .put("repairedPixels", repaired - before)
                    .put("unobservedPixels", unknown - missingBefore)
                    .put("masks", boxes(plan.masks));
            reportFrames.put(descriptor);
            if (zip != null) {
              String path = "frames/frame-" + i + ".png";
              PageArchive.image(zip, path, processed);
              descriptor.put("image", path);
              JSONObject metadata = readMetadata(plan.file);
              redact(metadata, plan);
              PageArchive.text(zip, "frames/frame-" + i + ".json", metadata.toString(2));
            }
          } finally {
            processed.recycle();
          }
        }
        if (zip != null) {
          PageArchive.text(
              zip,
              "analysis.json",
              new JSONObject()
                  .put("frames", reportFrames)
                  .put("repairedPixels", repaired)
                  .put("unobservedPixels", unknown)
                  .put("fill", "observed-pixels-only")
                  .put("headerPolicy", "first-in-group")
                  .put("footerPolicy", "last-in-group")
                  .toString(2));
          PageArchive.text(
              zip,
              "manifest.json",
              new JSONObject()
                  .put("format", "gandroidtools.long-capture")
                  .put("version", 1)
                  .put("frameCount", plans.size())
                  .put("width", width)
                  .put("height", height)
                  .put("analysis", "analysis.json")
                  .put("rawOriginalsIncluded", false)
                  .put("windowImages", "only-the-aligned-clean-frame-is-retained")
                  .toString(2));
        }
      }
      if (archive != null && !partial.renameTo(archive))
        throw new IllegalStateException("Cannot publish capture history");
      success = true;
      return new Result(List.copyOf(images), archive, repaired, unknown);
    } finally {
      if (sheet != null) sheet.recycle();
      if (partial != null) partial.delete();
      if (!success) for (File image : images) image.delete();
    }
  }

  private void analyze(List<File> frames) throws Exception {
    int[] previous = null;
    int group = 0;
    for (int i = 0; i < frames.size(); i++) {
      check();
      File file = frames.get(i);
      Plan plan = new Plan(file, readMetadata(file));
      plans.add(plan);
      int[] next = read(file);
      plan.group = group;
      if (previous != null) {
        Plan old = plans.get(i - 1);
        if (OverlapMatcher.same(previous, next)) {
          plan.origin = old.origin;
          plan.duplicate = true;
        } else {
          ScrollAnalysis.Motion motion =
              ScrollAnalysis.compare(
                  width,
                  height,
                  previous,
                  next,
                  old.anchors,
                  plan.anchors,
                  config.flag("autoFixed", true));
          if (!motion.matched()) {
            plan.group = ++group;
            ToolApp.app.log.add(
                "WARN", "capture.alignment", "No unique overlap; starting a separate image group");
          } else {
            plan.shift = motion.shift();
            plan.origin = old.origin + motion.shift();
            plan.header = old.header == 0 ? motion.top() : Math.min(old.header, motion.top());
            plan.footer = old.footer == 0 ? motion.bottom() : Math.min(old.footer, motion.bottom());
            if (i == 1 || plans.get(i - 2).group != group) {
              old.header = motion.top();
              old.footer = motion.bottom();
            }
            for (ScrollAnalysis.Box box : motion.fixed()) {
              add(old.masks, box);
              add(plan.masks, box);
            }
          }
        }
      }
      previous = next;
    }
    for (int start = 0; start < plans.size(); ) {
      int end = start + 1, top = Integer.MAX_VALUE, bottom = Integer.MAX_VALUE;
      while (end < plans.size() && plans.get(end).group == plans.get(start).group) end++;
      for (int i = start; i < end; i++)
        if (plans.get(i).shift > 0) {
          top = Math.min(top, plans.get(i).header);
          bottom = Math.min(bottom, plans.get(i).footer);
        }
      if (top == Integer.MAX_VALUE) top = bottom = 0;
      for (int i = start; i < end; i++) {
        plans.get(i).header = top;
        plans.get(i).footer = bottom;
        final int t = top, b = bottom;
        Plan plan = plans.get(i);
        plan.masks.removeIf(
            box -> !plan.manualMasks.contains(box) && (box.top() < t || box.bottom() > height - b));
      }
      for (int i = start; i < end; i++)
        for (int j = i + 1; j < end; j++)
          if (plans.get(i).origin == plans.get(j).origin) {
            for (ScrollAnalysis.Box box : new ArrayList<>(plans.get(i).masks))
              add(plans.get(j).masks, box);
            for (ScrollAnalysis.Box box : new ArrayList<>(plans.get(j).masks))
              add(plans.get(i).masks, box);
          }
      start = end;
    }
  }

  private void repair(int index, int[] pixels) throws Exception {
    Plan target = plans.get(index);
    BitSet holes = mask(target.masks, target.header, height - target.footer);
    for (int at = holes.nextSetBit(0); at >= 0; at = holes.nextSetBit(at + 1)) pixels[at] = 0;
    if (config.flag("historyRepair", true) && config.flag("autoFixed", true) && !holes.isEmpty()) {
      int used = 0;
      for (int distance = 1;
          distance < plans.size() && used < config.number("historyFrames") && !holes.isEmpty();
          distance++) {
        for (int candidate : new int[] {index - distance, index + distance}) {
          if (candidate < 0 || candidate >= plans.size() || used >= config.number("historyFrames"))
            continue;
          Plan donor = plans.get(candidate);
          if (donor.group != target.group || donor.origin == target.origin) continue;
          check();
          used++;
          int[] source = read(donor.file);
          BitSet blocked = mask(donor.masks, 0, height);
          for (int at = holes.nextSetBit(0); at >= 0; ) {
            int following = holes.nextSetBit(at + 1);
            int x = at % width, y = at / width;
            long sy = y + target.origin - donor.origin;
            if (sy >= donor.header && sy < height - donor.footer) {
              int sample = (int) sy * width + x;
              if (!blocked.get(sample) && (source[sample] >>> 24) >= 220) {
                pixels[at] = source[sample];
                holes.clear(at);
                repaired++;
              }
            }
            at = following;
          }
        }
      }
    }
    unknown += holes.cardinality();
    if (!holes.isEmpty()) transparent = true;
  }

  private BitSet mask(List<ScrollAnalysis.Box> boxes, int top, int bottom) {
    BitSet result = new BitSet(width * height);
    for (ScrollAnalysis.Box box : boxes) {
      int left = Math.max(0, box.left()), right = Math.min(width, box.right());
      if (left >= right) continue;
      for (int y = Math.max(top, box.top()); y < Math.min(bottom, box.bottom()); y++)
        result.set(y * width + left, y * width + right);
    }
    return result;
  }

  private void append(Bitmap frame, int from, int to) throws Exception {
    for (int y = Math.max(0, from); y < to; ) {
      check();
      int count = Math.min(to - y, sheet.getHeight() - filled);
      canvas.drawBitmap(
          frame,
          new Rect(0, y, width, y + count),
          new Rect(0, filled, width, filled + count),
          replace);
      y += count;
      filled += count;
      if (filled == sheet.getHeight()) flush();
    }
  }

  private void flush() throws Exception {
    if (filled == 0) return;
    boolean jpeg = config.flag("jpeg", false) && !transparent;
    File file = Exports.file(name + "-" + images.size() + (jpeg ? ".jpg" : ".png"));
    Bitmap image = Bitmap.createBitmap(sheet, 0, 0, width, filled);
    try (FileOutputStream out = new FileOutputStream(file)) {
      if (!image.compress(
          jpeg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG,
          config.number("jpegQuality"),
          out)) throw new IllegalStateException("Cannot encode long capture");
    } finally {
      if (image != sheet) image.recycle();
    }
    images.add(file);
    filled = 0;
  }

  private int[] read(File file) throws Exception {
    Bitmap image = BitmapFactory.decodeFile(file.toString());
    if (image == null) throw new IllegalStateException("Cannot decode capture frame");
    try {
      if (width == 0) {
        width = image.getWidth();
        height = image.getHeight();
      }
      if (image.getWidth() != width || image.getHeight() != height)
        throw new IllegalStateException("Capture dimensions changed");
      int[] pixels = new int[width * height];
      image.getPixels(pixels, 0, width, 0, 0, width, height);
      return pixels;
    } finally {
      image.recycle();
    }
  }

  static JSONObject readMetadata(File frame) throws Exception {
    File file = new File(frame.getParentFile(), frame.getName() + ".json");
    return file.exists() ? new JSONObject(FileText.read(file.toPath())) : new JSONObject();
  }

  private static void add(List<ScrollAnalysis.Box> masks, ScrollAnalysis.Box box) {
    if (!masks.contains(box)) masks.add(box);
  }

  private static JSONArray boxes(List<ScrollAnalysis.Box> masks) {
    JSONArray result = new JSONArray();
    for (ScrollAnalysis.Box b : masks)
      result.put(new JSONArray().put(b.left()).put(b.top()).put(b.right()).put(b.bottom()));
    return result;
  }

  private void redact(JSONObject metadata, Plan plan) throws Exception {
    JSONArray nodes = metadata.optJSONArray("nodes");
    if (nodes != null)
      for (int i = 0; i < nodes.length(); i++) {
        JSONObject value = nodes.getJSONObject(i);
        TreeNode node = new TreeNode(value);
        for (ScrollAnalysis.Box b : plan.masks)
          if (Rect.intersects(
              node.bounds,
              new Rect(
                  b.left() + plan.left,
                  b.top() + plan.top,
                  b.right() + plan.left,
                  b.bottom() + plan.top))) {
            value.put("text", "").put("description", "");
            break;
          }
      }
    metadata.put("processedMasks", boxes(plan.masks)).put("rawOriginalIncluded", false);
    JSONObject layers = metadata.optJSONObject("windowLayers");
    if (layers != null) {
      JSONArray windows = layers.optJSONArray("windows");
      if (windows != null)
        for (int i = 0; i < windows.length(); i++)
          windows.getJSONObject(i).put("title", "").put("pixelsRetainedSeparately", false);
    }
  }

  private void check() throws InterruptedException {
    if (cancelled.getAsBoolean()) throw new InterruptedException("Capture export cancelled");
  }
}
