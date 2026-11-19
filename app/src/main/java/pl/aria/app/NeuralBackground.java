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
        float size=Math.min(w*.24f,h*.23f), t=(SystemClock.uptimeMillis()-animationStart)/1000f;
        // Obrys półkul i pnia: asymetryczny, anatomicznie inspirowany, nie rozeta.
        Path brain=new Path();
        brain.moveTo(cx-size*.92f,cy+size*.30f);
        brain.cubicTo(cx-size*1.19f,cy+size*.10f,cx-size*1.15f,cy-size*.35f,cx-size*.90f,cy-size*.48f);
        brain.cubicTo(cx-size*.97f,cy-size*.83f,cx-size*.55f,cy-size*1.07f,cx-size*.25f,cy-size*.91f);
        brain.cubicTo(cx-size*.05f,cy-size*1.14f,cx+size*.26f,cy-size*1.02f,cx+size*.40f,cy-size*.84f);
        brain.cubicTo(cx+size*.79f,cy-size*.96f,cx+size*1.10f,cy-size*.64f,cx+size*.97f,cy-size*.35f);
        brain.cubicTo(cx+size*1.22f,cy-size*.09f,cx+size*1.09f,cy+size*.30f,cx+size*.85f,cy+size*.42f);
        brain.cubicTo(cx+size*.83f,cy+size*.68f,cx+size*.48f,cy+size*.74f,cx+size*.25f,cy+size*.66f);
        brain.cubicTo(cx+size*.13f,cy+size*.84f,cx+size*.03f,cy+size*.87f,cx-size*.08f,cy+size*.71f);
        brain.cubicTo(cx-size*.39f,cy+size*.82f,cx-size*.77f,cy+size*.64f,cx-size*.92f,cy+size*.30f);
        brain.close();
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2f,w*.003f));
        paint.setColor(Color.rgb(74,205,234));c.drawPath(brain,paint);
        // Bruzdy nie są losowe: stałe krzywe o niskiej jasności.
        paint.setStrokeWidth(Math.max(1f,w*.0018f));paint.setColor(Color.argb(115,74,205,234));
        Path folds=new Path();
        folds.moveTo(cx-size*.60f,cy-size*.66f);
        folds.cubicTo(cx-size*.70f,cy-size*.32f,cx-size*.30f,cy-size*.43f,cx-size*.43f,cy-size*.09f);
        folds.moveTo(cx-size*.10f,cy-size*.83f);
        folds.cubicTo(cx-size*.28f,cy-size*.57f,cx+size*.13f,cy-size*.46f,cx-size*.06f,cy-size*.17f);
        folds.moveTo(cx+size*.40f,cy-size*.70f);
        folds.cubicTo(cx+size*.21f,cy-size*.39f,cx+size*.73f,cy-size*.25f,cx+size*.47f,cy+size*.01f);
        folds.moveTo(cx-size*.76f,cy+size*.10f);
        folds.cubicTo(cx-size*.46f,cy+size*.03f,cx-size*.58f,cy+size*.39f,cx-size*.28f,cy+size*.46f);
        folds.moveTo(cx+size*.17f,cy+size*.23f);
        folds.cubicTo(cx+size*.45f,cy+size*.12f,cx+size*.32f,cy+size*.52f,cx+size*.60f,cy+size*.40f);
        c.drawPath(folds,paint);
        paint.setStyle(Paint.Style.FILL);
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
            // Akson: łagodna krzywa, nie sztywna kreska przecinająca napis ARIA.
            float vx=nx-cx,vy=ny-cy;
            float len=(float)Math.sqrt(vx*vx+vy*vy);
            float ux=len>0?vx/len:0,uy=len>0?vy/len:0;
            float sx=cx+ux*size*1.13f,sy=cy+uy*size*.94f;
            float ex=nx-ux*radius*.86f,ey=ny-uy*radius*.86f;
            Path axon=new Path();axon.moveTo(sx,sy);
            float bend=(module.hashCode()%2==0?1f:-1f)*Math.min(w,h)*.045f;
            float mx=(sx+ex)*.5f-uy*bend,my=(sy+ey)*.5f+ux*bend;
            axon.quadTo(mx,my,ex,ey);
            paint.setColor(Color.argb(primary?135:80,69,215,244));
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(primary?1.8f:1.2f);
            c.drawPath(axon,paint);paint.setStyle(Paint.Style.FILL);
            if(primary){
                float progress=(t*.55f)%1f,inv=1f-progress;
                float px=inv*inv*sx+2*inv*progress*mx+progress*progress*ex;
                float py=inv*inv*sy+2*inv*progress*my+progress*progress*ey;
                paint.setColor(Color.argb(225,107,233,249));c.drawCircle(px,py,3.3f,paint);
            }
            // Dendryty z odgałęzieniami, a nie promienie okręgu.
            for(int i=0;i<7;i++){
                double angle=i*Math.PI*2/7+.25;
                float ux2=(float)Math.cos(angle),uy2=(float)Math.sin(angle);
                float ax=nx+ux2*radius*.68f,ay=ny+uy2*radius*.68f;
                float bx=nx+ux2*radius*1.35f,by=ny+uy2*radius*1.35f;
                line(c,ax,ay,bx,by,primary?150:85,1.5f);
                float tx=-uy2,ty=ux2;
                line(c,bx,by,bx+ux2*radius*.38f+tx*radius*.26f,by+uy2*radius*.38f+ty*radius*.26f,primary?120:70,1.1f);
                line(c,bx,by,bx+ux2*radius*.35f-tx*radius*.24f,by+uy2*radius*.35f-ty*radius*.24f,primary?120:70,1.1f);
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
