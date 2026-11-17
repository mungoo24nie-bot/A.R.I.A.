package pl.aria.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Dekoracyjna wizualizacja etapów rozwoju. Nie przedstawia rzeczywistych neuronów modelu. */
public final class NeuralBackground extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int events = 0;
    private long started = System.currentTimeMillis();
    private static final int MAX_NODES = 72;

    public NeuralBackground(Context context) { super(context); }
    public void setGrowth(int eventCount) { events = Math.max(0, eventCount); invalidate(); }
    private float fract(float x) { return x - (float)Math.floor(x); }
    private float rand(int n, float salt) { return fract((float)Math.sin(n * 93.73f + salt) * 43758.5453f); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        // Liczba węzłów odzwierciedla liczbę wpisów w kronice; to metafora rozwoju.
        int count = Math.min(MAX_NODES, 16 + (int)(Math.sqrt(events) * 3.1));
        float[] xs = new float[count], ys = new float[count];
        float t = (System.currentTimeMillis() - started) / 1000f;
        for (int i=0; i<count; i++) {
            xs[i] = (0.08f + rand(i+1, 1.7f)*0.84f)*w + (float)Math.sin(t*0.25f+i)*3f;
            ys[i] = (0.05f + rand(i+1, 9.4f)*0.90f)*h + (float)Math.cos(t*0.22f+i)*3f;
        }
        float threshold = Math.min(w,h)*0.40f;
        paint.setStrokeWidth(1.7f);
        for(int i=0;i<count;i++) for(int j=i+1;j<count;j++) {
            float dx=xs[i]-xs[j],dy=ys[i]-ys[j];
            float d=(float)Math.sqrt(dx*dx+dy*dy);
            if(d<threshold) {
                int alpha=35+(int)(110*(1f-d/threshold));
                paint.setColor(Color.argb(alpha,55,204,234));
                canvas.drawLine(xs[i],ys[i],xs[j],ys[j],paint);
            }
        }
        for(int i=0;i<count;i++) {
            float pulse=(float)(0.5+0.5*Math.sin(t*1.2+i*0.9));
            paint.setColor(Color.argb(80+(int)(85*pulse),35,184,229));
            canvas.drawCircle(xs[i],ys[i],5.5f+3f*pulse,paint);
            paint.setColor(Color.argb(230,120,235,255));
            canvas.drawCircle(xs[i],ys[i],1.8f,paint);
        }
        postInvalidateDelayed(120);
    }
}
