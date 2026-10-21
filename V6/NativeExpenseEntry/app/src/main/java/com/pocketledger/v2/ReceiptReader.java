package com.pocketledger.v2;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.*;
import java.util.*;

/** Reads the user's chosen file locally; no camera permission or receipt upload service. */
final class ReceiptReader {
    private static final long MAX_BYTES=20L*1024*1024;
    private static final int MAX_PAGES=5;
    static String read(Context context,Uri uri) throws Exception {
        File file=File.createTempFile("split-receipt-",".bin",context.getCacheDir());
        try{
            try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)){
                if(in==null)throw new IOException("Could not open this file");
                byte[] buffer=new byte[16384];long count=0;int n;while((n=in.read(buffer))!=-1){count+=n;if(count>MAX_BYTES)throw new IOException("Choose a bill smaller than 20 MB");out.write(buffer,0,n);}
            }
            boolean pdf;try(InputStream in=new FileInputStream(file)){byte[] header=new byte[5];pdf=in.read(header)==5&&new String(header,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");}
            try(TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)){
                if(pdf){StringBuilder text=new StringBuilder();
                    try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(descriptor)){
                        if(renderer.getPageCount()>MAX_PAGES)throw new IOException("Choose a PDF with at most 5 pages");
                        for(int i=0;i<renderer.getPageCount();i++)try(PdfRenderer.Page page=renderer.openPage(i)){
                            float scale=2600f/Math.max(page.getWidth(),page.getHeight());Bitmap bitmap=Bitmap.createBitmap(Math.max(1,Math.round(page.getWidth()*scale)),Math.max(1,Math.round(page.getHeight()*scale)),Bitmap.Config.ARGB_8888);
                            try{bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);text.append(recognize(recognizer,bitmap)).append('\n');}finally{bitmap.recycle();}
                        }
                    }
                    return bounded(text.toString());
                }
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);
                if(options.outWidth<=0||options.outHeight<=0)throw new IOException("Choose a JPG, PNG, WebP image or an unlocked PDF");
                int sample=1;while((long)(options.outWidth/sample)*(options.outHeight/sample)>8_000_000L||Math.max(options.outWidth,options.outHeight)/sample>6000)sample*=2;
                options.inJustDecodeBounds=false;options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                Bitmap image=BitmapFactory.decodeFile(file.getPath(),options);if(image==null)throw new IOException("Could not read this image. Try exporting it as JPG or PNG.");
                try{
                    int orientation=ExifInterface.ORIENTATION_NORMAL;try{orientation=new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(IOException ignored){}
                    Matrix matrix=new Matrix();switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(270);matrix.postScale(-1,1);break;case 8:matrix.setRotate(270);break;default:break;}
                    if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,true);if(rotated!=image){image.recycle();image=rotated;}}
                    return bounded(recognize(recognizer,image));
                }finally{image.recycle();}
            }
        }catch(OutOfMemoryError e){throw new IOException("This image is too large to process on this phone. Export a smaller scan and try again.",e);}
        catch(SecurityException e){throw new IOException("This file could not be opened. Choose an unlocked PDF or upload its image instead.",e);}
        finally{if(!file.delete())file.deleteOnExit();}
    }
    private static String bounded(String text) throws IOException {if(text.trim().isEmpty())throw new IOException("No readable text found. Try a clearer scan, or add the items manually.");if(text.length()>50000)throw new IOException("This document is too long. Upload just the bill pages.");return text;}
    private static String recognize(TextRecognizer recognizer,Bitmap bitmap) throws Exception {
        Text text=Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)));
        // Rejoin OCR columns by their vertical positions before parsing item rows.
        List<Text.Element> elements=new ArrayList<>();for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines())for(Text.Element element:line.getElements())if(element.getBoundingBox()!=null)elements.add(element);
        elements.sort(Comparator.comparingInt(e->e.getBoundingBox().centerY()));List<List<Text.Element>> rows=new ArrayList<>();
        for(Text.Element element:elements){Rect box=element.getBoundingBox();List<Text.Element> row=rows.isEmpty()?null:rows.get(rows.size()-1);
            if(row==null||Math.abs(box.centerY()-row.get(0).getBoundingBox().centerY())>Math.max(box.height(),row.get(0).getBoundingBox().height())*.55f){row=new ArrayList<>();rows.add(row);}row.add(element);
        }
        StringBuilder out=new StringBuilder();for(List<Text.Element> row:rows){row.sort(Comparator.comparingInt(e->e.getBoundingBox().left));for(Text.Element element:row)out.append(element.getText()).append(' ');out.append('\n');}return out.toString();
    }
}
