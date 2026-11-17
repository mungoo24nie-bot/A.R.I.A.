package pl.aria.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ostrożne wnioskowanie ze źródeł. Wnioski nie są zapisywane jako fakty. */
final class EvidenceAnswer {
    private EvidenceAnswer() {}

    private static String norm(String s) {
        return Normalizer.normalize(s.toLowerCase(new Locale("pl", "PL")),
                Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace("ł", "l");
    }

    static String answer(SQLiteDatabase db, String question) {
        String q = norm(question);
        boolean residence = q.contains("mieszk") || q.contains("dom") ||
                q.contains("ojc") || q.contains("zamieszkan");
        boolean asks = q.contains("?") || q.startsWith("opowiedz") ||
                q.startsWith("przypomnij") || q.startsWith("co wiesz");
        if (!residence || !asks) return null;

        ArrayList<Long> ids = new ArrayList<>();
        ArrayList<String> lines = new ArrayList<>();
        try (Cursor c = db.rawQuery(
                "SELECT event_id,fragment FROM conversation_fragments " +
                "WHERE thread_key='residence' ORDER BY event_id ASC", null)) {
            while (c.moveToNext()) {
                ids.add(c.getLong(0));
                lines.add(c.getString(1));
            }
        }
        if (lines.isEmpty()) return null;

        String city = null;
        long cityId = -1;
        String with = null;
        long withId = -1;
        String year = null;
        long yearId = -1;
        boolean ambiguity = false;
        // Wybieraj najnowsze jawne stwierdzenia; nie łącz niezwiązanych sekwencji.
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            String n = norm(s);
            Matcher location = Pattern.compile(
                "(?iu)\\b(?:mieszkam|zamieszkuję)\\s+w\\s+([\\p{L}][\\p{L}\\s-]{1,50})"
            ).matcher(s);
            if (location.find()) {
                city = location.group(1).trim().replaceAll("[.,;]+$", "");
                cityId = ids.get(i);
                with = null; year = null;
                withId = -1; yearId = -1;
                ambiguity = false;
                continue;
            }
            if (city == null) continue;
            if (n.startsWith("razem z ojcem") || n.startsWith("z ojcem")) {
                with = "ojcem";
                withId = ids.get(i);
            } else if (n.startsWith("razem z mama") || n.startsWith("z mama")) {
                with = "mamą";
                withId = ids.get(i);
            }
            Matcher bought = Pattern.compile(
                "(?iu)\\bw\\s+domu\\s+kt[oó]ry\\s+kupił\\s+w\\s+(\\d{4})\\s+roku"
            ).matcher(s);
            if (bought.find()) {
                if (with != null && withId >= 0 && i > 0 &&
                    ids.get(i) > withId) {
                    year = bought.group(1);
                    yearId = ids.get(i);
                } else {
                    ambiguity = true;
                }
            }
        }
        if (city == null) return null;

        StringBuilder out = new StringBuilder("Z Twoich wiadomości wynika, że mieszkasz w ")
                .append(city).append(" (wiadomość #").append(cityId).append(")");
        if (with != null) {
            out.append(" razem z ").append(with)
               .append(" (wiadomość #").append(withId).append(")");
        }
        if (year != null && "ojcem".equals(with)) {
            out.append(", w domu, który Twój ojciec kupił w ")
               .append(year).append(" roku (wiadomość #").append(yearId).append(")");
        } else if (year != null) {
            out.append(". Wspomniałeś również o domu kupionym w ")
               .append(year).append(" roku (wiadomość #").append(yearId)
               .append("), ale nie potrafię wiarygodnie ustalić, kto go kupił");
        }
        out.append(".");
        if (ambiguity) {
            out.append(" Nie potrafię jednoznacznie ustalić, kto kupił wspomniany dom.");
        }
        out.append(" To interpretacja podanych fragmentów, a nie dosłowny cytat.");
        return out.toString();
    }
}
