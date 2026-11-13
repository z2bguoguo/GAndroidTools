// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/** Opaque, expandable hierarchy. Nearby lists retain the original snapshot indices. */
final class ControlPicker {
  interface Change {
    void set(int index, boolean checked);
  }

  private ControlPicker() {}

  static View create(
      Context context,
      List<TreeNode> nodes,
      List<Integer> subset,
      IntPredicate checked,
      Change change) {
    ListView list = new ListView(context);
    list.setBackgroundColor(Color.WHITE);
    list.setDividerHeight(Ui.dp(context, 1));
    Set<String> parents = new HashSet<>(), collapsed = new HashSet<>();
    for (TreeNode node : nodes) {
      int end = node.path.lastIndexOf('.');
      if (end >= 0) parents.add(node.path.substring(0, end));
    }
    list.setAdapter(
        new BaseAdapter() {
          final List<Integer> visible = new ArrayList<>();

          private void rebuild() {
            visible.clear();
            for (int i = 0; i < nodes.size(); i++) {
              if (subset != null && !subset.contains(i)) continue;
              String path = nodes.get(i).path;
              boolean hidden = false;
              for (String parent : collapsed)
                if (path.startsWith(parent + ".")) {
                  hidden = true;
                  break;
                }
              if (!hidden) visible.add(i);
            }
          }

          public int getCount() {
            rebuild();
            return visible.size();
          }

          public Object getItem(int position) {
            return nodes.get(visible.get(position));
          }

          public long getItemId(int position) {
            return visible.get(position);
          }

          public View getView(int position, View recycled, ViewGroup parent) {
            int index = visible.get(position);
            TreeNode node = nodes.get(index);
            LinearLayout row = Ui.row(context);
            row.setBackgroundColor(Color.WHITE);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(
                Ui.dp(context, Math.min(node.depth, 8) * 10),
                Ui.dp(context, 8),
                Ui.dp(context, 6),
                Ui.dp(context, 8));
            TextView expand =
                Ui.text(
                    context,
                    parents.contains(node.path) ? (collapsed.contains(node.path) ? "▸" : "▾") : "·",
                    21,
                    Ui.ACCENT);
            expand.setGravity(android.view.Gravity.CENTER);
            expand.setContentDescription(ToolApp.t("展开或收起子控件", "Expand or collapse children"));
            expand.setOnClickListener(
                v -> {
                  if (!collapsed.add(node.path)) collapsed.remove(node.path);
                  notifyDataSetChanged();
                });
            row.addView(
                expand, new LinearLayout.LayoutParams(Ui.dp(context, 36), Ui.dp(context, 48)));
            LinearLayout copy = Ui.column(context);
            TextView title = Ui.text(context, ControlLabels.title(node), 15, Ui.INK);
            title.setMaxLines(3);
            copy.addView(title);
            copy.addView(
                Ui.text(
                    context,
                    ControlLabels.type(node.className, ToolApp.app.config.english())
                        + " · "
                        + ToolApp.t("层级 ", "Level ")
                        + (node.depth + 1)
                        + "\n"
                        + (node.id.isEmpty() ? node.path : node.id)
                        + " · "
                        + node.bounds.toShortString(),
                    12,
                    Ui.MUTED));
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            CheckBox box = new CheckBox(context);
            box.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
            box.setContentDescription(ControlLabels.title(node));
            box.setChecked(checked.test(index));
            box.setOnCheckedChangeListener(
                (button, value) -> {
                  change.set(index, value);
                  notifyDataSetChanged();
                });
            row.addView(box);
            row.setOnClickListener(v -> box.setChecked(!box.isChecked()));
            return row;
          }
        });
    return list;
  }
}
