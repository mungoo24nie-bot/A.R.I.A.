package pl.aria.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pamięć tematyczna: przechowuje WYŁĄCZNIE oryginalne deklaracje użytkownika.
 * Nie interpretuje odpowiedzi modelu jako faktów. Nie obiecuje pełnego rozumienia.
 */
final class TopicMemory {
    private TopicMemory() {}

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS topic_memories (" +
                "event_id INTEGER PRIMARY KEY, topic TEXT NOT NULL, content TEXT NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS topic_memories_topic ON topic_memories(topic)");
    }

    static String norm(String s) {
        return Normalizer.normalize(s.toLowerCase(new Locale("pl","PL")),
                Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace("ł", "l");
    }

    static String topicOf(String text) {
        String t = text.trim();
        if (t.indexOf('?') >= 0 || t.length() < 9) return null;
        // "Mam kota o imieniu Filemon", "Mam psa o imieniu Bajgiel".
        Matcher pet = Pattern.compile("(?iu)\\bmam\\s+(?:fikcyjnego\\s+)?(?:psa|kota|królika|chomika|papugę|żółwia)\\s+o\\s+imieniu\\s+([\\p{L}]{2,30})\\b").matcher(t);
        if (pet.find()) return norm(pet.group(1));
        // "Bajgiel jest kundelkiem...", "Filemon ma 3 lata".
        Matcher named = Pattern.compile("(?u)^\\s*([\\p{Lu}][\\p{L}]{2,29})\\s+(?:jest|ma|lubi|mieszka|pochodzi)\\b").matcher(t);
        if (named.find()) return norm(named.group(1));
        // "Mój rower nazywa się Błysk" — nowy temat.
        Matcher namedThing = Pattern.compile("(?iu)\\b(?:moj|mój|moja|moje)\\s+[\\p{L}]+\\s+nazywa\\s+się\\s+([\\p{L}]{2,30})\\b").matcher(t);
        if (namedThing.find()) return norm(namedThing.group(1));
        return null;
    }

    static boolean ingest(SQLiteDatabase db, String text, long id) {
        String topic = topicOf(text);
        if (topic == null) return false;
        ContentValues values = new ContentValues();
        values.put("event_id", id);
        values.put("topic", topic);
        values.put("content", text.trim());
        db.insertWithOnConflict("topic_memories", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        return true;
    }

    static String answer(SQLiteDatabase db, String question) {
        String q = norm(question);
        if (!(q.contains("?") || q.startsWith("opowiedz") || q.startsWith("przypomnij")
                || q.startsWith("co wiesz"))) return null;
        Set<String> subjects = new HashSet<>();
        try (Cursor c = db.rawQuery("SELECT DISTINCT topic FROM topic_memories", null)) {
            while (c.moveToNext()) subjects.add(c.getString(0));
        }
        String topic = null;
        for (String candidate : subjects) {
            if (q.contains(candidate) && (topic == null || candidate.length() > topic.length()))
                topic = candidate;
        }
        if (topic == null) return null;
        ArrayList<String> records = new ArrayList<>();
        try (Cursor c = db.rawQuery(
                "SELECT event_id,content FROM topic_memories WHERE topic=? ORDER BY event_id ASC LIMIT 12",
                new String[]{topic})) {
            while (c.moveToNext()) {
                records.add("Wiadomość #" + c.getLong(0) + ": „" + c.getString(1) + "”");
            }
        }
        if (records.isEmpty()) return null;
        StringBuilder answer = new StringBuilder("Oto informacje o ").append(topic)
                .append(" zapisane w Twoich wypowiedziach:\n");
        for (String record : records) answer.append(record).append('\n');
        answer.append("Nie dopowiadam szczegółów, których nie podałeś.");
        return answer.toString().trim();
    }

    static String context(SQLiteDatabase db, String question) {
        String response = answer(db, question);
        if (response == null) return "";
        return "POTWIERDZONE WPISY UŻYTKOWNIKA (cytaty):\n" + response + "\n";
    }
}
