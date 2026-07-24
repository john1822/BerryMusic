package com.cloudstream.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.view.View;

public class CircularProgressView extends View {
    private Paint paint;
    private RectF rect;
    private int startAngle = 0;
    private int sweepAngle = 270;
    private boolean isAnimating = false;
    private Handler handler = new Handler();
    private Runnable animator = new Runnable() {
        @Override
        public void run() {
            if (isAnimating) {
                startAngle = (startAngle + 10) % 360;
                invalidate();
                handler.postDelayed(this, 30);
            }
        }
    };

    public CircularProgressView(Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getResources().getDisplayMetrics().density * 4);
        paint.setColor(Color.WHITE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        rect = new RectF();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float pad = paint.getStrokeWidth() / 2f + 2f;
        rect.set(pad, pad, w - pad, h - pad);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawArc(rect, startAngle, sweepAngle, false, paint);
    }

    public void startAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            handler.post(animator);
        }
    }

    public void stopAnimation() {
        isAnimating = false;
        handler.removeCallbacks(animator);
    }
}
