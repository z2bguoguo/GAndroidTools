// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

final class ControlLabels {
  private ControlLabels() {}

  static String type(String className, boolean english) {
    String simple = className.substring(className.lastIndexOf('.') + 1);
    if (english) return simple;
    return switch (simple) {
      case "TextView", "AppCompatTextView" -> "文字";
      case "EditText", "AppCompatEditText", "TextInputEditText" -> "输入框";
      case "Button", "MaterialButton", "AppCompatButton" -> "按钮";
      case "ImageButton" -> "图标按钮";
      case "ImageView", "AppCompatImageView" -> "图片";
      case "CheckBox", "MaterialCheckBox" -> "复选框";
      case "Switch", "SwitchCompat", "MaterialSwitch" -> "开关";
      case "RadioButton" -> "单选按钮";
      case "RecyclerView", "ListView", "LazyColumn" -> "列表";
      case "ScrollView", "NestedScrollView" -> "滚动区域";
      case "LinearLayout", "FrameLayout", "RelativeLayout", "ConstraintLayout", "ViewGroup" ->
          "布局容器";
      case "WebView" -> "网页";
      case "SeekBar", "Slider" -> "滑动条";
      case "ProgressBar" -> "进度条";
      case "View" -> "视图";
      default -> simple.isEmpty() ? "控件" : simple;
    };
  }

  static String title(TreeNode node) {
    if (node.password) return ToolApp.t("密码输入框（内容已排除）", "Password field (content excluded)");
    String value = !node.text.isBlank() ? node.text : node.description;
    if (value.isBlank() && !node.id.isBlank())
      value = node.id.substring(node.id.lastIndexOf('/') + 1);
    return value.isBlank() ? type(node.className, ToolApp.app.config.english()) : value;
  }
}
