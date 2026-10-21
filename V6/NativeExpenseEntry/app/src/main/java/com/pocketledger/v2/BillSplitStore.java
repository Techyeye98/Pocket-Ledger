package com.pocketledger.v2;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.JSONObject;
import java.util.*;

/** Separate local database: splitting never writes to the financial ledger. */
final class BillSplitStore extends SQLiteOpenHelper {
    BillSplitStore(Context context){super(context,"pocket_ledger_splits.db",null,3);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE bills (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL, updatedAt INTEGER NOT NULL)");createFriends(db);}
    private void createFriends(SQLiteDatabase db){db.execSQL("CREATE TABLE friends (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL UNIQUE, updatedAt INTEGER NOT NULL DEFAULT 0)");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){createFriends(db);
            try(Cursor rows=db.query("bills",new String[]{"payload"},null,null,null,null,"updatedAt DESC")){
                while(rows.moveToNext())try{
                    org.json.JSONArray people=new JSONObject(rows.getString(0)).getJSONArray("people");
                    for(int i=0;i<people.length();i++){JSONObject person=people.getJSONObject(i);String name=person.getString("name");if(!BillSplit.friendKey(name).equals("you"))db.insertWithOnConflict("friends",null,friendRow(person.getString("id"),name),SQLiteDatabase.CONFLICT_IGNORE);}
                }catch(Exception e){throw new IllegalStateException("Could not migrate saved split friends",e);}
            }
        }
        if(oldVersion==2)db.execSQL("ALTER TABLE friends ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0");
    }
    private ContentValues friendRow(String id,String name){ContentValues row=new ContentValues();row.put("id",id);row.put("name",name.trim());row.put("nameKey",BillSplit.friendKey(name));row.put("updatedAt",System.currentTimeMillis());return row;}
    List<BillSplit.Person> friends(){List<BillSplit.Person> result=new ArrayList<>();try(Cursor rows=getReadableDatabase().query("friends",new String[]{"id","name","updatedAt"},null,null,null,null,"name COLLATE NOCASE")){while(rows.moveToNext()){BillSplit.Person p=new BillSplit.Person(rows.getString(0),rows.getString(1));p.updatedAt=rows.getLong(2);result.add(p);}}return result;}
    void saveFriend(String id,String name){
        BillSplit.require(!BillSplit.friendKey(name).equals("you"),"You is already included automatically");ContentValues row=friendRow(id,name);
        SQLiteDatabase db=getWritableDatabase();try{if(db.update("friends",row,"id=?",new String[]{id})==0)db.insertOrThrow("friends",null,row);}catch(SQLiteConstraintException e){throw new IllegalArgumentException("This friend is already saved");}
    }
    void deleteFriend(String id){getWritableDatabase().delete("friends","id=?",new String[]{id});}
    List<BillSplit> all() throws Exception {
        List<BillSplit> bills=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("bills",new String[]{"payload"},null,null,null,null,"updatedAt DESC")){
            while(c.moveToNext()){BillSplit b=BillSplit.read(new JSONObject(c.getString(0)));b.calculate();bills.add(b);}
        }
        return bills;
    }
    void save(BillSplit bill) throws Exception {
        bill.calculate();bill.updatedAt=System.currentTimeMillis();
        ContentValues row=new ContentValues();row.put("id",bill.id);row.put("payload",bill.json().toString());row.put("updatedAt",bill.updatedAt);
        if(getWritableDatabase().insertWithOnConflict("bills",null,row,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new android.database.SQLException("Could not save this split bill");
    }
    void delete(String id){getWritableDatabase().delete("bills","id=?",new String[]{id});}
}
