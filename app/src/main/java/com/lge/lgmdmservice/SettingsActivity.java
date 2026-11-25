package com.lge.lgmdmservice;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Independent native Settings screen. Preferences are applied immediately. */
public final class SettingsActivity extends Activity {
    private static final String API = "https://yw-app-store-api.yw-app-installer-store-2026.workers.dev";
    private static final String UPDATE_MANIFEST_URL = "https://pub-8cbd861b18084f04b3f76f963c0bea0f.r2.dev/latest.json";
    private static final String UPDATE_APK_URL = "https://pub-8cbd861b18084f04b3f76f963c0bea0f.r2.dev/yw-app-store.apk";
    private static final int BG=0xfff5f7fb, PANEL=0xffffffff, EDGE=0xffe1e7f0, BLUE=0xff1769e0, INK=0xff152238, MUTED=0xff637187;
    private TextView banner;
    private String logExport;
    private final Handler messageHandler=new Handler(Looper.getMainLooper());
    private Runnable hideBanner;
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private SharedPreferences prefs(){return getSharedPreferences("yw_store",MODE_PRIVATE);}
    private GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(15));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private void message(String value){if(banner!=null){if(hideBanner!=null)messageHandler.removeCallbacks(hideBanner);banner.setText(value);banner.setTextSize(14);banner.setLetterSpacing(0.01f);banner.setIncludeFontPadding(false);banner.setShadowLayer(dp(0.35f),0f,dp(0.25f),0x55334455);banner.setVisibility(View.VISIBLE);banner.setAlpha(0f);banner.setTranslationY(dp(8));banner.animate().alpha(1f).translationY(0f).setDuration(180).start();hideBanner=()->banner.animate().alpha(0f).translationY(dp(6)).setDuration(150).withEndAction(()->{if(banner!=null){banner.setVisibility(View.GONE);banner.setAlpha(1f);banner.setTranslationY(0f);}}).start();messageHandler.postDelayed(hideBanner,2200);}}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(Build.VERSION.SDK_INT>=21){
            getWindow().requestFeature(android.view.Window.FEATURE_CONTENT_TRANSITIONS);
            getWindow().setSharedElementEnterTransition(new android.transition.TransitionSet().addTransition(new android.transition.ChangeBounds()).addTransition(new android.transition.ChangeImageTransform()).setDuration(260));
            getWindow().setSharedElementReturnTransition(new android.transition.TransitionSet().addTransition(new android.transition.ChangeBounds()).addTransition(new android.transition.ChangeImageTransform()).setDuration(220));
        }
        render();
    }
    private void render(){
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,0);return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(0,dp(4),dp(14),dp(4));
        BackGlyph back=new BackGlyph(this);back.setContentDescription("Back");back.setFocusable(true);back.setFocusableInTouchMode(false);back.setClickable(true);back.setBackground(backFocusBackground());back.setOnClickListener(v->finishPage());header.addView(back,new LinearLayout.LayoutParams(dp(50),dp(50)));
        ImageView gear=new ImageView(this);gear.setImageResource(R.drawable.ic_settings);gear.setScaleType(ImageView.ScaleType.CENTER_INSIDE);if(Build.VERSION.SDK_INT>=21)gear.setTransitionName("yw-settings-icon");header.addView(gear,new LinearLayout.LayoutParams(dp(34),dp(34)));
        TextView title=text("Settings",20,INK,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(10);header.addView(title,tp);root.addView(header,new LinearLayout.LayoutParams(-1,dp(54)));
        View divider=new View(this);divider.setBackgroundColor(EDGE);root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(12),dp(12),dp(12),dp(18));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(text("Downloads",12,BLUE,true));
        CheckBox mobile=new CheckBox(this);mobile.setChecked(prefs().getBoolean("mobile_data",false));setting(body,"Mobile data downloads","Allow downloads when Wi-Fi is unavailable",mobile,"mobile_data");
        TextView appearance=text("Appearance",12,BLUE,true);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(18);body.addView(appearance,ap);
        CheckBox installed=new CheckBox(this);installed.setChecked(prefs().getBoolean("show_installed",true));setting(body,"Show installed apps","Keep installed apps visible in the library",installed,"show_installed");
        CheckBox descriptions=new CheckBox(this);descriptions.setChecked(prefs().getBoolean("show_descriptions",true));setting(body,"Show app descriptions","Display the summary beneath each app name",descriptions,"show_descriptions");
        Button update=button("Update App Store");LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(-1,dp(50));up.gravity=Gravity.CENTER_HORIZONTAL;up.topMargin=dp(18);body.addView(update,up);update.setOnClickListener(v->{Intent page=new Intent(this,MainActivity.class);page.putExtra("yw_show_store_update",true);startActivity(page);});
        Button logs=button("Export diagnostic log");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.gravity=Gravity.CENTER_HORIZONTAL;lp.topMargin=dp(8);body.addView(logs,lp);logs.setOnClickListener(v->exportLogcat());
        TextView disclaimer=text("Use this app entirely at your own risk. I take no responsibility for any issues with the app. It is purely לתועלת הרבים.",12,MUTED,false);disclaimer.setGravity(Gravity.CENTER);disclaimer.setPadding(dp(8),dp(18),dp(8),dp(10));body.addView(disclaimer);
        banner=text("",13,Color.WHITE,true);banner.setMaxWidth(dp(300));banner.setMaxLines(1);banner.setEllipsize(android.text.TextUtils.TruncateAt.END);banner.setPadding(dp(11),dp(7),dp(11),dp(7));GradientDrawable bannerBg=shape(0xff263b59,0);bannerBg.setCornerRadius(dp(12));banner.setBackground(bannerBg);banner.setElevation(dp(5));banner.setVisibility(View.GONE);LinearLayout.LayoutParams bannerLp=new LinearLayout.LayoutParams(-1,-2);bannerLp.width=-2;bannerLp.gravity=Gravity.CENTER_HORIZONTAL;bannerLp.leftMargin=dp(12);bannerLp.rightMargin=dp(12);bannerLp.bottomMargin=dp(7);FrameLayout overlayRoot=new FrameLayout(this);overlayRoot.addView(root,new FrameLayout.LayoutParams(-1,-1));FrameLayout.LayoutParams overlayBannerLp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);overlayBannerLp.leftMargin=dp(12);overlayBannerLp.rightMargin=dp(12);overlayBannerLp.bottomMargin=dp(7);overlayRoot.addView(banner,overlayBannerLp);
        setContentView(overlayRoot);root.requestApplyInsets();back.requestFocus();
    }
    private final class BackGlyph extends View{private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);BackGlyph(android.content.Context context){super(context);setFocusable(true);setFocusableInTouchMode(false);}@Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);float x=getWidth()/2f,y=getHeight()/2f;paint.setColor(0xff263b59);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);android.graphics.Path path=new android.graphics.Path();path.moveTo(x+dp(3),y-dp(6));path.lineTo(x-dp(3),y);path.lineTo(x+dp(3),y+dp(6));canvas.drawPath(path,paint);canvas.drawLine(x-dp(3),y,x+dp(7),y,paint);}}
    private android.graphics.drawable.StateListDrawable focusBackground(){android.graphics.drawable.StateListDrawable s=new android.graphics.drawable.StateListDrawable();GradientDrawable focused=new GradientDrawable();focused.setColor(0xffedf4ff);focused.setCornerRadius(dp(12));focused.setStroke(dp(2),BLUE);s.addState(new int[]{android.R.attr.state_focused},focused);s.addState(new int[]{},shape(Color.TRANSPARENT,0));return s;}
    private android.graphics.drawable.Drawable backFocusBackground(){android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();GradientDrawable focused=new GradientDrawable();focused.setColor(0xffedf4ff);focused.setCornerRadius(dp(14));focused.setStroke(dp(2),BLUE);states.addState(new int[]{android.R.attr.state_focused},focused);states.addState(new int[]{},new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));return states;}
    private void setting(LinearLayout parent,String title,String subtitle,CheckBox check,String key){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(7),dp(8),dp(7));row.setBackground(shape(PANEL,EDGE));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(9);parent.addView(row,rp);
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text(title,14,INK,true));TextView sub=text(subtitle,11,MUTED,false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(3);copy.addView(sub,sp);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));check.setFocusable(false);check.setClickable(false);check.setScaleX(0.92f);check.setScaleY(0.92f);check.setContentDescription(title);row.addView(check);
        check.setOnCheckedChangeListener((button,checked)->{prefs().edit().putBoolean(key,checked).apply();DiagnosticLog.record(this,"Setting changed: "+key+"="+checked);});
        row.setFocusable(true);row.setFocusableInTouchMode(false);row.setClickable(true);row.setBackground(focusBackground());row.setContentDescription(title+": "+subtitle);row.setOnClickListener(v->check.setChecked(!check.isChecked()));
    }
    private Button button(String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(0xff123f86);b.setMinHeight(dp(50));b.setPadding(dp(8),dp(3),dp(8),dp(3));android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();GradientDrawable normal=new GradientDrawable();normal.setColor(PANEL);normal.setCornerRadius(dp(25));normal.setStroke(dp(1),EDGE);GradientDrawable focused=new GradientDrawable();focused.setColor(0xffedf4ff);focused.setCornerRadius(dp(25));focused.setStroke(dp(3),BLUE);states.addState(new int[]{android.R.attr.state_focused},focused);states.addState(new int[]{android.R.attr.state_pressed},focused);states.addState(new int[]{},normal);b.setBackground(states);b.setFocusable(true);b.setFocusableInTouchMode(false);return b;}
    private void finishPage(){if(Build.VERSION.SDK_INT>=21)finishAfterTransition();else finish();}
    @Override public void onBackPressed(){finishPage();}
    private void checkStoreUpdate(Button button){
        button.setEnabled(false);message("Checking for the latest App Store release…");
        new Thread(()->{HttpsURLConnection connection=null;try{
            connection=(HttpsURLConnection)new URL(UPDATE_MANIFEST_URL).openConnection();LegacyTlsCompat.configure(this,connection);connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setRequestProperty("Cache-Control","no-cache");
            int status=connection.getResponseCode();if(status<200||status>=300)throw new IOException("Update manifest unavailable (HTTP "+status+").");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);}
            JSONObject manifest=new JSONObject(bytes.toString("UTF-8"));String remote=manifest.optString("version","");if(remote.isEmpty())throw new IOException("The update manifest has no version.");
            PackageInfo installed=getPackageManager().getPackageInfo(getPackageName(),0);String current=installed.versionName==null?"":installed.versionName;
            JSONObject detail=new JSONObject();detail.put("id","yw-app-store-self-update");detail.put("name","YW App Store");detail.put("description","Update the YW App Store to the latest release.");detail.put("packageName",getPackageName());detail.put("version",remote);detail.put("sizeBytes",manifest.optLong("sizeBytes",0));detail.put("apkUrl",UPDATE_APK_URL);
            boolean newer=isNewer(remote,current);
            runOnUiThread(()->{button.setEnabled(true);if(!newer)message("YW App Store is already up to date.");Intent page=new Intent(this,MainActivity.class);page.putExtra("yw_detail_json",detail.toString());startActivity(page);});
        }catch(Exception error){try{
            PackageInfo installed=getPackageManager().getPackageInfo(getPackageName(),0);String current=installed.versionName==null?"":installed.versionName;
            JSONObject detail=new JSONObject();detail.put("id","yw-app-store-self-update");detail.put("name","YW App Store");detail.put("description","Update the YW App Store to the latest release.");detail.put("packageName",getPackageName());detail.put("version",current);detail.put("sizeBytes",0);detail.put("apkUrl",UPDATE_APK_URL);
            runOnUiThread(()->{button.setEnabled(true);message("No update release is currently published.");Intent page=new Intent(this,MainActivity.class);page.putExtra("yw_detail_json",detail.toString());startActivity(page);});
        }catch(Exception fallbackError){runOnUiThread(()->{button.setEnabled(true);message("Could not open App Store update details.");});}}finally{if(connection!=null)connection.disconnect();}}, "YW-store-self-update-check").start();
    }
    private boolean isNewer(String remote,String current){try{String[] a=remote.replaceFirst("^[vV]","").split("[^0-9]+"),b=current.replaceFirst("^[vV]","").split("[^0-9]+");for(int i=0;i<Math.max(a.length,b.length);i++){long x=i<a.length&&!a[i].isEmpty()?Long.parseLong(a[i]):0,y=i<b.length&&!b[i].isEmpty()?Long.parseLong(b[i]):0;if(x!=y)return x>y;}return false;}catch(Exception e){return remote.compareToIgnoreCase(current)>0;}}
    private void exportLogcat(){
        DiagnosticLog.record(this,"Settings: user requested diagnostic export");
        message("Preparing diagnostic log…");
        new Thread(()->{
            String output=DiagnosticLog.export(this);
            runOnUiThread(()->{
                logExport=output;
                Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);save.addCategory(Intent.CATEGORY_OPENABLE);
                save.setType("text/plain");save.putExtra(Intent.EXTRA_TITLE,"yw-app-store-diagnostics.txt");
                try{startActivityForResult(save,9201);}catch(Exception error){message("Could not open the file picker.");}
            });
        },"YW-settings-diagnostic-export").start();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==9201&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new IOException("Could not open file.");
                out.write((logExport==null?"":logExport).getBytes("UTF-8"));
                DiagnosticLog.record(this,"Diagnostic log exported from Settings");
                message("Diagnostic log exported. Upload the text file here.");
            }catch(Exception error){DiagnosticLog.record(this,"Diagnostic export failed: "+error);message("Could not export the diagnostic log.");}
            finally{logExport=null;}
        }
    }
}
