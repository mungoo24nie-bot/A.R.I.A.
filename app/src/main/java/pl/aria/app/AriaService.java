package pl.aria.app;

import android.app.*;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Jedno zadanie naraz; foreground service działa także po opuszczeniu Activity. */
public final class AriaService extends Service {
    private static final String CHANNEL="aria_processing_v1";
    private static final AtomicBoolean WORKING=new AtomicBoolean(false);
    static boolean isWorking(){return WORKING.get();}
    private Thread worker;
    @Override public void onCreate(){super.onCreate();
        NotificationChannel channel=new NotificationChannel(CHANNEL,"ARIA — przetwarzanie",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
    private Notification notice(String message,boolean ongoing){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("A.R.I.A.").setContentText(message)
            .setContentIntent(pi).setOngoing(ongoing).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        if(!WORKING.compareAndSet(false,true)){
            return START_NOT_STICKY;
        }
        startForeground(1808,notice("Analizuję pytanie w tle…",true));
        String prompt=intent.getStringExtra("prompt");
        String question=intent.getStringExtra("question");
        boolean diagnostic=intent.getBooleanExtra("diagnostic",false);
        int max=intent.getIntExtra("max_tokens",120);
        worker=new Thread(()->{
            String answer;
            try{
                if(prompt==null||prompt.isEmpty())throw new IllegalArgumentException("Brak pytania");
                java.io.File model=new java.io.File(getFilesDir(),"aria-qwen.gguf");
                if(!MainActivity.nativeLoadModel(model.getAbsolutePath()))throw new IllegalStateException("Model GGUF nie został wczytany");
                byte[] result=MainActivity.nativeGenerate(prompt,max);
                if(result==null)throw new IllegalStateException("Błąd silnika modelu");
                answer=DialogueEngine.clean(new String(result,StandardCharsets.UTF_8));
                if(DialogueEngine.invalid(answer,question==null?"":question))
                    answer="Nie udało mi się przygotować poprawnej odpowiedzi. Spróbuj zapytać inaczej.";
            }catch(Exception e){answer="Błąd przetwarzania: "+e.getMessage();}
            try(SQLiteDatabase db=openOrCreateDatabase("aria_memory.db",MODE_PRIVATE,null)){
                android.content.ContentValues values=new android.content.ContentValues();
                values.put("created_at",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ",Locale.ROOT).format(new Date()));
                values.put("type","aria");values.put("content",answer);
                values.put("source",diagnostic?"diagnostyka — ARIA 1.8":"model lokalny — ARIA 1.8");
                db.insert("events",null,values);
            }catch(Exception ignored){}
            WORKING.set(false);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1809,notice("Odpowiedź gotowa — dotknij, aby przeczytać",false));
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelfResult(startId);
        },"ARIA-Background-Inference");
        worker.start();
        return START_NOT_STICKY;
    }
    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
