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
    private static final int BG=0xfff5f7fb, PANEL=0xffffffff, EDGE=0xffe1e7f0, BLUE=0xff1769e0, INK=0xff152238, MUTED=0xff637187;
    private TextView banner;
    private String logExport;
    private final Handler messageHandler=new Handler(Looper.getMainLooper());
    private Runnable hideBanner;
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private SharedPreferences prefs(){return getSharedPreferences("yw_store",MODE_PRIVATE);}
    private GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(15));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private void message(String value){if(banner!=null){if(hideBanner!=null)messageHandler.removeCallbacks(hideBanner);banner.setText(value);banner.setVisibility(View.VISIBLE);banner.setAlpha(0f);banner.setTranslationY(dp(8));banner.animate().alpha(1f).translationY(0f).setDuration(180).start();hideBanner=()->banner.animate().alpha(0f).translationY(dp(6)).setDuration(150).withEndAction(()->{if(banner!=null){banner.setVisibility(View.GONE);banner.setAlpha(1f);banner.setTranslationY(0f);}}).start();messageHandler.postDelayed(hideBanner,2200);}}
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
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,0);return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(10),dp(4),dp(14),dp(4));
        ImageView back=new ImageView(this);back.setImageResource(R.drawable.ic_back);back.setColorFilter(0xff263b59);back.setPadding(dp(8),dp(8),dp(8),dp(8));back.setContentDescription("Back");back.setFocusable(true);back.setBackground(focusBackground());back.setOnClickListener(v->finishPage());header.addView(back,new LinearLayout.LayoutParams(dp(42),dp(42)));
        ImageView gear=new ImageView(this);gear.setImageResource(R.drawable.ic_settings);gear.setScaleType(ImageView.ScaleType.CENTER_INSIDE);if(Build.VERSION.SDK_INT>=21)gear.setTransitionName("yw-settings-icon");header.addView(gear,new LinearLayout.LayoutParams(dp(34),dp(34)));
        TextView title=text("Settings",20,INK,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(10);header.addView(title,tp);root.addView(header,new LinearLayout.LayoutParams(-1,dp(54)));
        View divider=new View(this);divider.setBackgroundColor(EDGE);root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(12),dp(12),dp(12),dp(18));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(text("Downloads",12,BLUE,true));
        CheckBox mobile=new CheckBox(this);mobile.setChecked(prefs().getBoolean("mobile_data",false));setting(body,"Mobile data downloads","Allow downloads when Wi-Fi is unavailable",mobile,"mobile_data");
        TextView appearance=text("Appearance",12,BLUE,true);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(18);body.addView(appearance,ap);
        CheckBox installed=new CheckBox(this);installed.setChecked(prefs().getBoolean("show_installed",true));setting(body,"Show installed apps","Keep installed apps visible in the library",installed,"show_installed");
        CheckBox descriptions=new CheckBox(this);descriptions.setChecked(prefs().getBoolean("show_descriptions",true));setting(body,"Show app descriptions","Display the summary beneath each app name",descriptions,"show_descriptions");
        Button update=button("Update App Store");LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(-1,dp(42));up.topMargin=dp(18);body.addView(update,up);update.setOnClickListener(v->checkStoreUpdate(update));
        Button logs=button("Export diagnostic log");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(8);body.addView(logs,lp);logs.setOnClickListener(v->exportLogcat());
        banner=text("",13,Color.WHITE,true);banner.setMaxWidth(dp(300));banner.setMaxLines(1);banner.setEllipsize(android.text.TextUtils.TruncateAt.END);banner.setPadding(dp(11),dp(7),dp(11),dp(7));GradientDrawable bannerBg=shape(0xff263b59,0);bannerBg.setCornerRadius(dp(12));banner.setBackground(bannerBg);banner.setElevation(dp(5));banner.setVisibility(View.GONE);LinearLayout.LayoutParams bannerLp=new LinearLayout.LayoutParams(-1,-2);bannerLp.width=-2;bannerLp.gravity=Gravity.CENTER_HORIZONTAL;bannerLp.leftMargin=dp(12);bannerLp.rightMargin=dp(12);bannerLp.bottomMargin=dp(7);root.addView(banner,bannerLp);
        setContentView(root);root.requestApplyInsets();back.requestFocus();
    }
    private android.graphics.drawable.StateListDrawable focusBackground(){android.graphics.drawable.StateListDrawable s=new android.graphics.drawable.StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},shape(0xffedf4ff,BLUE));s.addState(new int[]{},shape(Color.TRANSPARENT,0));return s;}
    private void setting(LinearLayout parent,String title,String subtitle,CheckBox check,String key){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(7),dp(8),dp(7));row.setBackground(shape(PANEL,EDGE));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(9);parent.addView(row,rp);
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text(title,14,INK,true));TextView sub=text(subtitle,11,MUTED,false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(3);copy.addView(sub,sp);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));check.setFocusable(false);check.setClickable(false);check.setScaleX(0.92f);check.setScaleY(0.92f);check.setContentDescription(title);row.addView(check);
        check.setOnCheckedChangeListener((button,checked)->prefs().edit().putBoolean(key,checked).apply());
        row.setFocusable(true);row.setFocusableInTouchMode(false);row.setClickable(true);row.setBackground(focusBackground());row.setContentDescription(title+": "+subtitle);row.setOnClickListener(v->check.setChecked(!check.isChecked()));
    }
    private Button button(String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(0xff123f86);b.setMinHeight(dp(46));b.setBackground(focusBackground());b.setFocusable(true);return b;}
    private void finishPage(){if(Build.VERSION.SDK_INT>=21)finishAfterTransition();else finish();}
    @Override public void onBackPressed(){finishPage();}
    private void checkStoreUpdate(Button button){
        String token=prefs().getString("device_token",null);if(token==null||token.length()<16){message("Authorize this device in YW App Store first.");return;}
        button.setEnabled(false);message("Checking Cloudflare for an App Store update…");
        new Thread(()->{HttpsURLConnection c=null;try{
            c=(HttpsURLConnection)new URL(API+"/api/catalog").openConnection();LegacyTlsCompat.configure(this,c);c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("X-Device-Id",prefs().getString("device_id",""));c.setRequestProperty("Accept","application/json");
            int status=c.getResponseCode();InputStream in=status>=200&&status<300?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null)try(InputStream stream=in){byte[] b=new byte[8192];int n;while((n=stream.read(b))!=-1)out.write(b,0,n);}
            if(status<200||status>=300)throw new IOException("Update check failed (HTTP "+status+").");
            JSONArray apps=new JSONObject(out.toString("UTF-8")).optJSONArray("apps");JSONObject self=null;if(apps!=null)for(int i=0;i<apps.length();i++){JSONObject app=apps.optJSONObject(i);if(app!=null&&getPackageName().equals(app.optString("packageName",""))){self=app;break;}}
            final JSONObject found=self;if(found==null){runOnUiThread(()->{button.setEnabled(true);message("No App Store release is published in Cloudflare yet.");});return;}
            PackageInfo installed=getPackageManager().getPackageInfo(getPackageName(),0);String current=installed.versionName,remote=found.optString("version","");
            if(!isNewer(remote,current)){runOnUiThread(()->{button.setEnabled(true);message("YW App Store is already up to date.");});return;}
            runOnUiThread(()->{button.setEnabled(true);Intent detail=new Intent(this,MainActivity.class);detail.putExtra("yw_detail_json",found.toString());detail.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);startActivity(detail);});
        }catch(Exception e){runOnUiThread(()->{button.setEnabled(true);message(e.getMessage()==null?"Could not check for an update.":e.getMessage());});}finally{if(c!=null)c.disconnect();}},"YW-store-self-update").start();
    }
    private boolean isNewer(String remote,String current){try{String[] a=remote.replaceFirst("^[vV]","").split("[^0-9]+"),b=current.replaceFirst("^[vV]","").split("[^0-9]+");for(int i=0;i<Math.max(a.length,b.length);i++){long x=i<a.length&&!a[i].isEmpty()?Long.parseLong(a[i]):0,y=i<b.length&&!b[i].isEmpty()?Long.parseLong(b[i]):0;if(x!=y)return x>y;}return false;}catch(Exception e){return remote.compareToIgnoreCase(current)>0;}}
    private void exportLogcat(){
        try{Process process=Runtime.getRuntime().exec(new String[]{"logcat","-d","-v","time","-t","1000"});ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=process.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}process.waitFor();logExport=out.toString("UTF-8");if(logExport.trim().isEmpty())logExport="No logcat entries were returned. Android may restrict access to system logs.";}
        catch(Exception e){logExport="Could not read logcat: "+e.getMessage();}
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);save.addCategory(Intent.CATEGORY_OPENABLE);save.setType("text/plain");save.putExtra(Intent.EXTRA_TITLE,"yw-app-store-logcat.txt");try{startActivityForResult(save,9201);}catch(Exception e){message("Could not open the file picker.");}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==9201&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException("Could not open file.");out.write((logExport==null?"":logExport).getBytes("UTF-8"));message("Diagnostic log exported. Upload the text file here.");}catch(Exception e){message("Could not export the diagnostic log.");}finally{logExport=null;}}}
}
