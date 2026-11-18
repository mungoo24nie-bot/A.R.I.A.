package pl.aria.app;

import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Locale;

/** Diagnostyka oparta na rzeczywistych pomiarach. Nie udaje liczby tokenów. */
final class AriaPerformance {
    private AriaPerformance() {}
    static void show(Context context, SQLiteDatabase db) {
        StringBuilder s=new StringBuilder();
        s.append("Czasy ścienne i CPU mogą się różnić: CPU sumuje pracę wątków.\\n");
        s.append("Bez zmian JNI nie znamy liczby tokenów ani prędkości tokenów/s.\\n\\n");
        try(Cursor c=db.rawQuery(
            "SELECT wall_ms,cpu_ms,model_load_ms,generation_ms,rejected,error_type,prompt_chars,answer_chars FROM aria_inference_diagnostics ORDER BY recorded_at DESC LIMIT 12",null)){
            int count=0; long sumWall=0,sumGeneration=0;int rejected=0;
            while(c.moveToNext()){
                count++;long wall=c.getLong(0),cpu=c.getLong(1),load=c.getLong(2),gen=c.getLong(3);
                sumWall+=wall;sumGeneration+=gen;rejected+=c.getInt(4);
                s.append("#").append(count).append(" całk. ").append(ms(wall))
                    .append(" | CPU ").append(ms(cpu))
                    .append(" | model ").append(ms(load))
                    .append(" | gener. ").append(ms(gen))
                    .append(" | prompt ").append(c.getInt(6)).append(" znaków")
                    .append(" | odp. ").append(c.getInt(7)).append(" znaków");
                if(c.getInt(4)!=0)s.append(" | ODRZUCONA");
                if(c.getString(5)!=null&&!c.getString(5).isEmpty())
                    s.append(" | BŁĄD ").append(c.getString(5));
                s.append("\\n");
            }
            if(count==0)s.append("Brak pomiarów. Zadaj pytanie po aktualizacji.\\n");
            else s.insert(0,"Próbek: "+count+" | średni czas: "+ms(sumWall/count)+
                " | średnie generowanie: "+ms(sumGeneration/count)+
                " | odrzucone: "+rejected+"\\n\\n");
        }catch(Exception e){
            s.append("Brak tabeli pomiarowej. Dane pojawią się po pierwszym zadaniu.\\n");
        }
        new AlertDialog.Builder(context).setTitle("ARIA — wydajność silnika")
            .setMessage(s.toString()).setPositiveButton("Zamknij",null).show();
    }
    private static String ms(long ms){
        return String.format(Locale.ROOT,"%.1f s",ms/1000.0);
    }
}
