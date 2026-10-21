package com.pocketledger.v2;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.lang.reflect.Field;
import java.util.Map;

/** Runs on a disposable emulator: creates a synthetic person through the real dialog. */
public final class ReviewRegressionInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            inlinePerson();
            result.putString("stream","PASS: inline person creation preserves the entry draft.\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
    private Object field(Object target,String name)throws Exception{
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private View button(View view,String text){
        if(view instanceof Button&&text.contentEquals(((Button)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            View found=button(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;
        }
        return null;
    }
    private AccessibilityNodeInfo editable(AccessibilityNodeInfo node){
        if(node==null)return null;if(node.isEditable())return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=editable(node.getChild(i));if(found!=null)return found;}
        return null;
    }
    @SuppressWarnings("unchecked") private void inlinePerson()throws Exception{
        LedgerActivity activity=(LedgerActivity)startActivitySync(LedgerActivity.intent(getTargetContext(),"entry").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            long deadline=android.os.SystemClock.uptimeMillis()+10000;
            boolean[] ready={false};
            while(!ready[0]&&android.os.SystemClock.uptimeMillis()<deadline){
                runOnMainSync(()->{try{ready[0]=((Map<?,?>)field(activity,"inputs")).containsKey("amount");}catch(Exception e){throw new RuntimeException(e);}});
                if(!ready[0])android.os.SystemClock.sleep(50);
            }
            check(ready[0],"Entry did not load");
            final long chosenAt=1792065600000L;
            final int[] payment={-1};
            final EditText[] originalAmount={null};
            runOnMainSync(()->{try{
                Map<String,EditText> inputs=(Map<String,EditText>)field(activity,"inputs");
                originalAmount[0]=inputs.get("amount");
                inputs.get("amount").setText("600");inputs.get("note").setText("Unfinished entry");inputs.get("own").setText("123");
                ((CheckBox)field(activity,"splitEnabled")).setChecked(true);
                Field at=activity.getClass().getDeclaredField("chosenAt");at.setAccessible(true);at.setLong(activity,chosenAt);
                payment[0]=((Map<String,LedgerChoice>)field(activity,"selects")).get("payment").getSelectedItemPosition();
                View add=button(activity.getWindow().getDecorView(),"Add person");check(add!=null,"Missing Add person");add.performClick();
            }catch(Exception e){throw new RuntimeException(e);}});
            waitForIdleSync();
            AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow(),name=editable(root);
            check(name!=null,"Person name dialog did not open");
            Bundle text=new Bundle();text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Draft regression "+System.currentTimeMillis());
            check(name.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,text),"Cannot enter person name");
            boolean clicked=false;
            for(AccessibilityNodeInfo save:root.findAccessibilityNodeInfosByText("Save"))if(save.isClickable()){clicked=save.performAction(AccessibilityNodeInfo.ACTION_CLICK);break;}
            check(clicked,"Cannot save person");
            deadline=android.os.SystemClock.uptimeMillis()+10000;boolean[] refreshed={false};
            while(!refreshed[0]&&android.os.SystemClock.uptimeMillis()<deadline){
                runOnMainSync(()->{try{refreshed[0]=((Map<String,EditText>)field(activity,"inputs")).get("amount")!=originalAmount[0]&&!((Boolean)field(activity,"busy"))&&((Map<String,EditText>)field(activity,"inputs")).keySet().stream().anyMatch(k->k.startsWith("share:"))&&button(activity.getWindow().getDecorView(),"Add person")!=null;}catch(Exception e){throw new RuntimeException(e);}});
                if(!refreshed[0])android.os.SystemClock.sleep(50);
            }
            waitForIdleSync();
            runOnMainSync(()->{try{
                Map<String,EditText> inputs=(Map<String,EditText>)field(activity,"inputs");
                check(refreshed[0],"Person save did not refresh entry");
                check("600".contentEquals(inputs.get("amount").getText()),"Amount reset");
                check("Unfinished entry".contentEquals(inputs.get("note").getText()),"Note reset");
                check("123".contentEquals(inputs.get("own").getText()),"Own allocation reset");
                check(((CheckBox)field(activity,"splitEnabled")).isChecked(),"Split toggle reset");
                check((Long)field(activity,"chosenAt")==chosenAt,"Date reset");
                check(((Map<String,LedgerChoice>)field(activity,"selects")).get("payment").getSelectedItemPosition()==payment[0],"Payment reset");
            }catch(Exception e){throw new RuntimeException(e);}});
        }finally{runOnMainSync(activity::finish);}
    }
}
