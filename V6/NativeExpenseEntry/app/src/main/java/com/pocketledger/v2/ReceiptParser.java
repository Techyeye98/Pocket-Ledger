package com.pocketledger.v2;

import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Conservative suggestions only. Every extracted row is reviewed before import. */
final class ReceiptParser {
    private static final Pattern NUMBER=Pattern.compile("(?<![\\p{L}\\d])[-−]?(?:\\d{1,3}(?:,\\d{2,3})+|\\d+)(?:\\.\\d{1,2})?(?![\\d.])");
    private static final Pattern IGNORE=Pattern.compile("(?i).*(?:gstin|fssai|invoice|bill\\s*(?:no|number|#)|table\\s*(?:no|number)|order\\s*(?:no|number|id)|phone|mobile|tel[: .]|www\\.|https?://|thank|cashier|server|waiter|payment|paid|cash tender|balance|change due|upi|card no).*|.*\\d{1,2}[/:]\\d{1,2}[/:]\\d{2,4}.*");
    static final class Candidate {
        String name;long amount;boolean selected=true;
        Candidate(String name,long amount){this.name=name;this.amount=amount;}
    }
    static final class Draft {
        String raw="",taxRate="";long tax,discount,extra,roundOff,total=-1;
        final List<Candidate> items=new ArrayList<>();
        JSONObject json() throws JSONException {JSONArray a=new JSONArray();for(Candidate c:items)a.put(new JSONObject().put("name",c.name).put("amount",c.amount).put("selected",c.selected));return new JSONObject().put("raw",raw).put("taxRate",taxRate).put("tax",tax).put("discount",discount).put("extra",extra).put("roundOff",roundOff).put("total",total).put("items",a);}
        static Draft read(JSONObject o) throws JSONException {Draft d=new Draft();d.raw=o.getString("raw");d.taxRate=o.optString("taxRate","");d.tax=o.getLong("tax");d.discount=o.getLong("discount");d.extra=o.getLong("extra");d.roundOff=o.optLong("roundOff",0);d.total=o.getLong("total");JSONArray a=o.getJSONArray("items");for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);Candidate c=new Candidate(x.getString("name"),x.getLong("amount"));c.selected=x.getBoolean("selected");d.items.add(c);}return d;}
    }
    static Draft parse(String text){
        Draft d=new Draft();d.raw=text;String pending="";long componentsTax=0,explicitTax=-1;
        for(String raw:text.split("\\r?\\n")){
            String line=raw.replace('₹',' ').replaceAll("(?i)\\b(?:INR|Rs\\.?)\\s*", " ").trim();if(line.isEmpty())continue;
            String lower=line.toLowerCase(Locale.ROOT);
            boolean summary=lower.matches(".*\\b(?:total|subtotal|sub total|cgst|sgst|igst|gst|tax|discount|disc|coupon|service charge|svc|tip|round\\s*off|rounding|amount payable|net amount|net payable|amount due)\\b.*");
            if(IGNORE.matcher(line).matches()){pending="";continue;}
            Matcher matcher=NUMBER.matcher(line);String last=null;int lastStart=-1,lastEnd=-1;
            while(matcher.find()){last=matcher.group();lastStart=matcher.start();lastEnd=matcher.end();}
            if(last==null){if(!summary&&line.matches(".*\\p{L}.*")&&!lower.matches(".*\\b(?:qty|quantity|rate|price|amount|restaurant|receipt|tax invoice)\\b.*"))pending=line;else pending="";continue;}
            String suffix=line.substring(lastEnd).trim();
            if(suffix.startsWith("%")){pending="";continue;} // A tax rate is not a tax amount.
            long value;try{value=Money.parse(last.replace(",","").replace('−','-'));}catch(Exception e){pending="";continue;}
            if(summary){
                if(lower.matches(".*\\b(?:round\\s*off|rounding)\\b.*")){d.roundOff=value;pending="";continue;}
                pending="";value=Math.abs(value);
                if(lower.matches(".*\\b(?:subtotal|sub total|total items|total qty|total quantity)\\b.*"))continue;
                if(lower.matches(".*\\b(?:discount|disc|coupon)\\b.*"))d.discount=Money.add(d.discount,value);
                else if(lower.matches(".*\\b(?:service charge|svc|tip)\\b.*"))d.extra=Money.add(d.extra,value);
                else if(lower.matches(".*\\b(?:cgst|sgst|igst)\\b.*"))componentsTax=Money.add(componentsTax,value);
                else if(lower.matches(".*\\b(?:gst|tax)\\b.*"))explicitTax=value;
                else if(lower.matches(".*\\b(?:total|amount payable|net amount|net payable|amount due)\\b.*"))d.total=value;
                continue;
            }
            if(value<=0||!suffix.matches("(?i)[ *A-Z]*")){pending="";continue;}
            String name=line.substring(0,lastStart).replaceAll("\\s+"," ").trim();
            // Strip quantity/unit-price columns; the rightmost number is the proposed line total.
            name=name.replaceAll("\\s+\\d+(?:\\.\\d+)?(?:\\s*[xX*]\\s*|\\s+).*$","").replaceAll("^\\d+[.)]?\\s+(?=\\p{L})","").trim();
            if(!name.matches(".*\\p{L}.*")){name=pending;}
            if(!name.isEmpty()&&name.matches(".*\\p{L}.*")&&d.items.size()<200)d.items.add(new Candidate(name,value));
            pending="";
        }
        d.tax=explicitTax>=0?explicitTax:componentsTax;
        return d;
    }
}
