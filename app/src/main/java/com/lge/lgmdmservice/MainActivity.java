package com.lge.lgmdmservice;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.Typeface;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

public class MainActivity extends Activity {
    private static final String PREF="yw_store";
    private static final String API="https://yw-app-store-api.yw-app-installer-store-2026.workers.dev";
    private static final int BG=0xfff5f7fb, PANEL=0xffffffff, EDGE=0xffe1e7f0, BLUE=0xff1769e0, BLUE_DARK=0xff123f86, BLUE_TINT=0xffedf4ff, INK=0xff152238, MUTED=0xff637187, GREEN=0xff087c58, GOLD=0xffffb300;
    private final Set<String> downloading=Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> cancelled=Collections.synchronizedSet(new HashSet<String>());
    private LinearLayout root, content;
    private String token, deviceId;
    private boolean catalogBusy;
    private JSONArray catalogApps;
    private GridLayout catalogList;
    private TextView downloadLabel;
    private ProgressBar downloadProgress;
    private LinearLayout downloadActions;
    private JSONObject selectedApp;
    private EditText searchBox;
    private boolean installedOnly;
    private String catalogQuery="";
    private Button filterAllButton,filterInstalledButton;

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private android.content.SharedPreferences prefs(){return getSharedPreferences(PREF,0);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs().edit().putBoolean("main_activity_launched",true).apply();NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.cancel(2301);deviceId=prefs().getString("device_id",null);if(deviceId==null){deviceId=UUID.randomUUID().toString();prefs().edit().putString("device_id",deviceId).apply();}token=prefs().getString("device_token",null);render();maybeRequestLocalUpdatePermission();}
    @Override protected void onStart(){super.onStart();prefs().edit().putBoolean("main_activity_launched",true).apply();}
    @Override protected void onStop(){prefs().edit().putBoolean("main_activity_launched",false).apply();super.onStop();}
    @Override protected void onResume(){super.onResume();token=prefs().getString("device_token",null);if(content!=null&&token!=null)loadCatalog();}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(17));d.setStroke(dp(1),EDGE);return d;}
    private GradientDrawable fillShape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private Drawable focusRing(int fill){GradientDrawable outer=new GradientDrawable();outer.setColor(Color.TRANSPARENT);outer.setCornerRadius(dp(15));outer.setStroke(dp(4),GOLD);GradientDrawable inner=new GradientDrawable();inner.setColor(fill);inner.setCornerRadius(dp(11));inner.setStroke(dp(1),Color.WHITE);return new android.graphics.drawable.LayerDrawable(new Drawable[]{outer,new android.graphics.drawable.InsetDrawable(inner,dp(4))});}
    private Button actionButton(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(dp(46));b.setPadding(dp(14),dp(7),dp(14),dp(7));b.setFocusable(true);b.setFocusableInTouchMode(false);b.setTextColor(primary?Color.WHITE:BLUE_DARK);StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},focusRing(primary?BLUE_DARK:0xfffff3c4));states.addState(new int[]{android.R.attr.state_pressed},focusRing(primary?BLUE_DARK:0xfffff8e6));states.addState(new int[]{},primary?fillShape(BLUE):focusShape(PANEL,EDGE,1));b.setBackground(states);return b;}
    private GradientDrawable focusShape(int color,int strokeColor,int strokeWidth){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));d.setStroke(dp(strokeWidth),strokeColor);return d;}
    private StateListDrawable focusBackground(){StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},focusRing(0xfffff7df));states.addState(new int[]{android.R.attr.state_pressed},focusRing(0xffe8efff));states.addState(new int[]{},focusShape(PANEL,EDGE,1));return states;}
    private void style(View v){if(v.isFocusable()){if(!(v instanceof Button))v.setBackground(focusBackground());v.setFocusable(true);v.setFocusableInTouchMode(v instanceof EditText);if(v instanceof Button){v.setFocusable(true);v.setFocusableInTouchMode(false);v.setMinimumHeight(dp(38));}else{v.setPadding(dp(12),dp(9),dp(12),dp(9));}if(v instanceof Button){((Button)v).setAllCaps(false);((Button)v).setTextSize(14);}}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)style(g.getChildAt(i));}}
    private void render(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(10),dp(6),dp(10),dp(6));root.setBackgroundColor(BG);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(4),dp(1),dp(2),dp(5));
        TextView logo=text("YW",17,Color.WHITE,true);logo.setGravity(Gravity.CENTER);logo.setBackground(fillShape(0xff10285a));header.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(38)));
        TextView title=text("YW",17,INK,true);LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);titleLp.leftMargin=dp(9);header.addView(title,titleLp);
        if(token!=null){TextView settings=text("⚙",23,INK,true);settings.setGravity(Gravity.CENTER);settings.setContentDescription("Settings");settings.setFocusable(true);settings.setBackground(focusBackground());settings.setOnClickListener(v->settings());settings.setOnLongClickListener(v->{openLogcat();return true;});header.addView(settings,new LinearLayout.LayoutParams(dp(42),dp(38)));}
        root.addView(header);View line=new View(this);line.setBackgroundColor(EDGE);root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(2),dp(5),dp(2),dp(8));content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(token==null){authorizationView();}else{loadCatalog();}
        setContentView(root);style(root);focusFirst();
    }
    private void authorizationView(){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(shape(PANEL));content.addView(card);
        card.addView(text("Authorize this device",20,INK,true));TextView note=text("Enter the one-time code supplied by your administrator to unlock the app catalog.",14,MUTED,false);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(8);card.addView(note,np);
        EditText code=new EditText(this);code.setSingleLine(true);code.setHint("Authorization code");code.setTextColor(INK);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(52));ep.topMargin=dp(14);card.addView(code,ep);
        Button submit=actionButton("Authorize and open catalog",true);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(50));bp.topMargin=dp(10);card.addView(submit,bp);
        submit.setOnClickListener(v->{String value=code.getText().toString().trim();if(value.length()<6){toast("Enter the authorization code.");return;}submit.setEnabled(false);new Thread(()->{try{JSONObject body=new JSONObject();body.put("code",value);body.put("deviceId",deviceId);JSONObject result=request("POST","/api/redeem",body,null);String t=result.optString("token","");if(t.length()<16)throw new IOException("Invalid device token");prefs().edit().putString("device_token",t).apply();runOnUiThread(()->{token=t;toast("Device authorized.");render();});}catch(Exception e){runOnUiThread(()->{submit.setEnabled(true);toast("Authorization failed: "+e.getMessage());});}}).start();});
        code.setOnEditorActionListener((v,id,event)->{submit.performClick();return true;});
    }
    private void addSettingRow(LinearLayout panel,String title,String subtitle,CheckBox check){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(13),dp(10),dp(10),dp(10));row.setBackground(shape(PANEL));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(9);panel.addView(row,rp);
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text(title,14,INK,true));TextView sub=text(subtitle,11,MUTED,false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(3);copy.addView(sub,sp);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));check.setContentDescription(title);row.addView(check);
    }
    private void settings(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(8),dp(16),dp(6));panel.setBackgroundColor(BG);
        panel.addView(text("Downloads",11,BLUE,true));
        CheckBox mobile=new CheckBox(this);mobile.setChecked(prefs().getBoolean("mobile_data",false));addSettingRow(panel,"Mobile data downloads","Allow downloads when Wi-Fi is unavailable",mobile);
        panel.addView(text("Appearance",11,BLUE,true));
        CheckBox installed=new CheckBox(this);installed.setChecked(prefs().getBoolean("show_installed",true));addSettingRow(panel,"Show installed apps","Keep installed apps visible in the library",installed);
        CheckBox descriptions=new CheckBox(this);descriptions.setChecked(prefs().getBoolean("show_descriptions",true));addSettingRow(panel,"Show app descriptions","Display the summary beneath each app name",descriptions);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Settings").setView(panel).setPositiveButton("Save",(d,w)->{
            prefs().edit().putBoolean("mobile_data",mobile.isChecked()).putBoolean("show_installed",installed.isChecked()).putBoolean("show_descriptions",descriptions.isChecked()).apply();
            if(catalogApps!=null)showCatalog(catalogApps,false);else loadCatalog();
        }).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(x->{dialog.getButton(-1).setTextColor(BLUE);dialog.getButton(-2).setTextColor(MUTED);});dialog.show();dialog.getWindow().setBackgroundDrawable(fillShape(PANEL));
    }
    private void refreshCatalog(){if(catalogBusy){toast("The catalog is already refreshing.");return;}content.removeAllViews();message("Refreshing catalog…");loadCatalog();}
    private void openLogcat(){String output;try{java.lang.Process process=Runtime.getRuntime().exec(new String[]{"logcat","-d","-v","time","-t","500"});ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=process.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);}process.waitFor();output=bytes.toString("UTF-8");if(output.trim().isEmpty())output="No logcat entries were returned. Android may restrict access to system logs."; }catch(Exception e){output="Could not read logcat: "+e.getMessage();}final String logText=output;ScrollView scroll=new ScrollView(this);TextView logs=text(logText,10,INK,false);logs.setTextIsSelectable(true);logs.setTypeface(Typeface.MONOSPACE);logs.setPadding(dp(8),dp(8),dp(8),dp(8));scroll.addView(logs);new AlertDialog.Builder(this).setTitle("Device logcat").setView(scroll).setPositiveButton("Close",null).setNeutralButton("Copy",(d,w)->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("YW App Store logcat",logText));toast("Logcat copied.");}).show();}
    
    private void maybeRequestLocalUpdatePermission(){if(Build.VERSION.SDK_INT>=23&&Build.VERSION.SDK_INT<=32&&checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){File local=new File(android.os.Environment.getExternalStorageDirectory(),"YWAI.apk");if(local.isFile())requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},902);}}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==902&&grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)toast("Storage access granted.");}
    private boolean hasNetwork(){return NetworkAccess.isAllowed(this);}
    private JSONObject request(String method,String path,JSONObject body,String bearer)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();if(c instanceof HttpsURLConnection)try{LegacyTlsCompat.configure(this,(HttpsURLConnection)c);}catch(Exception ignored){}c.setRequestMethod(method);c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-cache, no-store");c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("Accept","application/json");if(bearer!=null)c.setRequestProperty("Authorization","Bearer "+bearer);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes("UTF-8"));}}int status=c.getResponseCode();InputStream in=status>=200&&status<300?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){try(InputStream x=in){byte[] b=new byte[8192];int n;while((n=x.read(b))!=-1)out.write(b,0,n);}}c.disconnect();String raw=out.toString("UTF-8");JSONObject result=raw.isEmpty()?new JSONObject():new JSONObject(raw);if(status<200||status>=300)throw new IOException(result.optString("error","HTTP "+status));return result;}
    private void loadCatalog(){if(token==null||catalogBusy)return;if(!hasNetwork()){String cache=prefs().getString("cached_catalog_json",null);if(cache!=null)try{showCatalog(new JSONArray(cache),true);return;}catch(Exception ignored){}message("Connect to Wi-Fi or enable mobile data in Settings.");return;}catalogBusy=true;message("Loading apps…");new Thread(()->{try{JSONObject result=request("GET","/api/catalog",null,token);JSONArray apps=result.optJSONArray("apps");if(apps==null)throw new IOException("Invalid catalog response");prefs().edit().putString("cached_catalog_json",apps.toString()).apply();runOnUiThread(()->{catalogBusy=false;showCatalog(apps,false);});}catch(Exception e){runOnUiThread(()->{catalogBusy=false;String cache=prefs().getString("cached_catalog_json",null);try{if(cache!=null){showCatalog(new JSONArray(cache),true);message("Server unavailable — showing saved catalog.");}else message("Could not load catalog: "+e.getMessage());}catch(Exception x){message("Could not load catalog.");}});}}).start();}
    private void message(String s){if(content==null)return;TextView t=text(s,14,MUTED,false);content.addView(t);}
    private void showCatalog(JSONArray apps,boolean cached){
        catalogApps=apps;selectedApp=null;downloadLabel=null;downloadProgress=null;downloadActions=null;
        content.removeAllViews();
        if(cached){TextView cache=text("OFFLINE  ·  Saved app catalog",10,0xff9a5a00,true);cache.setPadding(dp(8),dp(4),dp(8),dp(4));cache.setBackground(fillShape(0xfffff2d8));content.addView(cache);}
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(4),dp(2),dp(2),dp(4));
        TextView count=text(apps.length()+" apps",12,MUTED,true);bar.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        TextView refresh=text("↻",22,BLUE,true);refresh.setGravity(Gravity.CENTER);refresh.setContentDescription("Refresh catalog");refresh.setFocusable(true);refresh.setBackground(focusBackground());refresh.setOnClickListener(v->refreshCatalog());bar.addView(refresh,new LinearLayout.LayoutParams(dp(38),dp(34)));content.addView(bar);
        catalogList=new GridLayout(this);catalogList.setOrientation(GridLayout.HORIZONTAL);catalogList.setAlignmentMode(GridLayout.ALIGN_BOUNDS);catalogList.setUseDefaultMargins(false);
        int widthDp=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
        int columns=Math.max(2,Math.min(5,widthDp/142));catalogList.setColumnCount(columns);
        LinearLayout.LayoutParams gridLp=new LinearLayout.LayoutParams(-1,-2);content.addView(catalogList,gridLp);
        int shown=0;boolean showInstalled=prefs().getBoolean("show_installed",true);
        for(int i=0;i<apps.length();i++){
            JSONObject a=apps.optJSONObject(i);if(a==null)continue;
            String pkg=a.optString("packageName","");boolean installed=isInstalled(pkg);
            if(installed&&!showInstalled)continue;
            shown++;String name=a.optString("name","App"),version=a.optString("version","");
            int row=shown-1;GridLayout.LayoutParams cell=new GridLayout.LayoutParams(GridLayout.spec(row/columns),GridLayout.spec(row%columns,1f));
            cell.width=0;cell.height=dp(139);cell.setMargins(dp(3),dp(3),dp(3),dp(3));
            LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER_HORIZONTAL);tile.setPadding(dp(6),dp(7),dp(6),dp(5));tile.setBackground(focusBackground());tile.setFocusable(true);tile.setClickable(true);tile.setContentDescription(name+(installed?", installed":", available"));
            ImageView icon=new ImageView(this);icon.setImageResource(android.R.drawable.sym_def_app_icon);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(48),dp(48));iconLp.bottomMargin=dp(4);tile.addView(icon,iconLp);
            loadCatalogIcon(icon,a.optString("iconUrl",""),a.optString("apkUrl",""),pkg,version);
            TextView appName=text(name,12,INK,true);appName.setGravity(Gravity.CENTER);appName.setMaxLines(2);appName.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(appName,new LinearLayout.LayoutParams(-1,dp(31)));
            TextView state=text(installed?"✓ Installed":"Available",10,installed?GREEN:BLUE,true);state.setGravity(Gravity.CENTER);LinearLayout.LayoutParams stateLp=new LinearLayout.LayoutParams(-1,-2);stateLp.topMargin=dp(2);tile.addView(state,stateLp);
            tile.setOnClickListener(v->showAppPage(a));catalogList.addView(tile,cell);
        }
        if(shown==0){TextView empty=text("No apps in the catalog",14,MUTED,false);empty.setGravity(Gravity.CENTER);content.addView(empty);}
        focusFirst();
    }
    private void showAppPage(JSONObject app){
        selectedApp=app;content.removeAllViews();downloadLabel=null;downloadProgress=null;downloadActions=null;
        String name=app.optString("name","App"),desc=app.optString("description",""),version=app.optString("version",""),pkg=app.optString("packageName",""),url=app.optString("apkUrl","");long size=app.optLong("sizeBytes",0);boolean installed=isInstalled(pkg);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=actionButton("‹  Back",false);back.setOnClickListener(v->showCatalog(catalogApps,false));top.addView(back,new LinearLayout.LayoutParams(-2,dp(38)));
        TextView refresh=text("YW  /  APP DETAILS",10,MUTED,true);LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-2,1);tl.leftMargin=dp(10);top.addView(refresh,tl);content.addView(top);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setGravity(Gravity.CENTER_HORIZONTAL);panel.setPadding(dp(14),dp(14),dp(14),dp(14));panel.setBackground(shape(PANEL));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(8);content.addView(panel,pp);
        ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(76),dp(76));ip.bottomMargin=dp(7);panel.addView(icon,ip);loadCatalogIcon(icon,app.optString("iconUrl",""),url,pkg,version);
        TextView heading=text(name,21,INK,true);heading.setGravity(Gravity.CENTER);panel.addView(heading);
        TextView state=text(installed?"✓  INSTALLED":"AVAILABLE TO INSTALL",10,installed?GREEN:BLUE,true);LinearLayout.LayoutParams statep=new LinearLayout.LayoutParams(-1,-2);statep.topMargin=dp(5);state.setGravity(Gravity.CENTER);panel.addView(state,statep);
        if(!desc.isEmpty()){TextView description=text(desc,14,MUTED,false);description.setGravity(Gravity.CENTER);description.setLineSpacing(dp(2),1f);LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,-2);dp1.topMargin=dp(12);panel.addView(description,dp1);}
        TextView meta=text("Version  "+(version.isEmpty()?"—":version)+"\nSize  "+(size>0?formatSize(size):"—")+"\nPackage  "+pkg,12,MUTED,false);meta.setGravity(Gravity.CENTER);meta.setLineSpacing(dp(3),1f);LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(-1,-2);ml.topMargin=dp(12);panel.addView(meta,ml);
        downloadLabel=text("",12,BLUE,true);downloadLabel.setGravity(Gravity.CENTER);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,-2);dl.topMargin=dp(12);panel.addView(downloadLabel,dl);
        downloadProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);downloadProgress.setMax(100);downloadProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));downloadProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffe8edf5));downloadProgress.setMinimumHeight(dp(3));downloadProgress.setVisibility(View.GONE);LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,dp(3));pl.topMargin=dp(8);panel.addView(downloadProgress,pl);
        downloadActions=new LinearLayout(this);downloadActions.setGravity(Gravity.CENTER);LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(-1,-2);al.topMargin=dp(13);panel.addView(downloadActions,al);
        if(installed){Button open=actionButton("Open app",true);open.setOnClickListener(v->openApp(pkg));downloadActions.addView(open,new LinearLayout.LayoutParams(-1,dp(46)));Button remove=actionButton("Uninstall",false);remove.setOnClickListener(v->uninstall(pkg));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(42));rp.topMargin=dp(6);panel.addView(remove,rp);}
        else{Button install=actionButton("Download and install",true);install.setOnClickListener(v->download(url,name,pkg,version,null,null));downloadActions.addView(install,new LinearLayout.LayoutParams(-1,dp(46)));}
        focusFirst();
    }
    private void showInstallPrompt(File file,String pkg,String version){
        JSONObject a=selectedApp;if(a==null){installApk(file,pkg,version);return;}
        content.removeAllViews();String name=a.optString("name","App"),desc=a.optString("description","");LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setGravity(Gravity.CENTER_HORIZONTAL);page.setPadding(dp(18),dp(14),dp(18),dp(12));page.setBackground(shape(PANEL));content.addView(page,new LinearLayout.LayoutParams(-1,-1));
        TextView check=text("✓",42,GREEN,true);check.setGravity(Gravity.CENTER);check.setScaleX(0.15f);check.setScaleY(0.15f);page.addView(check,new LinearLayout.LayoutParams(dp(70),dp(70)));check.animate().scaleX(1f).scaleY(1f).setDuration(320).start();
        page.addView(text("Download complete",20,INK,true));TextView title=text(name,16,MUTED,true);title.setGravity(Gravity.CENTER);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(7);page.addView(title,tp);
        if(!desc.isEmpty()){TextView summary=text(desc,13,MUTED,false);summary.setGravity(Gravity.CENTER);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(6);page.addView(summary,sp);}
        TextView note=text("The app is ready. Continue to Android's secure installer to finish.",12,MUTED,false);note.setGravity(Gravity.CENTER);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(12);page.addView(note,np);
        Button install=actionButton("Install now",true);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(48));bp.topMargin=dp(18);page.addView(install,bp);install.setOnClickListener(v->installApk(file,pkg,version));
        Button back=actionButton("Back to app details",false);LinearLayout.LayoutParams bb=new LinearLayout.LayoutParams(-1,dp(42));bb.topMargin=dp(7);page.addView(back,bb);back.setOnClickListener(v->showAppPage(a));focusFirst();
    }
    private String formatSize(long bytes){if(bytes<=0)return "";if(bytes>=1048576)return String.format(Locale.US,"%.1f MB",bytes/1048576d);return String.format(Locale.US,"%d KB",Math.max(1,bytes/1024));}
    private void loadCatalogIcon(ImageView view,String iconUrl,String apkUrl,String pkg,String version){
        view.setTag(pkg);
        // Catalog icons are served independently. Never download an entire APK merely
        // to extract its icon (some catalog APKs are over 50 MB).
        if(iconUrl!=null&&iconUrl.startsWith("data:image/png;base64,")){
            try{
                byte[] bytes=Base64.decode(iconUrl.substring("data:image/png;base64,".length()),Base64.DEFAULT);
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                if(bitmap!=null){view.setImageBitmap(bitmap);return;}
            }catch(Exception ignored){}
        }
        if(iconUrl!=null&&iconUrl.startsWith("https://")){
            view.setImageResource(android.R.drawable.sym_def_app_icon);
            new Thread(()->{HttpsURLConnection connection=null;try{connection=(HttpsURLConnection)new URL(iconUrl).openConnection();LegacyTlsCompat.configure(this,connection);connection.setConnectTimeout(10000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(true);try(InputStream input=connection.getInputStream()){Bitmap bitmap=BitmapFactory.decodeStream(input);if(bitmap!=null)runOnUiThread(()->{if(pkg.equals(view.getTag()))view.setImageBitmap(bitmap);});}}catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}}).start();return;
        }
        if(isInstalled(pkg)){
            try{view.setImageDrawable(getPackageManager().getApplicationIcon(pkg));return;}
            catch(Exception ignored){}
        }
        view.setImageResource(android.R.drawable.sym_def_app_icon);
    }
    private boolean sameSigningCertificates(PackageInfo installed,PackageInfo archive){
        if(Build.VERSION.SDK_INT>=28){
            if(installed.signingInfo==null||archive.signingInfo==null)return false;
            android.content.pm.SigningInfo oldInfo=installed.signingInfo;
            android.content.pm.SigningInfo newInfo=archive.signingInfo;
            android.content.pm.Signature[] oldCurrent=oldInfo.getApkContentsSigners();
            android.content.pm.Signature[] newCurrent=newInfo.getApkContentsSigners();
            if(oldCurrent==null||newCurrent==null||oldCurrent.length==0||newCurrent.length==0)return false;
            if(oldInfo.hasMultipleSigners()||newInfo.hasMultipleSigners())return sameSignerSet(oldCurrent,newCurrent);
            // For a single signer, allow Android signing-key rotation only when the
            // incoming APK's verified signing lineage includes the currently installed signer.
            android.content.pm.Signature[] lineage=newInfo.getSigningCertificateHistory();
            if(lineage!=null)for(android.content.pm.Signature oldSigner:oldCurrent)
                for(android.content.pm.Signature trusted:lineage)if(oldSigner.equals(trusted))return true;
            return false;
        }
        return sameSignerSet(installed.signatures,archive.signatures);
    }
    private boolean sameSignerSet(android.content.pm.Signature[] a,android.content.pm.Signature[] b){
        if(a==null||b==null||a.length==0||a.length!=b.length)return false;
        boolean[] matched=new boolean[b.length];
        for(android.content.pm.Signature signer:a){boolean found=false;for(int i=0;i<b.length;i++){if(!matched[i]&&signer.equals(b[i])){matched[i]=true;found=true;break;}}if(!found)return false;}
        return true;
    }
    private boolean isInstalled(String p){if(p==null||p.isEmpty())return false;try{getPackageManager().getPackageInfo(p,0);return true;}catch(Exception e){return false;}}
    private void focusFirst(){content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);View first=findButtonFocusable(content);if(first==null)first=findFocusable(content);if(first!=null){first.setFocusable(true);first.requestFocus();}}
    private View findButtonFocusable(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(v instanceof Button&&v.isFocusable()&&v.getVisibility()==View.VISIBLE)return v;if(v instanceof ViewGroup){View f=findButtonFocusable((ViewGroup)v);if(f!=null)return f;}}return null;}
    private View findFocusable(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(v.isFocusable()&&v.getVisibility()==View.VISIBLE&&!(v instanceof EditText))return v;if(v instanceof ViewGroup){View f=findFocusable((ViewGroup)v);if(f!=null)return f;}}return null;}
    private void download(String url,String name,String pkg,String version,LinearLayout card,LinearLayout actions){
        String key=pkg.isEmpty()?name:pkg;if(!url.startsWith("https://")){toast("No secure download URL is configured for this app.");return;}if(!hasNetwork()){toast("Connect to Wi-Fi or enable mobile data in Settings.");return;}if(!downloading.add(key)){toast("This download is already in progress.");return;}cancelled.remove(key);
        if(downloadLabel==null||downloadProgress==null||downloadActions==null){if(selectedApp!=null)showAppPage(selectedApp);else return;}
        downloadLabel.setText("Preparing download…");downloadProgress.setVisibility(View.VISIBLE);downloadProgress.setIndeterminate(true);downloadActions.removeAllViews();Button cancel=actionButton("Cancel",false);cancel.setOnClickListener(v->{cancelled.add(key);cancel.setEnabled(false);cancel.setText("Cancelling…");});downloadActions.addView(cancel,new LinearLayout.LayoutParams(-1,dp(42)));
        new Thread(()->{HttpURLConnection c=null;File file=new File(getCacheDir(),"yw-"+urlSafe(pkg.isEmpty()?name:pkg)+"-"+urlSafe(version)+".apk");File partial=new File(file.getAbsolutePath()+".part");try{
            c=(HttpURLConnection)new URL(url).openConnection();if(c instanceof HttpsURLConnection)try{LegacyTlsCompat.configure(this,(HttpsURLConnection)c);}catch(Exception ignored){}c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("X-Device-Id",deviceId);int status=c.getResponseCode();if(status<200||status>=300)throw new IOException("Download returned HTTP "+status);
            long total=c.getContentLength(),done=0;try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(partial)){byte[] buf=new byte[32768];int n;while((n=in.read(buf))!=-1){if(cancelled.contains(key))throw new InterruptedIOException("Download cancelled by user");out.write(buf,0,n);done+=n;final int pct=total>0?(int)Math.min(100,done*100/total):-1;final long current=done;runOnUiThread(()->{if(downloadLabel!=null){downloadProgress.setIndeterminate(false);if(pct>=0)downloadProgress.setProgress(pct);downloadLabel.setText(pct>=0?"Downloading  "+pct+"%  ·  "+formatSize(current)+" / "+formatSize(total):"Downloading  ·  "+formatSize(current));}});}}
            if(cancelled.contains(key))throw new InterruptedIOException("Download cancelled by user");if(partial.length()<1024)throw new IOException("Downloaded file is incomplete");if(total>0&&done!=total)throw new IOException("Download ended before all bytes arrived");
            int signatureFlags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageInfo archive=getPackageManager().getPackageArchiveInfo(partial.getAbsolutePath(),signatureFlags);if(archive==null||!pkg.equals(archive.packageName))throw new IOException("The downloaded APK does not match the catalog package.");if(version!=null&&!version.isEmpty()&&!version.equals(archive.versionName))throw new IOException("Downloaded version "+archive.versionName+" does not match catalog version "+version+".");if(isInstalled(pkg)){PackageInfo installed=getPackageManager().getPackageInfo(pkg,signatureFlags);if(!sameSigningCertificates(installed,archive))throw new IOException("The APK is signed with a different certificate from the installed app.");}
            if(file.exists()&&!file.delete())throw new IOException("Could not replace the previous APK cache");if(!partial.renameTo(file))throw new IOException("Could not finalize the downloaded APK");
            runOnUiThread(()->{downloading.remove(key);cancelled.remove(key);if(downloadProgress!=null){downloadProgress.setIndeterminate(false);downloadProgress.setProgress(100);}if(downloadLabel!=null)downloadLabel.setText("Download complete");if(downloadActions!=null){downloadActions.removeAllViews();TextView check=text("✓",30,GREEN,true);check.setGravity(Gravity.CENTER);check.setScaleX(0.1f);check.setScaleY(0.1f);downloadActions.addView(check,new LinearLayout.LayoutParams(-1,dp(42)));check.animate().scaleX(1f).scaleY(1f).setDuration(360).start();downloadActions.postDelayed(()->showInstallPrompt(file,pkg,version),650);}});
        }catch(Exception e){partial.delete();boolean wasCancelled=cancelled.contains(key);runOnUiThread(()->{downloading.remove(key);cancelled.remove(key);if(downloadLabel!=null)downloadLabel.setText(wasCancelled?"Download cancelled":"Download failed — try again");if(downloadProgress!=null)downloadProgress.setVisibility(View.GONE);if(downloadActions!=null){downloadActions.removeAllViews();Button retry=actionButton(wasCancelled?"Download again":"Retry",true);retry.setOnClickListener(v->download(url,name,pkg,version,null,null));downloadActions.addView(retry,new LinearLayout.LayoutParams(-1,dp(44)));}if(!wasCancelled)toast("Download failed: "+e.getMessage());});}finally{if(c!=null)c.disconnect();}}).start();
    }
    private String urlSafe(String s){return s.replaceAll("[^A-Za-z0-9._-]","_");}
    private void installApk(File file,String pkg,String version){PackageInstaller.Session session=null;try{int signatureFlags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageInfo archive=getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),signatureFlags);if(!file.isFile()||file.length()<1024||archive==null||!pkg.equals(archive.packageName))throw new IOException("Downloaded APK is invalid or belongs to another app.");if(version!=null&&!version.isEmpty()&&!version.equals(archive.versionName))throw new IOException("Downloaded APK version does not match the selected catalog version.");if(isInstalled(pkg)){PackageInfo installed=getPackageManager().getPackageInfo(pkg,signatureFlags);if(!sameSigningCertificates(installed,archive))throw new IOException("APK signing certificate does not match the installed app.");}PackageInstaller installer=getPackageManager().getPackageInstaller();PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(pkg);params.setSize(file.length());int id=installer.createSession(params);session=installer.openSession(id);try(InputStream in=new FileInputStream(file);OutputStream out=session.openWrite("base.apk",0,-1)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);session.fsync(out);}Intent callback=new Intent(this,InstallStatusReceiver.class);callback.setAction("com.lge.lginstallservies.INSTALL_STATUS."+id);callback.putExtra("yw_package_name",pkg);callback.putExtra("yw_version",version);int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;PendingIntent pi=PendingIntent.getBroadcast(this,id,callback,flags);session.commit(pi.getIntentSender());session.close();session=null;}catch(Exception e){if(session!=null)try{session.abandon();}catch(Exception ignored){}toast("Could not start installation: "+e.getMessage());}}
    private void openApp(String p){try{Intent i=getPackageManager().getLaunchIntentForPackage(p);if(i!=null)startActivity(i);else toast("No launch screen is available.");}catch(Exception e){toast("Could not open app.");}}
    private void uninstall(String p){try{startActivity(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+p)));}catch(Exception e){toast("Could not open uninstall screen.");}}
}
