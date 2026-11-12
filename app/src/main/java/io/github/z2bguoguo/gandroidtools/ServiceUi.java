package io.github.z2bguoguo.gandroidtools;

import android.content.Context;
import android.widget.LinearLayout;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.json.JSONObject;

public final class ServiceUi {
  private ServiceUi() {}

  public static LinearLayout build(Context context, JSONObject data, Runnable reload) {
    LinearLayout root = Ui.column(context);
    root.addView(Ui.title(context, ToolApp.t("无障碍管理", "Accessibility")));
    root.addView(
        Ui.body(
            context,
            ToolApp.t(
                "保持功能在悬浮工具服务运行期间检查勾选服务；关闭服务会同时取消保持。",
                "Keep-alive checks selected services while floating tools run. Disabling a service"
                    + " also removes it from keep-alive.")));
    LinkedHashSet<String> installed = new LinkedHashSet<>();
    for (String line : data.optString("installed").split("\n")) {
      String text = line.trim();
      if (text.matches("[a-zA-Z0-9_.$]+/[a-zA-Z0-9_.$]+")) installed.add(text);
    }
    for (String component : data.optString("enabled").split(":"))
      if (component.contains("/")) installed.add(component);
    if (installed.isEmpty())
      root.addView(
          Ui.body(
              context,
              ToolApp.t(
                  "系统未返回服务列表；请查看日志或打开系统设置。",
                  "The system returned no services; inspect logs or open system settings.")));
    for (String component : installed) {
      LinearLayout card = Ui.card(root);
      card.addView(Ui.text(context, component, 15, Ui.INK));
      boolean enabled = Arrays.asList(data.optString("enabled").split(":")).contains(component);
      boolean kept =
          Arrays.asList(ToolApp.app.config.text("keepServices", "").split(":")).contains(component);
      Ui.toggle(
          card,
          ToolApp.t("启用服务", "Enable service"),
          "",
          enabled,
          value -> {
            ToolApp.app.worker.execute(
                () -> {
                  try {
                    if (!value) keep(component, false);
                    ToolApp.app.bridge.call(
                        "service",
                        new JSONObject().put("component", component).put("enabled", value));
                    ToolApp.app.main.post(reload);
                  } catch (Exception e) {
                    ToolApp.app.log.error("accessibility", e);
                    Ui.toast(context, e.getMessage());
                    ToolApp.app.main.post(reload);
                  }
                });
          });
      Ui.toggle(
          card,
          ToolApp.t("保持此服务", "Keep this service enabled"),
          ToolApp.t("需要 Shizuku 持续连接", "Requires a live Shizuku connection"),
          kept,
          value -> {
            if (value && !enabled) {
              Ui.toast(context, ToolApp.t("请先启用服务", "Enable the service first"));
              return;
            }
            keep(component, value);
            if (value) OverlayService.start(context, "show");
          });
    }
    Ui.button(
        root,
        ToolApp.t("打开系统无障碍设置", "Open system accessibility settings"),
        () ->
            context.startActivity(
                new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)));
    return root;
  }

  private static synchronized void keep(String component, boolean value) {
    LinkedHashSet<String> set =
        new LinkedHashSet<>(Arrays.asList(ToolApp.app.config.text("keepServices", "").split(":")));
    set.remove("");
    if (value) set.add(component);
    else set.remove(component);
    ToolApp.app.config.set("keepServices", String.join(":", set));
  }
}
