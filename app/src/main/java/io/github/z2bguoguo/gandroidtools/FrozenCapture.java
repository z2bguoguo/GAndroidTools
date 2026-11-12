package io.github.z2bguoguo.gandroidtools;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
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
  private boolean outlines;
  private final Paint mask = new Paint();
  private final Paint stroke = new Paint(3);
  private final AtomicBoolean busy = new AtomicBoolean();
  private boolean disposed;

  public FrozenCapture(OverlayService overlay, android.content.Context context) {
    this.overlay = overlay;
    this.context = context;
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
                    .put("capturedAt", frozen.optString("capturedAt"));
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
            ToolApp.t("截图 · 已冻结画面与控件", "Screenshot · frozen image and controls"),
            17,
            Ui.INK);
    root.addView(title);
    Preview preview = new Preview();
    root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    root.addView(
        Ui.body(
            context,
            ToolApp.t(
                "点击画面切换最小控件；控件树可批量勾选。编辑期间不读取当前页面。",
                "Tap the image to toggle the smallest control, or use the tree. Editing never"
                    + " rereads the live page.")));
    LinearLayout row = Ui.row(context);
    Ui.button(row, ToolApp.t("控件树", "Controls"), this::tree);
    Ui.button(
        row,
        ToolApp.t("轮廓", "Outlines"),
        () -> {
          outlines = !outlines;
          preview.invalidate();
        });
    root.addView(row);
    LinearLayout save = Ui.row(context);
    Ui.button(save, ToolApp.t("保存截图", "Save image"), () -> save(false));
    Ui.button(save, ToolApp.t("保存页面包", "Save page archive"), () -> save(true));
    root.addView(save);
    Ui.button(root, ToolApp.t("关闭并释放快照", "Close and release snapshot"), overlay::closeFrozen);
    overlay.showFrozenPanel(root);
  }

  private void tree() {
    LinearLayout root = Ui.column(context);
    root.addView(Ui.title(context, ToolApp.t("冻结控件树", "Frozen control tree")));
    android.widget.ListView list = new android.widget.ListView(context);
    list.setChoiceMode(android.widget.ListView.CHOICE_MODE_MULTIPLE);
    List<String> labels = new ArrayList<>();
    for (TreeNode n : nodes) labels.add("  ".repeat(Math.min(n.depth, 6)) + n.label());
    list.setAdapter(
        new android.widget.ArrayAdapter<>(
            context, android.R.layout.simple_list_item_multiple_choice, labels));
    for (int i = 0; i < nodes.size(); i++) list.setItemChecked(i, selection.visible(i));
    list.setOnItemClickListener(
        (p, v, position, id) -> {
          toggle(position, list.isItemChecked(position));
          for (int i = 0; i < nodes.size(); i++) list.setItemChecked(i, selection.visible(i));
        });
    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    Ui.button(
        root,
        ToolApp.t("显示全部", "Show all"),
        () -> {
          selection.showAll();
          tree();
        });
    Ui.button(root, ToolApp.t("返回冻结截图", "Back to frozen image"), this::render);
    overlay.showFrozenPanel(root);
  }

  private void toggle(int index, boolean show) {
    selection.set(index, show);
  }

  private void paint(Canvas canvas, Bitmap source) {
    canvas.drawBitmap(source, 0, 0, null);
    mask.setColor(Color.WHITE);
    for (int i = 0; i < nodes.size(); i++)
      if (!selection.visible(i)) canvas.drawRect(nodes.get(i).bounds, mask);
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

  private final class Preview extends View {
    private final RectF bounds = new RectF();

    Preview() {
      super(context);
      setContentDescription(
          ToolApp.t("冻结截图预览，点击切换控件显示", "Frozen screenshot preview; tap to toggle a control"));
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      if (original == null) return;
      float scale =
          Math.min(
              (float) getWidth() / original.getWidth(), (float) getHeight() / original.getHeight());
      float left = (getWidth() - original.getWidth() * scale) / 2,
          top = (getHeight() - original.getHeight() * scale) / 2;
      bounds.set(left, top, left + original.getWidth() * scale, top + original.getHeight() * scale);
      canvas.save();
      canvas.translate(left, top);
      canvas.scale(scale, scale);
      paint(canvas, original);
      if (outlines) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2 / scale);
        stroke.setColor(Ui.ACCENT);
        for (int i = 0; i < nodes.size(); i++)
          if (selection.visible(i) && !nodes.get(i).bounds.isEmpty())
            canvas.drawRect(nodes.get(i).bounds, stroke);
      }
      canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      if (event.getActionMasked() != MotionEvent.ACTION_UP) return true;
      performClick();
      if (original == null || !bounds.contains(event.getX(), event.getY())) return true;
      int x = (int) ((event.getX() - bounds.left) * original.getWidth() / bounds.width()),
          y = (int) ((event.getY() - bounds.top) * original.getHeight() / bounds.height());
      long area = Long.MAX_VALUE;
      int index = -1;
      for (int i = 0; i < nodes.size(); i++) {
        Rect r = nodes.get(i).bounds;
        long size = (long) r.width() * r.height();
        if (r.contains(x, y) && size > 0 && size < area) {
          area = size;
          index = i;
        }
      }
      if (index >= 0) {
        toggle(index, !selection.visible(index));
        invalidate();
      }
      return true;
    }

    @Override
    public boolean performClick() {
      super.performClick();
      return true;
    }
  }
}
