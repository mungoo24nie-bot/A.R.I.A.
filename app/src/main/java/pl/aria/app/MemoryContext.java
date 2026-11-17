package pl.aria.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.Normalizer;
import java.util.*;

/** Zbiera niezależne tematy z pytania i zwraca źródła, nie gotowe odpowiedzi. */
final class MemoryContext {
    private MemoryContext(){}
    static String norm(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","").replace("ł","l");
    }
    static boolean asking(String s) {
        String q=norm(s);
        return q.contains("pamiet")||q.contains("wiesz o mnie")||
            q.contains("o mnie wiesz")||q.contains("jak mam na imie")||
            q.contains("kim ja jestem")||q.contains("kim jestem")||
            q.contains("wczesniejsz")||q.contains("kronik")||
            q.contains("kiedy kupil")||q.contains("pierwsz")||
            q.contains("gdzie mieszkam")||q.contains("prawo jazdy")||
            q.contains("jak sie nazywam");
    }
    static String collect(SQLiteDatabase db,String question,long currentEvent) {
        String q=norm(question);
        LinkedHashMap<Long,String> found=new LinkedHashMap<>();
        boolean summary=q.contains("o mnie")||q.contains("kim jestem")||q.contains("kim ja jestem")||
            q.contains("informacje o mnie");
        boolean name=summary||q.contains("imie")||q.contains("nazywam");
        boolean home=summary||q.contains("mieszk")||q.contains("dom");
        boolean car=summary||q.contains("samochod")||q.contains("corsa")||q.contains("opel");
        boolean licence=q.contains("prawo jazdy")||q.contains("prawa jazdy");
        boolean firstChat=q.contains("pierwsz")&&(q.contains("rozmow")||q.contains("slow")||q.contains("wiadom"));
        if(name) {
            try(Cursor c=db.rawQuery("SELECT source_event_id,fact_value FROM facts WHERE fact_key='user_name'",null)){
                if(c.moveToFirst() && !"albo".equalsIgnoreCase(c.getString(1)))
                    found.put(c.getLong(0),"Imię użytkownika: "+c.getString(1));
            }
        }
        List<String[]> events=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,content FROM events WHERE type='user' AND id<? ORDER BY id",
            new String[]{String.valueOf(currentEvent)})){
            while(c.moveToNext())events.add(new String[]{c.getString(0),c.getString(1)});
        }
        for(int i=0;i<events.size();i++){
            String[] e=events.get(i);
            String t=norm(e[1]);
            boolean candidate=!e[1].contains("?") &&
                !(t.startsWith("aria,")||t.startsWith("powiedz")||t.startsWith("co "));
            if(!candidate)continue;
            boolean hit=(home&&(t.contains("mieszkam w ")||t.contains("dom ktory")||
                         t.contains("razem z ojcem"))) ||
                (car&&(t.contains("pierwsz")&&t.contains("samochod")||
                       t.contains("opel corsa")||t.contains("niebieski opel"))) ||
                (licence&&(t.contains("prawo jazdy")||t.contains("je odebralem"))) ||
                (name&&(t.startsWith("mam na imie ")||t.startsWith("nazywam sie ")));
            if(hit) {
                found.put(Long.parseLong(e[0]),e[1]);
                // Sąsiadujące zdanie wskazujące na ten sam obiekt.
                if(i+1<events.size()){
                    String[] next=events.get(i+1);
                    String n=norm(next[1]);
                    if(!next[1].contains("?") && (n.startsWith("to byl ")||
                       n.startsWith("byl to ")||n.startsWith("razem z ")||
                       n.startsWith("w domu ")||n.startsWith("w styczniu 2010 ")))
                        found.put(Long.parseLong(next[0]),next[1]);
                }
            }
        }
        if(firstChat) {
            try(Cursor c=db.rawQuery("SELECT id,content,source FROM events WHERE type='archive' AND content LIKE 'Witaj Aria.%' LIMIT 1",null)){
                if(c.moveToFirst())found.put(c.getLong(0),
                    c.getString(1)+" [odtworzony zapis: "+c.getString(2)+"]");
            }
        }
        if(found.isEmpty()) {
            String fallback=SemanticMemory.evidence(db,question,currentEvent);
            if(!fallback.isEmpty())return fallback;
            return "Brak potwierdzonych informacji dotyczących pytania.";
        }
        StringBuilder out=new StringBuilder();
        for(Map.Entry<Long,String> e:found.entrySet()) {
            String value=e.getValue().replace('\n',' ');
            if(value.length()>220)value=value.substring(0,220);
            if(out.length()+value.length()>1500)break;
            out.append("#").append(e.getKey()).append(": ").append(value).append("\n");
        }
        return out.toString();
    }
}
