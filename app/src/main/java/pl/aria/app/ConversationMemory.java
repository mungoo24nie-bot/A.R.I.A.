package pl.aria.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pamięć sekwencji wypowiedzi. Zachowuje dosłowne fragmenty z numerami zdarzeń.
 * Bez automatycznego dopisywania wniosków jako pewnych faktów.
 */
final class ConversationMemory {
    private ConversationMemory() {}

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS conversation_fragments (" +
            "event_id INTEGER PRIMARY KEY, thread_key TEXT NOT NULL, fragment TEXT NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS conversation_thread_idx ON conversation_fragments(thread_key)");
    }

    private static String norm(String s) {
        return Normalizer.normalize(s.toLowerCase(new Locale("pl","PL")),
            Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace("ł", "l");
    }

    private static boolean isQuestion(String text) {
        String q = norm(text.trim());
        return q.contains("?") || q.startsWith("co wiesz") || q.startsWith("opowiedz") ||
               q.startsWith("przypomnij") || q.startsWith("jak ");
    }

    private static boolean isStart(String text) {
        String q = norm(text);
        return q.matches("(?s).*\\b(mieszkam|zamieszkuje|zyje)\\s+w\\s+.+");
    }

    private static boolean isFollowup(String text) {
        String q = norm(text.trim());
        return q.startsWith("razem z ") || q.startsWith("w domu ") ||
               q.startsWith("w mieszkaniu ") || q.startsWith("ktory ") ||
               q.startsWith("ktora ") || q.startsWith("ktore ") ||
               q.startsWith("tam ") || q.startsWith("w ktorym ");
    }

    static boolean ingest(SQLiteDatabase db, String text, long eventId) {
        if (isQuestion(text)) return false;
        String thread = null;
        if (isStart(text)) {
            thread = "residence";
        } else if (isFollowup(text)) {
            // Kontynuacja wyłącznie bezpośrednio po fragmencie tematu;
            // unikamy podpinania odległych wypowiedzi do dawnego kontekstu.
            try (Cursor c = db.rawQuery(
                    "SELECT event_id,thread_key FROM conversation_fragments ORDER BY event_id DESC LIMIT 1",
                    null)) {
                if (c.moveToFirst() && "residence".equals(c.getString(1))) {
                    long previousId = c.getLong(0);
                    try (Cursor intervening = db.rawQuery(
                            "SELECT COUNT(*) FROM events WHERE type='user' AND id>? AND id<?",
                            new String[]{String.valueOf(previousId), String.valueOf(eventId)})) {
                        if (intervening.moveToFirst() && intervening.getLong(0) == 0)
                            thread = "residence";
                    }
                }
            }
        }
        if (thread == null) return false;
        ContentValues v = new ContentValues();
        v.put("event_id", eventId);
        v.put("thread_key", thread);
        v.put("fragment", text.trim());
        db.insertWithOnConflict("conversation_fragments", null, v,
                SQLiteDatabase.CONFLICT_IGNORE);
        return true;
    }

    private static ArrayList<String> fragments(SQLiteDatabase db) {
        ArrayList<String> items = new ArrayList<>();
        try (Cursor c = db.rawQuery(
                "SELECT event_id,fragment FROM conversation_fragments " +
                "WHERE thread_key='residence' ORDER BY event_id ASC", null)) {
            while (c.moveToNext()) {
                items.add("#" + c.getLong(0) + " „" + c.getString(1) + "”");
            }
        }
        return items;
    }

    static String answer(SQLiteDatabase db, String question) {
        String q = norm(question);
        boolean residence = q.contains("mieszk") || q.contains("dom") ||
                q.contains("ojc") || q.contains("tarnobrzeg");
        if (!residence || !isQuestion(question)) return null;
        ArrayList<String> fragments = fragments(db);
        if (fragments.isEmpty()) return null;
        StringBuilder result = new StringBuilder(
            "W kronice znalazłam następujące wypowiedzi dotyczące miejsca zamieszkania:\n");
        for (String fragment : fragments) result.append(fragment).append("\n");
        result.append("To dosłowne źródła. Nie dopisuję informacji, których nie potwierdziłeś.");
        return result.toString().trim();
    }

    static boolean isResidenceRelated(String question) {
        String q = norm(question);
        return q.contains("mieszk") || q.contains("dom") || q.contains("ojc") ||
               q.contains("tarnobrzeg") || isFollowup(question);
    }

    static String context(SQLiteDatabase db) {
        ArrayList<String> fragments = fragments(db);
        if (fragments.isEmpty()) return "";
        StringBuilder result = new StringBuilder(
            "KONTEKST ROZMOWY (oryginalne fragmenty użytkownika, nie wnioski):\n");
        int start = Math.max(0, fragments.size() - 5);
        for (int i = start; i < fragments.size(); i++) {
            result.append(fragments.get(i)).append('\n');
        }
        return result.toString();
    }
}
