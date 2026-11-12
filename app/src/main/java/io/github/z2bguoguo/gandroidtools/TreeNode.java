package io.github.z2bguoguo.gandroidtools;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class TreeNode {
  public final String path, key, text, description, id, className, packageName;
  public final Rect bounds;
  public final boolean editable, clickable, scrollable, password, enabled;
  public final int depth;

  public TreeNode(JSONObject o) {
    key = o.optString("key");
    path = o.optString("path", key);
    text = o.optString("text");
    description = o.optString("description");
    id = o.optString("id");
    className = o.optString("class");
    packageName = o.optString("package");
    depth = o.optInt("depth");
    JSONArray r = o.optJSONArray("bounds");
    bounds = r == null ? new Rect() : new Rect(r.optInt(0), r.optInt(1), r.optInt(2), r.optInt(3));
    editable = o.optBoolean("editable");
    clickable = o.optBoolean("clickable");
    scrollable = o.optBoolean("scrollable");
    password = o.optBoolean("password");
    enabled = o.optBoolean("enabled");
  }

  public String label() {
    return (text.isEmpty() ? description : text)
        + "\n"
        + (id.isEmpty() ? className : id)
        + " "
        + bounds.toShortString();
  }

  public static List<TreeNode> parse(JSONObject o) {
    ArrayList<TreeNode> out = new ArrayList<>();
    JSONArray a = o.optJSONArray("nodes");
    if (a != null) for (int i = 0; i < a.length(); i++) out.add(new TreeNode(a.optJSONObject(i)));
    return out;
  }
}
