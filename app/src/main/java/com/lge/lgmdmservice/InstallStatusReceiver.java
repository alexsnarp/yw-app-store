package com.lge.lgmdmservice;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.widget.Toast;

public class InstallStatusReceiver extends BroadcastReceiver {
    private static final String CHANNEL="app_install_results";
    @Override public void onReceive(Context context,Intent intent){
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){Intent confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT);if(confirm!=null){confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(confirm);}return;}
        String pkg=intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);if(pkg==null)pkg=intent.getStringExtra("yw_package_name");
        String version=intent.getStringExtra("yw_version");
        if(status==PackageInstaller.STATUS_SUCCESS){
            show(context,pkg);StoreReporter.report(context,pkg,version==null?"":version,"installed");
        }else{
            String detail=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(context,"Installation did not complete"+(detail==null?"":": "+detail),Toast.LENGTH_LONG).show();
            StoreReporter.report(context,pkg,version==null?"":version,"failed");
        }
    }
    private void show(Context c,String pkg){
        String name=pkg==null?"App":pkg;
        try{name=c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception ignored){}
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"App installation",NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("App installed").setContentText(name+" was installed successfully").setAutoCancel(true);
        nm.notify((int)(System.currentTimeMillis()&0x7fffffff),b.build());
    }
}