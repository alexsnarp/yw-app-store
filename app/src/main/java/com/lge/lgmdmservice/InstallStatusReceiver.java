package com.lge.lgmdmservice;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;


public class InstallStatusReceiver extends BroadcastReceiver {
    private static final String CHANNEL="app_install_results";
    @Override public void onReceive(Context context,Intent intent){
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        String pkg=intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);if(pkg==null)pkg=intent.getStringExtra("yw_package_name");
        String version=intent.getStringExtra("yw_version");
        android.content.SharedPreferences prefs=context.getSharedPreferences("yw_store",Context.MODE_PRIVATE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            DiagnosticLog.record(context,"Android requested install confirmation for "+pkg);
            prefs.edit().putString("install_wait_state","awaiting_confirmation").putString("install_wait_package",pkg==null?"":pkg).putString("install_wait_version",version==null?"":version).apply();
            Intent confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirm==null){
                prefs.edit().remove("install_wait_state").remove("install_wait_package").remove("install_wait_version").remove("install_wait_session_id").apply();
                broadcastStatus(context,PackageInstaller.STATUS_FAILURE,pkg,"Android did not provide an installation confirmation screen.");
                DiagnosticLog.record(context,"Install confirmation intent missing for "+pkg);
                return;
            }
            broadcastStatus(context,status,pkg,intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(confirm);return;
        }
        prefs.edit().remove("install_wait_state").remove("install_wait_package").remove("install_wait_version").remove("install_wait_session_id").apply();
        String message=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if(status==PackageInstaller.STATUS_SUCCESS){
            DiagnosticLog.record(context,"PackageInstaller reported success for "+pkg);
            deleteInstalledApk(context,pkg,version);
            show(context,pkg);StoreReporter.report(context,pkg,version==null?"":version,"installed");
        }else if(status==PackageInstaller.STATUS_FAILURE_ABORTED){
            DiagnosticLog.record(context,"PackageInstaller installation canceled by user for "+pkg);
            StoreReporter.report(context,pkg,version==null?"":version,"canceled");
        }else{
            DiagnosticLog.record(context,"PackageInstaller failed for "+pkg+" status="+status+" message="+message);
            showFailure(context,pkg);
            StoreReporter.report(context,pkg,version==null?"":version,"failed");
        }
        broadcastStatus(context,status,pkg,message);
    }
    private void broadcastStatus(Context context,int status,String pkg,String message){
        Intent update=new Intent("com.lge.lginstallservies.INSTALL_STATUS_CHANGED");
        update.setPackage(context.getPackageName());
        update.putExtra(PackageInstaller.EXTRA_STATUS,status);
        update.putExtra("yw_package_name",pkg);
        update.putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE,message);
        context.sendBroadcast(update);
    }
    private String safe(String value){return value==null?"":value.replaceAll("[^A-Za-z0-9._-]","_");}
    private void deleteInstalledApk(Context context,String pkg,String version){
        if(pkg==null)return;
        if(version!=null&&!version.isEmpty()){
            java.io.File downloaded=new java.io.File(context.getFilesDir(),"yw-"+safe(pkg)+"-"+safe(version)+".apk");
            if(downloaded.isFile())downloaded.delete();
            java.io.File partial=new java.io.File(downloaded.getAbsolutePath()+".part");
            if(partial.isFile())partial.delete();
        }
    }
    private void showFailure(Context c,String pkg){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"App installation",NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_yw_notification).setContentTitle("Installation failed")
                .setContentText(c.getPackageName().equals(pkg)
                        ? "The YW App Store update could not be installed. You can retry from this notification."
                        : "The app could not be installed. You can try again from YW App Store.")
                .setAutoCancel(true);
        if (c.getPackageName().equals(pkg)) {
            Intent retry = new Intent(c, InstallUpdateService.class);
            retry.setAction(InstallUpdateService.ACTION_INSTALL_CLOUDFLARE_UPDATE);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent retryAction = PendingIntent.getService(c, 2406, retry, flags);
            b.addAction(android.R.drawable.ic_popup_sync, "Retry update", retryAction);
        }
        nm.notify((int)(System.currentTimeMillis()&0x7fffffff),b.build());
    }
    private android.graphics.Bitmap loadInstalledIcon(Context c,String pkg){try{android.graphics.drawable.Drawable d=c.getPackageManager().getApplicationIcon(pkg);int size=(int)(c.getResources().getDisplayMetrics().density*48+0.5f);android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);d.setBounds(0,0,size,size);d.draw(canvas);return bitmap;}catch(Exception ignored){return null;}}
    private void show(Context c,String pkg){
        String name=pkg==null?"App":pkg;
        try{name=c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception ignored){}
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"App installation",NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_status_check).setLargeIcon(loadInstalledIcon(c,pkg)).setContentTitle("App installed").setContentText(name+" was installed successfully").setAutoCancel(true);
        nm.notify((int)(System.currentTimeMillis()&0x7fffffff),b.build());
    }
}