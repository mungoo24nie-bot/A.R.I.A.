package pl.aria.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Dziennik trwałych stanów zadań; nie przechowuje treści prywatnych pytań. */
final class AriaTaskJournal {
    private AriaTaskJournal() {}
    private static void schema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS aria_task_journal ("+
            "task_id TEXT PRIMARY KEY, started_at INTEGER NOT NULL, finished_at INTEGER,"+
            "status TEXT NOT NULL, phase TEXT NOT NULL, error TEXT)");
    }
    // Dane do wznowienia pozostają tylko w prywatnej bazie aplikacji.
    // Wznowienie jest świadomym wyborem użytkownika, a nie cichym ponowieniem.
    private static void recoverySchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS aria_pending_recovery ("+
            "task_id TEXT PRIMARY KEY, prompt TEXT NOT NULL, question TEXT, " +
            "max_tokens INTEGER NOT NULL, diagnostic INTEGER NOT NULL)");
    }
    static void saveRecovery(Context context,String id,String prompt,String question,int max,boolean diagnostic) {
        if(prompt==null || prompt.isEmpty())return;
        try(SQLiteDatabase db=context.openOrCreateDatabase("aria_memory.db",Context.MODE_PRIVATE,null)) {
            recoverySchema(db);
            ContentValues v=new ContentValues();
            v.put("task_id",id);v.put("prompt",prompt);v.put("question",question);
            v.put("max_tokens",max);v.put("diagnostic",diagnostic?1:0);
            db.insertWithOnConflict("aria_pending_recovery",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        }catch(Exception ignored){}
    }
    static void clearRecovery(Context context,String id) {
        try(SQLiteDatabase db=context.openOrCreateDatabase("aria_memory.db",Context.MODE_PRIVATE,null)) {
            recoverySchema(db);
            db.delete("aria_pending_recovery","task_id=?",new String[]{id});
        }catch(Exception ignored){}
    }
    static Intent recoverable(Context context) {
        try(SQLiteDatabase db=context.openOrCreateDatabase("aria_memory.db",Context.MODE_PRIVATE,null)) {
            schema(db);recoverySchema(db);
            try(Cursor c=db.rawQuery(
                "SELECT r.task_id,r.prompt,r.question,r.max_tokens,r.diagnostic " +
                "FROM aria_pending_recovery r JOIN aria_task_journal j " +
                "ON j.task_id=r.task_id WHERE j.status='interrupted' " +
                "ORDER BY j.started_at DESC LIMIT 1",null)) {
                if(!c.moveToFirst())return null;
                return new Intent(context,AriaService.class)
                    .putExtra("recovery_task_id",c.getString(0))
                    .putExtra("prompt",c.getString(1))
                    .putExtra("question",c.getString(2))
                    .putExtra("max_tokens",c.getInt(3))
                    .putExtra("diagnostic",c.getInt(4)!=0);
            }
        }catch(Exception ignored){return null;}
    }
    static void interrupted(Context context) {
        try (SQLiteDatabase db=context.openOrCreateDatabase("aria_memory.db",Context.MODE_PRIVATE,null)) {
            schema(db);
            ContentValues v=new ContentValues();
            v.put("status","interrupted");
            v.put("phase","Proces Androida został zatrzymany przed ukończeniem");
            v.put("finished_at",System.currentTimeMillis());
            db.update("aria_task_journal",v,"status=?",new String[]{"running"});
        } catch(Exception ignored) {}
    }
    static void start(Context context,String id,long started) {
        try (SQLiteDatabase db=context.openOrCreateDatabase("aria_memory.db",Context.MODE_PRIVATE,null)) {
            schema(db);
            ContentValues v=new ContentValues();
            v.put("task_id",id);v.put("started_at",started);
            v.put("status","running");v.put("phase","starting");
            db.insertWithOnConflict("aria_task_journal",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        } catch(Exception ignored) {}
    }
    static void phase(Context context,String id,String phase) {
        if(id==null||id.isEmpty())return;
        try (SQLiteDatabase db=context.openOrCreateDatabase("aria_memory.db",Context.MODE_PRIVATE,null)) {
            schema(db);
            ContentValues v=new ContentValues();v.put("phase",phase);
            db.update("aria_task_journal",v,"task_id=? AND status=?",
                new String[]{id,"running"});
        } catch(Exception ignored) {}
    }
    static void finish(Context context,String id,String status,String error) {
        try (SQLiteDatabase db=context.openOrCreateDatabase("aria_memory.db",Context.MODE_PRIVATE,null)) {
            schema(db);
            ContentValues v=new ContentValues();
            v.put("status",status);v.put("phase","finished");
            v.put("finished_at",System.currentTimeMillis());v.put("error",error);
            db.update("aria_task_journal",v,"task_id=?",new String[]{id});
        } catch(Exception ignored) {}
    }
    static String report(Context context) {
        StringBuilder b=new StringBuilder();
        try (SQLiteDatabase db=context.openOrCreateDatabase("aria_memory.db",Context.MODE_PRIVATE,null)) {
            schema(db);
            try(Cursor c=db.rawQuery(
                "SELECT status,phase,started_at,finished_at,error FROM aria_task_journal "+
                "ORDER BY started_at DESC LIMIT 15",null)) {
                while(c.moveToNext()){
                    b.append(c.getString(0)).append(" | ").append(c.getString(1))
                        .append(" | ").append(new java.util.Date(c.getLong(2)));
                    if(!c.isNull(3))b.append(" | ").append(
                        (c.getLong(3)-c.getLong(2))/1000).append(" s");
                    if(!c.isNull(4))b.append(" | ").append(c.getString(4));
                    b.append("\\n");
                }
            }
        } catch(Exception e){return "Nie udało się odczytać dziennika: "+e.getClass().getSimpleName();}
        return b.length()==0?"Nie ma jeszcze zarejestrowanych zadań.":b.toString();
    }
}
