package pl.aria.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.util.ArrayList;
import java.util.HashMap;

/** Real persisted memory nodes and source-backed links, not model neuron activations. */
public final class NeuralBackground extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Long> ids=new ArrayList<>();
    private final ArrayList<int[]> links=new ArrayList<>();
    private boolean thinking;
    private long start=System.currentTimeMillis();
    public NeuralBackground(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    public void setGrowth(int ignored){invalidate();}
    public void setThinking(boolean value){thinking=value;invalidate();}
    public void refreshMemory(SQLiteDatabase db){
        ArrayList<Long> nodes=new ArrayList<>();ArrayList<int[]> edges=new ArrayList<>();
        HashMap<Long,Integer> index=new HashMap<>();
        try(Cursor c=db.rawQuery("SELECT id FROM events WHERE type IN ('user','aria','archive') ORDER BY id DESC LIMIT 80",null)){
            while(c.moveToNext()){long id=c.getLong(0);index.put(id,nodes.size());nodes.add(id);}
        }
        try(Cursor c=db.rawQuery("SELECT source_event_id FROM facts",null)){
            while(c.moveToNext()){
                Integer target=index.get(c.getLong(0));
                if(target!=null && !nodes.isEmpty())edges.add(new int[]{0,target});
            }
        }catch(Exception ignored){}
        // Only verified relation references, no fabricated graph links.
        synchronized(ids){ids.clear();ids.addAll(nodes);links.clear();links.addAll(edges);}
        postInvalidate();
    }
    private double hash(long id,int salt){double n=Math.sin(id*12.9898+salt*78.233)*43758.5453;return n-Math.floor(n);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);
        int count;ArrayList<Long> nodes;ArrayList<int[]> edges;
        synchronized(ids){nodes=new ArrayList<>(ids);edges=new ArrayList<>(links);}
        count=nodes.size();if(count==0){postInvalidateDelayed(300);return;}
        float w=getWidth(),h=getHeight();float time=(System.currentTimeMillis()-start)/1000f;
        float[] x=new float[count],y=new float[count];
        for(int i=0;i<count;i++){
            long id=nodes.get(i);x[i]=(float)((.08+.84*hash(id,1))*w+Math.sin(time*.25+i)*3);
            y[i]=(float)((.08+.84*hash(id,2))*h+Math.cos(time*.25+i)*3);
        }
        for(int[] edge:edges){if(edge[0]>=count||edge[1]>=count)continue;
            paint.setColor(Color.argb(thinking?165:90,45,211,247));paint.setStrokeWidth(1.8f);
            canvas.drawLine(x[edge[0]],y[edge[0]],x[edge[1]],y[edge[1]],paint);
        }
        for(int i=0;i<count;i++){
            float pulse=(float)(.5+.5*Math.sin(time*(thinking?3:0.7)+i));
            paint.setColor(Color.argb(110+(int)(100*pulse),42,197,241));
            canvas.drawCircle(x[i],y[i],3.5f+(thinking?3*pulse:1.5f*pulse),paint);
            paint.setColor(Color.rgb(150,243,255));canvas.drawCircle(x[i],y[i],1.4f,paint);
        }
        postInvalidateDelayed(thinking?70:160);
    }
}
