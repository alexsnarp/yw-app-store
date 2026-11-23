package com.lge.lgmdmservice;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persistent, bounded diagnostic history for app events plus logcat entries readable by this app. */
public final class DiagnosticLog {
    private static final Object LOCK=new Object();
    private static final long MAX_BYTES=5L*1024L*1024L;
    private static final String FILE_NAME="yw-app-store-diagnostics.log";
    private DiagnosticLog(){}

    public static void record(Context context,String event){
        if(context==null||event==null)return;
        String line=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(new Date())
                +" ["+Thread.currentThread().getName()+"] "+event+"\n";
        synchronized(LOCK){
            File file=new File(context.getFilesDir(),FILE_NAME);
            try{
                if(file.exists()&&file.length()>MAX_BYTES){
                    File previous=new File(context.getFilesDir(),FILE_NAME+".1");
                    if(previous.exists())previous.delete();
                    file.renameTo(previous);
                }
                try(FileOutputStream out=new FileOutputStream(file,true)){out.write(line.getBytes("UTF-8"));out.flush();}
            }catch(Exception ignored){}
        }
    }

    public static String export(Context context){
        StringBuilder result=new StringBuilder();
        result.append("YW App Store diagnostics\nAndroid: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\nDevice: ")
                .append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n\n");
        synchronized(LOCK){
            appendFile(result,new File(context.getFilesDir(),FILE_NAME+".1"));
            appendFile(result,new File(context.getFilesDir(),FILE_NAME));
        }
        result.append("\n\n===== Android logcat entries available to this app =====\n");
        Process process=null;
        try{
            process=Runtime.getRuntime().exec(new String[]{"logcat","-d","-v","threadtime"});
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){
                char[] buffer=new char[8192];int count;int limit=4*1024*1024;
                while(limit>0&&(count=reader.read(buffer,0,Math.min(buffer.length,limit)))!=-1){result.append(buffer,0,count);limit-=count;}
            }
            process.waitFor();
        }catch(Exception error){result.append("\nUnable to read some system log entries: ").append(error.toString()).append("\n");}
        finally{if(process!=null)process.destroy();}
        result.append("\n===== End of diagnostic export =====\n");
        return result.toString();
    }

    private static void appendFile(StringBuilder out,File file){
        if(!file.isFile())return;
        out.append("===== ").append(file.getName()).append(" =====\n");
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"))){
            char[] buffer=new char[8192];int count;while((count=reader.read(buffer))!=-1)out.append(buffer,0,count);
        }catch(Exception error){out.append("Could not read app log: ").append(error.toString()).append("\n");}
        out.append("\n");
    }
}
