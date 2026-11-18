package pl.aria.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import java.util.LinkedHashMap;

/** Żywa sieć: widoczne są wyłącznie moduły potwierdzone telemetrią. */
public final class NeuralBackground extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean thinking;
    private String active="",detail="",event="",eventDetail="";
    private long animationStart=SystemClock.uptimeMillis();
    private final LinkedHashMap<String,float[]> positions=new LinkedHashMap<>();
    public NeuralBackground(Context context){super(context);setClickable(true);
        positions.put("Rozmowa",new float[]{.17f,.23f});
        positions.put("Pamięć",new float[]{.82f,.24f});
        positions.put("Biblioteka",new float[]{.87f,.50f});
        positions.put("Uczenie",new float[]{.76f,.79f});
        positions.put("Rozumowanie",new float[]{.24f,.79f});
        positions.put("Silnik",new float[]{.13f,.50f});
        positions.put("Internet",new float[]{.50f,.09f});
    }
    public void setThinking(boolean v){if(thinking!=v){thinking=v;invalidate();}}
    public void setGrowth(int ignored){}
    public void refreshMemory(SQLiteDatabase ignored){} // history is not a visualization event
    public void update(AriaLiveState.Snapshot s){
        if(!active.equals(s.active)||!event.equals(s.event)||!detail.equals(s.detail)||!eventDetail.equals(s.eventDetail)){
            active=s.active;detail=s.detail;event=s.event;eventDetail=s.eventDetail;invalidate();
        }
    }
    private void line(Canvas c,float x1,float y1,float x2,float y2,int alpha,float stroke){
        paint.setColor(Color.argb(alpha,69,215,244));paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);c.drawLine(x1,y1,x2,y2,paint);paint.setStyle(Paint.Style.FILL);
    }
    @Override protected void onDraw(Canvas c){super.onDraw(c);
        float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
        float cx=w*.5f,cy=h*.47f;
        float size=Math.min(w*.20f,h*.19f), t=(SystemClock.uptimeMillis()-animationStart)/1000f;
        // Delikatny, stylizowany obrys mózgu zamiast koła.
        Path brain=new Path();
        brain.moveTo(cx,cy-size*.73f);
        brain.cubicTo(cx-size*.23f,cy-size*1.12f,cx-size*.80f,cy-size*.89f,cx-size*.75f,cy-size*.42f);
        brain.cubicTo(cx-size*1.16f,cy-size*.30f,cx-size*1.07f,cy+size*.25f,cx-size*.78f,cy+size*.36f);
        brain.cubicTo(cx-size*.89f,cy+size*.77f,cx-size*.40f,cy+size*.97f,cx,cy+size*.72f);
        brain.cubicTo(cx+size*.40f,cy+size*.97f,cx+size*.89f,cy+size*.77f,cx+size*.78f,cy+size*.36f);
        brain.cubicTo(cx+size*1.07f,cy+size*.25f,cx+size*1.16f,cy-size*.30f,cx+size*.75f,cy-size*.42f);
        brain.cubicTo(cx+size*.80f,cy-size*.89f,cx+size*.23f,cy-size*1.12f,cx,cy-size*.73f);
        brain.close();
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2f,w*.003f));
        paint.setColor(Color.rgb(74,205,234));c.drawPath(brain,paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(176,244,255));paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.max(18f,Math.min(28f,w*.065f)));paint.setFakeBoldText(true);
        c.drawText("A.R.I.A.",cx,cy+paint.getTextSize()*.34f,paint);
        paint.setFakeBoldText(false);
        for(String module:positions.keySet()){
            boolean primary=module.equals(active),secondary=module.equals(event);
            if(!primary&&!secondary)continue;
            float[] xy=positions.get(module);float nx=w*xy[0],ny=h*xy[1];
            float radius=Math.min(w,h)*.095f;
            // Połączenie wyłącznie z aktualnie aktywnym modułem.
            line(c,cx,cy,nx,ny,primary?120:75,1.5f);
            float progress=(t*.55f)%1f;
            paint.setColor(Color.argb(220,107,233,249));
            if(primary)c.drawCircle(cx+(nx-cx)*progress,cy+(ny-cy)*progress,3.3f,paint);
            // Neuron: jądro oraz krótkie wypustki.
            for(int i=0;i<7;i++){
                double angle=i*Math.PI*2/7;
                float ax=nx+(float)Math.cos(angle)*radius*.67f,ay=ny+(float)Math.sin(angle)*radius*.67f;
                float bx=nx+(float)Math.cos(angle)*radius*1.07f,by=ny+(float)Math.sin(angle)*radius*1.07f;
                line(c,ax,ay,bx,by,primary?140:85,1.4f);
            }
            paint.setColor(Color.argb(215,11,38,56));c.drawCircle(nx,ny,radius*.74f,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(primary?2.8f:1.7f);
            paint.setColor(primary?Color.rgb(91,227,249):Color.rgb(54,151,173));
            c.drawCircle(nx,ny,radius*.74f,paint);paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(205,247,253));paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.max(10f,Math.min(14f,w*.030f)));
            c.drawText(module,nx,ny+paint.getTextSize()*.32f,paint);
        }
        if(!active.isEmpty()||!event.isEmpty())postInvalidateDelayed(90);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        float w=getWidth(),h=getHeight();
        for(String module:positions.keySet()){
            if(!module.equals(active)&&!module.equals(event))continue;
            float[] xy=positions.get(module);
            float dx=e.getX()-w*xy[0],dy=e.getY()-h*xy[1];
            float r=Math.min(w,h)*.13f;
            if(dx*dx+dy*dy<=r*r){
                String text=module.equals(active)?detail:eventDetail;
                Toast.makeText(getContext(),module+": "+(text.isEmpty()?"Aktywność zarejestrowana":text),Toast.LENGTH_SHORT).show();
                performClick();return true;
            }
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
