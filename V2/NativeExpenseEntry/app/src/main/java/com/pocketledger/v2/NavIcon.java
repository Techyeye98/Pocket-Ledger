package com.pocketledger.v2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Code-drawn outlined navigation icon (Home, Ledger/receipt, Settings gear).
 * Geometry is authored in 24dp units and scaled to pixel density on draw, so
 * the icons stay crisp on every screen. Color is passed in for active/inactive states.
 */
public final class NavIcon extends View {

    public static final int HOME     = 0;
    public static final int LEDGER   = 1;
    public static final int SETTINGS = 2;

    private static final float SIZE = 24f;

    private final int type;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public NavIcon(Context context, int type, int color) {
        super(context);
        this.type = type;
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int px = Math.round(SIZE * getResources().getDisplayMetrics().density);
        setMeasuredDimension(px, px);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        canvas.save();
        canvas.scale(density, density);
        switch (type) {
            case HOME:     drawHome(canvas);     break;
            case LEDGER:   drawLedger(canvas);   break;
            case SETTINGS: drawSettings(canvas); break;
        }
        canvas.restore();
    }

    private void drawHome(Canvas canvas) {
        Path p = new Path();
        p.moveTo(12f, 3.4f);
        p.lineTo(21.2f, 12f);
        p.lineTo(17.6f, 12f);
        p.lineTo(17.6f, 20.6f);
        p.lineTo(13.8f, 20.6f);
        p.lineTo(13.8f, 15.6f);
        p.lineTo(10.2f, 15.6f);
        p.lineTo(10.2f, 20.6f);
        p.lineTo(6.4f, 20.6f);
        p.lineTo(6.4f, 12f);
        p.lineTo(2.8f, 12f);
        p.close();
        canvas.drawPath(p, paint);
    }

    private void drawLedger(Canvas canvas) {
        Path body = new Path();
        body.addRoundRect(5f, 3f, 19f, 21.4f, 2.6f, 2.6f, Path.Direction.CW);
        canvas.drawPath(body, paint);
        canvas.drawLine(8f, 8.4f, 16f, 8.4f, paint);
        canvas.drawLine(8f, 12.4f, 16f, 12.4f, paint);
        canvas.drawLine(8f, 16.4f, 13f, 16.4f, paint);
    }

    private void drawSettings(Canvas canvas) {
        Path gear = new Path();
        float cx = 12f, cy = 12f;
        float inner = 7f, outer = 10.6f;
        int teeth = 8;
        float step = 360f / teeth;   // 45 degrees
        float valley = 8f;           // half-width of each valley
        for (int k = 0; k < teeth; k++) {
            float base0 = k * step - 90f;
            float tipA  = base0 + valley;
            float tipB  = base0 + step - valley;
            float base1 = base0 + step;
            if (k == 0) gear.moveTo(ptX(cx, inner, base0), ptY(cy, inner, base0));
            gear.lineTo(ptX(cx, outer, tipA), ptY(cy, outer, tipA));
            gear.lineTo(ptX(cx, outer, tipB), ptY(cy, outer, tipB));
            gear.lineTo(ptX(cx, inner, base1), ptY(cy, inner, base1));
        }
        gear.close();
        canvas.drawPath(gear, paint);
        canvas.drawCircle(cx, cy, 4.4f, paint);
    }

    private static float ptX(float cx, float r, float deg) {
        return cx + r * (float) Math.cos(Math.toRadians(deg));
    }

    private static float ptY(float cy, float r, float deg) {
        return cy + r * (float) Math.sin(Math.toRadians(deg));
    }
}