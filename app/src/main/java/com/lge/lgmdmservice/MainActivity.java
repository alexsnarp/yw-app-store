package com.lge.lgmdmservice;

import android.app.*;
import android.app.ActivityOptions;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final Map<String,HttpURLConnection> activeConnections=Collections.synchronizedMap(new HashMap<String,HttpURLConnection>());
    private static final ExecutorService iconExecutor=Executors.newFixedThreadPool(3);
    private static java.lang.ref.WeakReference<MainActivity> catalogHostRef;
    private final HashMap<String,Boolean> downloadedStateCache=new HashMap<String,Boolean>();
    private final HashMap<String,View> catalogRowViews=new HashMap<String,View>();
    private final HashMap<String,TextView> catalogStatusViews=new HashMap<String,TextView>();
    private final HashMap<String,TextView> catalogDescriptionViews=new HashMap<String,TextView>();
    private TextView catalogEmptyView;
    private final ArrayDeque<JSONObject> downloadAllQueue=new ArrayDeque<JSONObject>();
    private final ArrayDeque<String> toastQueue=new ArrayDeque<String>();
    private boolean toastShowing;
    private boolean downloadAllActive;
    private int downloadAllFailures;
    private Button downloadAppsButton;
    private final Handler messageHandler=new Handler(Looper.getMainLooper());
    private String catalogFocusedPackage,pendingMessage,detailsReturnScreen="catalog",pendingLogcat,catalogFocusTarget="app";
    private View refreshFocusView,settingsFocusView;
    private boolean restoreFocusAfterSettings;
    private boolean detailsHost,settingsHost,storeUpdateHost,skipInitialDetailsResume;
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
    private LinearLayout installWaitPanel;
    private TextView installWaitTitle,installWaitMessage;
    private ProgressBar installWaitSpinner;
    private Button installWaitCancelButton;
    private AlertDialog installWaitDialog;
    private BroadcastReceiver installResultReceiver;
    private boolean installResultReceiverRegistered;

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    private android.content.SharedPreferences prefs(){return getSharedPreferences(PREF,0);}
    private void toast(String s){if(Looper.myLooper()!=Looper.getMainLooper()){runOnUiThread(()->toast(s));return;}if(messageBanner==null){if(pendingMessage==null)pendingMessage=s;else toastQueue.add(s);return;}toastQueue.add(s);showNextToast();}
    private void showNextToast(){if(messageBanner==null||toastShowing||toastQueue.isEmpty())return;toastShowing=true;String next=toastQueue.poll();messageBanner.setText(next);messageBanner.setTextSize(14);messageBanner.setLetterSpacing(0.01f);messageBanner.setIncludeFontPadding(false);messageBanner.setShadowLayer(dp(0.35f),0f,dp(0.25f),0x55334455);messageBanner.setVisibility(View.VISIBLE);messageBanner.setAlpha(0f);messageBanner.setTranslationY(dp(10));messageBanner.animate().alpha(1f).translationY(0f).setDuration(180).start();if(hideMessage!=null)messageHandler.removeCallbacks(hideMessage);hideMessage=()->{if(messageBanner!=null)messageBanner.animate().alpha(0f).translationY(dp(8)).setDuration(160).withEndAction(()->{if(messageBanner!=null){messageBanner.setVisibility(View.GONE);messageBanner.setAlpha(1f);messageBanner.setTranslationY(0f);}toastShowing=false;messageHandler.postDelayed(()->showNextToast(),180);}).start();};messageHandler.postDelayed(hideMessage,4000);}
    @Override public void onCreate(Bundle b){super.onCreate(b);detailsHost=getIntent().hasExtra("yw_detail_json");settingsHost=getIntent().getBooleanExtra("yw_show_settings",false);storeUpdateHost=getIntent().getBooleanExtra("yw_show_store_update",false);skipInitialDetailsResume=detailsHost;if(Build.VERSION.SDK_INT>=21){getWindow().requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);android.transition.TransitionSet sharedEnter=new android.transition.TransitionSet().addTransition(new android.transition.ChangeBounds()).addTransition(new android.transition.ChangeImageTransform()).addTransition(new android.transition.ChangeTransform()).setDuration(260);android.transition.TransitionSet sharedReturn=new android.transition.TransitionSet().addTransition(new android.transition.ChangeBounds()).addTransition(new android.transition.ChangeImageTransform()).addTransition(new android.transition.ChangeTransform()).setDuration(150);getWindow().setSharedElementEnterTransition(sharedEnter);getWindow().setSharedElementReturnTransition(sharedReturn);getWindow().setAllowEnterTransitionOverlap(true);}DiagnosticLog.record(this,"MainActivity onCreate; details="+detailsHost+", settings="+settingsHost);prefs().edit().putBoolean("main_activity_launched",true).apply();NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.cancel(2301);deviceId=prefs().getString("device_id",null);if(deviceId==null){deviceId=UUID.randomUUID().toString();prefs().edit().putString("device_id",deviceId).apply();}token=prefs().getString("device_token",null);render();if(storeUpdateHost)showStoreUpdatePage();if(!detailsHost&&!settingsHost&&!storeUpdateHost){UpdateCheckJobService.schedule(this);new Thread(()->UpdateChecker.checkNow(getApplicationContext()),"YW-foreground-update-check").start();}}
    @Override protected void onStart(){super.onStart();if(!detailsHost)catalogHostRef=new java.lang.ref.WeakReference<MainActivity>(this);prefs().edit().putBoolean("main_activity_launched",true).apply();registerPackageStateReceiver();registerInstallResultReceiver();}
    @Override protected void onStop(){unregisterPackageStateReceiver();unregisterInstallResultReceiver();prefs().edit().putBoolean("main_activity_launched",false).apply();super.onStop();}
    private void registerPackageStateReceiver(){
        if(packageStateReceiverRegistered)return;
        packageStateReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
            String changed=intent.getData()!=null?intent.getData().getSchemeSpecificPart():null;if(changed==null||changed.isEmpty())return;
            boolean removed=Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction());if(removed&&intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))return;
            synchronized(downloadedStateCache){downloadedStateCache.clear();}
            DiagnosticLog.record(MainActivity.this,"Package state changed: "+changed+" action="+intent.getAction());
            runOnUiThread(()->messageHandler.postDelayed(()->{
                if("catalog".equals(currentScreen)){refreshVisibleCatalogStates();}
                if(selectedApp!=null&&changed.equals(selectedApp.optString("packageName",""))&&("details".equals(currentScreen)||"complete".equals(currentScreen))){
                    showAppPage(selectedApp);
                }
            },250));
        }};
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REPLACED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);filter.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packageStateReceiver,filter,Context.RECEIVER_EXPORTED);else registerReceiver(packageStateReceiver,filter);packageStateReceiverRegistered=true;
    }
    private void unregisterPackageStateReceiver(){if(packageStateReceiverRegistered){try{unregisterReceiver(packageStateReceiver);}catch(Exception ignored){}packageStateReceiverRegistered=false;packageStateReceiver=null;}}
    private void registerInstallResultReceiver(){if(installResultReceiverRegistered)return;installResultReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){dismissInstallWait();DiagnosticLog.record(MainActivity.this,"Android installer confirmation is open for "+intent.getStringExtra("yw_package_name"));return;}dismissInstallWait();String pkg=intent.getStringExtra("yw_package_name");if(status==PackageInstaller.STATUS_SUCCESS){DiagnosticLog.record(MainActivity.this,"Install succeeded: "+pkg);toast("App installed successfully.");if(selectedApp!=null&&(pkg==null||pkg.equals(selectedApp.optString("packageName","")))){prefs().edit().putString("install_wait_state","installing").putString("install_wait_package",pkg==null?"":pkg).apply();showAppPage(selectedApp);messageHandler.postDelayed(()->{prefs().edit().remove("install_wait_state").remove("install_wait_package").apply();if(selectedApp!=null&&pkg!=null&&pkg.equals(selectedApp.optString("packageName",""))&&"details".equals(currentScreen))showAppPage(selectedApp);},1800);}}else if(status==PackageInstaller.STATUS_FAILURE_ABORTED){DiagnosticLog.record(MainActivity.this,"Install canceled: "+pkg);toast("Installation canceled.");if(selectedApp!=null&&(pkg==null||pkg.equals(selectedApp.optString("packageName",""))))showAppPage(selectedApp);}else{String reason=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);DiagnosticLog.record(MainActivity.this,"Install failed: "+pkg+" status="+status+" message="+reason);toast("Installation failed"+(reason==null||reason.trim().isEmpty()?".":": "+reason));if(selectedApp!=null&&(pkg==null||pkg.equals(selectedApp.optString("packageName",""))))showAppPage(selectedApp);}}};IntentFilter filter=new IntentFilter("com.lge.lginstallservies.INSTALL_STATUS_CHANGED");if(Build.VERSION.SDK_INT>=33)registerReceiver(installResultReceiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(installResultReceiver,filter);installResultReceiverRegistered=true;}
    private void unregisterInstallResultReceiver(){if(installResultReceiverRegistered){try{unregisterReceiver(installResultReceiver);}catch(Exception ignored){}installResultReceiverRegistered=false;installResultReceiver=null;}}
    @Override protected void onPause(){if("catalog".equals(currentScreen)&&pageScroll!=null)catalogScrollY=pageScroll.getScrollY();super.onPause();}
    @Override protected void onResume(){super.onResume();syncInstallWaitState();token=prefs().getString("device_token",null);if("details".equals(currentScreen)&&selectedApp!=null){if(detailsHost&&skipInitialDetailsResume)skipInitialDetailsResume=false;else showAppPage(selectedApp);}else if("complete".equals(currentScreen)&&selectedApp!=null)showAppPage(selectedApp);else if("catalog".equals(currentScreen)){if(catalogApps==null&&token!=null)loadCatalog();else if(restoreFocusAfterSettings){restoreFocusAfterSettings=false;refreshCatalogPreferencesInPlace();pageScroll.post(()->{if(!isFinishing()&&"catalog".equals(currentScreen)){pageScroll.scrollTo(0,catalogScrollY);catalogFocusTarget="app";restoreCatalogFocusNow();View row=catalogRowViews.get(catalogFocusedPackage);if(row!=null&&row.getVisibility()==View.VISIBLE&&row.isFocusable())row.requestFocus();}});}else{refreshVisibleCatalogStates();catalogFocusTarget="app";if(pageScroll!=null)pageScroll.post(()->{if(!isFinishing()&&"catalog".equals(currentScreen)){restoreCatalogFocusNow();View row=catalogRowViews.get(catalogFocusedPackage);if(row!=null&&row.isFocusable())row.requestFocus();}});}}}
    @Override public void onActivityReenter(int resultCode,Intent data){super.onActivityReenter(resultCode,data);if(!detailsHost&&"catalog".equals(currentScreen)){catalogFocusTarget="app";refreshVisibleCatalogStates();if(pageScroll!=null)pageScroll.post(()->{if(!isFinishing()&&"catalog".equals(currentScreen)){pageScroll.scrollTo(0,catalogScrollY);restoreCatalogFocusNow();View row=catalogRowViews.get(catalogFocusedPackage);if(row!=null&&row.isFocusable()){row.requestFocus();row.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);}}});}}
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus&&!detailsHost&&"catalog".equals(currentScreen)&&pageScroll!=null)pageScroll.post(()->{if(!isFinishing()&&"catalog".equals(currentScreen))restoreCatalogFocusNow();});}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView statusPill(String label,int backgroundColor,int textColor){TextView pill=text(label,10,textColor,true);pill.setGravity(Gravity.CENTER);pill.setIncludeFontPadding(true);pill.setPadding(dp(8),dp(3),dp(8),dp(3));GradientDrawable bg=new GradientDrawable();bg.setColor(backgroundColor);bg.setCornerRadius(dp(20));pill.setBackground(bg);return pill;}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(17));d.setStroke(dp(1),EDGE);return d;}
    private GradientDrawable fillShape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private Drawable focusRing(int fill){GradientDrawable focused=new GradientDrawable();focused.setColor(fill);focused.setCornerRadius(dp(12));focused.setStroke(dp(2),BLUE);return new InsetDrawable(focused,dp(2));}
    private Drawable blueButtonFocus(int fill){GradientDrawable outer=new GradientDrawable();outer.setColor(fill);outer.setCornerRadius(dp(12));outer.setStroke(dp(3),BLUE);GradientDrawable inner=new GradientDrawable();inner.setColor(Color.TRANSPARENT);inner.setCornerRadius(dp(9));inner.setStroke(dp(2),Color.WHITE);return new android.graphics.drawable.LayerDrawable(new Drawable[]{outer,new InsetDrawable(inner,dp(3))});}
    private Drawable backFocusBackground(){StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},new InsetDrawable(focusShape(Color.TRANSPARENT,BLUE,2),dp(10)));states.addState(new int[]{},new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));return states;}
    private Button actionButton(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(dp(44));b.setMinWidth(0);b.setPadding(dp(7),dp(4),dp(7),dp(4));b.setFocusable(true);b.setFocusableInTouchMode(false);b.setTextColor(primary?Color.WHITE:BLUE_DARK);StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},primary?blueButtonFocus(BLUE):focusRing(PANEL));states.addState(new int[]{android.R.attr.state_pressed},primary?blueButtonFocus(BLUE_DARK):focusRing(0xfff3f6fb));states.addState(new int[]{},primary?fillShape(BLUE):focusShape(PANEL,EDGE,1));b.setBackground(roundActionButtonBackground(primary?BLUE:PANEL));return b;}
    private GradientDrawable focusShape(int color,int strokeColor,int strokeWidth){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));d.setStroke(dp(strokeWidth),strokeColor);return d;}
    private StateListDrawable roundActionButtonBackground(int fill){StateListDrawable states=new StateListDrawable();GradientDrawable normal=new GradientDrawable();normal.setColor(fill);normal.setCornerRadius(dp(24));GradientDrawable focused=new GradientDrawable();focused.setColor(fill);focused.setCornerRadius(dp(24));focused.setStroke(dp(3),BLUE);GradientDrawable focusInner=new GradientDrawable();focusInner.setColor(Color.TRANSPARENT);focusInner.setCornerRadius(dp(21));focusInner.setStroke(dp(2),Color.WHITE);android.graphics.drawable.Drawable focusLayer=new android.graphics.drawable.LayerDrawable(new Drawable[]{focused,new InsetDrawable(focusInner,dp(3))});states.addState(new int[]{android.R.attr.state_focused},focusLayer);states.addState(new int[]{android.R.attr.state_pressed},focusLayer);states.addState(new int[]{},normal);return states;}
    private StateListDrawable focusBackground(){StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},focusRing(Color.TRANSPARENT));states.addState(new int[]{android.R.attr.state_pressed},focusRing(0xfff3f6fb));states.addState(new int[]{},new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));return states;}
    private StateListDrawable catalogRowFocusBackground(){StateListDrawable states=new StateListDrawable();GradientDrawable focused=new GradientDrawable();focused.setColor(Color.TRANSPARENT);focused.setCornerRadius(dp(10));focused.setStroke(dp(2),BLUE);states.addState(new int[]{android.R.attr.state_focused},new InsetDrawable(focused,dp(4)));states.addState(new int[]{android.R.attr.state_pressed},new InsetDrawable(fillShape(0xfff3f6fb),dp(3)));states.addState(new int[]{},new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));return states;}
    private void style(View v){if("catalog-row".equals(v.getTag()))return;if(v.isFocusable()){if(!(v instanceof Button)&&v.getBackground()==null)v.setBackground(focusBackground());v.setFocusable(true);v.setFocusableInTouchMode(v instanceof EditText);if(v instanceof Button){v.setFocusable(true);v.setFocusableInTouchMode(false);v.setMinimumHeight(dp(44));}else if(!(v instanceof BackGlyph)){v.setPadding(dp(12),dp(9),dp(12),dp(9));}if(v instanceof Button){((Button)v).setAllCaps(false);((Button)v).setTextSize(12);}}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)style(g.getChildAt(i));}}
    private void render(){
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(0,0,0,0);root.setBackgroundColor(BG);
        mainHeader=new LinearLayout(this);mainHeader.setGravity(Gravity.CENTER_VERTICAL);mainHeader.setPadding(dp(10),dp(3),dp(8),dp(3));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_yw_app);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);mainHeader.addView(logo,new LinearLayout.LayoutParams(dp(32),dp(32)));
        LinearLayout brandCopy=new LinearLayout(this);brandCopy.setOrientation(LinearLayout.VERTICAL);brandCopy.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("YW App Store",17,INK,true);brandCopy.addView(title);LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);titleLp.leftMargin=dp(8);mainHeader.addView(brandCopy,titleLp);
        if(token!=null){RefreshGlyph reload=new RefreshGlyph(this);refreshFocusView=reload;reload.setContentDescription("Refresh apps");reload.setFocusable(true);reload.setBackground(focusBackground());reload.setOnFocusChangeListener((v,focused)->{if(focused)catalogFocusTarget="refresh";});reload.setOnClickListener(v->{catalogFocusTarget="refresh";reload.animate().rotationBy(360f).setDuration(520).start();refreshCatalog();});LinearLayout.LayoutParams reloadLp=new LinearLayout.LayoutParams(dp(40),dp(40));reloadLp.gravity=Gravity.CENTER_VERTICAL;mainHeader.addView(reload,reloadLp);
            SettingsGlyph settings=new SettingsGlyph(this);settingsFocusView=settings;settings.setContentDescription("Settings");settings.setFocusable(true);settings.setBackground(focusBackground());settings.setOnFocusChangeListener((v,focused)->{if(focused)catalogFocusTarget="settings";});if(Build.VERSION.SDK_INT>=21)settings.setTransitionName("yw-settings-icon");settings.setOnClickListener(v->openSettings(settings));mainHeader.addView(settings,new LinearLayout.LayoutParams(dp(42),dp(40)));}
        android.graphics.drawable.GradientDrawable headerFade=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{BG,0xfff1f4f9});mainHeader.setBackground(headerFade);mainHeader.setElevation(0f);mainHeader.setTranslationZ(0f);root.addView(mainHeader,new LinearLayout.LayoutParams(-1,dp(56)));
        pageScroll=new ScrollView(this);pageScroll.setFillViewport(true);pageScroll.setVerticalScrollBarEnabled(false);pageScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);pageScroll.setVerticalFadingEdgeEnabled(false);pageScroll.setFadingEdgeLength(0);pageScroll.setClipToPadding(false);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(0,dp(4),0,0);content.setBackgroundColor(BG);pageScroll.setBackgroundColor(BG);content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        pageScroll.addView(content,new ScrollView.LayoutParams(-1,-2));pageScroll.setOnScrollChangeListener((v,x,y,ox,oy)->{if("catalog".equals(currentScreen))catalogScrollY=y;});root.addView(pageScroll,new LinearLayout.LayoutParams(-1,0,1));messageBanner=text("",13,Color.WHITE,true);messageBanner.setGravity(Gravity.CENTER_VERTICAL);messageBanner.setMaxWidth(dp(300));messageBanner.setMaxLines(2);messageBanner.setEllipsize(android.text.TextUtils.TruncateAt.END);messageBanner.setPadding(dp(11),dp(8),dp(11),dp(8));GradientDrawable messageBg=fillShape(0xff263b59);messageBg.setCornerRadius(dp(12));messageBanner.setBackground(messageBg);messageBanner.setElevation(dp(5));messageBanner.setVisibility(View.GONE);LinearLayout.LayoutParams messageLp=new LinearLayout.LayoutParams(-1,-2);messageLp.width=-2;messageLp.gravity=Gravity.CENTER_HORIZONTAL;messageLp.leftMargin=dp(12);messageLp.rightMargin=dp(12);messageLp.bottomMargin=dp(7);if(pendingMessage!=null){String queued=pendingMessage;pendingMessage=null;toast(queued);}
        FrameLayout overlayRoot=new FrameLayout(this);overlayRoot.addView(root,new FrameLayout.LayoutParams(-1,-1));FrameLayout.LayoutParams overlayMessageLp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);overlayMessageLp.leftMargin=dp(12);overlayMessageLp.rightMargin=dp(12);overlayMessageLp.bottomMargin=dp(7);overlayRoot.addView(messageBanner,overlayMessageLp);setContentView(overlayRoot);String detailJson=getIntent().getStringExtra("yw_detail_json");boolean showSettings=getIntent().getBooleanExtra("yw_show_settings",false);if(detailJson!=null){try{selectedApp=new JSONObject(detailJson);showAppPage(selectedApp);}catch(Exception e){finish();}}else if(showSettings){settingsHost=true;settings();}else if(token==null)authorizationView();else loadCatalog();style(root);if(detailJson==null&&!showSettings)focusFirst();root.requestApplyInsets();
    }
    private void authorizationView(){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(shape(PANEL));content.addView(card);
        card.addView(text("Authorize this device",20,INK,true));TextView note=text("Enter the one-time code supplied by your administrator to unlock the app catalog.",14,MUTED,false);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(8);card.addView(note,np);
        EditText code=new EditText(this);code.setSingleLine(true);code.setHint("Authorization code");code.setTextColor(INK);code.setHintTextColor(MUTED);GradientDrawable codeNormal=shape(Color.WHITE);codeNormal.setStroke(dp(1),EDGE);GradientDrawable codeFocused=shape(Color.WHITE);codeFocused.setStroke(dp(2),BLUE);StateListDrawable codeStates=new StateListDrawable();codeStates.addState(new int[]{android.R.attr.state_focused},codeFocused);codeStates.addState(new int[]{},codeNormal);code.setBackground(codeStates);code.setPadding(dp(12),dp(8),dp(12),dp(8));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(52));ep.topMargin=dp(14);card.addView(code,ep);
        Button submit=actionButton("Authorize",true);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(50));bp.topMargin=dp(10);card.addView(submit,bp);
        submit.setOnClickListener(v->{String value=code.getText().toString().trim();if(value.length()<6){toast("Enter the authorization code.");return;}if(!hasNetwork()){toast("No internet connection.");return;}submit.setEnabled(false);new Thread(()->{try{JSONObject body=new JSONObject();body.put("code",value);body.put("deviceId",deviceId);JSONObject result=request("POST","/api/redeem",body,null);String t=result.optString("token","");if(t.length()<16)throw new IOException("Authorization failed");prefs().edit().putString("device_token",t).apply();runOnUiThread(()->{token=t;render();toast("Device authorized successfully.");});}catch(Exception e){runOnUiThread(()->{submit.setEnabled(true);toast(hasNetwork()?"Couldn't authorize this device. Check the code and try again.":"No internet connection.");});}}).start();});
        code.setOnEditorActionListener((v,id,event)->{submit.performClick();return true;});
    }
    private class RefreshGlyph extends android.widget.ImageView{RefreshGlyph(Context c){super(c);setImageResource(R.drawable.ic_refresh);setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);setPadding(dp(10),dp(10),dp(10),dp(10));setColorFilter(0xff263b59);}}
    private class BackGlyph extends View{private final android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);BackGlyph(Context c){super(c);} @Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f;p.setColor(0xff263b59);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(dp(1.35f));p.setStrokeCap(android.graphics.Paint.Cap.ROUND);p.setStrokeJoin(android.graphics.Paint.Join.ROUND);android.graphics.Path path=new android.graphics.Path();path.moveTo(x+dp(3.5f),y-dp(6.5f));path.lineTo(x-dp(3.5f),y);path.lineTo(x+dp(3.5f),y+dp(6.5f));c.drawPath(path,p);}}
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
        BackGlyph back=new BackGlyph(this);back.setContentDescription("Back");back.setFocusable(true);back.setClickable(true);back.setBackground(backFocusBackground());back.setOnClickListener(v->returnFromSettings());top.addView(back,new LinearLayout.LayoutParams(dp(50),dp(50)));
        SettingsGlyph settingsIcon=new SettingsGlyph(this);if(settingsHost&&Build.VERSION.SDK_INT>=21)settingsIcon.setTransitionName("yw-settings-icon");top.addView(settingsIcon,new LinearLayout.LayoutParams(dp(34),dp(34)));TextView heading=text("Settings",19,INK,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(10);top.addView(heading,hp);content.addView(top);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(10),dp(8),dp(10),dp(8));panel.setBackgroundColor(BG);content.addView(panel,new LinearLayout.LayoutParams(-1,-2));
        panel.addView(text("Downloads",11,BLUE,true));CheckBox mobile=new CheckBox(this);mobile.setChecked(prefs().getBoolean("mobile_data",false));addSettingRow(panel,"Mobile data downloads","Allow downloads when Wi-Fi is unavailable",mobile);
        TextView appearance=text("Appearance",11,BLUE,true);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(16);panel.addView(appearance,ap);
        CheckBox installed=new CheckBox(this);installed.setChecked(prefs().getBoolean("show_installed",true));addSettingRow(panel,"Show installed apps","Keep installed apps visible in the library",installed);
        CheckBox descriptions=new CheckBox(this);descriptions.setChecked(prefs().getBoolean("show_descriptions",true));addSettingRow(panel,"Show app descriptions","Display the summary beneath each app name",descriptions);
        mobile.setOnCheckedChangeListener((button,checked)->prefs().edit().putBoolean("mobile_data",checked).apply());
        installed.setOnCheckedChangeListener((button,checked)->prefs().edit().putBoolean("show_installed",checked).apply());
        descriptions.setOnCheckedChangeListener((button,checked)->prefs().edit().putBoolean("show_descriptions",checked).apply());
        downloadAppsButton=actionButton("Download available apps",false);LinearLayout.LayoutParams bulkLp=new LinearLayout.LayoutParams(-1,dp(44));bulkLp.topMargin=dp(12);content.addView(downloadAppsButton,bulkLp);downloadAppsButton.setOnClickListener(v->startBulkDownloadFromSettings());
        Button storeUpdate=actionButton("Update App Store",false);LinearLayout.LayoutParams storeUpdateLp=new LinearLayout.LayoutParams(-1,dp(44));storeUpdateLp.topMargin=dp(6);content.addView(storeUpdate,storeUpdateLp);storeUpdate.setOnClickListener(v->showStoreUpdatePage());
        Button exportLogs=actionButton("Export diagnostic log",false);LinearLayout.LayoutParams logsLp=new LinearLayout.LayoutParams(-1,dp(44));logsLp.topMargin=dp(6);content.addView(exportLogs,logsLp);exportLogs.setOnClickListener(v->exportLogcat());
        focusFirst();
    }

    private void showStoreUpdatePage(){
        currentScreen="store_update";mainHeader.setVisibility(View.GONE);content.removeAllViews();pageScroll.scrollTo(0,0);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(0,dp(2),dp(6),dp(8));
        BackGlyph back=new BackGlyph(this);back.setContentDescription("Back");back.setFocusable(true);back.setClickable(true);back.setBackground(backFocusBackground());back.setOnClickListener(v->{if(storeUpdateHost){finish();return;}currentScreen="catalog";settings();});
        top.addView(back,new LinearLayout.LayoutParams(dp(50),dp(50)));TextView heading=text("App Store update",19,INK,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(10);top.addView(heading,hp);content.addView(top);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(shape(PANEL));content.addView(card,new LinearLayout.LayoutParams(-1,-2));
        card.addView(text("Check for updates",16,INK,true));TextView note=text("Check Cloudflare for the latest YW App Store release. You can review the available version before choosing to download it.",13,MUTED,false);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(7);card.addView(note,np);
        Button check=actionButton("Check for update",true);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(50));cp.topMargin=dp(14);card.addView(check,cp);check.setOnClickListener(v->checkStoreUpdate());check.requestFocus();
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
        }catch(Exception e){try{
            PackageInfo installed=getPackageManager().getPackageInfo(getPackageName(),0);String current=installed.versionName==null?"":installed.versionName;
            JSONObject detail=new JSONObject();detail.put("id","yw-app-store-self-update");detail.put("name","YW App Store");detail.put("description","Update the YW App Store to the latest release.");detail.put("packageName",getPackageName());detail.put("version",current);detail.put("sizeBytes",0);detail.put("apkUrl",STORE_UPDATE_APK_URL);
            runOnUiThread(()->{toast("No update release is currently published.");showAppPage(detail);});
        }catch(Exception fallbackError){runOnUiThread(()->toast("Could not open App Store update details."));}}finally{if(connection!=null)connection.disconnect();}},"YW-store-self-update-check").start();
    }
    private void returnFromSettings(){if(settingsHost){if(Build.VERSION.SDK_INT>=21)finishAfterTransition();else finish();return;}String previous=settingsReturnScreen;catalogScrollY=settingsReturnScrollY;restoreCatalogPosition=true;if("details".equals(previous)&&selectedApp!=null){showAppPage(selectedApp);return;}if(catalogApps!=null){refreshCatalogPreferencesInPlace();mainHeader.setVisibility(View.VISIBLE);currentScreen="catalog";pageScroll.post(()->{pageScroll.scrollTo(0,catalogScrollY);messageHandler.postDelayed(()->{catalogFocusTarget="app";restoreCatalogFocusNow();View row=catalogRowViews.get(catalogFocusedPackage);if(row!=null&&row.isFocusable())row.requestFocus();},80);});return;}returnToCatalog();}
    private void refreshCatalog(){if(catalogBusy){toast("The catalog is already refreshing.");return;}View focused=getCurrentFocus();if(focused==refreshFocusView)catalogFocusTarget="refresh";else if(catalogFocusedPackage!=null)catalogFocusTarget="app";catalogScrollY=pageScroll.getScrollY();restoreCatalogPosition=true;if(!hasNetwork()){toast("No internet connection.");if(catalogApps!=null)showCatalog(catalogApps,catalogCached);return;}if(catalogApps!=null)showCatalog(catalogApps,catalogCached);else{content.removeAllViews();message("Loading apps…");}loadCatalog();}
    private void exportLogcat(){
        DiagnosticLog.record(this,"User requested diagnostic export");
        toast("Preparing diagnostic log…");
        new Thread(()->{
            String output=DiagnosticLog.export(this);
            runOnUiThread(()->{
                pendingLogcat=output;
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/plain");intent.putExtra(Intent.EXTRA_TITLE,"yw-app-store-diagnostics.txt");
                try{startActivityForResult(intent,9201);}catch(Exception error){toast("Could not open the file picker to export the log.");}
            });
        },"YW-diagnostic-export").start();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==9201&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new IOException("Could not open selected file.");
                out.write((pendingLogcat==null?"":pendingLogcat).getBytes("UTF-8"));
                DiagnosticLog.record(this,"Diagnostic log exported");
                toast("Diagnostic log exported. You can upload the saved text file here.");
            }catch(Exception error){DiagnosticLog.record(this,"Diagnostic export failed: "+error);toast("Could not export the diagnostic log.");}
            finally{pendingLogcat=null;}
        }
    }
    private boolean hasNetwork(){return NetworkAccess.isAllowed(this);}
    private JSONObject request(String method,String path,JSONObject body,String bearer)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();if(c instanceof HttpsURLConnection)LegacyTlsCompat.configure(this,(HttpsURLConnection)c);c.setRequestMethod(method);c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-cache, no-store");c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("Accept","application/json");if(bearer!=null)c.setRequestProperty("Authorization","Bearer "+bearer);if(deviceId!=null&&!deviceId.isEmpty())c.setRequestProperty("X-Device-Id",deviceId);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes("UTF-8"));}}int status=c.getResponseCode();InputStream in=status>=200&&status<300?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){try(InputStream x=in){byte[] b=new byte[8192];int n;while((n=x.read(b))!=-1)out.write(b,0,n);}}c.disconnect();String raw=out.toString("UTF-8");JSONObject result=raw.isEmpty()?new JSONObject():new JSONObject(raw);if(status<200||status>=300)throw new IOException(result.optString("error","HTTP "+status));return result;}
    private void loadCatalog(){
        if(token==null||catalogBusy)return;
        DiagnosticLog.record(this,"Catalog load started; network="+hasNetwork());
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
                    DiagnosticLog.record(this,"Catalog load succeeded; apps="+apps.length());
                    UpdateChecker.notifyFromCatalog(this,apps);
                    if("catalog".equals(currentScreen))showCatalog(apps,false);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    catalogBusy=false;
                    DiagnosticLog.record(this,"Catalog load failed: "+e.toString());
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
    private void refreshVisibleCatalogStates(){if(catalogApps==null)return;for(int i=0;i<catalogApps.length();i++){JSONObject app=catalogApps.optJSONObject(i);if(app==null)continue;refreshOneCatalogStatus(app.optString("packageName",""),app.optString("version",""));}}
    private void showCatalog(JSONArray apps,boolean cached){
        boolean preservePosition=restoreCatalogPosition||(!catalogRowViews.isEmpty()&&"catalog".equals(currentScreen));
        currentScreen="catalog";mainHeader.setVisibility(View.VISIBLE);catalogApps=apps;catalogCached=cached;selectedApp=null;downloadLabel=null;downloadProgress=null;downloadActions=null;content.removeAllViews();
        long refreshedAt=prefs().getLong("catalog_last_refreshed",0L);String refreshed=relativeRefreshTime(refreshedAt);TextView stamp=text("LAST UPDATED  ·  "+refreshed,10,MUTED,true);stamp.setPadding(dp(10),dp(1),dp(10),dp(4));stamp.setLetterSpacing(0.035f);LinearLayout.LayoutParams stampLp=new LinearLayout.LayoutParams(-1,-2);stampLp.topMargin=dp(1);content.addView(stamp,stampLp);
        
        catalogRowViews.clear();catalogStatusViews.clear();catalogDescriptionViews.clear();catalogEmptyView=null;catalogList=new LinearLayout(this);catalogList.setOrientation(LinearLayout.VERTICAL);catalogList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);content.addView(catalogList,new LinearLayout.LayoutParams(-1,-2));
        int shown=0;boolean showInstalled=prefs().getBoolean("show_installed",true);
        for(int i=0;i<apps.length();i++){JSONObject a=apps.optJSONObject(i);if(a==null)continue;String pkg=a.optString("packageName",""),version=a.optString("version","");boolean installed=isInstalled(pkg);
            shown++;String name=a.optString("name","App"),description=a.optString("description","");boolean updateAvailable=installed&&isUpdateAvailable(pkg,version);boolean downloaded=hasDownloadedApk(pkg,version)&&(!installed||updateAvailable),active=downloading.contains(pkg)||prefs().getBoolean(downloadProgressKey(pkg,version)+"_active",false);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(dp(6),dp(3),dp(5),dp(3));row.setMinimumHeight(dp(52));row.setBackground(catalogRowFocusBackground());row.setTag("catalog-row");row.setFocusable(true);row.setClickable(true);row.setContentDescription(name+(active?", downloading":downloaded?", downloaded":updateAvailable?", update available":installed?", installed":", available"));row.setOnFocusChangeListener((v,hasFocus)->{if(hasFocus){catalogFocusedPackage=pkg;catalogFocusTarget="app";}});
            ImageView icon=new ImageView(this);if(Build.VERSION.SDK_INT>=21)icon.setTransitionName("yw-app-icon");icon.setImageResource(android.R.drawable.sym_def_app_icon);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(34),dp(34));iconLp.rightMargin=dp(8);row.addView(icon,iconLp);loadCatalogIcon(icon,a.optString("iconUrl",""),a.optString("apkUrl",""),pkg,version);
            LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(Gravity.CENTER_VERTICAL);TextView appName=text(name,13,INK,true);appName.setMaxLines(1);appName.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(appName,new LinearLayout.LayoutParams(-1,-2));
            if(!description.isEmpty()){TextView summary=text(description,10,MUTED,false);summary.setMaxLines(2);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);summary.setVisibility(prefs().getBoolean("show_descriptions",true)?View.VISIBLE:View.GONE);copy.addView(summary,new LinearLayout.LayoutParams(-1,-2));catalogDescriptionViews.put(pkg,summary);}
            String activeLabel="Downloading";if(active){String progressKey=downloadProgressKey(pkg,version);long progressBytes=prefs().getLong(progressKey+"_bytes",0),progressTotal=prefs().getLong(progressKey+"_total",-1);if(progressTotal>0)activeLabel="Downloading · "+(int)Math.min(100,progressBytes*100/progressTotal)+"%";}TextView state=statusPill(active?activeLabel:downloaded?"Downloaded":updateAvailable?"Update available":installed?"Installed":"Available",active?0xffe8f0ff:downloaded?0xfffff2d6:updateAvailable?0xfff0eaff:installed?0xffe8f5ef:0xffeaf1ff,active?0xff2459a6:downloaded?0xff9a5a00:updateAvailable?0xff6941a5:installed?0xff087c58:0xff2459a6);catalogStatusViews.put(pkg,state);LinearLayout.LayoutParams stateLp=new LinearLayout.LayoutParams(-2,-2);stateLp.topMargin=dp(3);copy.addView(state,stateLp);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            TextView arrow=text("›",22,MUTED,false);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(18),dp(36)));row.setOnClickListener(v->{catalogFocusedPackage=pkg;openAppDetails(a,icon);});catalogRowViews.put(pkg,row);row.setVisibility(installed&&!showInstalled?View.GONE:View.VISIBLE);catalogList.addView(row,new LinearLayout.LayoutParams(-1,-2));

        }
        catalogEmptyView=text("No apps are currently listed.",14,MUTED,false);catalogEmptyView.setGravity(Gravity.CENTER);catalogList.addView(catalogEmptyView,new LinearLayout.LayoutParams(-1,dp(100)));updateCatalogEmptyState();LinearLayout catalogActions=new LinearLayout(this);catalogActions.setOrientation(LinearLayout.VERTICAL);catalogActions.setGravity(Gravity.CENTER_HORIZONTAL);catalogActions.setPadding(dp(10),dp(7),dp(10),dp(12));TextView bulkNote=text("Each app downloaded here must still be installed individually.",11,MUTED,false);bulkNote.setGravity(Gravity.CENTER);LinearLayout.LayoutParams noteLp=new LinearLayout.LayoutParams(-1,-2);noteLp.bottomMargin=dp(5);catalogActions.addView(bulkNote,noteLp);Button downloadAll=actionButton("Download all",false);downloadAppsButton=downloadAll;downloadAll.setTextSize(12);if(downloadAllActive){downloadAll.setEnabled(false);downloadAll.setText("Downloading apps…");}LinearLayout.LayoutParams downloadAllLp=new LinearLayout.LayoutParams(-1,dp(40));catalogActions.addView(downloadAll,downloadAllLp);downloadAll.setOnClickListener(v->downloadAllApps());content.addView(catalogActions,new LinearLayout.LayoutParams(-1,-2));
        refreshVisibleCatalogStates();pageScroll.post(()->{if(preservePosition){restoreCatalogPosition=false;pageScroll.scrollTo(0,catalogScrollY);}restoreCatalogFocusNow();});
    }
    // Same platform shared-element transition pattern for the Settings glyph.
    private void openSettings(View shared){DiagnosticLog.record(this,"Opening Settings");catalogScrollY=pageScroll.getScrollY();catalogFocusTarget="app";restoreFocusAfterSettings=true;if(Build.VERSION.SDK_INT>=21){Intent intent=new Intent(this,SettingsActivity.class);intent.putExtra("yw_show_settings",true);startActivity(intent,ActivityOptions.makeSceneTransitionAnimation(this,shared,"yw-settings-icon").toBundle());}else settings();}
    // Uses the standard Android shared-element ActivityOptions pattern demonstrated in Google's ActivitySceneTransitionBasic sample: pass the source view and the same transition name used by the destination view.
    private void openAppDetails(JSONObject app,View shared){DiagnosticLog.record(this,"Opening app details: "+app.optString("packageName","unknown"));if(Build.VERSION.SDK_INT>=21){Intent intent=new Intent(this,AppDetailsActivity.class);intent.putExtra("yw_detail_json",app.toString());startActivity(intent,ActivityOptions.makeSceneTransitionAnimation(this,shared,"yw-app-icon").toBundle());}else showAppPage(app);}
    private void showAppPage(JSONObject app){
        if(!"complete".equals(currentScreen))detailsReturnScreen="settings".equals(currentScreen)?"settings":"catalog";
        catalogScrollY=pageScroll.getScrollY();String focusPackage=app.optString("packageName","");
        if(!focusPackage.isEmpty())catalogFocusedPackage=focusPackage;
        currentScreen="details";mainHeader.setVisibility(View.GONE);pageScroll.scrollTo(0,0);selectedApp=app;
        content.removeAllViews();downloadLabel=null;downloadProgress=null;downloadActions=null;downloadStatusView=null;installWaitPanel=null;
        String name=app.optString("name","App"),desc=app.optString("description",""),version=app.optString("version",""),pkg=app.optString("packageName",""),url=app.optString("apkUrl","");long size=app.optLong("sizeBytes",0);if("com.lge.ExaltWaze".equals(pkg))desc=(desc==null?"":desc)+(desc==null||desc.trim().isEmpty()?"":"\n\n")+"No mouse needed. Waze remote-control shortcuts: Press # to show numbers 1–9 beside the first nine pressable buttons. Press the matching number to activate it; press 0 for the next group of nine. Press * to hide the numbers.";
        boolean smartList="smartlist".equalsIgnoreCase(app.optString("id",""))||"com.android.cts.lionscribe.elist".equals(pkg);
        boolean installed=isInstalled(pkg),updateAvailable=installed&&isUpdateAvailable(pkg,version);
        File savedApk=apkFile(pkg,version);boolean downloaded=isValidDownloadedApk(savedApk,pkg,version)&&(!installed||updateAvailable);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(0,dp(1),dp(5),dp(4));
        BackGlyph back=new BackGlyph(this);back.setContentDescription("Back");back.setFocusable(true);back.setClickable(true);back.setBackground(backFocusBackground());back.setOnClickListener(v->returnToCatalog());
        top.addView(back,new LinearLayout.LayoutParams(dp(50),dp(50)));content.addView(top);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(smartList?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);panel.setGravity(smartList?Gravity.TOP:Gravity.CENTER_HORIZONTAL);panel.setPadding(dp(8),dp(8),dp(8),dp(8));panel.setBackgroundColor(BG);content.addView(panel,new LinearLayout.LayoutParams(-1,-2));
        ImageView icon=new ImageView(this);if(detailsHost&&Build.VERSION.SDK_INT>=21)icon.setTransitionName("yw-app-icon");icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconLp=smartList?new LinearLayout.LayoutParams(dp(64),dp(64)):new LinearLayout.LayoutParams(dp(76),dp(76));if(smartList)iconLp.rightMargin=dp(10);else iconLp.gravity=Gravity.CENTER_HORIZONTAL;panel.addView(icon,iconLp);loadCatalogIcon(icon,app.optString("iconUrl",""),url,pkg,version);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setGravity(smartList?Gravity.TOP:Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams infoLp=smartList?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2);if(!smartList)infoLp.topMargin=dp(7);panel.addView(info,infoLp);
        TextView heading=text(name,smartList?20:18,INK,true);heading.setMaxLines(2);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);if(!smartList)heading.setGravity(Gravity.CENTER);info.addView(heading);
        String installState=prefs().getString("install_wait_state","");boolean installing=pkg.equals(prefs().getString("install_wait_package",""))&&("awaiting_confirmation".equals(installState)||"installing".equals(installState));TextView state=statusPill(installing?"Installing":downloaded?"Downloaded":updateAvailable?"Update available":installed?"Installed":"Available",installing?0xffe8f0ff:downloaded?0xfffff2d6:updateAvailable?0xfff0eaff:installed?0xffe8f5ef:0xffeaf1ff,installing?0xff2459a6:downloaded?0xff9a5a00:updateAvailable?0xff6941a5:installed?0xff087c58:0xff2459a6);downloadStatusView=state;
        LinearLayout.LayoutParams stateLp=new LinearLayout.LayoutParams(-2,-2);stateLp.topMargin=dp(4);if(!smartList)stateLp.gravity=Gravity.CENTER_HORIZONTAL;info.addView(state,stateLp);
        if(!desc.isEmpty()){TextView description=text(desc,12,MUTED,false);if(!smartList)description.setGravity(Gravity.CENTER);LinearLayout.LayoutParams descLp=new LinearLayout.LayoutParams(-1,-2);descLp.topMargin=dp(6);info.addView(description,descLp);}
        TextView meta=text("Version "+(version.isEmpty()?"—":version)+"  ·  "+(size>0?formatSize(size):"—"),11,MUTED,false);if(!smartList)meta.setGravity(Gravity.CENTER);LinearLayout.LayoutParams metaLp=new LinearLayout.LayoutParams(-1,-2);metaLp.topMargin=dp(6);info.addView(meta,metaLp);
        downloadLabel=text("",12,BLUE,true);if(!smartList)downloadLabel.setGravity(Gravity.CENTER);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,-2);dl.topMargin=dp(5);info.addView(downloadLabel,dl);
        downloadProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);downloadProgress.setMax(100);downloadProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));downloadProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffe8edf5));downloadProgress.setMinimumHeight(dp(11));downloadProgress.setVisibility(View.GONE);LinearLayout.LayoutParams progressLp=new LinearLayout.LayoutParams(smartList?-1:dp(250),dp(11));progressLp.topMargin=dp(6);if(!smartList)progressLp.gravity=Gravity.CENTER_HORIZONTAL;info.addView(downloadProgress,progressLp);
        downloadActions=new LinearLayout(this);downloadActions.setGravity(Gravity.CENTER);downloadActions.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams actionsLp=new LinearLayout.LayoutParams(-1,-2);actionsLp.topMargin=dp(7);info.addView(downloadActions,actionsLp);
        boolean isStoreUpdate="yw-app-store-self-update".equals(app.optString("id",""));
        boolean hasLaunchScreen=installed&&getPackageManager().getLaunchIntentForPackage(pkg)!=null;
        if(isStoreUpdate){
            if(updateAvailable){Button update=actionButton("Download update",true);update.setOnClickListener(v->download(url,name,pkg,version,null,null));addActionPair(downloadActions,update,null);}
            else{TextView current=text("This version is already up to date.",12,GREEN,true);current.setGravity(Gravity.CENTER);downloadActions.addView(current,new LinearLayout.LayoutParams(-1,dp(40)));}
        }else if(installed){
            Button remove=actionButton("Uninstall",false);remove.setOnClickListener(v->uninstall(pkg));
            if(hasLaunchScreen){Button open=actionButton("Open",true);open.setOnClickListener(v->openApp(pkg));open.setBackground(roundActionButtonBackground(BLUE));addActionPair(downloadActions,open,remove);}
            else if(!downloaded&&!updateAvailable)addActionPair(downloadActions,remove,null);
            if(downloaded){Button installUpdate=actionButton("Install update",true);installUpdate.setOnClickListener(v->installApk(savedApk,pkg,version));addActionPair(downloadActions,installUpdate,null);}
            else if(updateAvailable){Button update=actionButton("Download update",false);update.setOnClickListener(v->download(url,name,pkg,version,null,null));addActionPair(downloadActions,update,null);}
            if(!hasLaunchScreen&&(downloaded||updateAvailable))addActionPair(downloadActions,remove,null);
        }else if(downloaded){
            Button install=actionButton("Install",true);install.setOnClickListener(v->installApk(savedApk,pkg,version));
            Button remove=actionButton("Delete download",false);remove.setOnClickListener(v->deleteDownloadedApk(savedApk,pkg,version));addActionPair(downloadActions,install,remove);
        }else{
            Button download=actionButton("Download",true);download.setOnClickListener(v->download(url,name,pkg,version,null,null));addActionPair(downloadActions,download,null);
        }
        if(installing){
            downloadActions.setVisibility(View.GONE);
            ProgressBar sleekInstall=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            sleekInstall.setIndeterminate(true);
            if(Build.VERSION.SDK_INT>=21){android.content.res.ColorStateList installTint=android.content.res.ColorStateList.valueOf(BLUE);sleekInstall.setProgressTintList(installTint);sleekInstall.setIndeterminateTintList(installTint);}
            sleekInstall.setMinimumHeight(dp(4));
            LinearLayout.LayoutParams sleekLp=new LinearLayout.LayoutParams(smartList?-1:dp(230),dp(5));
            sleekLp.topMargin=dp(14);if(!smartList)sleekLp.gravity=Gravity.CENTER_HORIZONTAL;
            info.addView(sleekInstall,sleekLp);
        }
        if(smartList){
            LinearLayout warning=new LinearLayout(this);warning.setOrientation(LinearLayout.VERTICAL);warning.setPadding(dp(4),dp(5),dp(4),dp(5));warning.setBackgroundColor(Color.TRANSPARENT);
            LinearLayout.LayoutParams warningLp=new LinearLayout.LayoutParams(-1,-2);warningLp.topMargin=dp(8);warningLp.leftMargin=dp(2);warningLp.rightMargin=dp(2);content.addView(warning,warningLp);
            TextView warningHeading=text("Important Smartlist notice",13,0xff9b3b20,true);warningHeading.setGravity(Gravity.CENTER);warning.addView(warningHeading);
            TextView warningBody=text("Contacts may crash if Smartlist contact sync is enabled. Turn off Sync contacts before downloading locations. Use Smartlist Help for recovery steps.",12,INK,false);warningBody.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams warningBodyLp=new LinearLayout.LayoutParams(-1,-2);warningBodyLp.topMargin=dp(4);warning.addView(warningBody,warningBodyLp);
            Button contactsHelp=actionButton("Smartlist Help",false);contactsHelp.setTextSize(10);contactsHelp.setMaxLines(1);contactsHelp.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams helpLp=new LinearLayout.LayoutParams(-1,dp(42));helpLp.topMargin=dp(6);warning.addView(contactsHelp,helpLp);contactsHelp.setOnClickListener(v->showContactsHelpPage());
        }
        if(downloading.contains(pkg))restoreActiveDownload(pkg,version);
        syncInstallWaitState();focusFirst();
    }
    private void showContactsHelpPage(){
        currentScreen="contacts_help";mainHeader.setVisibility(View.GONE);root.setBackgroundColor(BG);pageScroll.setBackgroundColor(BG);content.setBackgroundColor(BG);content.removeAllViews();pageScroll.scrollTo(0,0);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(0,dp(2),0,dp(8));
        BackGlyph back=new BackGlyph(this);back.setContentDescription("Back");back.setFocusable(true);back.setClickable(true);back.setBackground(backFocusBackground());back.setOnClickListener(v->returnFromContactsHelp());top.addView(back,new LinearLayout.LayoutParams(dp(50),dp(50)));
        TextView eyebrow=text("SMARTLIST HELP",13,BLUE,true);eyebrow.setLetterSpacing(0.08f);LinearLayout.LayoutParams eyebrowLp=new LinearLayout.LayoutParams(0,-2,1);eyebrowLp.leftMargin=dp(8);top.addView(eyebrow,eyebrowLp);content.addView(top);
        
        TextView intro=text("If contacts added by Smartlist are causing the Contacts app to crash, follow these steps carefully.",14,MUTED,false);LinearLayout.LayoutParams introLp=new LinearLayout.LayoutParams(-1,-2);introLp.topMargin=dp(8);content.addView(intro,introLp);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setBackgroundColor(Color.TRANSPARENT);LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);cardLp.topMargin=dp(10);content.addView(card,cardLp);
        String[] steps={"Export your contacts first, and make sure the export completed successfully.","Delete the contacts from the device.","Import the contacts again from the export you just made."};
        for(int i=0;i<steps.length;i++){LinearLayout item=new LinearLayout(this);item.setGravity(Gravity.TOP);item.setOrientation(LinearLayout.HORIZONTAL);TextView number=statusPill(String.valueOf(i+1),0xffeaf1ff,0xff2459a6);LinearLayout.LayoutParams numberLp=new LinearLayout.LayoutParams(dp(28),dp(28));numberLp.rightMargin=dp(10);item.addView(number,numberLp);TextView detail=text(steps[i],13,INK,false);item.addView(detail,new LinearLayout.LayoutParams(0,-2,1));card.addView(item);if(i<steps.length-1){View divider=new View(this);divider.setBackgroundColor(EDGE);LinearLayout.LayoutParams dividerLp=new LinearLayout.LayoutParams(-1,dp(1));dividerLp.topMargin=dp(7);dividerLp.bottomMargin=dp(7);card.addView(divider);}}
        TextView note=text("After importing, open Contacts again. If it still crashes, check that Sync contacts remains turned off in Smartlist settings before trying again.",13,INK,false);note.setPadding(dp(10),dp(10),dp(10),dp(10));note.setBackground(shape(PANEL));LinearLayout.LayoutParams noteLp=new LinearLayout.LayoutParams(-1,-2);noteLp.topMargin=dp(10);card.addView(note,noteLp);
        focusFirst();
    }
    private void returnFromContactsHelp(){if(selectedApp!=null)showAppPage(selectedApp);else returnToCatalog();}
    private void addActionPair(LinearLayout parent,Button first,Button second){
        int height=dp(44);
        if(first!=null){first.setTextSize(11);first.setMaxLines(1);first.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,height,1f);if(parent.getChildCount()>0)lp.leftMargin=dp(3);if(second!=null)lp.rightMargin=dp(3);parent.addView(first,lp);}
        if(second!=null){second.setTextSize(11);second.setMaxLines(1);second.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,height,1f);lp.leftMargin=dp(3);parent.addView(second,lp);}
    }
    private String downloadProgressKey(String pkg,String version){return "download_"+urlSafe(pkg)+"_"+urlSafe(version);}
    private void restoreActiveDownload(String pkg,String version){
        String key=pkg;String prefKey=downloadProgressKey(pkg,version);
        long bytes=prefs().getLong(prefKey+"_bytes",0),total=prefs().getLong(prefKey+"_total",-1);
        downloadLabel.setText(total>0?(int)Math.min(100,bytes*100/total)+"%  ·  "+formatSize(bytes):formatSize(bytes));
        downloadProgress.setIndeterminate(total<=0);if(total>0)downloadProgress.setProgress((int)Math.min(100,bytes*100/total));downloadProgress.setVisibility(View.VISIBLE);
        downloadActions.removeAllViews();Button cancel=actionButton("Cancel",false);cancel.setOnClickListener(v->{cancelled.add(key);HttpURLConnection active=activeConnections.get(key);if(active!=null)active.disconnect();cancel.setEnabled(false);cancel.setText("Cancelling…");});addActionPair(downloadActions,cancel,null);
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
            iconExecutor.execute(()->{HttpsURLConnection connection=null;try{connection=(HttpsURLConnection)new URL(iconUrl).openConnection();LegacyTlsCompat.configure(this,connection);connection.setConnectTimeout(10000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(true);connection.setUseCaches(false);try(InputStream input=connection.getInputStream()){Bitmap bitmap=BitmapFactory.decodeStream(input);if(bitmap!=null){saveCatalogIcon(bitmap,pkg,version);runOnUiThread(()->{if(pkg.equals(view.getTag()))view.setImageBitmap(bitmap);});}}}catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}});return;
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
    private void refreshCatalogHost(String pkg,String version){try{MainActivity host=catalogHostRef==null?null:catalogHostRef.get();if(host!=null&&host.catalogApps!=null)host.runOnUiThread(()->{if(host.catalogApps!=null&&"catalog".equals(host.currentScreen)){synchronized(host.downloadedStateCache){host.downloadedStateCache.remove(pkg+"|"+version);}host.refreshOneCatalogStatus(pkg,version);}});}catch(Exception ignored){}}
    private void refreshOneCatalogStatus(String pkg,String version){
        TextView state=catalogStatusViews.get(pkg);View row=catalogRowViews.get(pkg);if(state==null||row==null)return;
        String installState=prefs().getString("install_wait_state","");boolean installing=pkg.equals(prefs().getString("install_wait_package",""))&&("awaiting_confirmation".equals(installState)||"installing".equals(installState));
        boolean active=downloading.contains(pkg)||prefs().getBoolean(downloadProgressKey(pkg,version)+"_active",false);
        if(active){String key=downloadProgressKey(pkg,version);long bytes=prefs().getLong(key+"_bytes",0),total=prefs().getLong(key+"_total",-1);String label=total>0?"Downloading "+(int)Math.min(100,bytes*100/total)+"%":"Downloading";applyStatus(state,label,0xffe8f0ff,0xff2459a6);row.setContentDescription(pkg+", downloading");return;}
        boolean installed=isInstalled(pkg),update=installed&&isUpdateAvailable(pkg,version),downloaded=hasDownloadedApk(pkg,version)&&(!installed||update);
        String label=installing?"Installing":active?"Downloading":downloaded?"Downloaded":update?"Update available":installed?"Installed":"Available";
        if(active){String key=downloadProgressKey(pkg,version);long bytes=prefs().getLong(key+"_bytes",0),total=prefs().getLong(key+"_total",-1);label=total>0?"Downloading "+(int)Math.min(100,bytes*100/total)+"%":"Downloading";}
        applyStatus(state,label,(active||installing)?0xffe8f0ff:downloaded?0xfffff2d6:update?0xfff0eaff:installed?0xffe8f5ef:0xffeaf1ff,(active||installing)?0xff2459a6:downloaded?0xff9a5a00:update?0xff6941a5:installed?0xff087c58:0xff2459a6);
        row.setContentDescription(pkg+(installing?", installing":active?", downloading":downloaded?", downloaded":update?", update available":installed?", installed":", available"));
    }
    private void updateCatalogEmptyState(){if(catalogEmptyView==null)return;boolean any=false;for(View row:catalogRowViews.values())if(row.getVisibility()==View.VISIBLE){any=true;break;}catalogEmptyView.setVisibility(any?View.GONE:View.VISIBLE);}
    private void refreshCatalogPreferencesInPlace(){if(catalogApps==null||catalogRowViews.isEmpty())return;boolean showInstalled=prefs().getBoolean("show_installed",true),showDescriptions=prefs().getBoolean("show_descriptions",true);for(int i=0;i<catalogApps.length();i++){JSONObject app=catalogApps.optJSONObject(i);if(app==null)continue;String pkg=app.optString("packageName","");View row=catalogRowViews.get(pkg);if(row!=null)row.setVisibility(isInstalled(pkg)&&!showInstalled?View.GONE:View.VISIBLE);TextView description=catalogDescriptionViews.get(pkg);if(description!=null)description.setVisibility(showDescriptions?View.VISIBLE:View.GONE);}updateCatalogEmptyState();refreshVisibleCatalogStates();}
    private void restoreCatalogFocusNow(){if(!"app".equals(catalogFocusTarget)){if("refresh".equals(catalogFocusTarget)&&refreshFocusView!=null){refreshFocusView.requestFocus();return;}if("settings".equals(catalogFocusTarget)&&settingsFocusView!=null){settingsFocusView.requestFocus();return;}}View row=catalogRowViews.get(catalogFocusedPackage);if(row!=null&&row.getVisibility()!=View.VISIBLE)row=null;if(row==null&&!catalogRowViews.isEmpty()){for(View candidate:catalogRowViews.values())if(candidate.getVisibility()==View.VISIBLE){row=candidate;break;}}if(row!=null&&row.isFocusable()){row.requestFocus();return;}if(catalogFocusedPackage==null)focusFirst();}
    private void restoreCatalogFocusDelayed(long delay){if(pageScroll==null)return;pageScroll.scrollTo(0,catalogScrollY);restoreCatalogFocusNow();}
    private void focusFirst(){if(content==null)return;content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);content.post(()->{if(content==null)return;View first=findButtonFocusable(content);if(first==null)first=findFocusable(content);if(first!=null){first.setFocusable(true);first.requestFocus();}});}
    private View findButtonFocusable(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(v instanceof Button&&v.isFocusable()&&v.getVisibility()==View.VISIBLE)return v;if(v instanceof ViewGroup){View f=findButtonFocusable((ViewGroup)v);if(f!=null)return f;}}return null;}
    private View findFocusable(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(v.isFocusable()&&v.getVisibility()==View.VISIBLE&&!(v instanceof EditText))return v;if(v instanceof ViewGroup){View f=findFocusable((ViewGroup)v);if(f!=null)return f;}}return null;}
    private void startBulkDownloadFromSettings(){MainActivity host=catalogHostRef==null?null:catalogHostRef.get();if(host==null)host=this;host.downloadAllApps();}
    private void downloadAllApps(){
        if(downloadAllActive){toast("Download all is already running.");return;}
        if(catalogApps==null||catalogApps.length()==0){toast("The app list is not loaded yet.");return;}
        if(!hasNetwork()){toast("No internet connection.");return;}
        downloadAllQueue.clear();downloadAllFailures=0;
        for(int i=0;i<catalogApps.length();i++){JSONObject app=catalogApps.optJSONObject(i);if(app==null)continue;String pkg=app.optString("packageName",""),version=app.optString("version",""),url=app.optString("apkUrl","");if(pkg.isEmpty()||!url.startsWith("https://"))continue;boolean installed=isInstalled(pkg),update=installed&&isUpdateAvailable(pkg,version);if(installed&&!update)continue;if(hasDownloadedApk(pkg,version)||downloading.contains(pkg))continue;downloadAllQueue.add(app);}
        if(downloadAllQueue.isEmpty()){toast("All available apps are already downloaded or installed.");return;}
        downloadAllActive=true;if(downloadAppsButton!=null){downloadAppsButton.setEnabled(false);downloadAppsButton.setText("Downloading apps…");}toast("Downloading available apps.");startNextCatalogDownload();
    }
    private void startNextCatalogDownload(){
        JSONObject app=downloadAllQueue.poll();
        if(app==null){
            downloadAllActive=false;
            if(downloadAppsButton!=null){downloadAppsButton.setEnabled(true);downloadAppsButton.setText("Download all");}
            refreshVisibleCatalogStates();
            toast(downloadAllFailures==0?"All available apps downloaded successfully.":"Bulk download finished with "+downloadAllFailures+" failed item(s).");
            return;
        }
        String pkg=app.optString("packageName",""),version=app.optString("version",""),url=app.optString("apkUrl","");
        String key=pkg;String prefKey=downloadProgressKey(pkg,version);
        if(!downloading.add(key)){startNextCatalogDownload();return;}
        cancelled.remove(key);
        prefs().edit().putBoolean(prefKey+"_active",true).putLong(prefKey+"_bytes",0).putLong(prefKey+"_total",-1).apply();
        refreshOneCatalogStatus(pkg,version);
        new Thread(()->{
            HttpURLConnection connection=null;File file=apkFile(pkg,version),partial=new File(file.getAbsolutePath()+".part");boolean ok=false;
            try{
                connection=(HttpURLConnection)new URL(url).openConnection();activeConnections.put(key,connection);
                if(connection instanceof HttpsURLConnection)LegacyTlsCompat.configure(this,(HttpsURLConnection)connection);
                connection.setConnectTimeout(30000);connection.setReadTimeout(120000);
                connection.setRequestProperty("Authorization","Bearer "+token);connection.setRequestProperty("X-Device-Id",deviceId);
                int status=connection.getResponseCode();if(status<200||status>=300)throw new IOException("HTTP "+status);
                long total=connection.getContentLength(),done=0,lastPersist=0,lastUi=0;
                try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(partial)){
                    byte[] buffer=new byte[262144];int count;
                    while((count=input.read(buffer))!=-1){
                        output.write(buffer,0,count);done+=count;long now=System.currentTimeMillis();
                        if(now-lastPersist>=1000||(total>0&&done==total)){prefs().edit().putLong(prefKey+"_bytes",done).putLong(prefKey+"_total",total).apply();lastPersist=now;}
                        if(now-lastUi>=500||(total>0&&done==total)){lastUi=now;runOnUiThread(()->refreshCatalogHost(pkg,version));}
                    }
                    output.flush();
                }
                prefs().edit().putLong(prefKey+"_bytes",done).putLong(prefKey+"_total",total).apply();
                if(partial.length()<1024||(total>0&&done!=total))throw new IOException("Incomplete download");
                int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
                PackageInfo archive=getPackageManager().getPackageArchiveInfo(partial.getAbsolutePath(),flags);
                if(archive==null||!pkg.equals(archive.packageName)||(!version.isEmpty()&&!version.equals(archive.versionName)))throw new IOException("APK metadata mismatch");
                if(isInstalled(pkg)){PackageInfo installed=getPackageManager().getPackageInfo(pkg,flags);if(!sameSigningCertificates(installed,archive))throw new IOException("Signing certificate mismatch");}
                if(file.exists()&&!file.delete())throw new IOException("Cannot replace cached APK");
                if(!partial.renameTo(file))throw new IOException("Cannot save downloaded APK");
                synchronized(downloadedStateCache){downloadedStateCache.put(pkg+"|"+version,Boolean.TRUE);}
                DiagnosticLog.record(this,"Bulk download completed: "+pkg+" bytes="+file.length());ok=true;
            }catch(Exception error){
                partial.delete();android.util.Log.e("YWAppStore","Download all failed for "+pkg,error);
                DiagnosticLog.record(this,"Bulk download failed for "+pkg+": "+android.util.Log.getStackTraceString(error));
            }finally{
                if(connection!=null)connection.disconnect();activeConnections.remove(key);
                prefs().edit().putBoolean(prefKey+"_active",false).apply();
                final boolean succeeded=ok;
                runOnUiThread(()->{downloading.remove(key);refreshCatalogHost(pkg,version);if(!succeeded)downloadAllFailures++;startNextCatalogDownload();});
            }
        },"YW-download-all-"+urlSafe(pkg)).start();
    }
    private void download(String url,String name,String pkg,String version,LinearLayout card,LinearLayout actions){
        DiagnosticLog.record(this,"Download requested: "+pkg+" version="+version);
        String key=pkg.isEmpty()?name:pkg;
        if(!url.startsWith("https://")){toast("This app isn't available to download right now.");return;}
        if(!hasNetwork()){toast("No internet connection.");return;}
        if(!downloading.add(key)){toast("This download is already in progress.");return;}
        cancelled.remove(key);
        if(downloadLabel==null||downloadProgress==null||downloadActions==null){if(selectedApp!=null)showAppPage(selectedApp);else{downloading.remove(key);return;}}
        prefs().edit().putBoolean(downloadProgressKey(pkg,version)+"_active",true).putLong(downloadProgressKey(pkg,version)+"_bytes",0).putLong(downloadProgressKey(pkg,version)+"_total",-1).apply();
        downloadLabel.setText("0%  ·  0 KB");downloadProgress.setVisibility(View.VISIBLE);downloadProgress.setIndeterminate(true);
        downloadActions.removeAllViews();Button cancel=actionButton("Cancel",false);
        cancel.setOnClickListener(v->{cancelled.add(key);HttpURLConnection active=activeConnections.get(key);if(active!=null)active.disconnect();cancel.setEnabled(false);cancel.setText("Cancelling…");});
        addActionPair(downloadActions,cancel,null);refreshCatalogHost();
        new Thread(()->{
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            HttpURLConnection connection=null;File file=apkFile(pkg.isEmpty()?name:pkg,version);File partial=new File(file.getAbsolutePath()+".part");
            try{
                connection=(HttpURLConnection)new URL(url).openConnection();activeConnections.put(key,connection);
                if(connection instanceof HttpsURLConnection)LegacyTlsCompat.configure(this,(HttpsURLConnection)connection);
                connection.setConnectTimeout(30000);connection.setReadTimeout(120000);connection.setUseCaches(false);connection.setRequestProperty("Accept-Encoding","identity");connection.setRequestProperty("Authorization","Bearer "+token);connection.setRequestProperty("X-Device-Id",deviceId);
                if(cancelled.contains(key))throw new InterruptedIOException("Download canceled");
                int status=connection.getResponseCode();if(status<200||status>=300)throw new IOException("Download returned HTTP "+status);
                long total=connection.getContentLength(),done=0,lastPersist=0,lastUi=0;
                try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(partial)){
                    byte[] buffer=new byte[262144];int count;
                    while((count=input.read(buffer))!=-1){
                        if(cancelled.contains(key))throw new InterruptedIOException("Download canceled by user");
                        output.write(buffer,0,count);done+=count;long now=System.currentTimeMillis();
                        final long current=done;final int percent=total>0?(int)Math.min(100,done*100/total):-1;
                        if(now-lastPersist>=1000||(total>0&&done==total)){prefs().edit().putLong(downloadProgressKey(pkg,version)+"_bytes",current).putLong(downloadProgressKey(pkg,version)+"_total",total).apply();lastPersist=now;}
                        if(now-lastUi>=1000||(total>0&&done==total)){lastUi=now;runOnUiThread(()->{if(isCurrentDownload(key)){downloadProgress.setIndeterminate(false);if(percent>=0)downloadProgress.setProgress(percent);downloadLabel.setText(percent>=0?percent+"%  ·  "+formatSize(current):formatSize(current));}refreshCatalogHost(pkg,version);});}
                    }
                    output.flush();
                }
                prefs().edit().putLong(downloadProgressKey(pkg,version)+"_bytes",done).putLong(downloadProgressKey(pkg,version)+"_total",total).apply();
                if(cancelled.contains(key))throw new InterruptedIOException("Download canceled by user");
                if(partial.length()<1024)throw new IOException("Downloaded file is incomplete");
                if(total>0&&done!=total)throw new IOException("Download ended before all bytes arrived");
                int signatureFlags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
                PackageInfo archive=getPackageManager().getPackageArchiveInfo(partial.getAbsolutePath(),signatureFlags);
                if(archive==null||!pkg.equals(archive.packageName))throw new IOException("The downloaded APK does not match the catalog package.");
                if(version!=null&&!version.isEmpty()&&!version.equals(archive.versionName))throw new IOException("Downloaded version "+archive.versionName+" does not match catalog version "+version+".");
                if(isInstalled(pkg)){PackageInfo installed=getPackageManager().getPackageInfo(pkg,signatureFlags);if(!sameSigningCertificates(installed,archive))throw new IOException("The APK is signed with a different certificate from the installed app.");}
                if(file.exists()&&!file.delete())throw new IOException("Could not replace the previous APK cache");
                if(!partial.renameTo(file))throw new IOException("Could not finalize the downloaded APK");
                synchronized(downloadedStateCache){downloadedStateCache.put(pkg+"|"+version,Boolean.TRUE);}
                DiagnosticLog.record(this,"Download completed: "+pkg+" bytes="+file.length());
                prefs().edit().putBoolean(downloadProgressKey(pkg,version)+"_active",false).apply();
                runOnUiThread(()->{downloading.remove(key);cancelled.remove(key);if(isCurrentDownload(key))showInstallPrompt(file,pkg,version);refreshCatalogHost(pkg,version);});
            }catch(Exception error){
                partial.delete();boolean wasCancelled=cancelled.contains(key);
                prefs().edit().putBoolean(downloadProgressKey(pkg,version)+"_active",false).apply();
                android.util.Log.e("YWAppStore","Download failed for "+pkg,error);DiagnosticLog.record(this,"Download failed for "+pkg+": "+android.util.Log.getStackTraceString(error));
                runOnUiThread(()->{
                    downloading.remove(key);cancelled.remove(key);refreshCatalogHost();
                    if(isCurrentDownload(key)){downloadLabel.setText(wasCancelled?"Download canceled":"Download failed — "+(error.getMessage()==null?"try again":error.getMessage()));downloadProgress.setVisibility(View.GONE);downloadActions.removeAllViews();Button retry=actionButton(wasCancelled?"Download again":"Retry",true);retry.setOnClickListener(v->download(url,name,pkg,version,null,null));addActionPair(downloadActions,retry,null);}
                    if(!wasCancelled)toast(hasNetwork()?"Download failed: "+(error.getMessage()==null?"please try again.":error.getMessage()):"No internet connection.");
                });
            }finally{if(connection!=null)connection.disconnect();activeConnections.remove(key);}
        },"YW-download-"+urlSafe(pkg)).start();
    }
    private void deleteDownloadedApk(File file,String pkg,String version){if(file==null||!file.isFile()){toast("Downloaded file was not found.");return;}if(!file.delete()){toast("Could not delete the downloaded APK.");return;}File partial=new File(file.getAbsolutePath()+".part");if(partial.exists())partial.delete();synchronized(downloadedStateCache){downloadedStateCache.remove(pkg+"|"+version);}prefs().edit().remove(downloadProgressKey(pkg,version)+"_active").remove(downloadProgressKey(pkg,version)+"_bytes").remove(downloadProgressKey(pkg,version)+"_total").apply();if(selectedApp!=null&&pkg.equals(selectedApp.optString("packageName",""))){showAppPage(selectedApp);toast("Download deleted.");}else{refreshVisibleCatalogStates();toast("Download deleted.");}}
    private File apkFile(String pkg,String version){return new File(getFilesDir(),"yw-"+urlSafe(pkg)+"-"+urlSafe(version)+".apk");}
    private boolean isValidDownloadedApk(File file,String pkg,String version){try{if(file==null||!file.isFile()||file.length()<1024)return false;int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageInfo archive=getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),flags);return archive!=null&&pkg.equals(archive.packageName)&&(version==null||version.isEmpty()||version.equals(archive.versionName));}catch(Exception e){return false;}}
    private boolean hasDownloadedApk(String pkg,String version){String key=pkg+"|"+version;synchronized(downloadedStateCache){Boolean cached=downloadedStateCache.get(key);if(cached!=null)return cached.booleanValue();}boolean valid=isValidDownloadedApk(apkFile(pkg,version),pkg,version);synchronized(downloadedStateCache){downloadedStateCache.put(key,Boolean.valueOf(valid));}return valid;}
    private boolean isCurrentDownload(String key){return "details".equals(currentScreen)&&selectedApp!=null&&key.equals(selectedApp.optString("packageName",""))&&downloadLabel!=null&&downloadProgress!=null&&downloadActions!=null;}
    @Override public void onBackPressed(){if(detailsHost){restoreCatalogHostFocusBeforeReturn();if(Build.VERSION.SDK_INT>=21)finishAfterTransition();else finish();return;}if(settingsHost&&"settings".equals(currentScreen)){if(Build.VERSION.SDK_INT>=21)finishAfterTransition();else finish();return;}if(settingsHost&&"details".equals(currentScreen)){returnToCatalog();return;}if("settings".equals(currentScreen)){returnFromSettings();return;}if("contacts_help".equals(currentScreen)){returnFromContactsHelp();return;}if("complete".equals(currentScreen)){if(selectedApp!=null)showAppPage(selectedApp);else returnToCatalog();return;}if("details".equals(currentScreen)){returnToCatalog();return;}super.onBackPressed();}
    private void restoreCatalogHostFocusBeforeReturn(){try{MainActivity host=catalogHostRef==null?null:catalogHostRef.get();if(host==null||host.catalogRowViews==null)return;host.catalogFocusTarget="app";View row=host.catalogRowViews.get(host.catalogFocusedPackage);if(row!=null&&row.isFocusable())row.requestFocus();}catch(Exception ignored){}}
    private String urlSafe(String s){return s.replaceAll("[^A-Za-z0-9._-]","_");}
    private void showInstallWait(String title,String message){
        if(isFinishing())return;
        if(installWaitDialog==null){ProgressBar spinner=new ProgressBar(this);LinearLayout wrap=new LinearLayout(this);wrap.setGravity(Gravity.CENTER);wrap.setPadding(dp(12),dp(8),dp(12),dp(8));wrap.addView(spinner,new LinearLayout.LayoutParams(dp(34),dp(34)));installWaitDialog=new AlertDialog.Builder(this).setView(wrap).setCancelable(false).create();}
        if(installWaitDialog.isShowing())installWaitDialog.dismiss();
        installWaitDialog.setTitle(title==null?"Please wait":title);installWaitDialog.setMessage(message==null?"Please wait…":message);installWaitDialog.show();
    }
    private void dismissInstallWait(){if(installWaitDialog!=null&&installWaitDialog.isShowing())installWaitDialog.dismiss();if(installWaitPanel!=null)installWaitPanel.setVisibility(View.GONE);}
    private void syncInstallWaitState(){String state=prefs().getString("install_wait_state","");if("preparing".equals(state))showInstallWait("Preparing installer","Checking the downloaded app and preparing Android installation…");else dismissInstallWait();}
    private void cancelInstallWait(){
        int sessionId=prefs().getInt("install_wait_session_id",-1);
        if(sessionId>=0){try{getPackageManager().getPackageInstaller().abandonSession(sessionId);}catch(Exception ignored){}}
        prefs().edit().putBoolean("install_wait_cancel_requested",true).remove("install_wait_state").remove("install_wait_package").remove("install_wait_version").remove("install_wait_session_id").apply();
        dismissInstallWait();
        toast("Installation wait canceled. If Android's confirmation is open, cancel it there too.");
    }
    private void buildInstallWaitPanel(LinearLayout parent){
        installWaitPanel=new LinearLayout(this);installWaitPanel.setOrientation(LinearLayout.VERTICAL);
        installWaitPanel.setPadding(dp(12),dp(10),dp(12),dp(8));installWaitPanel.setBackground(shape(PANEL));
        LinearLayout.LayoutParams waitLp=new LinearLayout.LayoutParams(-1,-2);waitLp.topMargin=dp(8);parent.addView(installWaitPanel,waitLp);
        LinearLayout statusLine=new LinearLayout(this);statusLine.setGravity(Gravity.CENTER_VERTICAL);
        installWaitPanel.addView(statusLine,new LinearLayout.LayoutParams(-1,-2));
        installWaitSpinner=new ProgressBar(this);installWaitSpinner.setIndeterminate(true);
        statusLine.addView(installWaitSpinner,new LinearLayout.LayoutParams(dp(28),dp(28)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp=new LinearLayout.LayoutParams(0,-2,1);copyLp.leftMargin=dp(10);statusLine.addView(copy,copyLp);
        installWaitTitle=text("Preparing installer",14,INK,true);copy.addView(installWaitTitle);
        installWaitMessage=text("Please wait…",12,MUTED,false);LinearLayout.LayoutParams msgLp=new LinearLayout.LayoutParams(-1,-2);msgLp.topMargin=dp(2);copy.addView(installWaitMessage,msgLp);
        installWaitCancelButton=actionButton("Cancel",false);LinearLayout.LayoutParams cancelLp=new LinearLayout.LayoutParams(dp(112),dp(40));cancelLp.gravity=Gravity.RIGHT;cancelLp.topMargin=dp(7);installWaitPanel.addView(installWaitCancelButton,cancelLp);
        installWaitCancelButton.setOnClickListener(v->cancelInstallWait());installWaitPanel.setVisibility(View.GONE);
    }
    private void installApk(File file,String pkg,String version){
        DiagnosticLog.record(this,"Install requested: "+pkg+" version="+version);
        if(file==null||!file.isFile()){toast("Downloaded APK was not found.");return;}
        String existing=prefs().getString("install_wait_state","");
        if(!existing.isEmpty()){toast("An installation is already in progress.");return;}
        prefs().edit().putString("install_wait_state","preparing").putString("install_wait_package",pkg)
                .putString("install_wait_version",version==null?"":version).putInt("install_wait_session_id",-1).putBoolean("install_wait_cancel_requested",false).apply();
        refreshCatalogHost(pkg,version);
        showInstallWait("Preparing installer","Checking the downloaded app and preparing Android installation…");
        new Thread(()->{
            PackageInstaller.Session session=null;int sessionId=-1;
            try{
                int signatureFlags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
                PackageInfo archive=getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),signatureFlags);
                if(file.length()<1024||archive==null||!pkg.equals(archive.packageName))throw new IOException("Downloaded APK is invalid or belongs to another app.");
                if(version!=null&&!version.isEmpty()&&!version.equals(archive.versionName))throw new IOException("Downloaded APK version does not match the selected catalog version.");
                if(isInstalled(pkg)){PackageInfo installed=getPackageManager().getPackageInfo(pkg,signatureFlags);if(!sameSigningCertificates(installed,archive))throw new IOException("APK signing certificate does not match the installed app.");}
                if(prefs().getBoolean("install_wait_cancel_requested",false))throw new InterruptedIOException("Installation canceled by user");
                runOnUiThread(()->showInstallWait("Sending app to Android","Please wait while Android prepares the installation…"));
                PackageInstaller installer=getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(pkg);params.setSize(file.length());sessionId=installer.createSession(params);
                prefs().edit().putInt("install_wait_session_id",sessionId).apply();
                session=installer.openSession(sessionId);
                try(InputStream in=new FileInputStream(file);OutputStream out=session.openWrite("base.apk",0,-1)){
                    byte[] buffer=new byte[65536];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);session.fsync(out);
                }
                if(prefs().getBoolean("install_wait_cancel_requested",false))throw new InterruptedIOException("Installation canceled by user");
                Intent callback=new Intent(this,InstallStatusReceiver.class);callback.setAction("com.lge.lginstallservies.INSTALL_STATUS."+sessionId);
                callback.putExtra("yw_package_name",pkg);callback.putExtra("yw_version",version);
                int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
                PendingIntent pending=PendingIntent.getBroadcast(this,sessionId,callback,flags);
                prefs().edit().putString("install_wait_state","awaiting_confirmation").apply();
                runOnUiThread(()->dismissInstallWait());
                session.commit(pending.getIntentSender());session.close();session=null;
            }catch(Exception error){
                if(session!=null)try{session.abandon();}catch(Exception ignored){}
                if(sessionId>=0)try{getPackageManager().getPackageInstaller().abandonSession(sessionId);}catch(Exception ignored){}
                boolean wasCanceled=prefs().getBoolean("install_wait_cancel_requested",false)||error instanceof InterruptedIOException;
                prefs().edit().remove("install_wait_state").remove("install_wait_package").remove("install_wait_version").remove("install_wait_session_id").putBoolean("install_wait_cancel_requested",false).apply();
                runOnUiThread(()->{dismissInstallWait();if(!wasCanceled)toast("Couldn't start the installation: "+(error.getMessage()==null?"Please try again.":error.getMessage()));});
            }
        },"YW-prepare-install-"+urlSafe(pkg)).start();
    }
    private void openApp(String p){try{Intent i=getPackageManager().getLaunchIntentForPackage(p);if(i!=null)startActivity(i);else toast("No launch screen is available.");}catch(Exception e){toast("Could not open app.");}}
    private void uninstall(String p){try{startActivity(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+p)));}catch(Exception e){toast("Could not open uninstall screen.");}}
}
