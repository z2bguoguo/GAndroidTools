// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Captured pixels and metadata stay frozen while the user edits visibility. */
public final class FrozenCapture {
  private final OverlayService overlay;
  private final android.content.Context context;
  private Bitmap original;
  private JSONObject metadata;
  private List<TreeNode> nodes = List.of();
  private VisibilityState selection;
  private boolean outlines = true;
  private CaptureSession rulesTarget;
  private boolean ignoreRules;
  private boolean[] ruleSelected, includeDraft, excludeDraft;
  private final Paint mask = new Paint();
  private final Paint stroke = new Paint(3);
  private final AtomicBoolean busy = new AtomicBoolean();
  private boolean disposed;

  public FrozenCapture(OverlayService overlay, android.content.Context context) {
    this.overlay = overlay;
    this.context = context;
  }

  public FrozenCapture(
      OverlayService overlay,
      android.content.Context context,
      CaptureSession target,
      boolean ignore) {
    this(overlay, context);
    rulesTarget = target;
    ignoreRules = ignore;
  }

  private boolean checked(int index) {
    return rulesTarget == null ? selection.visible(index) : ruleSelected[index];
  }

  private void loadRules() {
    if (includeDraft == null) {
      includeDraft = new boolean[nodes.size()];
      excludeDraft = new boolean[nodes.size()];
      List<String> included = rulesTarget.ruleKeys(false), excluded = rulesTarget.ruleKeys(true);
      for (int i = 0; i < nodes.size(); i++) {
        includeDraft[i] = included.contains(nodes.get(i).path);
        excludeDraft[i] = excluded.contains(nodes.get(i).path);
      }
    }
    ruleSelected = ignoreRules ? excludeDraft : includeDraft;
  }

  private void applyRules() {
    if (metadata == null || !metadata.optBoolean("treeAvailable", true)) return;
    List<TreeNode> included = new ArrayList<>(), excluded = new ArrayList<>();
    for (int i = 0; i < nodes.size(); i++) {
      if (includeDraft[i]) included.add(nodes.get(i));
      if (excludeDraft[i]) excluded.add(nodes.get(i));
    }
    rulesTarget.rules(included, false);
    rulesTarget.rules(excluded, true);
  }

  public void capture() {
    if (!busy.compareAndSet(false, true)) return;
    ToolApp.app.worker.execute(
        () -> {
          Bitmap shot = null;
          try {
            overlay.hideForCapture(true);
            Thread.sleep(150);
            JSONObject frozen = ToolApp.app.bridge.call("freeze");
            shot = ToolApp.app.bridge.screenshot(-frozen.getInt("captureId"));
            JSONObject tree =
                new JSONObject()
                    .put("nodes", frozen.getJSONArray("nodes"))
                    .put("capturedAt", frozen.optString("capturedAt"))
                    .put("treeAvailable", frozen.optBoolean("treeAvailable", true))
                    .put("treeError", frozen.optString("treeError"))
                    .put("captureBackend", frozen.optString("captureBackend"))
                    .put("controlBackend", frozen.optString("controlBackend"));
            ToolApp.app.log.add("INFO", "screenshot.backend", frozen.optString("captureBackend"));
            if (!frozen.optString("captureWarning").isEmpty())
              ToolApp.app.log.add(
                  "WARN", "screenshot.fallback", frozen.getString("captureWarning"));
            if (!tree.optBoolean("treeAvailable")) {
              ToolApp.app.log.add("WARN", "screenshot.controls", tree.getString("treeError"));
              Ui.toast(
                  context,
                  ToolApp.t(
                      "截图已保留；控件树暂不可用，详情见日志",
                      "Screenshot retained; controls unavailable. See logs."));
            }
            Bitmap result = shot;
            shot = null;
            ToolApp.app.main.post(
                () -> {
                  if (disposed) {
                    result.recycle();
                    return;
                  }
                  if (original != null) original.recycle();
                  original = result;
                  metadata = tree;
                  nodes = TreeNode.parse(tree);
                  selection =
                      new VisibilityState(
                          nodes.stream()
                              .map(n -> n.path)
                              .collect(java.util.stream.Collectors.toList()));
                  if (rulesTarget != null) loadRules();
                  render();
                });
          } catch (Exception e) {
            ToolApp.app.log.error("screenshot", e);
            Ui.toast(context, e.getMessage());
          } finally {
            if (shot != null) shot.recycle();
            overlay.hideForCapture(false);
            busy.set(false);
          }
        });
  }

