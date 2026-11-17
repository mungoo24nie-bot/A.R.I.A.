package pl.aria.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Metryki bez modyfikowania danych i bez udawania nauki wag modelu. */
final class AriaDiagnostics {
    private AriaDiagnostics() {}
    static String report(SQLiteDatabase db) {
        long users=0, answers=0, corrections=0;
        try(Cursor c=db.rawQuery(
            "SELECT type,COUNT(*) FROM events GROUP BY type",null)) {
            while(c.moveToNext()) {
                if("user".equals(c.getString(0)))users=c.getLong(1);
                if("aria".equals(c.getString(0)))answers=c.getLong(1);
            }
        }
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM corrections",null)) {
            if(c.moveToFirst())corrections=c.getLong(0);
        }
        return "Wiadomości użytkownika: "+users+"\nOdpowiedzi ARII: "+answers+
            "\nZapisane korekty: "+corrections+
            "\nTryb diagnostyczny uruchamia model bez kroniki i wspomnień."+
            "\nLiczba wiadomości nie oznacza liczby zdobytych umiejętności.";
    }
}
