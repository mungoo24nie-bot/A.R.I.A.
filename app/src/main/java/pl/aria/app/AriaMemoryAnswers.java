package pl.aria.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

/** Direct, source-grounded recall. No LLM inference for exact memory questions. */
final class AriaMemoryAnswers {
    private AriaMemoryAnswers() {}
    static String answer(SQLiteDatabase db,String question,long currentEvent) {
        String q=DialogueEngine.normalize(question);
        if(q.contains("jak mam na imie")||q.contains("pamietasz moje imie")||q.contains("jak sie nazywam")) {
            try(Cursor c=db.rawQuery("SELECT fact_value,source_event_id FROM facts WHERE fact_key='user_name'",null)) {
                if(c.moveToFirst())return "Zapamiętałam, że masz na imię "+c.getString(0)+". Źródło: wiadomość #"+c.getLong(1)+".";
            }
            return "Nie mam jeszcze potwierdzonego zapisu Twojego imienia w tabeli faktów.";
        }
        boolean summary=(q.contains("co pamietasz")||q.contains("co o mnie wiesz")||q.contains("co wiesz o mnie")||q.contains("wczesniejszych rozmow")||q.contains("naszych rozmow"));
        if(!summary)return null;
        StringBuilder b=new StringBuilder("Znalazłam w mojej lokalnej pamięci:\n");
        int n=0;
        try(Cursor c=db.rawQuery("SELECT fact_key,fact_value,source_event_id FROM facts ORDER BY updated_at DESC LIMIT 8",null)) {
            while(c.moveToNext()) {b.append("• ").append(c.getString(0)).append(": ").append(c.getString(1))
                .append(" [wiadomość #").append(c.getLong(2)).append("]\n");n++;}
        }catch(Exception ignored){}
        // User statements only. Assistant replies are never treated as facts.
        LinkedHashSet<String> snippets=new LinkedHashSet<>();
        try(Cursor c=db.rawQuery("SELECT id,content FROM events WHERE type='user' AND id<? ORDER BY id DESC LIMIT 70",new String[]{String.valueOf(currentEvent)})) {
            while(c.moveToNext()&&snippets.size()<5) {
                String t=c.getString(1).trim();
                if(t.length()<18||t.contains("?")||t.length()>450)continue;
                String norm=DialogueEngine.normalize(t);
                if(norm.startsWith("powiedz")||norm.startsWith("aria")||norm.startsWith("sprawdz"))continue;
                snippets.add("• Wiadomość #"+c.getLong(0)+": "+t.substring(0,Math.min(140,t.length())));
            }
        }
        for(String snippet:snippets){b.append(snippet).append('\n');n++;}
        try(Cursor c=db.rawQuery("SELECT title FROM library_docs ORDER BY id DESC LIMIT 3",null)) {
            while(c.moveToNext()) {b.append("• Dokument w bibliotece: ").append(c.getString(0)).append('\n');n++;}
        }catch(Exception ignored){}
        if(n==0)return "W bazie nie znalazłam potwierdzonych faktów ani wcześniejszych wypowiedzi użytkownika. Sprawdź kronikę lub import pamięci.";
        b.append("To zapisane źródła, a nie moje domysły. Nie wszystkie wypowiedzi są trwałymi faktami.");
        return b.toString();
    }
}
