package com.pocketledger.v2;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** A labelled V6 field with a themed, accessible single-choice dialog. */
final class LedgerChoice extends LinearLayout {
    private final TextView value;
    private final String title;
    private final List<String> options;
    private int selected;
    private AdapterView.OnItemSelectedListener listener;

    LedgerChoice(Context c, String title, List<String> options, int initial) {
        super(c); this.title=title; this.options=new ArrayList<>(options);
        selected=options.isEmpty()?-1:Math.max(0, Math.min(initial,options.size()-1));
        setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Design.dp(c,56));
        setPadding(Design.dp(c,16),Design.dp(c,14),Design.dp(c,14),Design.dp(c,14));
        setBackground(Design.strokeShape(Design.surface(c),Design.border(c),12,c));
        value=Components.text(c,"",16,Design.text(c),android.graphics.Typeface.NORMAL);
        addView(value,new LayoutParams(0, LayoutParams.WRAP_CONTENT,1));
        NavIcon arrow=new NavIcon(c,NavIcon.ICON_CHEVRON,Design.muted(c));arrow.setRotation(90);
        addView(arrow,new LayoutParams(Design.dp(c,20),Design.dp(c,20)));
        setFocusable(true);setClickable(true);update();
        setOnClickListener(v->{if(!this.options.isEmpty())LedgerUi.choose(c,title,this.options,selected,this::selectFromDialog);});
    }
    private void selectFromDialog(int position) {
        setSelection(position);
        if(listener!=null)listener.onItemSelected(null,this,selected,selected);
    }
    private void update(){
        String text=selected<0?(title.contains("Person")?"Add a person to continue":"Add an account to continue"):LedgerUi.label(options.get(selected));
        value.setText(text);value.setTextColor(selected<0?Design.textSoft(getContext()):Design.text(getContext()));
        setContentDescription(title+". "+text+". Choose an option.");
    }
    int getSelectedItemPosition(){return selected;}
    void setSelection(int position){selected=options.isEmpty()?-1:Math.max(0,Math.min(position,options.size()-1));update();}
    void setOnItemSelectedListener(AdapterView.OnItemSelectedListener listener){this.listener=listener;}
}
