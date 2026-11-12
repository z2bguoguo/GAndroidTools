// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.function.Consumer;

public final class InfoUi {
  private final Context context;
  private final Consumer<View> show;
  private final Consumer<String> input;
  private long parent;
  private String query = "";
  private int offset;

  public InfoUi(Context context, Consumer<View> show, Consumer<String> input) {
    this.context = context;
    this.show = show;
    this.input = input;
  }

  public void open() {
    open(0);
  }

  public void open(long folder) {
    parent = folder;
    offset = 0;
    query = "";
    render();
  }

  private void render() {
    LinearLayout root = Ui.column(context);
    root.setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), Ui.dp(context, 12));
    root.addView(Ui.title(context, ToolApp.t("信息本", "Notebook")));
    if (parent != 0)
      Ui.button(
          root,
          ToolApp.t("返回上层 · ", "Parent folder · ") + ToolApp.app.info.get(parent).title(),
          () -> open(ToolApp.app.info.get(parent).parent()));
    EditText search =
        Ui.input(root, ToolApp.t("按标题查找当前分类", "Search this folder by title"), query, false);
    Ui.button(
        root,
        ToolApp.t("查找", "Search"),
        () -> {
          query = search.getText().toString();
          offset = 0;
          render();
        });
    Ui.button(
        root,
        ToolApp.t("＋ 添加信息或分类", "＋ Add information or folder"),
        () -> add(parent, this::render));
    java.util.List<InfoStore.Entry> list = ToolApp.app.info.list(parent, query, offset);
    for (InfoStore.Entry entry : list) {
      LinearLayout card = Ui.card(root);
      String badge = entry.kind().equals("folder") ? "▣ " : entry.privateEntry() ? "◈ " : "◇ ";
      Ui.entry(
          card,
          badge + entry.title(),
          entry.privateEntry()
              ? ToolApp.t("生物识别验证后查看与使用", "Biometric authentication required")
              : entry.kind().equals("folder")
                  ? ToolApp.t("打开分类", "Open folder")
                  : ToolApp.t("点击填写 · 长按管理", "Tap to fill · hold to manage"),
          () -> {
            if (entry.kind().equals("folder")) open(entry.id());
            else use(entry, entry.kind().equals("totp"));
          });
      card.setOnLongClickListener(
          v -> {
            Ui.dialog(context)
                .setTitle(entry.title())
                .setItems(
                    new String[] {ToolApp.t("查看", "View"), ToolApp.t("删除", "Delete")},
                    (d, n) -> {
                      if (n == 0) {
                        if (entry.kind().equals("folder")) open(entry.id());
                        else use(entry, true);
                      } else if (n == 1)
                        Ui.dialog(context)
                            .setMessage(
                                ToolApp.t("删除该条目及其子分类？", "Delete this entry and its subfolders?"))
                            .setPositiveButton(
                                ToolApp.t("删除", "Delete"),
                                (dialog, x) -> {
                                  ToolApp.app.info.delete(entry.id());
                                  render();
                                })
                            .setNegativeButton(ToolApp.t("取消", "Cancel"), null)
                            .show();
                    })
                .show();
            return true;
          });
    }
    if (list.isEmpty())
      root.addView(Ui.body(context, ToolApp.t("这里还没有信息", "No information here yet")));
    if (offset > 0)
      Ui.button(
          root,
          ToolApp.t("上一页", "Previous page"),
          () -> {
            offset = Math.max(0, offset - 100);
            render();
          });
    if (list.size() == 100)
      Ui.button(
          root,
          ToolApp.t("下一页", "Next page"),
          () -> {
            offset += 100;
            render();
          });
    show.accept(Ui.scroll(context, root));
  }

  public void add(long folder, Runnable done) {
    LinearLayout root = Ui.column(context);
    root.setPadding(30, 8, 30, 8);
    EditText title = Ui.input(root, ToolApp.t("标题", "Title"), "", false);
    EditText value =
        Ui.input(
            root,
            ToolApp.t("信息内容或 otpauth://totp URI", "Information or otpauth://totp URI"),
            "",
            true);
    final int[] kind = {0};
    android.widget.RadioGroup group = new android.widget.RadioGroup(context);
    String[] labels = {
      ToolApp.t("普通信息", "Text"),
      ToolApp.t("隐私信息（加密）", "Private text (encrypted)"),
      ToolApp.t("MFA 动态码（加密）", "MFA / TOTP (encrypted)"),
      ToolApp.t("分类文件夹", "Folder")
    };
    for (int i = 0; i < labels.length; i++) {
      android.widget.RadioButton radio = new android.widget.RadioButton(context);
      radio.setText(labels[i]);
      radio.setId(View.generateViewId());
      group.addView(radio);
    }
    group.check(group.getChildAt(0).getId());
    group.setOnCheckedChangeListener(
        (g, id) -> {
          kind[0] = g.indexOfChild(g.findViewById(id));
          value.setVisibility(kind[0] == 3 ? View.GONE : View.VISIBLE);
          value.setInputType(
              kind[0] == 1 || kind[0] == 2
                  ? android.text.InputType.TYPE_CLASS_TEXT
                      | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                  : android.text.InputType.TYPE_CLASS_TEXT
                      | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        });
    root.addView(group);
    android.app.Dialog dialog =
        Ui.dialog(context)
            .setTitle(ToolApp.t("添加到信息本", "Add to notebook"))
            .setView(root)
            .setNegativeButton(ToolApp.t("取消", "Cancel"), null)
            .setPositiveButton(ToolApp.t("保存", "Save"), null)
            .create();
    dialog.setOnShowListener(
        x ->
            ((androidx.appcompat.app.AlertDialog) dialog)
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      String name = title.getText().toString(), text = value.getText().toString();
                      try {
                        if (name.isBlank())
                          throw new IllegalArgumentException(ToolApp.t("请输入标题", "Enter a title"));
                        String type = new String[] {"text", "private", "totp", "folder"}[kind[0]];
                        if (type.equals("totp")) Totp.parse(text);
                        if (kind[0] == 1 || kind[0] == 2) {
                          javax.crypto.Cipher cipher = ToolApp.app.info.encryptCipher();
                          AuthActivity.authorize(
                              context,
                              cipher,
                              c -> {
                                try {
                                  ToolApp.app.info.add(
                                      folder, name, type, InfoStore.encrypt(c, text));
                                  dialog.dismiss();
                                  value.setText("");
                                  done.run();
                                } catch (Exception e) {
                                  Ui.toast(context, e.getClass().getSimpleName());
                                }
                              });
                        } else {
                          ToolApp.app.info.add(folder, name, type, text);
                          dialog.dismiss();
                          done.run();
                        }
                      } catch (Exception e) {
                        Ui.toast(context, e.getMessage());
                      }
                    }));
    dialog.show();
  }

  private void use(InfoStore.Entry entry, boolean view) {
    if (!entry.privateEntry()) {
      if (view) viewText(entry.title(), entry.value(), () -> input.accept(entry.value()));
      else input.accept(entry.value());
      return;
    }
    try {
      AuthActivity.authorize(
          context,
          ToolApp.app.info.decryptCipher(entry.value()),
          cipher -> {
            try {
              String plain = InfoStore.decrypt(cipher, entry.value());
              if (entry.kind().equals("totp")) {
                if (view) otp(entry, Totp.parse(plain));
                else input.accept(Totp.code(Totp.parse(plain), System.currentTimeMillis() / 1000));
              } else if (view) viewText(entry.title(), plain, () -> use(entry, false));
              else input.accept(plain);
            } catch (Exception e) {
              Ui.toast(
                  context,
                  ToolApp.t(
                      "隐私信息无法解密；请检查生物识别是否变化",
                      "Cannot decrypt; biometric enrollment may have changed"));
            }
          });
    } catch (Exception e) {
      Ui.toast(context, e.getClass().getSimpleName());
    }
  }

  private void viewText(String title, String value, Runnable fill) {
    TextView text = Ui.text(context, value, 18, Ui.INK);
    text.setPadding(30, 20, 30, 20);
    text.setTextIsSelectable(true);
    androidx.appcompat.app.AlertDialog d =
        Ui.dialog(context)
            .setTitle(title)
            .setView(text)
            .setPositiveButton(
                ToolApp.t("填写当前输入框", "Fill current field"), (dialog, n) -> fill.run())
            .setNegativeButton(ToolApp.t("关闭", "Close"), null)
            .create();
    d.getWindow();
    d.setOnShowListener(
        x -> {
          d.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
          ToolApp.app.main.postDelayed(d::dismiss, 30000);
        });
    d.show();
  }

  private void otp(InfoStore.Entry entry, Totp.Account account) {
    LinearLayout root = Ui.column(context);
    root.setPadding(30, 20, 30, 20);
    TextView code = Ui.title(context, "");
    code.setTextSize(36);
    root.addView(code);
    TextView remaining = Ui.body(context, "");
    root.addView(remaining);
    ProgressBar progress =
        new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(account.period());
    root.addView(progress, new LinearLayout.LayoutParams(-1, Ui.dp(context, 6)));
    androidx.appcompat.app.AlertDialog dialog =
        Ui.dialog(context)
            .setTitle(entry.title())
            .setView(root)
            .setPositiveButton(ToolApp.t("填写动态码", "Fill code"), (d, n) -> use(entry, false))
            .setNegativeButton(ToolApp.t("关闭", "Close"), null)
            .create();
    Handler handler = ToolApp.app.main;
    long expiry = android.os.SystemClock.elapsedRealtime() + 30000;
    Runnable tick =
        new Runnable() {
          @Override
          public void run() {
            if (!dialog.isShowing()) return;
            if (android.os.SystemClock.elapsedRealtime() >= expiry) {
              dialog.dismiss();
              return;
            }
            try {
              long now = System.currentTimeMillis() / 1000;
              code.setText(Totp.code(account, now));
              int left = account.period() - (int) (now % account.period());
              progress.setProgress(left);
              remaining.setText(ToolApp.t("剩余 ", "Expires in ") + left + " s");
              handler.postDelayed(this, 250);
            } catch (Exception e) {
              dialog.dismiss();
            }
          }
        };
    dialog.setOnDismissListener(d -> handler.removeCallbacks(tick));
    dialog.setOnShowListener(
        d -> {
          dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
          tick.run();
        });
    dialog.show();
  }
}
