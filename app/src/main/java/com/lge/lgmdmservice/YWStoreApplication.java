package com.lge.lgmdmservice;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/** Captures activity lifecycle and uncaught failures in the persistent diagnostic log. */
public final class YWStoreApplication extends Application implements Application.ActivityLifecycleCallbacks {
    @Override public void onCreate(){
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
        DiagnosticLog.record(this,"Application started");
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            DiagnosticLog.record(this,"FATAL uncaught exception on "+thread.getName()+": "+android.util.Log.getStackTraceString(error));
            if(previous!=null)previous.uncaughtException(thread,error);
        });
    }
    @Override public void onActivityCreated(Activity activity,Bundle state){DiagnosticLog.record(this,"Activity created: "+activity.getClass().getSimpleName());}
    @Override public void onActivityStarted(Activity activity){DiagnosticLog.record(this,"Activity started: "+activity.getClass().getSimpleName());}
    @Override public void onActivityResumed(Activity activity){DiagnosticLog.record(this,"Activity resumed: "+activity.getClass().getSimpleName());}
    @Override public void onActivityPaused(Activity activity){DiagnosticLog.record(this,"Activity paused: "+activity.getClass().getSimpleName());}
    @Override public void onActivityStopped(Activity activity){DiagnosticLog.record(this,"Activity stopped: "+activity.getClass().getSimpleName());}
    @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){DiagnosticLog.record(this,"Activity state saved: "+activity.getClass().getSimpleName());}
    @Override public void onActivityDestroyed(Activity activity){DiagnosticLog.record(this,"Activity destroyed: "+activity.getClass().getSimpleName());}
}
