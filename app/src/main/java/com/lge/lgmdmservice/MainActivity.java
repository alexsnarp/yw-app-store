package com.lge.lgmdmservice;

import android.app.*;
import android.app.ActivityOptions;
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
    private static final String STORE_UPDATE_MANIFEST_URL="https://pub-8cbd861b18084f04b3f76f963c0bea0f.r2.dev/latest.json";
    private static final String STORE_UPDATE_APK_URL="https://pub-8cbd861b18084f04b3f76f963c0bea0f.r2.dev/yw-app-store.apk";
    private static final int BG=0xfff5f7fb, PANEL=0xffffffff, EDGE=0xffe1e7f0, BLUE=0xff1769e0, BLUE_DARK=0xff123f86, BLUE_TINT=0xffedf4ff, INK=0xff152238, MUTED=0xff637187, GREEN=0xff087c58, GOLD=0xffffb300;
    private static final Set<String> downloading=Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> cancelled=Collections.synchronizedSet(new HashSet<String>());
    private static java.lang.ref.WeakReference<MainActivity> catalogHostRef;
    private final HashMap<String,Boolean> downloadedStateCache=new HashMap<String,Boolean>();
    private final HashMap<String,View> catalogRowViews=new HashMap<String,View>();
    private final HashMap<String,TextView> catalogStatusViews=new HashMap<String,TextView>();
    private final ArrayDeque<JSONObject> downloadAllQueue=new ArrayDeque<JSONObject>();
    private boolean downloadAllActive;
    private int downloadAllFailures;
    private Button downloadAllButton;
    private final Handler messageHandler=new Handler(Looper.getMainLooper());
    private String catalogFocusedPackage,pendingMessage,detailsReturnScreen="catalog",pendingLogcat,catalogFocusTarget="app";
    private View refreshFocusView,settingsFocusView;
    private boolean restoreFocusAfterSettings;
    private boolean detailsHost,settingsHost;
    private TextView messageBanner,downloadStatusView;
    private Runnable hideMessage;
    private LinearLayout root, content, mainHeader;
    private ScrollView pageScroll;
    private String currentScreen="catalog";
    private boolean catalogCached,restoreCatalogPosition;
    private int catalogScrollY,settingsReturnScrollY;
    private String settingsReturnScreen="catalog";
    private String token, deviceId;
    private boolean catalogBusy;
    private BroadcastReceiver packageStateReceiver;
    private boolean packageStateReceiverRegistered;
    private JSONArray catalogApps;
    private LinearLayout catalogList;
    private TextView downloadLabel;
    private ProgressBar downloadProgress;
    private LinearLayout downloadActions;
    private JSONObject selectedApp;
    private EditText searchBox;
    private boolean installedOnly;
    private String catalogQuery="";
    private Button filterAllButton,filterInstalledButton;

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    private android.content.SharedPreferences prefs(){return getSharedPreferences(PREF,0);}
    private void toast(String s){if(Looper.myLooper()!=Looper.getMainLooper()){runOnUiThread(()->toast(s));return;}if(messageBanner==null){pendingMessage=s;return;}messageBanner.setText(s);messageBanner.setTextSize(14);messageBanner.setLetterSpacing(0.01f);messageBanner.setIncludeFontPadding(false);messageBanner.setShadowLayer(dp(0.35f),0f,dp(0.25f),0x55334455);messageBanner.setVisibility(View.VISIBLE);messageBanner.setAlpha(0f);messageBanner.setTranslationY(dp(10));messageBanner.animate().alpha(1f).translationY(0f).setDuration(180).start();if(hideMessage!=null)messageHandler.removeCallbacks(hideMessage);hideMessage=()->{if(messageBanner!=null)messageBanner.animate().alpha(0f).translationY(dp(8)).setDuration(160).withEndAction(()->{if(messageBanner!=null){messageBanner.setVisibility(View.GONE);messageBanner.setAlpha(1f);messageBanner.setTranslationY(0f);}}).start();};messageHandler.postDelayed(hideMessage,2200);}
    @Override public void onCreate(Bundle b){super.onCreate(b);detailsHost=getIntent().hasExtra("yw_detail_json");settingsHost=getIntent().getBooleanExtra("yw_show_settings",false);if(Build.VERSION.SDK_INT>=21){getWindow().requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);android.transition.TransitionSet sharedEnter=new android.transition.TransitionSet().addTransition(new android.transition.ChangeBounds()).addTransition(new android.transition.ChangeImageTransform()).setDuration(260);android.transition.TransitionSet sharedReturn=new android.transition.TransitionSet().addTransition(new android.transition.ChangeBounds()).addTransition(new android.transition.ChangeImageTransform()).setDuration(220);getWindow().setSharedElementEnterTransition(sharedEnter);getWindow().setSharedElementReturnTransition(sharedReturn);}prefs().edit().putBoolean("main_activity_launched",true).apply();NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.cancel(2301);deviceId=prefs().getString("device_id",null);if(deviceId==null){deviceId=UUID.randomUUID().toString();prefs().edit().putString("device_id",deviceId).apply();}token=prefs().getString("device_token",null);render();if(!detailsHost&&!settingsHost){UpdateCheckJobService.schedule(this);}}
    @Override protected void onStart(){super.onStart();if(!detailsHost)catalogHostRef=new java.lang.ref.WeakReference<MainActivity>(this);prefs().edit().putBoolean("main_activity_launched",true).apply();registerPackageStateReceiver();}
    @Override protected void onStop(){unregisterPackageStateReceiver();prefs().edit().putBoolean("main_activity_launched",false).apply();super.onStop();}
    private void registerPackageStateReceiver(){
        if(packageStateReceiverRegistered)return;
        packageStateReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
            String changed=intent.getData()!=null?intent.getData().getSchemeSpecificPart():null;if(changed==null||changed.isEmpty())return;
            boolean removed=Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction());if(removed&&intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))return;
            runOnUiThread(()->messageHandler.postDelayed(()->{
                if("catalog".equals(currentScreen)){refreshVisibleCatalogStates();}
                if(selectedApp!=null&&changed.equals(selectedApp.optString("packageName",""))&&"details".equals(currentScreen)){
                    showAppPage(selectedApp);if(!removed&&isInstalled(changed))toast("App installed successfully.");
                }
            },250));
        }};
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REPLACED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);filter.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packageStateReceiver,filter,Context.RECEIVER_EXPORTED);else registerReceiver(packageStateReceiver,filter);packageStateReceiverRegistered=true;
    }
    private void unregisterPackageStateReceiver(){if(packageStateReceiverRegistered){try{unregisterReceiver(packageStateReceiver);}catch(Exception ignored){}packageStateReceiverRegistered=false;packageStateReceiver=null;}}
    @Override protected void onPause(){if("catalog".equals(currentScreen)&&pageScroll!=null)catalogScrollY=pageScroll.getScrollY();super.onPause();}
    @Override protected void onResume(){super.onResume();token=prefs().getString("device_token",null);if("details".equals(currentScreen)&&selectedApp!=null){showAppPage(selectedApp);}else if("complete".equals(currentScreen)&&selectedApp!=null&&isInstalled(selectedApp.optString("packageName","")))showAppPage(selectedApp);else if("catalog".equals(currentScreen)){if(catalogApps==null&&token!=null)loadCatalog();else if(catalogApps!=null){restoreCatalogPosition=true;showCatalog(catalogApps,catalogCached);if(restoreFocusAfterSettings){restoreFocusAfterSettings=false;restoreCatalogFocusNow();}}}}
    @Override public void onActivityReenter(int resultCode,Intent data){super.onActivityReenter(resultCode,data);if(!detailsHost&&"catalog".equals(currentScreen)){View row=catalogRowViews.get(catalogFocusedPackage);if(row!=null){row.requestFocus();row.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);catalogFocusTarget="app";}}}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView statusPill(String label,int backgroundColor,int textColor){TextView pill=text(label,10,textColor,true);pill.setGravity(Gravity.CENTER);pill.setIncludeFontPadding(true);pill.setPadding(dp(8),dp(3),dp(8),dp(3));GradientDrawable bg=new GradientDrawable();bg.setColor(backgroundColor);bg.setCornerRadius(dp(20));pill.setBackground(bg);return pill;}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(17));d.setStroke(dp(1),EDGE);return d;}
    private GradientDrawable fillShape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private Drawable focusRing(int fill){GradientDrawable focused=new GradientDrawable();focused.setColor(fill);focused.setCornerRadius(dp(12));focused.setStroke(dp(3),0xffffb52e);return focused;}
    private Button actionButton(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(dp(34));b.setMinWidth(0);b.setPadding(dp(5),dp(3),dp(5),dp(3));b.setFocusable(true);b.setFocusableInTouchMode(false);b.setTextColor(primary?Color.WHITE:BLUE_DARK);StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},focusRing(primary?BLUE_DARK:BLUE_TINT));states.addState(new int[]{android.R.attr.state_pressed},focusRing(primary?BLUE_DARK:0xffe8efff));states.addState(new int[]{},primary?fillShape(BLUE):focusShape(PANEL,EDGE,1));b.setBackground(states);return b;}
    private GradientDrawable focusShape(int color,int strokeColor,int strokeWidth){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));d.setStroke(dp(strokeWidth),strokeColor);return d;}
    private StateListDrawable focusBackground(){StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},focusRing(BLUE_TINT));states.addState(new int[]{android.R.attr.state_pressed},focusRing(0xffe8efff));states.addState(new int[]{},new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));return states;}
    private StateListDrawable catalogRowFocusBackground(){StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},focusRing(BLUE_TINT));states.addState(new int[]{android.R.attr.state_pressed},fillShape(0xffe8efff));states.addState(new int[]{},new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));return states;}
    private void style(View v){if(v.isFocusable()){if(!(v instanceof Button))v.setBackground(focusBackground());v.setFocusable(true);v.setFocusableInTouchMode(v instanceof EditText);if(v instanceof Button){v.setFocusable(true);v.setFocusableInTouchMode(false);v.setMinimumHeight(dp(40));}else{v.setPadding(dp(12),dp(9),dp(12),dp(9));}if(v instanceof Button){((Button)v).setAllCaps(false);((Button)v).setTextSize(12);}}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)style(g.getChildAt(i));}}
    private void render(){
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(0,0,0,0);root.setBackgroundColor(BG);
        mainHeader=new LinearLayout(this);mainHeader.setGravity(Gravity.CENTER_VERTICAL);mainHeader.setPadding(dp(10),dp(3),dp(8),dp(3));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_yw_app);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);mainHeader.addView(logo,new LinearLayout.LayoutParams(dp(32),dp(32)));
        LinearLayout brandCopy=new LinearLayout(this);brandCopy.setOrientation(LinearLayout.VERTICAL);brandCopy.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("YW App Store",17,INK,true);brandCopy.addView(title);LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);titleLp.leftMargin=dp(8);mainHeader.addView(brandCopy,titleLp);
        if(token!=null){RefreshGlyph reload=new RefreshGlyph(this);refreshFocusView=reload;reload.setContentDescription("Refresh apps");reload.setFocusable(true);reload.setBackground(focusBackground());reload.setOnFocusChangeListener((v,focused)->{if(focused)catalogFocusTarget="refresh";});reload.setOnClickListener(v->{catalogFocusTarget="refresh";reload.animate().rotationBy(360f).setDuration(520).start();refreshCatalog();});LinearLayout.LayoutParams reloadLp=new LinearLayout.LayoutParams(dp(40),dp(40));reloadLp.gravity=Gravity.CENTER_VERTICAL;mainHeader.addView(reload,reloadLp);
            downloadAllButton=actionButton("Download all",false);downloadAllButton.setTextSize(11);downloadAllButton.setOnClickListener(v->downloadAllApps());LinearLayout.LayoutParams allLp=new LinearLayout.LayoutParams(dp(94),dp(38));allLp.gravity=Gravity.CENTER_VERTICAL;allLp.rightMargin=dp(3);mainHeader.addView(downloadAllButton,mainHeader.getChildCount()-1,allLp);
            SettingsGlyph settings=new SettingsGlyph(this);settingsFocusView=settings;settings.setContentDescription("Settings");settings.setFocusable(true);settings.setBackground(focusBackground());settings.setOnFocusChangeListener((v,focused)->{if(focused)catalogFocusTarget="settings";});if(Build.VERSION.SDK_INT>=21)settings.setTransitionName("yw-settings-icon");settings.setOnClickListener(v->openSettings(settings));mainHeader.addView(settings,new LinearLayout.LayoutParams(dp(42),dp(40)));}
        android.graphics.drawable.GradientDrawable headerFade=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{BG,0xfff1f4f9});mainHeader.setBackground(headerFade);mainHeader.setElevation(0f);mainHeader.setTranslationZ(0f);root.addView(mainHeader,new LinearLayout.LayoutParams(-1,dp(56)));
        pageScroll=new ScrollView(this);pageScroll.setFillViewport(true);pageScroll.setVerticalScrollBarEnabled(false);pageScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);pageScroll.setVerticalFadingEdgeEnabled(false);pageScroll.setFadingEdgeLength(0);pageScroll.setClipToPadding(false);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(0,dp(4),0,0);content.setBackgroundColor(BG);pageScroll.setBackgroundColor(BG);content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        pageScroll.addView(content,new ScrollView.LayoutParams(-1,-2));pageScroll.setOnScrollChangeListener((v,x,y,ox,oy)->{if("catalog".equals(currentScreen))catalogScrollY=y;});root.addView(pageScroll,new LinearLayout.LayoutParams(-1,0,1));messageBanner=text("",13,Color.WHITE,true);messageBanner.setGravity(Gravity.CENTER_VERTICAL);messageBanner.setMaxWidth(dp(300));messageBanner.setMaxLines(2);messageBanner.setEllipsize(android.text.TextUtils.TruncateAt.END);messageBanner.setPadding(dp(11),dp(8),dp(11),dp(8));GradientDrawable messageBg=fillShape(0xff263b59);messageBg.setCornerRadius(dp(12));messageBanner.setBackground(messageBg);messageBanner.setElevation(dp(5));messageBanner.setVisibility(View.GONE);LinearLayout.LayoutParams messageLp=new LinearLayout.LayoutParams(-1,-2);messageLp.width=-2;messageLp.gravity=Gravity.CENTER_HORIZONTAL;messageLp.leftMargin=dp(12);messageLp.rightMargin=dp(12);messageLp.bottomMargin=dp(7);if(pendingMessage!=null){String queued=pendingMessage;pendingMessage=null;toast(queued);}
        FrameLayout overlayRoot=new FrameLayout(this);overlayRoot.addView(root,new FrameLayout.LayoutParams(-1,-1));FrameLayout.LayoutParams overlayMessageLp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);overlayMessageLp.leftMargin=dp(12);overlayMessageLp.rightMargin=dp(12);overlayMessageLp.bottomMargin=dp(7);overlayRoot.addView(messageBanner,overlayMessageLp);setContentView(overlayRoot);String detailJson=getIntent().getStringExtra("yw_detail_json");boolean showSettings=getIntent().getBooleanExtra("yw_show_settings",false);if(detailJson!=null){try{selectedApp=new JSONObject(detailJson);showAppPage(selectedApp);}catch(Exception e){finish();}}else if(showSettings){settingsHost=true;settings();}else if(token==null)authorizationView();else loadCatalog();style(root);if(detailJson==null&&!showSettings)focusFirst();root.requestApplyInsets();
    }
    private void authorizationView(){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(shape(PANEL));content.addView(card);
        card.addView(text("Authorize this device",20,INK,true));TextView note=text("Enter the one-time code supplied by your administrator to unlock the app catalog.",14,MUTED,false);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(8);card.addView(note,np);
        EditText code=new EditText(this);code.setSingleLine(true);code.setHint("Authorization code");code.setTextColor(INK);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(52));ep.topMargin=dp(14);card.addView(code,ep);
        Button submit=actionButton("Authorize and open catalog",true);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(50));bp.topMargin=dp(10);card.addView(submit,bp);
        submit.setOnClickListener(v->{String value=code.getText().toString().trim();if(value.length()<6){toast("Enter the authorization code.");return;}if(!hasNetwork()){toast("No internet connection.");return;}submit.setEnabled(false);new Thread(()->{try{JSONObject body=new JSONObject();body.put("code",value);body.put("deviceId",deviceId);JSONObject result=request("POST","/api/redeem",body,null);String t=result.optString("token","");if(t.length()<16)throw new IOException("Authorization failed");prefs().edit().putString("device_token",t).apply();runOnUiThread(()->{token=t;toast("Authorization successful.");render();});}catch(Exception e){runOnUiThread(()->{submit.setEnabled(true);toast(hasNetwork()?"Couldn't authorize this device. Check the code and try again.":"No internet connection.");});}}).start();});
        code.setOnEditorActionListener((v,id,event)->{submit.performClick();return true;});
    }
    private class RefreshGlyph extends android.widget.ImageView{RefreshGlyph(Context c){super(c);setImageResource(R.drawable.ic_refresh);setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);setPadding(dp(10),dp(10),dp(10),dp(10));setColorFilter(0xff263b59);}}
    private class BackGlyph extends View{private final android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);BackGlyph(Context c){super(c);} @Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f;p.setColor(0xff263b59);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(dp(2.0f));p.setStrokeCap(android.graphics.Paint.Cap.ROUND);p.setStrokeJoin(android.graphics.Paint.Join.ROUND);android.graphics.Path path=new android.graphics.Path();path.moveTo(x+dp(2.5f),y-dp(5));path.lineTo(x-dp(2.5f),y);path.lineTo(x+dp(2.5f),y+dp(5));c.drawPath(path,p);}}
    private class SettingsGlyph extends View{
        private final Drawable gear;
        SettingsGlyph(Context context){super(context);gear=context.getDrawable(R.drawable.ic_settings);}
        @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);if(gear!=null){int size=dp(24),left=(getWidth()-size)/2,top=(getHeight()-size)/2;gear.setBounds(left,top,left+size,top+size);gear.draw(canvas);}}
    }
    private void addSettingRow(LinearLayout panel,String title,String subtitle,CheckBox check){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(13),dp(10),dp(10),dp(10));row.setBackground(catalogRowFocusBackground());row.setFocusable(true);row.setFocusableInTouchMode(false);row.setClickable(true);row.setContentDescription(title+": "+subtitle);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(9);panel.addView(row,rp);
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);copy.addView(text(title,15,INK,true));TextView sub=text(subtitle,12,MUTED,false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(3);copy.addView(sub,sp);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));check.setFocusable(false);check.setClickable(false);check.setContentDescription(null);row.addView(check);
        row.setOnClickListener(v->check.setChecked(!check.isChecked()));
    }
    private void settings(){
        settingsReturnScreen=currentScreen;settingsReturnScrollY=catalogScrollY;currentScreen="settings";mainHeader.setVisibility(View.GONE);pageScroll.scrollTo(0,0);content.removeAllViews();
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(0,dp(2),dp(5),dp(7));
        BackGlyph back=new BackGlyph(this);back.setContentDescription("Back");back.setFocusable(true);back.setClickable(true);back.setBackground(focusBackground());back.setOnClickListener(v->returnFromSettings());top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        SettingsGlyph settingsIcon=new SettingsGlyph(this);if(settingsHost&&Build.VERSION.SDK_INT>=21)settingsIcon.setTransitionName("yw-settings-icon");top.addView(settingsIcon,new LinearLayout.LayoutParams(dp(34),dp(34)));TextView heading=text("Settings",19,INK,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(10);top.addView(heading,hp);content.addView(top);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(10),dp(8),dp(10),dp(8));panel.setBackgroundColor(BG);content.addView(panel,new LinearLayout.LayoutParams(-1,-2));
        panel.addView(text("Downloads",11,BLUE,true));CheckBox mobile=new CheckBox(this);mobile.setChecked(prefs().getBoolean("mobile_data",false));addSettingRow(panel,"Mobile data downloads","Allow downloads when Wi-Fi is unavailable",mobile);
        TextView appearance=text("Appearance",11,BLUE,true);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(16);panel.addView(appearance,ap);
        CheckBox installed=new CheckBox(this);installed.setChecked(prefs().getBoolean("show_installed",true));addSettingRow(panel,"Show installed apps","Keep installed apps visible in the library",installed);
        CheckBox descriptions=new CheckBox(this);descriptions.setChecked(prefs().getBoolean("show_descriptions",true));addSettingRow(panel,"Show app descriptions","Display the summary beneath each app name",descriptions);
        mobile.setOnCheckedChangeListener((button,checked)->prefs().edit().putBoolean("mobile_data",checked).apply());
        installed.setOnCheckedChangeListener((button,checked)->prefs().edit().putBoolean("show_installed",checked).apply());
        descriptions.setOnCheckedChangeListener((button,checked)->prefs().edit().putBoolean("show_descriptions",checked).apply());
        Button storeUpdate=actionButton("Update App Store",false);LinearLayout.LayoutParams storeUpdateLp=new LinearLayout.LayoutParams(dp(300),dp(44));storeUpdateLp.gravity=Gravity.CENTER_HORIZONTAL;storeUpdateLp.topMargin=dp(12);content.addView(storeUpdate,storeUpdateLp);storeUpdate.setOnClickListener(v->checkStoreUpdate());
        Button exportLogs=actionButton("Export diagnostic log",false);LinearLayout.LayoutParams logsLp=new LinearLayout.LayoutParams(-1,dp(44));logsLp.topMargin=dp(6);content.addView(exportLogs,logsLp);exportLogs.setOnClickListener(v->exportLogcat());
        focusFirst();
    }
    private void checkStoreUpdate(){
        if(!hasNetwork()){toast("No internet connection.");return;}
        toast("Checking for the latest App Store release…");
        new Thread(()->{HttpsURLConnection connection=null;try{
            connection=(HttpsURLConnection)new URL(STORE_UPDATE_MANIFEST_URL).openConnection();LegacyTlsCompat.configure(this,connection);connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setRequestProperty("Cache-Control","no-cache");
            int status=connection.getResponseCode();if(status<200||status>=300)throw new IOException("Update manifest unavailable.");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);}
            JSONObject manifest=new JSONObject(bytes.toString("UTF-8"));String remote=manifest.optString("version","");if(remote.isEmpty())throw new IOException("The update manifest has no version.");
            PackageInfo installed=getPackageManager().getPackageInfo(getPackageName(),0);String current=installed.versionName==null?"":installed.versionName;
            JSONObject detail=new JSONObject();detail.put("id","yw-app-store-self-update");detail.put("name","YW App Store");detail.put("description","Update the YW App Store to the latest release.");detail.put("packageName",getPackageName());detail.put("version",remote);detail.put("sizeBytes",manifest.optLong("sizeBytes",0));detail.put("apkUrl",STORE_UPDATE_APK_URL);
            boolean newer=isUpdateAvailable(getPackageName(),remote);
            runOnUiThread(()->{if(!newer)toast("YW App Store is already up to date.");showAppPage(detail);});
        }catch(Exception e){runOnUiThread(()->toast(e.getMessage()==null?"Could not check for an App Store update.":e.getMessage()));}finally{if(connection!=null)connection.disconnect();}},"YW-store-self-update-check").start();
    }
    private void returnFromSettings(){if(settingsHost){if(Build.VERSION.SDK_INT>=21)finishAfterTransition();else finish();return;}String previous=settingsReturnScreen;catalogScrollY=settingsReturnScrollY;restoreCatalogPosition=true;if("details".equals(previous)&&selectedApp!=null){showAppPage(selectedApp);return;}if(catalogApps!=null){showCatalog(catalogApps,catalogCached);return;}returnToCatalog();}
    private void refreshCatalog(){if(catalogBusy){toast("The catalog is already refreshing.");return;}View focused=getCurrentFocus();if(focused==refreshFocusView)catalogFocusTarget="refresh";else if(catalogFocusedPackage!=null)catalogFocusTarget="app";catalogScrollY=pageScroll.getScrollY();restoreCatalogPosition=true;if(!hasNetwork()){toast("No internet connection.");if(catalogApps!=null)showCatalog(catalogApps,catalogCached);return;}if(catalogApps!=null)showCatalog(catalogApps,catalogCached);else{content.removeAllViews();message("Loading apps…");}loadCatalog();}
    private void exportLogcat(){String output;try{java.lang.Process process=Runtime.getRuntime().exec(new String[]{"logcat","-d","-v","time","-t","1000"});ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=process.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);}process.waitFor();output=bytes.toString("UTF-8");if(output.trim().isEmpty())output="No logcat entries were returned. Android may restrict access to system logs.";}catch(Exception e){output="Could not read logcat: "+e.getMessage();}pendingLogcat=output;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("text/plain");intent.putExtra(Intent.EXTRA_TITLE,"yw-app-store-logcat.txt");try{startActivityForResult(intent,9201);}catch(Exception e){toast("Could not open the file picker to export the log.");}}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==9201&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException("Could not open selected file.");out.write((pendingLogcat==null?"":pendingLogcat).getBytes("UTF-8"));toast("Diagnostic log exported. You can upload the saved text file here.");}catch(Exception e){toast("Could not export the diagnostic log.");}finally{pendingLogcat=null;}}}
    
        private boolean hasNetwork(){return NetworkAccess.isAllowed(this);}
    private JSONObject request(String method,String path,JSONObject body,String bearer)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();if(c instanceof HttpsURLConnection)LegacyTlsCompat.configure(this,(HttpsURLConnection)c);c.setRequestMethod(method);c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-cache, no-store");c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("Accept","application/json");if(bearer!=null)c.setRequestProperty("Authorization","Bearer "+bearer);if(deviceId!=null&&!deviceId.isEmpty())c.setRequestProperty("X-Device-Id",deviceId);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes("UTF-8"));}}int status=c.getResponseCode();InputStream in=status>=200&&status<300?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){try(InputStream x=in){byte[] b=new byte[8192];int n;while((n=x.read(b))!=-1)out.write(b,0,n);}}c.disconnect();String raw=out.toString("UTF-8");JSONObject result=raw.isEmpty()?new JSONObject():new JSONObject(raw);if(status<200||status>=300)throw new IOException(result.optString("error","HTTP "+status));return result;}
    private void loadCatalog(){
        if(token==null||catalogBusy)return;
        // Restore the last successful catalog before attempting the network. This keeps
        // the library usable offline and lets a stale catalog remain visible during refresh.
        if(catalogApps==null){
            String saved=prefs().getString("cached_catalog_json",null);
            if(saved!=null&&!saved.isEmpty()){
                try{
                    JSONArray cached=new JSONArray(saved);
                    if(cached.length()>0){
                        catalogApps=cached;
                        catalogCached=true;
                        if("catalog".equals(currentScreen))showCatalog(cached,true);
                    }
                }catch(Exception ignored){
                    prefs().edit().remove("cached_catalog_json").apply();
                }
            }
        }
        if(!hasNetwork()){
            if(catalogApps!=null){
                if(!"catalog".equals(currentScreen))showCatalog(catalogApps,catalogCached);
            }else{
                content.removeAllViews();
                message("Connect to the internet once to load the app catalog.");
            }
            return;
        }
        catalogBusy=true;
        if(catalogApps==null){
            content.removeAllViews();
            message("Loading apps…");
        }else if("catalog".equals(currentScreen)){
            showCatalog(catalogApps,catalogCached);
        }
        new Thread(()->{
            try{
                JSONObject result=request("GET","/api/catalog",null,token);
                JSONArray apps=result.optJSONArray("apps");
                if(apps==null)throw new IOException("Invalid catalog response");
                // Persist the complete last-known-good catalog before publishing it to UI.
                prefs().edit().putString("cached_catalog_json",apps.toString())
                        .putLong("catalog_last_refreshed",System.currentTimeMillis()).apply();
                runOnUiThread(()->{
                    catalogBusy=false;
                    catalogApps=apps;
                    catalogCached=false;
                    UpdateChecker.notifyFromCatalog(this,apps);
                    if("catalog".equals(currentScreen))showCatalog(apps,false);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    catalogBusy=false;
                    if(catalogApps==null){
                        content.removeAllViews();
                        message("Could not load apps. Check your connection and try again.");
                    }else{
                        catalogCached=true;
                        if("catalog".equals(currentScreen))showCatalog(catalogApps,true);
                        toast("Refresh failed. The saved app list is still available.");
                    }
                });
            }
        },"YW-catalog-load").start();
    }
    private String relativeRefreshTime(long timestamp){if(timestamp<=0)return "Not refreshed yet";java.util.Calendar then=java.util.Calendar.getInstance(),now=java.util.Calendar.getInstance();then.setTimeInMillis(timestamp);if(android.text.format.DateUtils.isToday(timestamp))return "Today";now.add(java.util.Calendar.DAY_OF_YEAR,-1);if(then.get(java.util.Calendar.YEAR)==now.get(java.util.Calendar.YEAR)&&then.get(java.util.Calendar.DAY_OF_YEAR)==now.get(java.util.Calendar.DAY_OF_YEAR))return "Yesterday";long days=Math.max(0,(System.currentTimeMillis()-timestamp)/86400000L);if(days<7)return "Earlier this week";if(days<14)return "Last week";return "A month ago";}
    private void message(String s){if(content==null)return;TextView t=text(s,14,MUTED,false);content.addView(t);}
    private void returnToCatalog(){if(detailsHost){restoreCatalogHostFocusBeforeReturn();if(Build.VERSION.SDK_INT>=21)finishAfterTransition();else finish();return;}if("settings".equals(detailsReturnScreen)){detailsReturnScreen="catalog";currentScreen="catalog";settings();return;}restoreCatalogPosition=true;if(catalogApps!=null){showCatalog(catalogApps,catalogCached);return;}currentScreen="catalog";mainHeader.setVisibility(View.VISIBLE);content.removeAllViews();loadCatalog();}
    private void applyStatus(TextView view,String label,int bg,int fg){if(view==null)return;view.setText(label);GradientDrawable pill=new GradientDrawable();pill.setColor(bg);pill.setCornerRadius(dp(20));view.setBackground(pill);view.setTextColor(fg);}
    private void refreshVisibleCatalogStates(){if(catalogApps==null)return;synchronized(downloadedStateCache){downloadedStateCache.clear();}for(int i=0;i<catalogApps.length();i++){JSONObject app=catalogApps.optJSONObject(i);if(app==null)continue;String pkg=app.optString("packageName",""),version=app.optString("version","");TextView state=catalogStatusViews.get(pkg);View row=catalogRowViews.get(pkg);if(state==null||row==null)continue;boolean installed=isInstalled(pkg),update=installed&&isUpdateAvailable(pkg,version),downloaded=hasDownloadedApk(pkg,version)&&(!installed||update),active=downloading.contains(pkg)||prefs().getBoolean(downloadProgressKey(pkg,version)+"_active",false);String label=active?"Downloading":downloaded?"Downloaded":update?"Update available":installed?"Installed":"Available";applyStatus(state,label,active?0xffe8f0ff:downloaded?0xfffff2d6:update?0xfff0eaff:installed?0xffe8f5ef:0xffeaf1ff,active?0xff2459a6:downloaded?0xff9a5a00:update?0xff6941a5:installed?0xff087c58:0xff2459a6);row.setContentDescription(app.optString("name","App")+(active?", downloading":downloaded?", ready to install":update?", update available":installed?", installed":", available"));}}
    private void showCatalog(JSONArray apps,boolean cached){
        boolean preservePosition=restoreCatalogPosition||(!catalogRowViews.isEmpty()&&"catalog".equals(currentScreen));
        currentScreen="catalog";mainHeader.setVisibility(View.VISIBLE);catalogApps=apps;catalogCached=cached;selectedApp=null;downloadLabel=null;downloadProgress=null;downloadActions=null;content.removeAllViews();
        long refreshedAt=prefs().getLong("catalog_last_refreshed",0L);String refreshed=relativeRefreshTime(refreshedAt);TextView stamp=text("Last refreshed  ·  "+refreshed,11,MUTED,false);stamp.setPadding(dp(10),dp(7),dp(10),dp(6));LinearLayout.LayoutParams stampLp=new LinearLayout.LayoutParams(-1,-2);stampLp.topMargin=dp(3);content.addView(stamp,stampLp);
        catalogRowViews.clear();catalogStatusViews.clear();catalogList=new LinearLayout(this);catalogList.setOrientation(LinearLayout.VERTICAL);catalogList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);content.addView(catalogList,new LinearLayout.LayoutParams(-1,-2));
        int shown=0;boolean showInstalled=prefs().getBoolean("show_installed",true);
        for(int i=0;i<apps.length();i++){JSONObject a=apps.optJSONObject(i);if(a==null)continue;String pkg=a.optString("packageName",""),version=a.optString("version","");boolean installed=isInstalled(pkg);if(installed&&!showInstalled)continue;
            shown++;String name=a.optString("name","App"),description=a.optString("description","");boolean updateAvailable=installed&&isUpdateAvailable(pkg,version);boolean downloaded=hasDownloadedApk(pkg,version)&&(!installed||updateAvailable),active=downloading.contains(pkg)||prefs().getBoolean(downloadProgressKey(pkg,version)+"_active",false);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(dp(6),dp(0),dp(5),dp(0));row.setMinimumHeight(dp(44));row.setBackground(catalogRowFocusBackground());row.setFocusable(true);row.setClickable(true);row.setContentDescription(name+(active?", downloading":downloaded?", downloaded":updateAvailable?", update available":installed?", installed":", available"));row.setOnFocusChangeListener((v,hasFocus)->{if(hasFocus){catalogFocusedPackage=pkg;catalogFocusTarget="app";}});
            ImageView icon=new ImageView(this);if(Build.VERSION.SDK_INT>=21)icon.setTransitionName("yw-app-icon");icon.setImageResource(android.R.drawable.sym_def_app_icon);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(34),dp(34));iconLp.rightMargin=dp(9);row.addView(icon,iconLp);loadCatalogIcon(icon,a.optString("iconUrl",""),a.optString("apkUrl",""),pkg,version);
            LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(Gravity.CENTER_VERTICAL);TextView appName=text(name,14,INK,true);appName.setMaxLines(1);appName.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(appName,new LinearLayout.LayoutParams(-1,-2));
            if(prefs().getBoolean("show_descriptions",true)&&!description.isEmpty()){TextView summary=text(description,11,MUTED,false);summary.setMaxLines(1);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(summary,new LinearLayout.LayoutParams(-1,-2));}
            TextView state=statusPill(active?"Downloading":downloaded?"Downloaded":updateAvailable?"Update available":installed?"Installed":"Available",active?0xffe8f0ff:downloaded?0xfffff2d6:updateAvailable?0xfff0eaff:installed?0xffe8f5ef:0xffeaf1ff,active?0xff2459a6:downloaded?0xff9a5a00:updateAvailable?0xff6941a5:installed?0xff087c58:0xff2459a6);catalogStatusViews.put(pkg,state);LinearLayout.LayoutParams stateLp=new LinearLayout.LayoutParams(-2,-2);stateLp.topMargin=dp(3);copy.addView(state,stateLp);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            TextView arrow=text("›",22,MUTED,false);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(18),dp(36)));row.setOnClickListener(v->{catalogFocusedPackage=pkg;openAppDetails(a,icon);});catalogRowViews.put(pkg,row);catalogList.addView(row,new LinearLayout.LayoutParams(-1,-2));

        }
        if(shown==0){TextView empty=text("No apps are currently listed.",14,MUTED,false);empty.setGravity(Gravity.CENTER);catalogList.addView(empty,new LinearLayout.LayoutParams(-1,dp(100)));}
        pageScroll.post(()->{if(preservePosition){restoreCatalogPosition=false;pageScroll.scrollTo(0,catalogScrollY);}restoreCatalogFocusNow();});
    }
    // Same platform shared-element transition pattern for the Settings glyph.
    private void openSettings(View shared){catalogScrollY=pageScroll.getScrollY();if(catalogFocusedPackage!=null)catalogFocusTarget="app";restoreFocusAfterSettings=true;if(Build.VERSION.SDK_INT>=21){Intent intent=new Intent(this,SettingsActivity.class);intent.putExtra("yw_show_settings",true);startActivity(intent,ActivityOptions.makeSceneTransitionAnimation(this,shared,"yw-settings-icon").toBundle());}else settings();}
    // Uses the standard Android shared-element ActivityOptions pattern demonstrated in Google's ActivitySceneTransitionBasic sample: pass the source view and the same transition name used by the destination view.
    private void openAppDetails(JSONObject app,View shared){if(Build.VERSION.SDK_INT>=21){Intent intent=new Intent(this,AppDetailsActivity.class);intent.putExtra("yw_detail_json",app.toString());startActivity(intent,ActivityOptions.makeSceneTransitionAnimation(this,shared,"yw-app-icon").toBundle());}else showAppPage(app);}
    private void showAppPage(JSONObject app){
        if(!"complete".equals(currentScreen))detailsReturnScreen="settings".equals(currentScreen)?"settings":"catalog";catalogScrollY=pageScroll.getScrollY();String focusPackage=app.optString("packageName","");if(!focusPackage.isEmpty())catalogFocusedPackage=focusPackage;
        currentScreen="details";mainHeader.setVisibility(View.GONE);pageScroll.scrollTo(0,0);selectedApp=app;content.removeAllViews();downloadLabel=null;downloadProgress=null;downloadActions=null;downloadStatusView=null;
        String name=app.optString("name","App"),desc=app.optString("description",""),version=app.optString("version",""),pkg=app.optString("packageName",""),url=app.optString("apkUrl","");long size=app.optLong("sizeBytes",0);boolean installed=isInstalled(pkg);boolean updateAvailable=installed&&isUpdateAvailable(pkg,version);
        File savedApk=apkFile(pkg,version);boolean downloaded=isValidDownloadedApk(savedApk,pkg,version)&&(!installed||updateAvailable);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(0,dp(1),dp(5),dp(4));ImageView back=new ImageView(this);back.setImageResource(R.drawable.ic_back);back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);back.setColorFilter(0xff263b59);back.setPadding(0,dp(8),dp(8),dp(8));back.setContentDescription("Back");back.setFocusable(true);back.setClickable(true);back.setBackground(focusBackground());back.setOnClickListener(v->returnToCatalog());top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));content.addView(top);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setGravity(Gravity.CENTER_HORIZONTAL);panel.setPadding(dp(12),dp(8),dp(12),dp(10));panel.setBackgroundColor(BG);content.addView(panel,new LinearLayout.LayoutParams(-1,-2));
        ImageView icon=new ImageView(this);if(detailsHost&&Build.VERSION.SDK_INT>=21)icon.setTransitionName("yw-app-icon");icon.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(80),dp(80));ip.bottomMargin=dp(7);panel.addView(icon,ip);loadCatalogIcon(icon,app.optString("iconUrl",""),url,pkg,version);
        TextView heading=text(name,20,INK,true);heading.setGravity(Gravity.CENTER);heading.setMaxLines(1);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);panel.addView(heading);
        TextView state=statusPill(downloaded?"Downloaded":updateAvailable?"Update available":installed?"Installed":"Available",downloaded?0xfffff2d6:updateAvailable?0xfff0eaff:installed?0xffe8f5ef:0xffeaf1ff,downloaded?0xff9a5a00:updateAvailable?0xff6941a5:installed?0xff087c58:0xff2459a6);downloadStatusView=state;LinearLayout.LayoutParams statep=new LinearLayout.LayoutParams(-2,-2);statep.topMargin=dp(5);state.setGravity(Gravity.CENTER);panel.addView(state,statep);
        if(!desc.isEmpty()){TextView description=text(desc,15,MUTED,false);description.setGravity(Gravity.CENTER);description.setMaxLines(2);description.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,-2);dp1.topMargin=dp(8);panel.addView(description,dp1);}
        if("smartlist".equalsIgnoreCase(app.optString("id",""))||"com.android.cts.lionscribe.elist".equals(pkg)){
            LinearLayout warning=new LinearLayout(this);warning.setOrientation(LinearLayout.VERTICAL);warning.setPadding(dp(12),dp(10),dp(12),dp(10));warning.setBackground(shape(PANEL));LinearLayout.LayoutParams warningLp=new LinearLayout.LayoutParams(-1,-2);warningLp.topMargin=dp(12);panel.addView(warning,warningLp);
            warning.addView(text("Important warning before downloading locations in Smartlist",14,0xff9b3b20,true));
            TextView warningBody=text("Make sure to turn off Sync contacts in Smartlist settings before downloading locations. If you do not, you may have issues with your contacts.",13,INK,false);LinearLayout.LayoutParams warningBodyLp=new LinearLayout.LayoutParams(-1,-2);warningBodyLp.topMargin=dp(6);warning.addView(warningBody,warningBodyLp);
            Button contactsHelp=actionButton("If your contacts are crashing, click here",false);LinearLayout.LayoutParams helpLp=new LinearLayout.LayoutParams(dp(300),dp(42));helpLp.gravity=Gravity.CENTER_HORIZONTAL;helpLp.topMargin=dp(8);warning.addView(contactsHelp,helpLp);
            contactsHelp.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Contacts troubleshooting").setMessage("If contacts registered in Smartlist are crashing, first export your contacts and verify that the export is complete. Then delete the contacts from the device and import the exported contacts again. Contacts that were registered in Smartlist and were crashing may appear as duplicate numbers afterward, but should open normally.").setPositiveButton("OK",null).show());
        }
        TextView meta=text("Version  "+(version.isEmpty()?"—":version)+"  ·  Size  "+(size>0?formatSize(size):"—"),12,MUTED,false);meta.setGravity(Gravity.CENTER);LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(-1,-2);ml.topMargin=dp(9);panel.addView(meta,ml);
        downloadLabel=text("",12,BLUE,true);downloadLabel.setGravity(Gravity.CENTER);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,-2);dl.topMargin=dp(4);panel.addView(downloadLabel,dl);
        downloadProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);downloadProgress.setMax(100);downloadProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));downloadProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffe8edf5));downloadProgress.setMinimumHeight(dp(6));downloadProgress.setVisibility(View.GONE);LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,dp(6));pl.topMargin=dp(6);panel.addView(downloadProgress,pl);
        downloadActions=new LinearLayout(this);downloadActions.setGravity(Gravity.CENTER);downloadActions.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(-1,-2);al.topMargin=dp(7);panel.addView(downloadActions,al);
        boolean isStoreUpdate="yw-app-store-self-update".equals(app.optString("id",""));boolean hasLaunchScreen=installed&&getPackageManager().getLaunchIntentForPackage(pkg)!=null;
        if(isStoreUpdate){if(updateAvailable){Button update=actionButton("Download update",true);update.setOnClickListener(v->download(url,name,pkg,version,null,null));addActionPair(downloadActions,update,null);}else{TextView current=text("This version is already up to date.",13,GREEN,true);current.setGravity(Gravity.CENTER);downloadActions.addView(current,new LinearLayout.LayoutParams(-1,dp(40)));}}
        else if(installed){
            Button remove=actionButton("Uninstall",false);remove.setOnClickListener(v->uninstall(pkg));
            if(hasLaunchScreen){Button open=actionButton("Open app",true);open.setOnClickListener(v->openApp(pkg));addActionPair(downloadActions,open,remove);}
            else if(!downloaded&&!updateAvailable){addActionPair(downloadActions,remove,null);}
            if(downloaded){Button installUpdate=actionButton("Install update",true);installUpdate.setOnClickListener(v->installApk(savedApk,pkg,version));addActionPair(downloadActions,installUpdate,null);}
            else if(updateAvailable){Button update=actionButton("Download update",false);update.setOnClickListener(v->download(url,name,pkg,version,null,null));addActionPair(downloadActions,update,null);}
            if(!hasLaunchScreen&&(downloaded||updateAvailable)){addActionPair(downloadActions,remove,null);}
        }else if(downloaded){Button install=actionButton("Install",true);install.setOnClickListener(v->installApk(savedApk,pkg,version));Button remove=actionButton("Delete download",false);remove.setOnClickListener(v->deleteDownloadedApk(savedApk,pkg,version));addActionPair(downloadActions,install,remove);}
        else{Button install=actionButton("Download",true);install.setOnClickListener(v->download(url,name,pkg,version,null,null));addActionPair(downloadActions,install,null);}
        if(downloading.contains(pkg))restoreActiveDownload(pkg,version);
        focusFirst();
    }
    private void addActionPair(LinearLayout parent,Button first,Button second){LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);line.setGravity(Gravity.CENTER);LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(second==null?dp(230):dp(340),dp(42));rowLp.gravity=Gravity.CENTER_HORIZONTAL;rowLp.topMargin=dp(6);parent.addView(line,rowLp);if(first!=null){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(second==null?-1:0,dp(42),second==null?0f:1f);lp.rightMargin=second!=null?dp(5):0;line.addView(first,lp);}if(second!=null){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1f);lp.leftMargin=dp(5);line.addView(second,lp);}}
    private String downloadProgressKey(String pkg,String version){return "download_"+urlSafe(pkg)+"_"+urlSafe(version);}
    private void restoreActiveDownload(String pkg,String version){
        String key=pkg;String prefKey=downloadProgressKey(pkg,version);
        long bytes=prefs().getLong(prefKey+"_bytes",0),total=prefs().getLong(prefKey+"_total",-1);
        downloadLabel.setText(total>0?(int)Math.min(100,bytes*100/total)+"%  ·  "+formatSize(bytes)+" / "+formatSize(total):formatSize(bytes));
        downloadProgress.setIndeterminate(total<=0);if(total>0)downloadProgress.setProgress((int)Math.min(100,bytes*100/total));downloadProgress.setVisibility(View.VISIBLE);
        downloadActions.removeAllViews();Button cancel=actionButton("Cancel",false);cancel.setOnClickListener(v->{cancelled.add(key);cancel.setEnabled(false);cancel.setText("Cancelling…");});addActionPair(downloadActions,cancel,null);
    }
    private void showInstallPrompt(File file,String pkg,String version){
        // Keep the user on the same app detail view; only update its status and actions.
        currentScreen="details";
        if(downloadStatusView!=null)applyStatus(downloadStatusView,"Downloaded",0xfffff2d6,0xff9a5a00);
        if(downloadLabel!=null)downloadLabel.setText("Downloaded");
        if(downloadProgress!=null){downloadProgress.setProgress(100);downloadProgress.setIndeterminate(false);downloadProgress.setVisibility(View.GONE);}
        if(downloadActions!=null){
            downloadActions.removeAllViews();
            Button install=actionButton("Install",true);
            install.setOnClickListener(v->installApk(file,pkg,version));
            Button remove=actionButton("Delete download",false);
            remove.setOnClickListener(v->deleteDownloadedApk(file,pkg,version));
            addActionPair(downloadActions,install,remove);
        }
        if(downloadActions!=null)downloadActions.post(()->{if(downloadActions!=null&&downloadActions.getChildCount()>0&&downloadActions.getChildAt(0).isFocusable())downloadActions.getChildAt(0).requestFocus();});
        synchronized(downloadedStateCache){downloadedStateCache.put(pkg+"|"+version,Boolean.TRUE);}
    }
    private String formatSize(long bytes){if(bytes<=0)return "";if(bytes>=1048576)return String.format(Locale.US,"%.1f MB",bytes/1048576d);return String.format(Locale.US,"%d KB",Math.max(1,bytes/1024));}
    private File catalogIconFile(String pkg,String version){File dir=new File(getFilesDir(),"catalog-icons");if(!dir.exists())dir.mkdirs();String safe=(pkg+"_"+version).replaceAll("[^A-Za-z0-9._-]","_");return new File(dir,safe+".png");}
    private void saveCatalogIcon(Bitmap bitmap,String pkg,String version){if(bitmap==null)return;File target=catalogIconFile(pkg,version),temp=new File(target.getParentFile(),target.getName()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))return;out.flush();if(target.exists())target.delete();if(!temp.renameTo(target))temp.delete();}catch(Exception ignored){if(temp.exists())temp.delete();}}
    private void loadCatalogIcon(ImageView view,String iconUrl,String apkUrl,String pkg,String version){
        view.setTag(pkg);
        File cached=catalogIconFile(pkg,version);
        if(cached.isFile()&&cached.length()>0){Bitmap bitmap=BitmapFactory.decodeFile(cached.getAbsolutePath());if(bitmap!=null)view.setImageBitmap(bitmap);}
        else view.setImageResource(android.R.drawable.sym_def_app_icon);
        // Cache icon bytes separately from catalog JSON, so app artwork remains visible offline.
        if(iconUrl!=null&&iconUrl.startsWith("data:image/")){
            try{int comma=iconUrl.indexOf(',');if(comma>0){byte[] bytes=Base64.decode(iconUrl.substring(comma+1),Base64.DEFAULT);Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap!=null){saveCatalogIcon(bitmap,pkg,version);if(pkg.equals(view.getTag()))view.setImageBitmap(bitmap);return;}}}catch(Exception ignored){}
        }
        if(iconUrl!=null&&iconUrl.startsWith("https://")){
            new Thread(()->{HttpsURLConnection connection=null;try{connection=(HttpsURLConnection)new URL(iconUrl).openConnection();LegacyTlsCompat.configure(this,connection);connection.setConnectTimeout(10000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(true);try(InputStream input=connection.getInputStream()){Bitmap bitmap=BitmapFactory.decodeStream(input);if(bitmap!=null){saveCatalogIcon(bitmap,pkg,version);runOnUiThread(()->{if(pkg.equals(view.getTag()))view.setImageBitmap(bitmap);});}}}catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}}).start();return;
        }
        if(!cached.isFile()&&isInstalled(pkg)){try{view.setImageDrawable(getPackageManager().getApplicationIcon(pkg));return;}catch(Exception ignored){}}
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
    private boolean isInstalled(String p){if(p==null||p.isEmpty())return false;try{getPackageManager().getPackageInfo(p,0);return true;}catch(Exception e){if("com.lge.ExaltWaze".equals(p))android.util.Log.w("YWAppStore","PackageManager did not report Waze package com.lge.ExaltWaze as installed.",e);return false;}}
    private boolean isUpdateAvailable(String pkg,String remoteVersion){if(pkg==null||pkg.isEmpty()||remoteVersion==null||remoteVersion.isEmpty())return false;try{String installed=getPackageManager().getPackageInfo(pkg,0).versionName;if(installed==null||installed.equals(remoteVersion))return false;String[] a=remoteVersion.replaceFirst("^[vV]","").split("[^0-9]+"),b=installed.replaceFirst("^[vV]","").split("[^0-9]+");for(int i=0;i<Math.max(a.length,b.length);i++){long av=i<a.length&&!a[i].isEmpty()?Long.parseLong(a[i]):0L,bv=i<b.length&&!b[i].isEmpty()?Long.parseLong(b[i]):0L;if(av!=bv)return av>bv;}return !remoteVersion.equals(installed)&&remoteVersion.compareTo(installed)>0;}catch(Exception e){return false;}}
    private void refreshCatalogHost(){try{MainActivity host=catalogHostRef==null?null:catalogHostRef.get();if(host!=null&&host.catalogApps!=null)host.runOnUiThread(()->{if(host.catalogApps!=null&&"catalog".equals(host.currentScreen))host.refreshVisibleCatalogStates();});}catch(Exception ignored){}}
    private void restoreCatalogFocusNow(){if("refresh".equals(catalogFocusTarget)&&refreshFocusView!=null){refreshFocusView.requestFocus();return;}if("settings".equals(catalogFocusTarget)&&settingsFocusView!=null){settingsFocusView.requestFocus();return;}View row=catalogRowViews.get(catalogFocusedPackage);if(row!=null){row.requestFocus();return;}focusFirst();}
    private void restoreCatalogFocusDelayed(long delay){if(pageScroll==null)return;pageScroll.scrollTo(0,catalogScrollY);restoreCatalogFocusNow();}
    private void focusFirst(){if(content==null)return;content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);content.post(()->{if(content==null)return;View first=findButtonFocusable(content);if(first==null)first=findFocusable(content);if(first!=null){first.setFocusable(true);first.requestFocus();}});}
    private View findButtonFocusable(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(v instanceof Button&&v.isFocusable()&&v.getVisibility()==View.VISIBLE)return v;if(v instanceof ViewGroup){View f=findButtonFocusable((ViewGroup)v);if(f!=null)return f;}}return null;}
    private View findFocusable(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(v.isFocusable()&&v.getVisibility()==View.VISIBLE&&!(v instanceof EditText))return v;if(v instanceof ViewGroup){View f=findFocusable((ViewGroup)v);if(f!=null)return f;}}return null;}
    private void downloadAllApps(){
        if(downloadAllActive){toast("Download all is already running.");return;}
        if(catalogApps==null||catalogApps.length()==0){toast("The app list is not loaded yet.");return;}
        if(!hasNetwork()){toast("No internet connection.");return;}
        downloadAllQueue.clear();downloadAllFailures=0;
        for(int i=0;i<catalogApps.length();i++){JSONObject app=catalogApps.optJSONObject(i);if(app==null)continue;String pkg=app.optString("packageName",""),version=app.optString("version",""),url=app.optString("apkUrl","");if(pkg.isEmpty()||!url.startsWith("https://"))continue;boolean installed=isInstalled(pkg),update=installed&&isUpdateAvailable(pkg,version);if(installed&&!update)continue;if(hasDownloadedApk(pkg,version)||downloading.contains(pkg))continue;downloadAllQueue.add(app);}
        if(downloadAllQueue.isEmpty()){toast("All available apps are already downloaded or installed.");return;}
        downloadAllActive=true;if(downloadAllButton!=null){downloadAllButton.setEnabled(false);downloadAllButton.setText("Downloading…");}toast("Downloading all available apps.");startNextCatalogDownload();
    }
    private void startNextCatalogDownload(){
        JSONObject app=downloadAllQueue.poll();
        if(app==null){downloadAllActive=false;if(downloadAllButton!=null){downloadAllButton.setEnabled(true);downloadAllButton.setText("Download all");}refreshVisibleCatalogStates();toast(downloadAllFailures==0?"All apps downloaded successfully.": "Download all finished with "+downloadAllFailures+" failed item(s).");return;}
        String pkg=app.optString("packageName",""),version=app.optString("version",""),url=app.optString("apkUrl","");String key=pkg;
        if(!downloading.add(key)){startNextCatalogDownload();return;}
        cancelled.remove(key);String prefKey=downloadProgressKey(pkg,version);prefs().edit().putBoolean(prefKey+"_active",true).putLong(prefKey+"_bytes",0).putLong(prefKey+"_total",-1).apply();refreshVisibleCatalogStates();
        new Thread(()->{HttpURLConnection connection=null;File file=apkFile(pkg,version),partial=new File(file.getAbsolutePath()+".part");boolean ok=false;try{
            connection=(HttpURLConnection)new URL(url).openConnection();if(connection instanceof HttpsURLConnection)LegacyTlsCompat.configure(this,(HttpsURLConnection)connection);connection.setConnectTimeout(30000);connection.setReadTimeout(600000);connection.setRequestProperty("Authorization","Bearer "+token);connection.setRequestProperty("X-Device-Id",deviceId);int status=connection.getResponseCode();if(status<200||status>=300)throw new IOException("HTTP "+status);
            long total=connection.getContentLength(),done=0;try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(partial)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);done+=n;prefs().edit().putLong(prefKey+"_bytes",done).putLong(prefKey+"_total",total).apply();}}
            if(partial.length()<1024||(total>0&&done!=total))throw new IOException("Incomplete download");
            int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageInfo archive=getPackageManager().getPackageArchiveInfo(partial.getAbsolutePath(),flags);if(archive==null||!pkg.equals(archive.packageName)||(!version.isEmpty()&&!version.equals(archive.versionName)))throw new IOException("APK metadata mismatch");
            if(isInstalled(pkg)){PackageInfo installed=getPackageManager().getPackageInfo(pkg,flags);if(!sameSigningCertificates(installed,archive))throw new IOException("Signing certificate mismatch");}
            if(file.exists()&&!file.delete())throw new IOException("Cannot replace cached APK");if(!partial.renameTo(file))throw new IOException("Cannot save downloaded APK");synchronized(downloadedStateCache){downloadedStateCache.put(pkg+"|"+version,Boolean.TRUE);}ok=true;
        }catch(Exception error){partial.delete();android.util.Log.e("YWAppStore","Download all failed for "+pkg,error);}finally{if(connection!=null)connection.disconnect();prefs().edit().putBoolean(prefKey+"_active",false).apply();final boolean succeeded=ok;runOnUiThread(()->{downloading.remove(key);if(!succeeded)downloadAllFailures++;refreshVisibleCatalogStates();startNextCatalogDownload();});}}, "YW-download-all-"+urlSafe(pkg)).start();
    }
    private void download(String url,String name,String pkg,String version,LinearLayout card,LinearLayout actions){
        String key=pkg.isEmpty()?name:pkg;if(!url.startsWith("https://")){toast("This app isn't available to download right now.");return;}if(!hasNetwork()){toast("No internet connection.");return;}if(!downloading.add(key)){toast("This download is already in progress.");return;}cancelled.remove(key);
        if(downloadLabel==null||downloadProgress==null||downloadActions==null){if(selectedApp!=null)showAppPage(selectedApp);else return;}
        prefs().edit().putBoolean(downloadProgressKey(pkg,version)+"_active",true).putLong(downloadProgressKey(pkg,version)+"_bytes",0).putLong(downloadProgressKey(pkg,version)+"_total",-1).apply();
        downloadLabel.setText("0%  ·  0 KB");downloadProgress.setVisibility(View.VISIBLE);downloadProgress.setIndeterminate(true);downloadActions.removeAllViews();Button cancel=actionButton("Cancel",false);cancel.setOnClickListener(v->{cancelled.add(key);cancel.setEnabled(false);cancel.setText("Cancelling…");});addActionPair(downloadActions,cancel,null);refreshCatalogHost();
        new Thread(()->{HttpURLConnection c=null;File file=apkFile(pkg.isEmpty()?name:pkg,version);File partial=new File(file.getAbsolutePath()+".part");try{
            c=(HttpURLConnection)new URL(url).openConnection();if(c instanceof HttpsURLConnection)LegacyTlsCompat.configure(this,(HttpsURLConnection)c);c.setConnectTimeout(30000);c.setReadTimeout(600000);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("X-Device-Id",deviceId);int status=c.getResponseCode();if(status<200||status>=300)throw new IOException("Download returned HTTP "+status);
            long total=c.getContentLength(),done=0;try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(partial)){byte[] buf=new byte[32768];int n;while((n=in.read(buf))!=-1){if(cancelled.contains(key))throw new InterruptedIOException("Download cancelled by user");out.write(buf,0,n);done+=n;final int pct=total>0?(int)Math.min(100,done*100/total):-1;final long current=done;prefs().edit().putLong(downloadProgressKey(pkg,version)+"_bytes",current).putLong(downloadProgressKey(pkg,version)+"_total",total).apply();runOnUiThread(()->{if(isCurrentDownload(key)){downloadProgress.setIndeterminate(false);if(pct>=0)downloadProgress.setProgress(pct);downloadLabel.setText(pct>=0?pct+"%  ·  "+formatSize(current)+" / "+formatSize(total):formatSize(current));}});}}
            if(cancelled.contains(key))throw new InterruptedIOException("Download cancelled by user");if(partial.length()<1024)throw new IOException("Downloaded file is incomplete");if(total>0&&done!=total)throw new IOException("Download ended before all bytes arrived");
            int signatureFlags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageInfo archive=getPackageManager().getPackageArchiveInfo(partial.getAbsolutePath(),signatureFlags);if(archive==null||!pkg.equals(archive.packageName))throw new IOException("The downloaded APK does not match the catalog package.");if(version!=null&&!version.isEmpty()&&!version.equals(archive.versionName))throw new IOException("Downloaded version "+archive.versionName+" does not match catalog version "+version+".");if(isInstalled(pkg)){PackageInfo installed=getPackageManager().getPackageInfo(pkg,signatureFlags);if(!sameSigningCertificates(installed,archive))throw new IOException("The APK is signed with a different certificate from the installed app.");}
            if(file.exists()&&!file.delete())throw new IOException("Could not replace the previous APK cache");if(!partial.renameTo(file))throw new IOException("Could not finalize the downloaded APK");synchronized(downloadedStateCache){downloadedStateCache.put(pkg+"|"+version,Boolean.TRUE);}
            prefs().edit().putBoolean(downloadProgressKey(pkg,version)+"_active",false).apply();runOnUiThread(()->{downloading.remove(key);cancelled.remove(key);if(isCurrentDownload(key))showInstallPrompt(file,pkg,version);refreshCatalogHost();});
        }catch(Exception e){partial.delete();boolean wasCancelled=cancelled.contains(key);prefs().edit().putBoolean(downloadProgressKey(pkg,version)+"_active",false).apply();runOnUiThread(()->{downloading.remove(key);cancelled.remove(key);refreshCatalogHost();if(isCurrentDownload(key)){downloadLabel.setText(wasCancelled?"Download cancelled":"Download failed — "+(e.getMessage()==null?"try again":e.getMessage()));downloadProgress.setVisibility(View.GONE);downloadActions.removeAllViews();Button retry=actionButton(wasCancelled?"Download again":"Retry",true);retry.setOnClickListener(v->download(url,name,pkg,version,null,null));addActionPair(downloadActions,retry,null);}if(!wasCancelled)toast(hasNetwork()?"Download failed: "+(e.getMessage()==null?"please try again.":e.getMessage()):"No internet connection.");});}finally{if(c!=null)c.disconnect();}}).start();
    }
    private void deleteDownloadedApk(File file,String pkg,String version){if(file==null||!file.isFile()){toast("Downloaded file was not found.");return;}if(!file.delete()){toast("Could not delete the downloaded APK.");return;}File partial=new File(file.getAbsolutePath()+".part");if(partial.exists())partial.delete();synchronized(downloadedStateCache){downloadedStateCache.remove(pkg+"|"+version);}prefs().edit().remove(downloadProgressKey(pkg,version)+"_active").remove(downloadProgressKey(pkg,version)+"_bytes").remove(downloadProgressKey(pkg,version)+"_total").apply();if(selectedApp!=null&&pkg.equals(selectedApp.optString("packageName",""))){showAppPage(selectedApp);toast("Download deleted.");}else{refreshVisibleCatalogStates();toast("Download deleted.");}}
    private File apkFile(String pkg,String version){return new File(getFilesDir(),"yw-"+urlSafe(pkg)+"-"+urlSafe(version)+".apk");}
    private boolean isValidDownloadedApk(File file,String pkg,String version){try{if(file==null||!file.isFile()||file.length()<1024)return false;int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageInfo archive=getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),flags);return archive!=null&&pkg.equals(archive.packageName)&&(version==null||version.isEmpty()||version.equals(archive.versionName));}catch(Exception e){return false;}}
    private boolean hasDownloadedApk(String pkg,String version){String key=pkg+"|"+version;synchronized(downloadedStateCache){Boolean cached=downloadedStateCache.get(key);if(cached!=null)return cached.booleanValue();}boolean valid=isValidDownloadedApk(apkFile(pkg,version),pkg,version);synchronized(downloadedStateCache){downloadedStateCache.put(key,Boolean.valueOf(valid));}return valid;}
    private boolean isCurrentDownload(String key){return "details".equals(currentScreen)&&selectedApp!=null&&key.equals(selectedApp.optString("packageName",""))&&downloadLabel!=null&&downloadProgress!=null&&downloadActions!=null;}
    @Override public void onBackPressed(){if(detailsHost){restoreCatalogHostFocusBeforeReturn();if(Build.VERSION.SDK_INT>=21)finishAfterTransition();else finish();return;}if(settingsHost&&"settings".equals(currentScreen)){if(Build.VERSION.SDK_INT>=21)finishAfterTransition();else finish();return;}if(settingsHost&&"details".equals(currentScreen)){returnToCatalog();return;}if("settings".equals(currentScreen)){returnFromSettings();return;}if("complete".equals(currentScreen)){if(selectedApp!=null)showAppPage(selectedApp);else returnToCatalog();return;}if("details".equals(currentScreen)){returnToCatalog();return;}super.onBackPressed();}
    private void restoreCatalogHostFocusBeforeReturn(){try{MainActivity host=catalogHostRef==null?null:catalogHostRef.get();if(host==null||host.catalogRowViews==null)return;View row=host.catalogRowViews.get(catalogFocusedPackage);if(row!=null){row.requestFocus();host.catalogFocusTarget="app";}}catch(Exception ignored){}}
    private String urlSafe(String s){return s.replaceAll("[^A-Za-z0-9._-]","_");}
    private void installApk(File file,String pkg,String version){PackageInstaller.Session session=null;try{int signatureFlags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageInfo archive=getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),signatureFlags);if(!file.isFile()||file.length()<1024||archive==null||!pkg.equals(archive.packageName))throw new IOException("Downloaded APK is invalid or belongs to another app.");if(version!=null&&!version.isEmpty()&&!version.equals(archive.versionName))throw new IOException("Downloaded APK version does not match the selected catalog version.");if(isInstalled(pkg)){PackageInfo installed=getPackageManager().getPackageInfo(pkg,signatureFlags);if(!sameSigningCertificates(installed,archive))throw new IOException("APK signing certificate does not match the installed app.");}PackageInstaller installer=getPackageManager().getPackageInstaller();PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(pkg);params.setSize(file.length());int id=installer.createSession(params);session=installer.openSession(id);try(InputStream in=new FileInputStream(file);OutputStream out=session.openWrite("base.apk",0,-1)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);session.fsync(out);}Intent callback=new Intent(this,InstallStatusReceiver.class);callback.setAction("com.lge.lginstallservies.INSTALL_STATUS."+id);callback.putExtra("yw_package_name",pkg);callback.putExtra("yw_version",version);int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;PendingIntent pi=PendingIntent.getBroadcast(this,id,callback,flags);session.commit(pi.getIntentSender());session.close();session=null;}catch(Exception e){if(session!=null)try{session.abandon();}catch(Exception ignored){}toast("Couldn't start the installation. Please try again.");}}
    private void openApp(String p){try{Intent i=getPackageManager().getLaunchIntentForPackage(p);if(i!=null)startActivity(i);else toast("No launch screen is available.");}catch(Exception e){toast("Could not open app.");}}
    private void uninstall(String p){try{startActivity(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+p)));}catch(Exception e){toast("Could not open uninstall screen.");}}
}
