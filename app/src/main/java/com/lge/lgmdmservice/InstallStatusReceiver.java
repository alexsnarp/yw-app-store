package com.lge.lgmdmservice;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;


public class InstallStatusReceiver extends BroadcastReceiver {
    private static final String CHANNEL="app_install_results";
    @Override public void onReceive(Context context,Intent intent){
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){Intent confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT);if(confirm!=null){confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(confirm);}return;}
        String pkg=intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);if(pkg==null)pkg=intent.getStringExtra("yw_package_name");
        String version=intent.getStringExtra("yw_version");
        if(status==PackageInstaller.STATUS_SUCCESS){
            deleteInstalledApk(context,pkg,version);
            show(context,pkg);StoreReporter.report(context,pkg,version==null?"":version,"installed");
        }else{
            showFailure(context,pkg);
            StoreReporter.report(context,pkg,version==null?"":version,"failed");
        }
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
        if(context.getPackageName().equals(pkg)){
        }
    }
    private void showFailure(Context c,String pkg){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"App installation",NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_yw_notification).setContentTitle("Installation failed").setContentText("The app could not be installed. You can try again from YW App Store.").setAutoCancel(true);
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