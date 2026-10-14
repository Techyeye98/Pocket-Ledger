package com.pocketledger.v2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class NavIcon extends View {
    public static final int ICON_GEAR = 1;
    public static final int ICON_FAB = 2;
    public static final int ICON_SEARCH = 3;
    public static final int ICON_CLOSE = 4;
    public static final int ICON_FILTER = 5;
    public static final int ICON_BACK = 6;
    public static final int ICON_CHEVRON = 7;
    public static final int ICON_ARCHIVE = 8;
    
    private int type;
    private Paint paint;
    private int color;

    public NavIcon(Context context, int type, int color) {
        super(context);
        this.type = type;
        this.color = color;
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Design.dp(context, 2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2;
        float cy = h / 2;
        float p = Design.dp(getContext(), 4);

        if (type == ICON_GEAR) {
            // Minimalist geometric gear (circle with small ticks)
            canvas.drawCircle(cx, cy, w / 4, paint);
            for (int i = 0; i < 4; i++) {
                canvas.save();
                canvas.rotate(i * 45, cx, cy);
                canvas.drawLine(cx, p, cx, cy - w / 4 - p/2, paint);
                canvas.drawLine(cx, cy + w / 4 + p/2, cx, h - p, paint);
                canvas.restore();
            }
        } else if (type == ICON_FAB) {
            // Plus
            canvas.drawLine(cx, p, cx, h - p, paint);
            canvas.drawLine(p, cy, w - p, cy, paint);
        } else if (type == ICON_CLOSE) {
            // X
            canvas.drawLine(p, p, w - p, h - p, paint);
            canvas.drawLine(w - p, p, p, h - p, paint);
        } else if (type == ICON_SEARCH) {
            // Minimalist search
            float r = w / 3.5f;
            canvas.drawCircle(cx - p/2, cy - p/2, r, paint);
            canvas.drawLine(cx + r - p/2, cy + r - p/2, w - p, h - p, paint);
        } else if (type == ICON_FILTER) {
            // Three horizontal lines
            canvas.drawLine(p, cy - p, w - p, cy - p, paint);
            canvas.drawLine(p + p/2, cy, w - p - p/2, cy, paint);
            canvas.drawLine(p + p, cy + p, w - p - p, cy + p, paint);
        } else if (type == ICON_BACK) {
            canvas.drawLine(cx + p * 1.5f, cy, cx - p * 1.5f, cy, paint);
            canvas.drawLine(cx - p * 1.5f, cy, cx - p / 2, cy - p, paint);
            canvas.drawLine(cx - p * 1.5f, cy, cx - p / 2, cy + p, paint);
        } else if (type == ICON_CHEVRON) {
            canvas.drawLine(cx - p / 2, cy - p, cx + p / 2, cy, paint);
            canvas.drawLine(cx + p / 2, cy, cx - p / 2, cy + p, paint);
        } else if (type == ICON_ARCHIVE) {
            canvas.drawRect(p, cy - p / 2, w - p, h - p, paint);
            canvas.drawLine(p, cy - p * 1.5f, w - p, cy - p * 1.5f, paint);
            canvas.drawLine(cx - p / 2, cy + p, cx + p / 2, cy + p, paint);
        }
    }
}
