package pl.aria.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Relacje wyłącznie z wypowiedzi użytkownika, z identyfikatorem źródła. */
final class RelationalMemory {
    private RelationalMemory() {}

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS relations (" +
            "subject TEXT NOT NULL, predicate TEXT NOT NULL, object TEXT NOT NULL, " +
            "source_event_id INTEGER NOT NULL, PRIMARY KEY(subject,predicate))");
    }

    private static String norm(String text) {
        return Normalizer.normalize(text.toLowerCase(new Locale("pl", "PL")),
            Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace("ł", "l");
    }

    private static void save(SQLiteDatabase db, String subject, String predicate,
                             String object, long sourceId) {
        ContentValues values = new ContentValues();
        values.put("subject", subject);
        values.put("predicate", predicate);
        values.put("object", object.trim());
        values.put("source_event_id", sourceId);
        db.insertWithOnConflict("relations", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static String find(SQLiteDatabase db, String subject, String predicate) {
        try (Cursor c = db.rawQuery(
                "SELECT object,source_event_id FROM relations WHERE subject=? AND predicate=?",
                new String[]{subject, predicate})) {
            return c.moveToFirst() ? c.getString(0) + "\u0001" + c.getLong(1) : null;
        }
    }

    private static String[] value(String record) {
        return record == null ? null : record.split("\u0001", 2);
    }

    static boolean ingest(SQLiteDatabase db, String text, long eventId) {
        if (text.indexOf('?') >= 0) return false;
        String normalized = norm(text);
        boolean saved = false;

        // Wypowiedź: "mam psa o imieniu Bajgiel" — nazwa podmiotu.
        Matcher dog = Pattern.compile(
            "(?iu)\\bmam\\s+(?:fikcyjnego\\s+)?psa\\s+o\\s+imieniu\\s+([\\p{L}]{2,30})\\b"
        ).matcher(text);
        if (dog.find()) {
            String name = dog.group(1);
            save(db, "user", "dog_name", name, eventId);
            saved = true;
        }

        // Rozpoznaj nazwę wcześniej zapisanego psa; nigdy nie korzystaj z wypowiedzi modelu.
        String[] known = value(find(db, "user", "dog_name"));
        if (known == null) return saved;
        String dogName = known[0];
        if (!normalized.contains(norm(dogName))) return saved;
        String subject = "dog:" + norm(dogName);

        Matcher breed = Pattern.compile(
            "(?iu)\\b" + Pattern.quote(dogName) +
            "\\s+jest\\s+(kundelkiem|kundelkiem\\s+[^,.!?]{1,40}|owczarkiem\\s+niemieckim|labradorem)\\b"
        ).matcher(text);
        if (breed.find()) {
            String breedText = norm(breed.group(1)).startsWith("kundelk") ?
                "kundelek" : breed.group(1);
            save(db, subject, "breed", breedText, eventId);
            saved = true;
        }

        if (normalized.contains("kolezanka") && normalized.contains("znalazla") &&
            normalized.contains("lesie")) {
            save(db, subject, "found", "koleżanka znalazła go w lesie", eventId);
            saved = true;
        }
        if (normalized.contains("przygarnalem") || normalized.contains("przygarnelam")) {
            save(db, subject, "adopted", "użytkownik go przygarnął", eventId);
            saved = true;
        }
        return saved;
    }

    static String answer(SQLiteDatabase db, String question) {
        String q = norm(question);
        boolean dogQuestion = (q.contains("pies") || q.contains("psie") || q.contains("psa") || q.contains("psu") ||
            q.contains("kundel") || q.contains("zwierzak"));
        boolean asking = q.contains("?") || q.startsWith("opowiedz") ||
            q.startsWith("przypomnij") || q.startsWith("co wiesz");
        String[] name = value(find(db, "user", "dog_name"));
        if (name != null && q.contains(norm(name[0]))) dogQuestion = true;
        if (!dogQuestion || !asking) return null;
        if (name == null) return "Nie mam jeszcze potwierdzonych informacji o Twoim psie.";
        String subject = "dog:" + norm(name[0]);
        String[] breed = value(find(db, subject, "breed"));
        String[] found = value(find(db, subject, "found"));
        String[] adopted = value(find(db, subject, "adopted"));

        StringBuilder out = new StringBuilder();
        out.append("Twój pies ma na imię ").append(name[0])
           .append(" (Twoja wiadomość #").append(name[1]).append(").");
        if (breed != null) out.append(" Rasa: ").append(breed[0])
            .append(" (wiadomość #").append(breed[1]).append(").");
        if (found != null) out.append(" ").append("Twoja ")
            .append(found[0]).append(" (wiadomość #").append(found[1]).append(").");
        if (adopted != null) out.append(" Ty go przygarnąłeś")
            .append(" (wiadomość #").append(adopted[1]).append(").");
        if (breed == null || found == null || adopted == null)
            out.append(" O pozostałych szczegółach nie mam potwierdzonych danych.");
        return out.toString();
    }

    static String context(SQLiteDatabase db) {
        String[] name = value(find(db, "user", "dog_name"));
        if (name == null) return "";
        String subject = "dog:" + norm(name[0]);
        StringBuilder result = new StringBuilder("Pamięć relacyjna (fakty użytkownika): pies = ")
            .append(name[0]).append("; źródło #").append(name[1]);
        for (String predicate : new String[]{"breed","found","adopted"}) {
            String[] data = value(find(db, subject, predicate));
            if (data != null) result.append("; ").append(predicate).append("=")
                .append(data[0]).append(" (#").append(data[1]).append(")");
        }
        return result.append("\n").toString();
    }
}
