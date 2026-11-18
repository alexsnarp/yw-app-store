package com.lge.lgmdmservice;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Periodic, network-constrained background check for catalog updates. */
public class UpdateCheckJobService extends JobService {
    private static final int JOB_ID = 0x5957;

    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        for (JobInfo pending : scheduler.getAllPendingJobs()) {
            if (pending.getId() == JOB_ID) return;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, UpdateCheckJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(12L * 60L * 60L * 1000L)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            UpdateChecker.checkNow(getApplicationContext());
            jobFinished(params, false);
        }, "YW-update-check").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