  private void render() {
    LinearLayout root = Ui.column(context);
    root.setBackgroundColor(Ui.BG);
    root.setPadding(12, 12, 12, 12);
    TextView title =
        Ui.text(
            context,
            rulesTarget == null
                ? ToolApp.t("截图 · 已冻结画面与控件", "Screenshot · frozen image and controls")
                : ToolApp.t("长截图 · 轮廓选择", "Long capture · control outlines"),
            17,
            Ui.INK);
    root.addView(title);
    if (!metadata.optBoolean("treeAvailable", true)) {
      root.addView(
          Ui.body(
              context,
              ToolApp.t(
                  "控件树不可用：本次保留截图。请在首页启用控件兼容通道，或释放其他工具的连接后重试。",
                  "Controls unavailable: this capture contains pixels only. Enable the control"
                      + " fallback on Home, or release the other tool's connection and retry.")));
    }
    ControlPreview preview =
        new ControlPreview(
            context,
            original,
            nodes,
            rulesTarget == null,
            this::checked,
            this::toggle,
            indices -> tree(indices));
    preview.outlines = outlines;
    root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    root.addView(
        Ui.body(
            context,
            ToolApp.t(
                "点击切换最小控件；长按列出附近全部控件。控件树使用独立背景。隐藏区域导出为透明，无法恢复截图中被遮挡的内容。",
                "Tap a control; hold for all nearby controls. The tree has an opaque background."
                    + " Hidden pixels export transparently; occluded content cannot be"
                    + " reconstructed.")));
    LinearLayout row = Ui.row(context);
    Ui.button(row, ToolApp.t("控件树", "Controls"), () -> tree(null));
    Ui.button(
        row,
        ToolApp.t("轮廓", "Outlines"),
        () -> {
          outlines = !outlines;
          preview.outlines = outlines;
          preview.invalidate();
        });
    root.addView(row);
    if (rulesTarget != null) {
      Ui.button(
          root,
          ignoreRules
              ? ToolApp.t("当前：忽略控件 · 切换保留", "Ignoring controls · switch to include")
              : ToolApp.t("当前：保留控件 · 切换忽略", "Including controls · switch to exclude"),
          () -> {
            ignoreRules = !ignoreRules;
            loadRules();
            render();
          });
      Ui.button(
              root,
              ToolApp.t("应用长截图规则", "Apply long capture rules"),
              () -> {
                applyRules();
                overlay.closeFrozen();
              })
          .setEnabled(metadata.optBoolean("treeAvailable", true));
      Ui.button(root, ToolApp.t("取消", "Cancel"), overlay::closeFrozen);
      overlay.showFrozenPanel(root);
      return;
    }
    LinearLayout save = Ui.row(context);
    Ui.button(save, ToolApp.t("保存截图", "Save image"), () -> save(false));
    Ui.button(save, ToolApp.t("保存页面包", "Save page archive"), () -> save(true));
    root.addView(save);
    Ui.button(root, ToolApp.t("关闭并释放快照", "Close and release snapshot"), overlay::closeFrozen);
    overlay.showFrozenPanel(root);
  }

  private void tree(List<Integer> nearby) {
    LinearLayout root = Ui.column(context);
    root.setBackgroundColor(Ui.BG);
    root.setPadding(Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 12));
    root.addView(
        Ui.title(
            context,
            nearby == null
                ? ToolApp.t("冻结控件树", "Frozen control tree")
                : ToolApp.t("附近控件 · 可多选", "Nearby controls · multiple selection")));
    root.addView(
        Ui.body(
            context,
            ToolApp.t(
                "点击箭头展开层级，点击条目或复选框选择。", "Expand with arrows; select with rows or checkboxes.")));
    root.addView(
        ControlPicker.create(context, nodes, nearby, this::checked, this::toggle),
        new LinearLayout.LayoutParams(-1, 0, 1));
    Ui.button(
        root,
        ToolApp.t("显示／选择全部", "Show / select all"),
        () -> {
          if (rulesTarget == null) selection.showAll();
          else java.util.Arrays.fill(ruleSelected, true);
          tree(nearby);
        });
    Ui.button(root, ToolApp.t("返回轮廓预览", "Back to outline preview"), this::render);
    overlay.showFrozenPanel(root);
  }

  private void toggle(int index, boolean show) {
    if (rulesTarget == null) selection.set(index, show);
    else ruleSelected[index] = show;
  }

  private void paint(Canvas canvas, Bitmap source) {
    ControlPreview.drawImage(canvas, source, nodes, selection::visible);
  }

  private void save(boolean archive) {
    if (original == null || !busy.compareAndSet(false, true)) return;
    Bitmap image =
        Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
    paint(new Canvas(image), original);
    JSONObject tree;
    try {
      tree = new JSONObject(metadata.toString());
      JSONArray entries = tree.getJSONArray("nodes");
      for (int i = 0; i < entries.length(); i++) {
        JSONObject n = entries.getJSONObject(i);
        n.put("visible", selection.visible(i));
        if (!selection.visible(i)) n.put("text", "").put("description", "");
      }
    } catch (Exception e) {
      image.recycle();
      busy.set(false);
      return;
    }
    ToolApp.app.worker.execute(
        () -> {
          try {
            File file =
                Exports.file(
                    "screenshot-" + System.currentTimeMillis() + (archive ? ".gatpage" : ".png"));
            if (archive) {
              try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
                text(
                    zip,
                    "manifest.json",
                    new JSONObject()
                        .put("format", "gandroidtools.page")
                        .put("version", 1)
                        .put("image", "screen.png")
                        .put("controls", "controls.json")
                        .put("edited", true)
                        .put("capturedAt", tree.optString("capturedAt"))
                        .put("screenWidth", image.getWidth())
                        .put("screenHeight", image.getHeight())
                        .toString(2));
                text(zip, "controls.json", tree.toString(2));
                zip.putNextEntry(new ZipEntry("screen.png"));
                image.compress(Bitmap.CompressFormat.PNG, 100, zip);
                zip.closeEntry();
              }
            } else
              try (FileOutputStream out = new FileOutputStream(file)) {
                image.compress(Bitmap.CompressFormat.PNG, 100, out);
              }
            ToolApp.app.log.add("INFO", "screenshot", "Edited frozen screenshot exported");
            ToolApp.app.main.post(
                () -> Ui.toast(context, ToolApp.t("已保存到首页的导出文件", "Saved to Home → Exports")));
          } catch (Exception e) {
            ToolApp.app.log.error("screenshot.export", e);
            Ui.toast(context, e.getMessage());
          } finally {
            image.recycle();
            busy.set(false);
          }
        });
  }

  private static void text(ZipOutputStream zip, String name, String text) throws Exception {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  public void dispose() {
    disposed = true;
    if (original != null) {
      original.recycle();
      original = null;
    }
    metadata = null;
    nodes = List.of();
    selection = null;
  }
}
