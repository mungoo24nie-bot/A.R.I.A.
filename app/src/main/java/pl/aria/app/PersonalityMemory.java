package pl.aria.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Locale;

/** Uczenie preferencji przez jawne wskazówki użytkownika, bez zmiany wag modelu. */
final class PersonalityMemory {
    private PersonalityMemory(){}
    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS aria_preferences ("+
            "key TEXT PRIMARY KEY,value TEXT NOT NULL,source_event_id INTEGER NOT NULL)");
    }
    static boolean learn(SQLiteDatabase db,String message,long eventId) {
        String n=MemoryContext.norm(message.trim());
        String key=null, value=null;
        if(n.startsWith("aria, mow do mnie ")||n.startsWith("mow do mnie ")){
            key="address";value=message.substring(message.toLowerCase(Locale.ROOT).indexOf("mnie ")+5).trim();
        }else if(n.startsWith("aria, odpowiadaj ")||n.startsWith("odpowiadaj ")){
            key="style";value=message.substring(message.toLowerCase(Locale.ROOT).indexOf("odpowiadaj ")+11).trim();
        }else if(n.startsWith("wole zebys ")||n.startsWith("chce zebys ")){
            key="preference";value=message.trim();
        }
        if(key==null||value==null||value.length()<3||value.length()>170||value.contains("?"))return false;
        ContentValues v=new ContentValues();
        v.put("key",key);v.put("value",value);v.put("source_event_id",eventId);
        db.insertWithOnConflict("aria_preferences",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        return true;
    }
    static String prompt(SQLiteDatabase db) {
        StringBuilder b=new StringBuilder();
        try(Cursor c=db.rawQuery("SELECT key,value FROM aria_preferences ORDER BY key",null)){
            while(c.moveToNext()){
                if(b.length()>400)break;
                b.append(c.getString(0)).append(": ").append(c.getString(1).replace('\n',' ')).append("\n");
            }
        }
        return b.toString();
    }
    static String overview(SQLiteDatabase db) {
        String s=prompt(db);
        return s.isEmpty()?"Nie zapisano jeszcze preferencji rozmowy.":
            "Zapamiętane preferencje:\n"+s+
            "\nZmiana preferencji: napisz np. „Odpowiadaj krótko i konkretnie”.";
    }
}
