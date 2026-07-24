package com.cloudstream.player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.Shape;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SplashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize TLS Fix early
        TLSFix.enableTLSv12();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        
        ShapeDrawable bg = new ShapeDrawable(new Shape() {
            @Override
            public void draw(Canvas canvas, Paint paint) {
                paint.setColor(Color.parseColor("#0A0A1A"));
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
                paint.setColor(Color.parseColor("#1A1A2E"));
                canvas.drawCircle(getWidth()/2f, getHeight()/2f, Math.min(getWidth(), getHeight()), paint);
            }
        });
        root.setBackgroundDrawable(bg);

        TextView title = new TextView(this);
        title.setText("BerryMusic");
        title.setTextColor(Color.WHITE);
        title.setTextSize(36);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        setContentView(root);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            }
        }, 2000);
    }
}
