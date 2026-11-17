package com.lge.lgmdmservice;
import android.content.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;
public final class StoreReporter {
 private static final String API="https://yw-app-store-api.yw-app-installer-store-2026.workers.dev";
 public static void report(Context c,String pkg,String version,String status){
  String token=c.getSharedPreferences("yw_store",0).getString("device_token",null);if(token==null||pkg==null)return;
  new Thread(()->{HttpURLConnection h=null;try{JSONObject j=new JSONObject();j.put("packageName",pkg);j.put("version",version);j.put("status",status);h=(HttpURLConnection)new URL(API+"/api/report").openConnection();if(h instanceof HttpsURLConnection)try{LegacyTlsCompat.configure(c,(HttpsURLConnection)h);}catch(Exception ignored){}h.setRequestMethod("POST");h.setConnectTimeout(10000);h.setReadTimeout(10000);h.setDoOutput(true);h.setRequestProperty("Authorization","Bearer "+token);h.setRequestProperty("Content-Type","application/json");try(OutputStream o=h.getOutputStream()){o.write(j.toString().getBytes("UTF-8"));}h.getResponseCode();}catch(Exception ignored){}finally{if(h!=null)h.disconnect();}}).start();
 }
}