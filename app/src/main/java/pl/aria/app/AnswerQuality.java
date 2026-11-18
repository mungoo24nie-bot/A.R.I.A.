package pl.aria.app;

import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.Normalizer;
import java.util.Locale;

/** Walidacja odpowiedzi: odrzucamy artefakty, a nie poprawne krótkie odpowiedzi. */
final class AnswerQuality {
    private AnswerQuality() {}

    static String reason(String answer, String question) {
        if (answer == null || answer.trim().isEmpty()) return "empty";
        String raw=answer.trim();
        String a=normalize(raw);
        String q=normalize(question == null ? "" : question);
        if (a.isEmpty()) return "empty";
        if (a.equals(q) && !q.isEmpty()) return "echo";
        if (raw.contains("<|im_start|>") || raw.contains("<|im_end|>") ||
            raw.contains("<think>") || raw.contains("</think>")) return "model_markers";
        if (a.startsWith("slucham wypowiedz uzytkownika") ||
            a.startsWith("odpowiedz powinna") ||
            a.contains("odpowiedz powinna zawierac") ||
            a.startsWith("okay the user") || a.startsWith("i should") ||
            a.startsWith("the user said") || a.startsWith("let me check")) return "internal_monologue";
        if (a.contains("nowa wiadomosc") || a.contains("ostatnia rozmowa") ||
            a.contains("odpowiedz na to pytanie") || a.contains("co wiedzisz") ||
            a.contains("daj mi pomoc") || a.contains("masz wiedziec")) return "template_artifact";
        // Odpowiedzi typu "4", "tak", "nie", "OK" są dozwolone.
        // Nie oceniamy tutaj poprawności merytorycznej odpowiedzi.
        return "";
    }

    private static String normalize(String s) {
        String lower=Normalizer.normalize(s.toLowerCase(new Locale("pl","PL")),
            Normalizer.Form.NFD).replaceAll("\\p{M}+","").replace("ł","l");
        return lower.replaceAll("[^a-z0-9 ]","").replaceAll(" +"," ").trim();
    }

    static void record(SQLiteDatabase db,String taskId,String reason,int rawLength,int cleanLength) {
        db.execSQL("CREATE TABLE IF NOT EXISTS aria_answer_quality ("+
            "task_id TEXT PRIMARY KEY, reason TEXT NOT NULL,"+
            "raw_chars INTEGER NOT NULL, clean_chars INTEGER NOT NULL,"+
            "recorded_at INTEGER NOT NULL)");
        android.content.ContentValues v=new android.content.ContentValues();
        v.put("task_id",taskId);
        v.put("reason",reason.isEmpty()?"accepted":reason);
        v.put("raw_chars",rawLength);
        v.put("clean_chars",cleanLength);
        v.put("recorded_at",System.currentTimeMillis());
        db.insertWithOnConflict("aria_answer_quality",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    static void show(Context context) {
        StringBuilder report=new StringBuilder("Ostatnie 12 odpowiedzi (bez treści rozmów):\n\n");
        try(SQLiteDatabase db=context.openOrCreateDatabase("aria_memory.db",Context.MODE_PRIVATE,null)) {
            db.execSQL("CREATE TABLE IF NOT EXISTS aria_answer_quality ("+
                "task_id TEXT PRIMARY KEY, reason TEXT NOT NULL,"+
                "raw_chars INTEGER NOT NULL, clean_chars INTEGER NOT NULL,"+
                "recorded_at INTEGER NOT NULL)");
            try(Cursor c=db.rawQuery(
                "SELECT reason,raw_chars,clean_chars,recorded_at FROM aria_answer_quality "+
                "ORDER BY recorded_at DESC LIMIT 12",null)) {
                if(!c.moveToFirst())report.append("Brak pomiarów. Zadaj pytanie ARII.");
                else do {
                    report.append(new java.text.SimpleDateFormat("dd.MM HH:mm",Locale.ROOT)
                        .format(new java.util.Date(c.getLong(3))))
                        .append(" | ").append(c.getString(0))
                        .append(" | surowa: ").append(c.getInt(1))
                        .append(" znaków | po czyszczeniu: ").append(c.getInt(2))
                        .append(" znaków\n");
                }while(c.moveToNext());
            }
        }catch(Exception e){report.append("Błąd diagnostyki: ").append(e.getClass().getSimpleName());}
        new AlertDialog.Builder(context).setTitle("ARIA — kontrola odpowiedzi")
            .setMessage(report.toString()).setPositiveButton("Zamknij",null).show();
    }
}
