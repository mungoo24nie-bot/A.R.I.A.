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
    private static final String ACTION_CANCEL="pl.aria.app.CANCEL_INFERENCE";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long startElapsed,startWall,startCpu;
    private volatile String taskId="";
    private volatile boolean cancelRequested=false;
    private volatile String phase="Przygotowuję odpowiedź";
    private volatile long phaseStarted=0;
    private volatile long phaseCpuStarted=0;
    private volatile long lastPhaseDuration=0;
    private void setPhase(String next){
        long now=SystemClock.elapsedRealtime();
        lastPhaseDuration=phaseStarted==0?0:now-phaseStarted;
        phaseStarted=now;
        phaseCpuStarted=android.os.Process.getElapsedCpuTime();
        phase=next;
    }
    private PowerManager.WakeLock wakeLock;
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(WORKING.get()){
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID,
                notice(phase, progressText(),true,startWall));
            handler.postDelayed(this,1000);
        }
    }};
    private String progressText(){
        long[] stats=MainActivity.nativeInferenceStats();
        String detail="Łącznie: "+duration(SystemClock.elapsedRealtime()-startElapsed);
        if(stats!=null && stats.length>=5 && stats[0]>=1 && stats[0]<=3){
            long generated=stats[2],ms=stats[4];
            detail+=" | tokeny: "+generated;
            if(ms>0)detail+=" | "+String.format(Locale.ROOT,"%.2f tok/s",1000.0*generated/ms);
        }
        return detail;
    }
    @Override public void onCreate(){super.onCreate();
        NotificationChannel ch=new NotificationChannel(CHANNEL,"ARIA — przetwarzanie",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }
    private static String duration(long ms){long sec=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%02d:%02d",sec/60,sec%60);}
    private Notification notice(String text,String details,boolean ongoing,long when){
        PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("A.R.I.A. — "+text).setContentText(details)
            .setContentIntent(pi).setOngoing(ongoing).setShowWhen(true).setWhen(when)
            .setOnlyAlertOnce(ongoing);
        if(ongoing){
            Intent cancel=new Intent(this,AriaService.class).setAction(ACTION_CANCEL);
            PendingIntent action=PendingIntent.getService(this,1,cancel,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,"Anuluj",action);
        }
        return builder.build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        if(ACTION_CANCEL.equals(intent.getAction())){
            cancelRequested=true;
            MainActivity.nativeCancelGeneration();
            return START_NOT_STICKY;
        }
        if(!WORKING.compareAndSet(false,true)) {
            // Nie porzucaj po cichu nowego pytania, kiedy poprzednie nadal trwa.
            return START_NOT_STICKY;
        }
        cancelRequested=false;
        MainActivity.nativeResetCancellation();
        startElapsed=SystemClock.elapsedRealtime();startWall=System.currentTimeMillis();startCpu=android.os.Process.getElapsedCpuTime();taskId=java.util.UUID.randomUUID().toString();setPhase("Przygotowuję odpowiedź");
        startForeground(NOTIFICATION_ID,notice("Myślę nad odpowiedzią","Czas przetwarzania: 00:00",true,startWall));
        handler.postDelayed(tick,1000);
        try{
            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
            wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"pl.aria.app:Inference");
            wakeLock.acquire(20*60*1000L);
        }catch(Exception ignored){}
        final String prompt=intent.getStringExtra("prompt");
        final String question=intent.getStringExtra("question");
        final boolean diagnostic=intent.getBooleanExtra("diagnostic",false);
        final int max=intent.getIntExtra("max_tokens",120);
        new Thread(()->{
            String answer;String source="model lokalny — ARIA 2.0.2";
            long t0=SystemClock.elapsedRealtime();
            long loadMs=0,generateMs=0; boolean rejected=false; String errorType="";
            long[] nativeStats=null;
            try {
                if((prompt==null||prompt.isEmpty()) && (question==null||!AriaInternet.requested(question)))throw new IllegalArgumentException("Brak pytania");
                if(question!=null && AriaInternet.requested(question)) {
                    setPhase("Pobieram wyniki wyszukiwania");
                    // Search results are presented as attributed excerpts, never invented by the model.
                    String evidence=AriaInternet.fetch(question);
                    answer="Znalazłam następujące wyniki internetowe. To fragmenty, nie potwierdzone fakty.\n\n"+evidence;
                    source="wyniki internetu na polecenie — ARIA 2.0.2";
                } else {
                    setPhase("Wczytuję lokalny model");
                    java.io.File model=new java.io.File(getFilesDir(),"aria-qwen.gguf");
                    if(!MainActivity.nativeLoadModel(model.getAbsolutePath()))throw new IllegalStateException("Nie udało się wczytać modelu GGUF");
                    if(cancelRequested)throw new InterruptedException("Anulowano podczas ładowania modelu");
                    loadMs=SystemClock.elapsedRealtime()-t0;
                    setPhase("Generuję odpowiedź lokalnie");
                    long generationStart=SystemClock.elapsedRealtime();
                    byte[] bytes=MainActivity.nativeGenerate(prompt,max);
                    generateMs=SystemClock.elapsedRealtime()-generationStart;
                    nativeStats=MainActivity.nativeInferenceStats();
                    if(cancelRequested){
                        answer="Generowanie anulowano na Twoje polecenie.";
                        source="anulowane przez użytkownika";
                    } else if(bytes==null)throw new IllegalStateException("Silnik modelu nie zwrócił odpowiedzi");
                    else {
                    answer=DialogueEngine.clean(new String(bytes,StandardCharsets.UTF_8));
                    if(DialogueEngine.invalid(answer,question==null?"":question)) {
                        rejected=true; source="odrzucona odpowiedź modelu — ARIA Reforma I";
                        answer="Nie potrafię teraz odpowiedzieć na to pytanie. Odpowiedź modelu została odrzucona przez kontrolę jakości.";
                    }
                    }
                }
            }catch(Throwable e){
                answer="Nie udało się zakończyć zadania: "+e.getClass().getSimpleName()+": "+e.getMessage();
                errorType=e.getClass().getSimpleName(); source="błąd przetwarzania — ARIA Reforma I";
            }
            setPhase("Zapisuję odpowiedź");
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
                m.put("status",errorType.isEmpty()?(rejected?"rejected":"complete"):"error");
                m.put("source",source);
                db.insert("aria_telemetry",null,m);
                db.execSQL("CREATE TABLE IF NOT EXISTS aria_inference_diagnostics (task_id TEXT PRIMARY KEY, question_chars INTEGER, prompt_chars INTEGER, answer_chars INTEGER, rejected INTEGER, error_type TEXT, wall_ms INTEGER, cpu_ms INTEGER, model_load_ms INTEGER, generation_ms INTEGER, recorded_at INTEGER)");
                ContentValues details=new ContentValues();
                details.put("task_id",taskId);
                details.put("question_chars",question==null?0:question.length());
                details.put("prompt_chars",prompt==null?0:prompt.length());
                details.put("answer_chars",answer.length());
                details.put("rejected",rejected?1:0);
                details.put("error_type",errorType);
                details.put("wall_ms",SystemClock.elapsedRealtime()-startElapsed);
                details.put("cpu_ms",android.os.Process.getElapsedCpuTime()-startCpu);
                details.put("model_load_ms",loadMs);
                details.put("generation_ms",generateMs);
                details.put("recorded_at",System.currentTimeMillis());
                db.insertWithOnConflict("aria_inference_diagnostics",null,details,SQLiteDatabase.CONFLICT_REPLACE);
                db.execSQL("CREATE TABLE IF NOT EXISTS aria_native_metrics (task_id TEXT PRIMARY KEY, prompt_tokens INTEGER, output_tokens INTEGER, prefill_ms INTEGER, decode_ms INTEGER, phase INTEGER)");
                if(nativeStats!=null && nativeStats.length>=5){
                    ContentValues nm=new ContentValues();
                    nm.put("task_id",taskId);nm.put("phase",nativeStats[0]);
                    nm.put("prompt_tokens",nativeStats[1]);nm.put("output_tokens",nativeStats[2]);
                    nm.put("prefill_ms",nativeStats[3]);nm.put("decode_ms",nativeStats[4]);
                    db.insertWithOnConflict("aria_native_metrics",null,nm,SQLiteDatabase.CONFLICT_REPLACE);
                }
            }catch(Exception ignored){}
            long elapsed=SystemClock.elapsedRealtime()-startElapsed;
            try{
                handler.removeCallbacks(tick);
                WORKING.set(false);
                if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
                stopForeground(STOP_FOREGROUND_REMOVE);
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID+1,
                    notice(!saved?"Błąd zapisu odpowiedzi":cancelRequested?"Anulowano":rejected?"Odpowiedź odrzucona":errorType.isEmpty()?"Odpowiedź gotowa":"Błąd przetwarzania",
                        "Czas: "+duration(elapsed)+" | CPU: "+duration(android.os.Process.getElapsedCpuTime()-startCpu),false,System.currentTimeMillis()));
            }finally{stopSelfResult(startId);}
        },"ARIA-Inference-Reforma-I").start();
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){handler.removeCallbacks(tick);if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();super.onDestroy();}
}
