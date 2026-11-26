package com.haierlite.tv;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "HaierBoot";
    private static final String BOOT_START = "com.haierlite.tv.BOOT_START";
    private static final String RECEIVED_AT = "received_at";
    private static final int JOB_ID = 172;
    private static final long DELAY_MS = 8_000L;
    private static final long MAX_AGE_MS = 30_000L;

    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Log.i(TAG, "BOOT_COMPLETED received");
        if (!isDefaultHome(context)) {
            Log.i(TAG, "Boot launch skipped: Haier Lite TV is not the selected HOME");
            return;
        }
        PersistableBundle extras = new PersistableBundle();
        extras.putLong(RECEIVED_AT, SystemClock.elapsedRealtime());
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, LaunchJob.class))
                .setMinimumLatency(DELAY_MS)
                .setOverrideDeadline(MAX_AGE_MS)
                .setExtras(extras)
                .build();
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        Log.i(TAG, scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
                ? "One boot launch attempt scheduled" : "Could not schedule the boot launch");
    }

    private static boolean isDefaultHome(Context context) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = context.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null && resolved.activityInfo != null
                && context.getPackageName().equals(resolved.activityInfo.packageName);
    }

    static void logLaunch(Intent intent) {
        if (intent != null && BOOT_START.equals(intent.getAction())) {
            Log.i(TAG, "MainActivity received the boot launch");
        }
    }

    public static final class LaunchJob extends JobService {
        @Override public boolean onStartJob(JobParameters params) {
            long age = SystemClock.elapsedRealtime() - params.getExtras().getLong(RECEIVED_AT);
            if (age > MAX_AGE_MS || !isDefaultHome(this)) {
                Log.i(TAG, "Boot launch skipped: startup window closed or HOME changed");
                return false;
            }
            PowerManager power = getSystemService(PowerManager.class);
            AudioManager audio = getSystemService(AudioManager.class);
            // Android 11 exposes no reliable top-app signal to an ordinary launcher.
            // Avoid a foreground takeover whenever a viewer can already be using the TV.
            if (power.isInteractive() || audio.isMusicActive()) {
                Log.i(TAG, "Boot launch skipped: display is interactive or media is active");
                return false;
            }
            Intent launch = new Intent(this, MainActivity.class)
                    .setAction(BOOT_START)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(launch);
                Log.i(TAG, "Boot launch requested; Android may reject background activity starts");
            } catch (SecurityException | android.content.ActivityNotFoundException exception) {
                Log.w(TAG, "Android rejected the boot launch", exception);
            }
            return false;
        }

        @Override public boolean onStopJob(JobParameters params) {
            return false;
        }
    }
}
