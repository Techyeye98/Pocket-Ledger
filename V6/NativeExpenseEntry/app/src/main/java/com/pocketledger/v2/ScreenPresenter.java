package com.pocketledger.v2;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;

/** Keeps the window and navigation mounted; swaps only completed screen content. */
final class ScreenPresenter {
    private final Activity activity;
    private final LinearLayout root;
    private final FrameLayout stage;
    private final FrameLayout navigation;
    private View current;
    private String route="";
    private int revision;
    static final int NAVIGATION_MS=160;
    static final int CONTENT_MS=120;
    static final PathInterpolator EASE_OUT=new PathInterpolator(.23f,1f,.32f,1f);

    ScreenPresenter(Activity activity) {
        this.activity=activity;
        root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Design.background(activity));
        stage=new FrameLayout(activity);navigation=new FrameLayout(activity);
        root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(navigation,new LinearLayout.LayoutParams(-1,-2));
        activity.setContentView(root);
    }
    void navigation(View view) {
        navigation.removeAllViews();
        if(view!=null)navigation.addView(view);
        navigation.setVisibility(view==null?View.GONE:View.VISIBLE);
    }
    void show(View incoming,String key,boolean motion) {
        root.setBackgroundColor(Design.background(activity));
        if(activity.isFinishing()||activity.isDestroyed()||incoming==current)return;
        final int request=++revision;
        final boolean sameRoute=route.equals(key);
        final View outgoing=current;
        final boolean interrupted=stage.getChildCount()>1;
        ScrollView oldScroll=scroll(outgoing);
        final int scrollY=sameRoute&&oldScroll!=null?oldScroll.getScrollY():-1;
        View focused=outgoing==null?null:outgoing.findFocus();
        Object focusTag=sameRoute&&focused instanceof EditText?focused.getTag():null;
        int selection=focused instanceof EditText?((EditText)focused).getSelectionStart():-1;

        // A new tap cancels the old transition instead of queuing another one.
        for(int i=stage.getChildCount()-1;i>=0;i--){View child=stage.getChildAt(i);child.animate().cancel();
            if(child!=outgoing)stage.removeView(child);else{child.setAlpha(1);child.setTranslationY(0);}}
        if(incoming.getParent() instanceof ViewGroup)((ViewGroup)incoming.getParent()).removeView(incoming);
        incoming.animate().cancel();incoming.setAlpha(1);incoming.setTranslationY(0);
        incoming.setClickable(true);incoming.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        stage.addView(incoming,new FrameLayout.LayoutParams(-1,-1));current=incoming;route=key;

        if(!sameRoute){
            if(focused!=null){android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(focused.getWindowToken(),0);}
            incoming.setFocusableInTouchMode(true);incoming.requestFocus();
        }
        if(focusTag!=null){View replacement=incoming.findViewWithTag(focusTag);if(replacement instanceof EditText){
            EditText edit=(EditText)replacement;edit.requestFocus();edit.setSelection(Math.max(0,Math.min(selection,edit.length())));}}
        if(scrollY>=0){incoming.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw(){incoming.getViewTreeObserver().removeOnPreDrawListener(this);
                ScrollView next=scroll(incoming);if(request==revision&&next!=null)next.scrollTo(0,scrollY);return true;}
        });}
        if(!motion||interrupted||outgoing==null||!ValueAnimator.areAnimatorsEnabled()){
            if(outgoing!=null)stage.removeView(outgoing);return;
        }
        outgoing.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        incoming.setAlpha(0);incoming.setTranslationY(sameRoute?0:Design.dp(activity,8));
        outgoing.animate().alpha(0).setDuration(CONTENT_MS).setInterpolator(EASE_OUT).start();
        incoming.animate().alpha(1).translationY(0).setDuration(sameRoute?CONTENT_MS:NAVIGATION_MS)
                .setInterpolator(EASE_OUT).withEndAction(()->{if(request==revision&&outgoing!=current)stage.removeView(outgoing);}).start();
    }
    boolean isRoute(String key){return route.equals(key);}
    private static ScrollView scroll(View view){
        if(view instanceof ScrollView)return (ScrollView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){ScrollView found=scroll(group.getChildAt(i));if(found!=null)return found;}}
        return null;
    }
    void dispose(){revision++;for(int i=0;i<stage.getChildCount();i++)stage.getChildAt(i).animate().cancel();}
}
