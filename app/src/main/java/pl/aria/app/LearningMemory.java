package pl.aria.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.Normalizer;
import java.util.Locale;

/** Korekty użytkownika, z zachowaniem obu źródeł. Bez modyfikowania wag modelu. */
final class LearningMemory {
    private LearningMemory() {}
    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS corrections (" +
            "correction_event_id INTEGER PRIMARY KEY, previous_aria_event_id INTEGER NOT NULL, " +
            "previous_answer TEXT NOT NULL, correction TEXT NOT NULL)");
    }
    private static String norm(String s) {
        return Normalizer.normalize(s.toLowerCase(new Locale("pl","PL")),
                Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace("ł","l");
    }
    static boolean isCorrection(String text) {
        String s=norm(text.trim());
        return s.startsWith("nie,") || s.startsWith("nie.") ||
            s.startsWith("to nieprawda") || s.startsWith("to blad") ||
            s.startsWith("pomylilas") || s.startsWith("poprawiam cie") ||
            s.startsWith("zle zrozumialas") || s.startsWith("to nie tak");
    }
    static boolean ingest(SQLiteDatabase db, String text, long id) {
        if (!isCorrection(text)) return false;
        try (Cursor c=db.rawQuery(
                "SELECT id,content FROM events WHERE type='aria' AND id<? " +
                "ORDER BY id DESC LIMIT 1", new String[]{String.valueOf(id)})) {
            if (!c.moveToFirst()) return false;
            long previous=c.getLong(0);
            // Korekta powinna dotyczyć bezpośrednio poprzedniej odpowiedzi.
            try (Cursor intervening=db.rawQuery(
                    "SELECT COUNT(*) FROM events WHERE type='user' AND id>? AND id<?",
                    new String[]{String.valueOf(previous),String.valueOf(id)})) {
                if (intervening.moveToFirst() && intervening.getInt(0)>0) return false;
            }
            ContentValues v=new ContentValues();
            v.put("correction_event_id",id);
            v.put("previous_aria_event_id",previous);
            v.put("previous_answer",c.getString(1));
            v.put("correction",text);
            db.insertWithOnConflict("corrections",null,v,SQLiteDatabase.CONFLICT_IGNORE);
            return true;
        }
    }
    static String recent(SQLiteDatabase db) {
        StringBuilder b=new StringBuilder();
        try (Cursor c=db.rawQuery(
                "SELECT correction_event_id,correction FROM corrections " +
                "ORDER BY correction_event_id DESC LIMIT 2",null)) {
            while(c.moveToNext()) {
                b.append("Korekta użytkownika #").append(c.getLong(0)).append(": ")
                    .append(c.getString(1).replace('\n',' ')).append('\n');
            }
        }
        return b.toString();
    }
}
