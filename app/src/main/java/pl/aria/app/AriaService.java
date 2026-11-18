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

/** Single foreground inference worker, elapsed timer and completion notification. */
public final class AriaService extends Service {
    private static final String CHANNEL="aria_processing_v191";
    private static final AtomicBoolean WORKING=new AtomicBoolean(false);
    static boolean isWorking(){return WORKING.get();}
    private static final int NOTIFICATION_ID=1808;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long startElapsed,startWall,startCpu;
    private volatile String taskId="";
    private volatile String phase="Przygotowuję odpowiedź";
    private PowerManager.WakeLock wakeLock;
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(WORKING.get()){
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID,
                notice(phase, "Czas: "+duration(SystemClock.elapsedRealtime()-startElapsed)+" | CPU: "+duration(android.os.Process.getElapsedCpuTime()-startCpu),true,startWall));
            handler.postDelayed(this,1000);
        }
    }};
    @Override public void onCreate(){super.onCreate();
        NotificationChannel ch=new NotificationChannel(CHANNEL,"ARIA — przetwarzanie",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }
    private static String duration(long ms){long sec=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%02d:%02d",sec/60,sec%60);}
    private Notification notice(String text,String details,boolean ongoing,long when){
        PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("A.R.I.A. — "+text).setContentText(details)
            .setContentIntent(pi).setOngoing(ongoing).setShowWhen(true).setWhen(when)
            .setOnlyAlertOnce(ongoing).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        if(!WORKING.compareAndSet(false,true))return START_NOT_STICKY;
        startElapsed=SystemClock.elapsedRealtime();startWall=System.currentTimeMillis();startCpu=android.os.Process.getElapsedCpuTime();taskId=java.util.UUID.randomUUID().toString();
        startForeground(NOTIFICATION_ID,notice("Myślę nad odpowiedzią","Czas przetwarzania: 00:00",true,startWall));
        handler.postDelayed(tick,1000);
        try{
            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
            wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"pl.aria.app:Inference");
            wakeLock.acquire(30*60*1000L);
        }catch(Exception ignored){}
        final String prompt=intent.getStringExtra("prompt");
        final String question=intent.getStringExtra("question");
        final boolean diagnostic=intent.getBooleanExtra("diagnostic",false);
        final int max=intent.getIntExtra("max_tokens",120);
        new Thread(()->{
            String answer;String source="model lokalny — ARIA 2.0";
            long t0=SystemClock.elapsedRealtime();
            long loadMs=0,generateMs=0;
            try {
                if(prompt==null||prompt.isEmpty())throw new IllegalArgumentException("Brak pytania");
                if(question!=null && AriaInternet.requested(question)) {
                    phase="Pobieram wyniki wyszukiwania";
                    // Search results are presented as attributed excerpts, never invented by the model.
                    String evidence=AriaInternet.fetch(question);
                    answer="Znalazłam następujące wyniki internetowe. To fragmenty, nie potwierdzone fakty.\n\n"+evidence;
                    source="wyniki internetu na polecenie — ARIA 2.0";
                } else {
                    phase="Wczytuję lokalny model";
                    java.io.File model=new java.io.File(getFilesDir(),"aria-qwen.gguf");
                    if(!MainActivity.nativeLoadModel(model.getAbsolutePath()))throw new IllegalStateException("Nie udało się wczytać modelu GGUF");
                    loadMs=SystemClock.elapsedRealtime()-t0;
                    phase="Generuję odpowiedź lokalnie";
                    long generationStart=SystemClock.elapsedRealtime();
                    byte[] bytes=MainActivity.nativeGenerate(prompt,max);
                    generateMs=SystemClock.elapsedRealtime()-generationStart;
                    if(bytes==null)throw new IllegalStateException("Silnik modelu nie zwrócił odpowiedzi");
                    answer=DialogueEngine.clean(new String(bytes,StandardCharsets.UTF_8));
                    if(DialogueEngine.invalid(answer,question==null?"":question))
                        answer="Nie udało mi się przygotować wiarygodnej odpowiedzi. Nie będę zgadywać.";
                }
            }catch(Throwable e){
                answer="Nie udało się zakończyć zadania: "+e.getClass().getSimpleName()+": "+e.getMessage();
                source="błąd przetwarzania — ARIA 2.0";
            }
            phase="Zapisuję odpowiedź";
            boolean saved=false;
            try(SQLiteDatabase db=openOrCreateDatabase("aria_memory.db",MODE_PRIVATE,null)){
                ContentValues v=new ContentValues();
                v.put("created_at",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ",Locale.ROOT).format(new Date()));
                v.put("type","aria");v.put("content",answer);v.put("source",source);
                saved=db.insert("events",null,v)>0;
                db.execSQL("CREATE TABLE IF NOT EXISTS aria_telemetry (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id TEXT, created_at INTEGER, duration_ms INTEGER, cpu_ms INTEGER, load_ms INTEGER, generate_ms INTEGER, status TEXT, source TEXT)");
                ContentValues m=new ContentValues();
                m.put("task_id",taskId);m.put("created_at",startWall);
                m.put("duration_ms",SystemClock.elapsedRealtime()-startElapsed);
                m.put("cpu_ms",android.os.Process.getElapsedCpuTime()-startCpu);
                m.put("load_ms",loadMs);m.put("generate_ms",generateMs);
                m.put("status",answer.startsWith("Nie udało się")?"error":"complete");
                m.put("source",source);
                db.insert("aria_telemetry",null,m);
            }catch(Exception ignored){}
            long elapsed=SystemClock.elapsedRealtime()-startElapsed;
            try{
                handler.removeCallbacks(tick);
                WORKING.set(false);
                if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
                stopForeground(STOP_FOREGROUND_REMOVE);
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID+1,
                    notice(saved?"Odpowiedź gotowa":"Błąd zapisu odpowiedzi",
                        "Czas: "+duration(elapsed)+" | CPU: "+duration(android.os.Process.getElapsedCpuTime()-startCpu),false,System.currentTimeMillis()));
            }finally{stopSelfResult(startId);}
        },"ARIA-Inference-1.9.2").start();
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){handler.removeCallbacks(tick);if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();super.onDestroy();}
}
