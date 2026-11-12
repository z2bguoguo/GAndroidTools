package io.github.z2bguoguo.gandroidtools;

import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Selectors {
  private static final Set<String> FIELDS = Set.of("id", "text", "class", "package", "description");

  private Selectors() {}

  private static java.util.Set<String> keys(JSONObject value) {
    java.util.Set<String> result = new java.util.HashSet<>();
    java.util.Iterator<String> it = value.keys();
    while (it.hasNext()) result.add(it.next());
    return result;
  }

  public static JSONObject unique(JSONArray tree, JSONObject selector) throws Exception {
    if (selector.length() == 0 || !FIELDS.containsAll(keys(selector)))
      throw new IllegalArgumentException("Specify supported selector fields");
    JSONObject match = null;
    for (int i = 0; i < tree.length(); i++) {
      JSONObject node = tree.getJSONObject(i);
      boolean same = true;
      for (String key : keys(selector))
        if (!node.optString(key).equals(selector.getString(key))) same = false;
      if (same) {
        if (match != null) throw new IllegalStateException("Ambiguous macro selector");
        match = node;
      }
    }
    return match;
  }
}
