package com.pocketledger.v2;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

/** Tapping the selected option again clears it; no choice is required to save. */
final class SpendingTypePicker extends LinearLayout {
    private String value;
    private final Button need, want;

    SpendingTypePicker(Context context, String initialValue) {
        super(context);
        setOrientation(HORIZONTAL);
        value = SpendingType.normalize(initialValue);
        need = addOption("Need · essential", SpendingType.NEED);
        want = addOption("Want · optional", SpendingType.WANT);
        render();
    }
    private Button addOption(String label, String type) {
        Button button = Components.textButton(getContext(), label);
        LayoutParams params = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(0, 0, Design.dp(getContext(), 8), 0);
        button.setMinHeight(Design.dp(getContext(), 48));
        button.setOnClickListener(v -> { value = type.equals(value) ? SpendingType.UNTAGGED : type; render(); });
        addView(button, params);
        return button;
    }
    void setValue(String value) { this.value = SpendingType.normalize(value); render(); }
    String getValue() { return value; }
    private void render() {
        style(need, SpendingType.NEED);
        style(want, SpendingType.WANT);
    }
    private void style(Button button, String type) {
        boolean active = type.equals(value);
        button.setSelected(active);
        button.setTextColor(active ? Design.onPrimary(getContext()) : Design.text(getContext()));
        button.setBackground(Design.shape(active ? Design.primary(getContext()) : Design.surfaceRaised(getContext()), 12, getContext()));
        button.setContentDescription(SpendingType.label(type) + (active ? ", selected. Tap to clear." : ", not selected. Tap to select."));
    }
}
