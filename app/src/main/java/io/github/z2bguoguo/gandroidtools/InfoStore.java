package io.github.z2bguoguo.gandroidtools;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class InfoStore extends SQLiteOpenHelper {
  public record Entry(long id, long parent, String title, String kind, String value) {
    public boolean privateEntry() {
      return kind.equals("private") || kind.equals("totp");
    }
  }

  public InfoStore(Context c) {
    super(c, "information.db", null, 1);
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    db.setForeignKeyConstraintsEnabled(true);
    db.enableWriteAheadLogging();
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT,parent INTEGER REFERENCES"
            + " entries(id) ON DELETE CASCADE,title TEXT NOT NULL,kind TEXT NOT NULL,value TEXT NOT"
            + " NULL)");
    db.execSQL("CREATE INDEX entries_parent ON entries(parent, title)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int before, int after) {
    throw new IllegalStateException("Unsupported database migration");
  }

  public long add(long parent, String title, String kind, String value) {
    if (title.isBlank() || title.length() > 200 || value.length() > 200000)
      throw new IllegalArgumentException("Invalid information entry size");
    if (!java.util.Set.of("folder", "text", "private", "totp").contains(kind))
      throw new IllegalArgumentException("Invalid entry type");
    if (parent != 0 && !get(parent).kind.equals("folder"))
      throw new IllegalArgumentException("Parent must be a folder");
    ContentValues row = new ContentValues();
    if (parent == 0) row.putNull("parent");
    else row.put("parent", parent);
    row.put("title", title);
    row.put("kind", kind);
    row.put("value", value);
    return getWritableDatabase().insertOrThrow("entries", null, row);
  }

  public Entry get(long id) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,coalesce(parent,0),title,kind,value FROM entries WHERE id=?",
                new String[] {"" + id})) {
      if (!c.moveToFirst()) throw new IllegalArgumentException("Entry no longer exists");
      return entry(c);
    }
  }

  public List<Entry> list(long parent, String query, int offset) {
    List<Entry> out = new ArrayList<>();
    String where = parent == 0 ? "parent IS NULL" : "parent=?";
    List<String> parameters = new ArrayList<>();
    if (parent != 0) parameters.add(Long.toString(parent));
    if (!query.isBlank()) {
      where += " AND title LIKE ? ESCAPE '\\'";
      parameters.add(
          "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
    }
    String[] args = parameters.toArray(new String[0]);
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,coalesce(parent,0),title,kind,value FROM entries WHERE "
                    + where
                    + " ORDER BY kind='folder' DESC,title COLLATE NOCASE LIMIT 100 OFFSET "
                    + Math.max(0, offset),
                args)) {
      while (c.moveToNext()) out.add(entry(c));
    }
    return out;
  }

  private static Entry entry(Cursor c) {
    return new Entry(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4));
  }

  public void delete(long id) {
    getWritableDatabase().delete("entries", "id=?", new String[] {"" + id});
  }

  private SecretKey key() throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    if (!ks.containsAlias("information-biometric-v1")) {
      KeyGenerator gen =
          KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      KeyGenParameterSpec.Builder spec =
          new KeyGenParameterSpec.Builder(
                  "information-biometric-v1",
                  KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .setUserAuthenticationRequired(true)
              .setInvalidatedByBiometricEnrollment(true);
      if (android.os.Build.VERSION.SDK_INT >= 30)
        spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
      gen.init(spec.build());
      gen.generateKey();
    }
    return (SecretKey) ks.getKey("information-biometric-v1", null);
  }

  public Cipher encryptCipher() throws Exception {
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, key());
    return c;
  }

  public Cipher decryptCipher(String encoded) throws Exception {
    String[] parts = encoded.split(":", 2);
    if (parts.length != 2) throw new IllegalArgumentException("Invalid private entry");
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(
        Cipher.DECRYPT_MODE,
        key(),
        new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
    return c;
  }

  public static String encrypt(Cipher c, String text) throws Exception {
    return Base64.encodeToString(c.getIV(), Base64.NO_WRAP)
        + ":"
        + Base64.encodeToString(
            c.doFinal(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)), Base64.NO_WRAP);
  }

  public static String decrypt(Cipher c, String text) throws Exception {
    return new String(
        c.doFinal(Base64.decode(text.split(":", 2)[1], Base64.NO_WRAP)),
        java.nio.charset.StandardCharsets.UTF_8);
  }
}
