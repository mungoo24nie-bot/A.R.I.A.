package pl.aria.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Wyszukiwanie dowodów w historii bez ograniczania do psa lub domu.
 * Indeks jest odtwarzany z events, więc nie zmienia schematu bazy.
 * Zwraca wyłącznie oryginalne wypowiedzi użytkownika i ich sąsiadów.
 */
final class GeneralMemory {
    private GeneralMemory() {}

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
        "aria","jest","jestem","bylo","byla","byly","byc","mam","moje",
        "moim","moja","moj","mnie","twoje","twoim","twoja","ktory",
        "ktora","ktore","jak","jaki","jakie","jakiej","gdzie","kiedy",
        "skad","czy","sie","oraz","albo","przez","tego","te","tym",
        "wiem","wiesz","powiedz","przypomnij","pamietasz","wczesniej",
        "rozmow","rozmowie","wiadomosc","wiadomosci","informacje","temat"
    ));

    private static String norm(String value) {
        return Normalizer.normalize(value.toLowerCase(new Locale("pl","PL")),
                Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replace("ł","l");
    }

    private static Set<String> terms(String value) {
        Set<String> result = new HashSet<>();
        for (String raw : norm(value).split("[^a-z0-9]+")) {
            if (raw.length() < 3 || STOP.contains(raw)) continue;
            // Prosty rdzeń fleksyjny: pies/psie nie zostanie połączony,
            // ale np. Tarnobrzeg/Tarnobrzegu, samochód/samochodu już tak.
            String stem = raw.length() >= 7 ? raw.substring(0, raw.length()-2)
                    : raw.length() >= 5 ? raw.substring(0,raw.length()-1) : raw;
            result.add(stem);
        }
        return result;
    }

    private static final class Entry {
        final long id;
        final String text;
        final Set<String> words;
        int score;
        Entry(long id, String text) {
            this.id=id; this.text=text; this.words=terms(text);
        }
    }

    private static ArrayList<Entry> find(SQLiteDatabase db, String question, long excludeId) {
        ArrayList<Entry> all = new ArrayList<>();
        try (Cursor c = db.rawQuery(
                "SELECT id,content FROM events WHERE type='user' ORDER BY id ASC",null)) {
            while(c.moveToNext()) {
                long id=c.getLong(0);
                if (id != excludeId) all.add(new Entry(id,c.getString(1)));
            }
        }
        if (!all.isEmpty()) {
            Entry last=all.get(all.size()-1);
            if (last.text.equals(question)) all.remove(all.size()-1);
        }
        Set<String> query=terms(question);
        ArrayList<Entry> matched=new ArrayList<>();
        for (Entry e:all) {
            int score=0;
            for (String word:query) {
                for (String candidate:e.words) {
                    if (candidate.equals(word)) { score+=4; break; }
                    if (word.length()>=4 && candidate.length()>=4 &&
                            (candidate.startsWith(word) || word.startsWith(candidate))) {
                        score+=2; break;
                    }
                }
            }
            e.score=score;
            if (score>0) matched.add(e);
        }
        Collections.sort(matched,(a,b)-> {
            int c=Integer.compare(b.score,a.score);
            return c!=0?c:Long.compare(b.id,a.id);
        });
        // Oprócz dopasowanego zdania pobierz najbliższe wypowiedzi użytkownika:
        // kontynuacje typu "razem z ojcem" lub "w domu..." mogą nie zawierać słów pytania.
        ArrayList<Entry> chosen=new ArrayList<>();
        for (Entry hit:matched) {
            if (chosen.size()>=7) break;
            int index=all.indexOf(hit);
            for (int j=Math.max(0,index-1);j<=Math.min(all.size()-1,index+2);j++) {
                Entry e=all.get(j);
                if (Math.abs(e.id-hit.id)>8) continue;
                if (!chosen.contains(e)) chosen.add(e);
            }
        }
        Collections.sort(chosen,Comparator.comparingLong(a->a.id));
        if(chosen.size()>10) chosen.subList(10,chosen.size()).clear();
        return chosen;
    }

    static String evidence(SQLiteDatabase db, String question, long excludeId) {
        ArrayList<Entry> entries=find(db,question,excludeId);
        if(entries.isEmpty()) return "";
        StringBuilder out=new StringBuilder();
        for(Entry e:entries) {
            String text=e.text.replace('\n',' ').trim();
            if(text.length()>260) text=text.substring(0,260)+" [fragment]";
            out.append("#").append(e.id).append(": ").append(text).append('\n');
        }
        return out.toString();
    }

    static boolean isMemoryQuestion(String question) {
        String q=norm(question);
        return q.contains("pamiet") || q.contains("wiesz o") ||
                q.contains("wiesz na temat") || q.contains("wczesniej") ||
                q.contains("rozmawial") || q.contains("powiedzial") ||
                q.contains("przypomnij") || q.contains("kronik") ||
                q.contains("co mowilem");
    }

    static String directAnswer(SQLiteDatabase db, String question, long excludeId) {
        if(!isMemoryQuestion(question)) return null;
        String found=evidence(db,question,excludeId);
        if(found.isEmpty()) return null;
        return "Znalazłam w Twoich wcześniejszych wiadomościach:\n" + found +
                "To źródłowe fragmenty, nie gotowy wniosek. " +
                "Nie przypisuję Ci informacji, których nie potwierdziłeś.";
    }
}
