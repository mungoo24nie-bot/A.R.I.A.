package pl.aria.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/** Telemetria rzeczywistych zdarzeń aplikacji, bez symulowania pracy modułów. */
final class AriaLiveState {
    private static final String STORE="aria_live_state";
    private AriaLiveState() {}
    static void phase(Context c,String module,String description) {
        c.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit()
            .putString("module",module).putString("description",description)
            .putLong("changed",SystemClock.elapsedRealtime()).apply();
    }
    static void activity(Context c,String module,String description) {
        c.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit()
            .putString("event_module",module).putString("event_description",description)
            .putLong("event_at",SystemClock.elapsedRealtime()).apply();
    }
    static void idle(Context c) { phase(c,"","Gotowa do rozmowy"); }
    static Snapshot read(Context c,boolean working) {
        SharedPreferences p=c.getSharedPreferences(STORE,Context.MODE_PRIVATE);
        long now=SystemClock.elapsedRealtime();
        String active=working?p.getString("module",""):"";
        String detail=working?p.getString("description",""):"";
        if(now-p.getLong("changed",0)>120000){active="";detail="";}
        String event=p.getString("event_module","");
        String eventDetail=p.getString("event_description","");
        if(now-p.getLong("event_at",0)>2400){event="";eventDetail="";}
        return new Snapshot(active,detail,event,eventDetail);
    }
    static final class Snapshot {
        final String active,detail,event,eventDetail;
        Snapshot(String a,String d,String e,String ed){active=a;detail=d;event=e;eventDetail=ed;}
    }
}
