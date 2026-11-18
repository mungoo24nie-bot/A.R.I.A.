package pl.aria.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Widok trwałych danych SQLite, nie mapa neuronów Qwen. */
public final class NeuralBackground extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<String> nodes=new ArrayList<>();
    private final ArrayList<int[]> edges=new ArrayList<>();
    private boolean thinking=false;
    private final long started=System.currentTimeMillis();
    public NeuralBackground(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    public void setThinking(boolean value){thinking=value;invalidate();}
    public void setGrowth(int ignored){invalidate();}
    public void refreshMemory(SQLiteDatabase db){
        ArrayList<String> newNodes=new ArrayList<>();ArrayList<int[]> newEdges=new ArrayList<>();
        HashMap<String,Integer> positions=new HashMap<>();
        try(Cursor c=db.rawQuery("SELECT id FROM events WHERE type IN ('user','aria','archive') ORDER BY id DESC LIMIT 65",null)){
            while(c.moveToNext())add("event:"+c.getLong(0),newNodes,positions);
        }
        try(Cursor c=db.rawQuery("SELECT fact_key,source_event_id FROM facts LIMIT 30",null)){
            while(c.moveToNext()){
                String fact="fact:"+c.getString(0),event="event:"+c.getLong(1);
                int a=add(fact,newNodes,positions);Integer b=positions.get(event);
                if(b!=null)newEdges.add(new int[]{a,b});
            }
        }catch(Exception ignored){}
        try(Cursor c=db.rawQuery("SELECT id FROM library_docs ORDER BY id DESC LIMIT 20",null)){
            while(c.moveToNext())add("document:"+c.getLong(0),newNodes,positions);
        }catch(Exception ignored){}
        synchronized(nodes){nodes.clear();nodes.addAll(newNodes);edges.clear();edges.addAll(newEdges);}
        postInvalidate();
    }
    private static int add(String key,ArrayList<String> list,HashMap<String,Integer> map){
        Integer existing=map.get(key);if(existing!=null)return existing;
        int index=list.size();list.add(key);map.put(key,index);return index;
    }
    private static double hash(String id,int salt){double v=Math.sin(id.hashCode()*0.013+salt*75.34)*43758.5453;return v-Math.floor(v);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);
        ArrayList<String> snapshot;ArrayList<int[]> connections;
        synchronized(nodes){snapshot=new ArrayList<>(nodes);connections=new ArrayList<>(edges);}
        int n=snapshot.size();if(n==0)return;
        float w=getWidth(),h=getHeight(),t=(System.currentTimeMillis()-started)/1000f;
        float[] x=new float[n],y=new float[n];
        for(int i=0;i<n;i++){String id=snapshot.get(i);
            x[i]=(float)((.08+.84*hash(id,1))*w+Math.sin(t*.28+i)*2);
            y[i]=(float)((.08+.84*hash(id,2))*h+Math.cos(t*.28+i)*2);
        }
        for(int[] edge:connections){p.setColor(Color.argb(thinking?180:110,45,211,247));p.setStrokeWidth(1.7f);c.drawLine(x[edge[0]],y[edge[0]],x[edge[1]],y[edge[1]],p);}
        for(int i=0;i<n;i++){
            float pulse=(float)(.5+.5*Math.sin(t*(thinking?2.5:.65)+i));
            boolean fact=snapshot.get(i).startsWith("fact:"),doc=snapshot.get(i).startsWith("document:");
            p.setColor(Color.argb(115+(int)(90*pulse),doc?245:fact?130:42,doc?184:fact?240:197,doc?88:241));
            c.drawCircle(x[i],y[i],(fact?5:doc?6:3.4f)+(thinking?2.5f*pulse:1.2f*pulse),p);
            p.setColor(Color.rgb(180,243,255));c.drawCircle(x[i],y[i],1.3f,p);
        }
        postInvalidateDelayed(thinking?90:180);
    }
}
