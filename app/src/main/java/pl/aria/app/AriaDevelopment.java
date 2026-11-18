package pl.aria.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.Intent;
import android.app.AlertDialog;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Read-only self-audit and human-approved export of change proposals. No self-installation. */
final class AriaDevelopment {
    private AriaDevelopment() {}
    static String report(Context ctx, SQLiteDatabase db) {
        StringBuilder s=new StringBuilder("A.R.I.A. — raport samorozwoju\n");
        s.append("Utworzono: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT).format(new Date())).append("\n\n");
        s.append("Silnik: lokalny model GGUF; brak niezależnego kompilatora APK.\n");
        s.append("Zasada: propozycje nie są wykonywane ani instalowane automatycznie.\n\n");
        int user=count(db,"SELECT count(*) FROM events WHERE type='user'");
        int answers=count(db,"SELECT count(*) FROM events WHERE type='aria'");
        int errors=count(db,"SELECT count(*) FROM events WHERE type='aria' AND (content LIKE '%Nie udało się%' OR content LIKE '%nie mam możliwości%')");
        s.append("Wiadomości użytkownika: ").append(user).append("\nOdpowiedzi: ").append(answers).append("\nWykryte odpowiedzi błędne: ").append(errors).append("\n\n");
        s.append("PROPOZYCJE ZMIAN (do oceny człowieka):\n");
        if(errors>0)s.append("1. Zbadać błędy odpowiedzi i dołączyć testy regresyjne.\n");
        s.append("2. Zmierzyć szybkość tokenów i opóźnienia przy wygaszonym ekranie.\n");
        s.append("3. Przetestować połączenie HTTPS i obsługę źródeł sieciowych.\n");
        s.append("4. Weryfikować odpowiedzi z biblioteki względem rzeczywistych fragmentów.\n");
        s.append("5. Rozwijać nowe moduły w osobnej gałęzi GitHub, uruchamiać testy CI i wymagać zatwierdzenia.\n");
        s.append("\nTELEMETRIA PRACY W TLE:\n");
        try(Cursor c=db.rawQuery("SELECT duration_ms,cpu_ms,load_ms,generate_ms,status FROM aria_telemetry ORDER BY id DESC LIMIT 5",null)){
            int n=0;
            while(c.moveToNext()){
                n++;
                s.append("Zadanie ").append(n).append(": całkowity ")
                 .append(c.getLong(0)/1000).append(" s; CPU ")
                 .append(c.getLong(1)/1000).append(" s; model ")
                 .append(c.getLong(2)/1000).append(" s; generowanie ")
                 .append(c.getLong(3)/1000).append(" s; status ")
                 .append(c.getString(4)).append("\n");
            }
            if(n==0)s.append("Brak ukończonych zadań po aktualizacji.\n");
        }catch(Exception e){s.append("Dane pojawią się po pierwszym ukończonym zadaniu.\n");}
        s.append("\nPROPOZYCJE STRUKTURALNE (JSON):\n").append(AriaSelfDevelopment.proposal(db));
        s.append("\nSTATUS: propozycja do akceptacji. Nie zmieniono kodu ani nie wysłano danych do sieci.");
        return s.toString();
    }
    private static int count(SQLiteDatabase db,String sql){try(Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getInt(0):0;}catch(Exception e){return -1;}}
    static void show(Context ctx,SQLiteDatabase db){
        final String report=report(ctx,db);
        new AlertDialog.Builder(ctx).setTitle("Samorozwój ARII — audyt i propozycje")
            .setMessage(report).setNegativeButton("Zamknij",null)
            .setPositiveButton("Zatwierdź eksport",(d,w)->{
                Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT,"ARIA — propozycje rozwoju do przeglądu");
                send.putExtra(Intent.EXTRA_TEXT,report);
                ctx.startActivity(Intent.createChooser(send,"Wyślij raport do przeglądu"));
            }).show();
    }
}
