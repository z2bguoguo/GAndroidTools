// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** Captured pixels and metadata stay frozen while the user edits visibility. */
public final class FrozenCapture {
  private final OverlayService overlay;
  private final android.content.Context context;
  private Bitmap original;
  private LayerSnapshot layers;
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
  private static final AtomicBoolean exporting = new AtomicBoolean();
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
          LayerSnapshot capturedLayers = null;
          try {
            overlay.hideForCapture(true);
            Thread.sleep(150);
            Bridge.FrozenFrame frame = ToolApp.app.bridge.freeze(rulesTarget == null);
            JSONObject frozen = frame.metadata();
            shot = frame.image();
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
            if (rulesTarget == null) capturedLayers = LayerSnapshot.capture(frozen);
            LayerSnapshot retainedLayers = capturedLayers;
            capturedLayers = null;
            Bitmap result = shot;
            shot = null;
            ToolApp.app.main.post(
                () -> {
                  if (disposed) {
                    result.recycle();
                    if (retainedLayers != null) retainedLayers.close();
                    return;
                  }
                  if (original != null) original.recycle();
                  original = result;
                  if (layers != null) layers.close();
                  layers = retainedLayers;
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
            if (capturedLayers != null) capturedLayers.close();
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
    if (layers != null)
      root.addView(
          Ui.body(
              context,
              ToolApp.t("已冻结窗口图像：", "Frozen window images: ")
                  + layers.pixelCount()
                  + ToolApp.t(
                      " · 控件树和可用图层随图层包保存；不同回调可能不是同一帧。",
                      " · Tree and available layers are retained in the page pack; callbacks may"
                          + " represent different frames.")));

    if (layers != null && layers.pixelCount() == 0)
      root.addView(
          Ui.body(
              context,
              ToolApp.t(
                  "独立窗口图像需要 Android 14+ 和首页控件兼容通道；不可用时仍保存控件树、片段及图层状态。",
                  "Independent window images require Android 14+ and the control fallback on Home."
                      + " Trees, crops and layer status are retained when window pixels are"
                      + " unavailable.")));
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
    Ui.button(
        save,
        ToolApp.app.config.flag("layerPack", true)
            ? ToolApp.t("保存截图与图层包", "Save image and layers")
            : ToolApp.t("保存截图", "Save image"),
        () -> save(false));
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
    if (original == null || busy.get()) return;
    if (!exporting.compareAndSet(false, true)) {
      Ui.toast(context, ToolApp.t("另一张截图正在导出，请稍候", "Another screenshot is exporting; please wait"));
      return;
    }
    if (!busy.compareAndSet(false, true)) {
      exporting.set(false);
      return;
    }
    Bitmap image =
        Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
    paint(new Canvas(image), original);
    JSONObject tree;
    List<TreeNode> hidden = new ArrayList<>();
    LayerSnapshot retained = layers == null ? null : layers.retain();
    try {
      tree = new JSONObject(metadata.toString());
      JSONArray entries = tree.getJSONArray("nodes");
      for (int i = 0; i < entries.length(); i++) {
        entries.getJSONObject(i).put("visible", selection.visible(i));
        if (!selection.visible(i)) hidden.add(nodes.get(i));
      }
    } catch (Exception error) {
      if (retained != null) retained.close();
      image.recycle();
      busy.set(false);
      exporting.set(false);
      return;
    }
    ToolApp.app.worker.execute(
        () -> {
          try {
            String name = "screenshot-" + System.currentTimeMillis();
            if (!archive)
              try (FileOutputStream out = new FileOutputStream(Exports.file(name + ".png"))) {
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, out))
                  throw new IllegalStateException("Cannot encode screenshot");
              }
            if (archive || ToolApp.app.config.flag("layerPack", true))
              PageArchive.write(Exports.file(name + ".gatpage"), image, tree, retained, hidden);
            ToolApp.app.log.add(
                "INFO", "screenshot", "Edited screenshot and requested layer archive exported");
            Ui.toast(context, ToolApp.t("已保存，可在首页导出文件中分享", "Saved; share from Home → Exports"));
          } catch (Exception error) {
            ToolApp.app.log.error("screenshot.export", error);
            Ui.toast(context, error.getMessage());
          } finally {
            if (retained != null) retained.close();
            image.recycle();
            busy.set(false);
            exporting.set(false);
          }
        });
  }

  public void dispose() {
    disposed = true;
    if (original != null) {
      original.recycle();
      original = null;
    }
    if (layers != null) {
      layers.close();
      layers = null;
    }
    metadata = null;
    nodes = List.of();
    selection = null;
  }
}
