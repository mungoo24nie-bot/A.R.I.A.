package pl.aria.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;

/** Wytwarza ustrukturyzowane propozycje zmian; nie wykonuje ich i nie wysyła danych. */
final class AriaSelfDevelopment {
    private AriaSelfDevelopment() {}
    static String proposal(SQLiteDatabase db) {
        try {
            JSONObject report=new JSONObject();
            report.put("schema","aria-development-proposal/v1");
            report.put("requires_user_approval",true);
            report.put("can_modify_installed_apk",false);
            JSONArray changes=new JSONArray();
            try(Cursor c=db.rawQuery("SELECT status,duration_ms,cpu_ms,load_ms,generate_ms FROM aria_telemetry ORDER BY id DESC LIMIT 20",null)){
                long slow=0,fail=0,count=0;
                while(c.moveToNext()){
                    count++;if(c.getLong(1)>120000)slow++;
                    if("error".equals(c.getString(0)))fail++;
                }
                report.put("observed_tasks",count);
                if(slow>0)changes.put(change("performance","Zbadaj długie zadania",slow+" zadań trwało ponad 120 sekund"));
                if(fail>0)changes.put(change("reliability","Zbadaj błędy zadań",fail+" zadań zakończyło się błędem"));
            }catch(Exception e){report.put("telemetry","not_available");}
            changes.put(change("testing","Uruchom kompilację CI i testy regresyjne","Bez podpisanego APK nie wdrażaj zmian"));
            report.put("proposals",changes);
            return report.toString(2);
        }catch(Exception e){return "{\"error\":\"proposal_generation_failed\"}";}
    }
    private static JSONObject change(String area,String title,String reason)throws Exception{
        JSONObject x=new JSONObject();x.put("area",area);x.put("title",title);
        x.put("evidence",reason);x.put("status","proposed_not_applied");return x;
    }
}
