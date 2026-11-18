package pl.aria.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Wizualizacja aktywności aplikacji, nie rzeczywistych neuronów modelu. */
public final class NeuralBackground extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int growth=0;private boolean thinking=false;
    private long start=System.currentTimeMillis();
    public NeuralBackground(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    public void setGrowth(int count){growth=Math.max(0,count);invalidate();}
    public void setThinking(boolean value){if(thinking!=value){thinking=value;invalidate();}}
    private double rand(int x){double v=Math.sin(x*76.73)*43758.5453;return v-Math.floor(v);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);
        float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
        float t=(System.currentTimeMillis()-start)/1000f;
        int n=Math.min(65,22+(int)Math.sqrt(growth)*2);
        float[] x=new float[n],y=new float[n];
        for(int i=0;i<n;i++){
            x[i]=(float)((0.08+0.84*rand(i+9))*w+Math.sin(t*.45+i)*5);
            y[i]=(float)((0.08+0.84*rand(i+31))*h+Math.cos(t*.43+i)*5);
        }
        float max=Math.min(w,h)*.44f;
        for(int i=0;i<n;i++)for(int j=i+1;j<n;j++){
            float dx=x[i]-x[j],dy=y[i]-y[j],dist=(float)Math.hypot(dx,dy);
            if(dist>max)continue;
            float glow=(float)(.5+.5*Math.sin(t*(thinking?3:0.5)+i*.6+j*.13));
            int alpha=(int)((thinking?100:60)*(1-dist/max)+(thinking?50:20)*glow);
            p.setColor(Color.argb(Math.min(230,alpha),45,211,247));p.setStrokeWidth(thinking?2.1f:1.5f);
            c.drawLine(x[i],y[i],x[j],y[j],p);
        }
        for(int i=0;i<n;i++){
            float pulse=(float)(.5+.5*Math.sin(t*(thinking?4:1)+i));
            p.setColor(Color.argb(70+(int)(130*pulse),42,197,241));
            c.drawCircle(x[i],y[i],thinking?7+4*pulse:5+2*pulse,p);
            p.setColor(Color.argb(235,150,243,255));c.drawCircle(x[i],y[i],2.1f,p);
        }
        postInvalidateDelayed(thinking?50:150);
    }
}
