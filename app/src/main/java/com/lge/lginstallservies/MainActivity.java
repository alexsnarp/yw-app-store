package com.lge.lginstallservies;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Typeface;
import android.net.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout root, list;
    private TextView status;
    private android.content.SharedPreferences prefs;
    private String endpoint, code;
    private static final String DEFAULT_ENDPOINT = "https://REPLACE-WITH-YOUR-WORKER";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("store", MODE_PRIVATE);
        endpoint = prefs.getString("endpoint", DEFAULT_ENDPOINT);
        code = prefs.getString("code", "");
        buildUi();
        if (!DEFAULT_ENDPOINT.equals(endpoint)) loadCatalog();
        else status.setText("Set your secure store server in Settings to load apps.");
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(1); root.setPadding(20,18,20,12);
        root.setBackgroundColor(0xfffafafa);
        setContentView(root);
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this); title.setText("YW App Store"); title.setTextSize(25); title.setTextColor(0xff202124); title.setTypeface(null, Typeface.BOLD);
        bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button settings = new Button(this); settings.setText("Settings"); settings.setOnClickListener(v->settings()); bar.addView(settings);
        root.addView(bar);
        status = new TextView(this); status.setTextColor(0xff555555); status.setTextSize(14); status.setPadding(0,12,0,12); root.addView(status);
        ScrollView scroll = new ScrollView(this); list = new LinearLayout(this); list.setOrientation(1); scroll.addView(list);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button refresh = new Button(this); refresh.setText("Refresh apps"); refresh.setOnClickListener(v->loadCatalog()); root.addView(refresh);
    }

    private void settings() {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(1); panel.setPadding(24,12,24,12);
        EditText url = new EditText(this); url.setSingleLine(true); url.setHint("Secure Worker URL (https://...)"); url.setText(endpoint); panel.addView(url);
        EditText activation = new EditText(this); activation.setSingleLine(true); activation.setHint("Device authorization code"); activation.setText(code); panel.addView(activation);
        new AlertDialog.Builder(this).setTitle("Store settings").setView(panel)
          .setPositiveButton("Save", (d,w)->{
            endpoint=url.getText().toString().trim().replaceAll("/+$","");
            code=activation.getText().toString().trim();
            prefs.edit().putString("endpoint",endpoint).putString("code",code).apply();
            loadCatalog();
          }).setNegativeButton("Cancel",null).show();
    }

    private boolean online() {
        try {
            android.net.ConnectivityManager cm=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            android.net.Network n=cm.getActiveNetwork();
            android.net.NetworkCapabilities c=cm.getNetworkCapabilities(n);
            return c!=null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch(Exception e) { return false; }
    }

    private void loadCatalog() {
        if (DEFAULT_ENDPOINT.equals(endpoint) || !endpoint.startsWith("https://")) { status.setText("Configure a valid HTTPS Worker URL in Settings."); return; }
        if (!online()) { status.setText("No internet connection. Connect using Wi-Fi or mobile data."); return; }
        status.setText("Loading apps…"); list.removeAllViews();
        io.execute(()->{
            try {
                URL u=new URL(endpoint+"/api/catalog");
                HttpURLConnection c=(HttpURLConnection)u.openConnection(); c.setConnectTimeout(15000); c.setReadTimeout(20000);
                c.setRequestProperty("Authorization","Bearer "+code);
                c.setRequestProperty("X-Device-Id", android.provider.Settings.Secure.getString(getContentResolver(),android.provider.Settings.Secure.ANDROID_ID));
                int response=c.getResponseCode();
                InputStream in=response>=200&&response<300?c.getInputStream():c.getErrorStream();
                ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n; while((n=in.read(buf))>0)out.write(buf,0,n);
                if(response<200||response>=300) throw new IOException("Server returned HTTP "+response);
                JSONObject data=new JSONObject(out.toString("UTF-8")); JSONArray apps=data.optJSONArray("apps");
                runOnUiThread(()->{
                    list.removeAllViews();
                    if(apps==null||apps.length()==0){status.setText("No apps are available.");return;}
                    status.setText(apps.length()+" apps available");
                    for(int i=0;i<apps.length();i++) try { addApp(apps.getJSONObject(i)); } catch(Exception ignored) {}
                });
            } catch(Exception e) { runOnUiThread(()->status.setText("Could not load apps: "+e.getMessage())); }
        });
    }

    private void addApp(JSONObject app) {
        String name=app.optString("name",app.optString("title","App"));
        String desc=app.optString("description","");
        LinearLayout card=new LinearLayout(this); card.setOrientation(1); card.setPadding(14,12,14,12);
        card.setBackgroundColor(0xffffffff);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,6,0,6); card.setLayoutParams(cp);
        TextView t=new TextView(this); t.setText(name); t.setTextSize(18); t.setTypeface(null,Typeface.BOLD); t.setTextColor(0xff202124); card.addView(t);
        TextView d=new TextView(this); d.setText(desc); d.setTextColor(0xff666666); card.addView(d);
        Button action=new Button(this); action.setText("Download"); action.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("The secure download service must be configured on your Worker before downloads can start.").setPositiveButton("OK",null).show()); card.addView(action);
        list.addView(card);
    }
    @Override protected void onDestroy(){super.onDestroy();io.shutdownNow();}
}
