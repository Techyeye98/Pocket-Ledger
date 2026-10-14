package com.pocketledger.v2;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A real, touch-selectable category donut. The accessible legend lives in MainActivity. */
public final class DonutChartView extends View {
    public static class CategorySpend {
        String name;
        double amount;
        public CategorySpend(String name, double amount) { this.name = name; this.amount = amount; }
    }

    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private List<CategorySpend> data = new ArrayList<>();
    private double total;
    private String currency = "";
    private int selected = -1;
    private float emphasis = 1f;
    private ValueAnimator selectionAnimator;

    public DonutChartView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.BUTT);
        label.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(List<CategorySpend> items, String currency) {
        data = new ArrayList<>(items);
        this.currency = currency;
        total = 0;
        for (CategorySpend item : data) total += item.amount;
        selected = data.isEmpty() ? -1 : 0;
        updateDescription();
        invalidate();
    }

    public void setSelectedIndex(int index) {
        if (index < 0 || index >= data.size() || index == selected) return;
        selected = index;
        if (selectionAnimator != null) selectionAnimator.cancel();
        if (ValueAnimator.areAnimatorsEnabled()) {
            emphasis = 0f;
            selectionAnimator = ValueAnimator.ofFloat(0f, 1f);
            selectionAnimator.setDuration(180);
            selectionAnimator.addUpdateListener(a -> {
                emphasis = (float) a.getAnimatedValue();
                invalidate();
            });
            selectionAnimator.start();
        } else {
            emphasis = 1f;
            invalidate();
        }
        updateDescription();
    }

    public static int colorFor(Context context, int index) {
        int[] light = {0xFF087C4B, 0xFF149B8B, 0xFFC58D31, 0xFF80B98B, 0xFF3D6F68, 0xFF9FBEAB};
        int[] dark = {0xFF65E2A4, 0xFF4BCDC0, 0xFFF2C879, 0xFF7CAD91, 0xFF4A9282, 0xFFB0CABA};
        int[] palette = Design.dark(context) ? dark : light;
        return palette[index % palette.length];
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Design.dp(getContext(), 198));
    }

    @Override protected void onDraw(Canvas canvas) {
        if (data.isEmpty() || total <= 0) return;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.34f;
        float baseStroke = Design.dp(getContext(), 22);
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
        float angle = -90f;
        for (int i = 0; i < data.size(); i++) {
            float sweep = (float) (data.get(i).amount / total * 360f);
            float gap = Math.min(2.2f, sweep / 4f);
            arc.setColor(colorFor(getContext(), i));
            arc.setStrokeWidth(baseStroke + (i == selected ? Design.dp(getContext(), 5) * emphasis : 0));
            canvas.drawArc(bounds, angle + gap / 2f, Math.max(0, sweep - gap), false, arc);
            angle += sweep;
        }
        CategorySpend active = data.get(Math.max(0, selected));
        label.setColor(Design.textSoft(getContext()));
        label.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        label.setTextSize(Design.dp(getContext(), 12));
        String name = active.name;
        while (name.length() > 4 && label.measureText(name) > radius * 1.65f) name = name.substring(0, name.length() - 2) + "…";
        canvas.drawText(name, cx, cy - Design.dp(getContext(), 9), label);
        label.setColor(Design.text(getContext()));
        label.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        label.setTextSize(Design.dp(getContext(), 22));
        String amount = currency + String.format(Locale.US, "%.0f", active.amount);
        while (label.getTextSize() > Design.dp(getContext(), 14) && label.measureText(amount) > radius * 1.7f)
            label.setTextSize(label.getTextSize() - Design.dp(getContext(), 1));
        canvas.drawText(amount, cx, cy + Design.dp(getContext(), 19), label);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && total > 0) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float dx = event.getX() - cx;
            float dy = event.getY() - cy;
            float distance = (float) Math.hypot(dx, dy);
            float radius = Math.min(getWidth(), getHeight()) * 0.34f;
            if (distance >= radius - Design.dp(getContext(), 32) && distance <= radius + Design.dp(getContext(), 32)) {
                float angle = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 450) % 360);
                float end = 0;
                for (int i = 0; i < data.size(); i++) {
                    end += data.get(i).amount / total * 360f;
                    if (angle <= end) { setSelectedIndex(i); break; }
                }
                super.performClick();
                return true;
            }
            return performClick();
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        if (!data.isEmpty()) setSelectedIndex((selected + 1) % data.size());
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        if (selectionAnimator != null) selectionAnimator.cancel();
        super.onDetachedFromWindow();
    }

    private void updateDescription() {
        if (selected < 0) setContentDescription("Spending categories, no data");
        else setContentDescription("Spending categories. " + data.get(selected).name + ", "
                + currency + String.format(Locale.US, "%.2f", data.get(selected).amount)
                + ". Activate to hear the next category.");
    }
}
