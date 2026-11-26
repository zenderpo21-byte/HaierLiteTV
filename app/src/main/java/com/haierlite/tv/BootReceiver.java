package com.haierlite.tv;

import android.app.AppOpsManager;
import android.app.role.RoleManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "HaierBoot";
    private static final String BOOT_START = "com.haierlite.tv.BOOT_START";
    private static final ComponentName YANDEX_HOME = new ComponentName(
            "com.yandex.tv.home", "com.yandex.tv.home.HomeActivity");
    private static final long[] CHECK_AT_MS = {2_000, 5_000, 8_000};
    private static final long MAX_WINDOW_MS = 9_000;
    private static final long LOOKBACK_MS = 60_000;

    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Log.i(TAG, "BOOT_COMPLETED received");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.i(TAG, "Boot launch skipped: Android version lacks the required foreground checks");
            return;
        }

        Context app = context.getApplicationContext();
        PendingResult broadcast = goAsync();
        Handler handler = new Handler(Looper.getMainLooper());
        long startedAt = SystemClock.elapsedRealtime();
        long firstCheck = SystemClock.uptimeMillis();
        Log.i(TAG, "Boot check window started: checks at 2, 5 and 8 seconds");
        handler.postAtTime(new Runnable() {
            private int index;

            @Override public void run() {
                boolean continueChecks = false;
                try {
                    if (SystemClock.elapsedRealtime() - startedAt >= MAX_WINDOW_MS) {
                        Log.i(TAG, "Boot launch skipped: startup window expired");
                    } else {
                        Log.i(TAG, "Boot check " + (index + 1) + "/" + CHECK_AT_MS.length);
                        continueChecks = shouldCheckAgain(app);
                    }
                } catch (RuntimeException exception) {
                    Log.w(TAG, "Boot check failed safely", exception);
                } finally {
                    index++;
                    if (continueChecks && index < CHECK_AT_MS.length
                            && handler.postAtTime(this, firstCheck + CHECK_AT_MS[index])) {
                        Log.i(TAG, "Waiting for HOME selection or a usable foreground event");
                    } else {
                        Log.i(TAG, "Boot check window finished");
                        broadcast.finish();
                    }
                }
            }
        }, firstCheck + CHECK_AT_MS[0]);
    }

    private static boolean isDefaultHome(Context context) {
        RoleManager roles = context.getSystemService(RoleManager.class);
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
            return roles.isRoleHeld(RoleManager.ROLE_HOME);
        }
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = context.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null && resolved.activityInfo != null
                && context.getPackageName().equals(resolved.activityInfo.packageName);
    }

    private static boolean shouldCheckAgain(Context context) {
        if (!isDefaultHome(context)) {
            Log.i(TAG, "Boot launch deferred: Haier Lite TV is not the current HOME role holder");
            return true;
        }
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        if (ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
                context.getPackageName()) != AppOpsManager.MODE_ALLOWED) {
            Log.i(TAG, "Boot launch skipped: usage access is required to verify Yandex HOME is in front");
            return false;
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.i(TAG, "Boot launch skipped: optional background-launch permission was not granted");
            return false;
        }

        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio.isMusicActive() || !audio.getActiveRecordingConfigurations().isEmpty()) {
            Log.i(TAG, "Boot launch skipped: playback or microphone capture is active");
            return false;
        }
        Foreground foreground = readForeground(context);
        if (foreground == Foreground.UNKNOWN) {
            Log.i(TAG, "Boot launch deferred: no current foreground activity in usage events");
            return true;
        }
        if (foreground != Foreground.YANDEX_HOME) {
            Log.i(TAG, "Boot launch skipped: the foreground activity is not the stock Yandex HOME screen");
            return false;
        }

        try {
            context.startActivity(new Intent(context, MainActivity.class)
                    .setAction(BOOT_START)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Log.i(TAG, "Boot launch requested; MainActivity receipt confirms platform acceptance");
        } catch (SecurityException | android.content.ActivityNotFoundException exception) {
            Log.w(TAG, "Android rejected the boot launch", exception);
        }
        return false;
    }

    private enum Foreground { UNKNOWN, YANDEX_HOME, OTHER }

    private static Foreground readForeground(Context context) {
        UsageStatsManager usage = context.getSystemService(UsageStatsManager.class);
        long now = System.currentTimeMillis();
        long window = Math.min(LOOKBACK_MS, SystemClock.elapsedRealtime());
        UsageEvents events = usage.queryEvents(now - window, now);
        if (events == null) return Foreground.UNKNOWN;

        UsageEvents.Event event = new UsageEvents.Event();
        String resumedPackage = null;
        String resumedClass = null;
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                resumedPackage = event.getPackageName();
                resumedClass = event.getClassName();
            } else if ((type == UsageEvents.Event.ACTIVITY_PAUSED || type == UsageEvents.Event.ACTIVITY_STOPPED)
                    && java.util.Objects.equals(resumedPackage, event.getPackageName())
                    && java.util.Objects.equals(resumedClass, event.getClassName())) {
                resumedPackage = null;
                resumedClass = null;
            }
        }
        if (resumedPackage == null || resumedClass == null) return Foreground.UNKNOWN;
        return YANDEX_HOME.getPackageName().equals(resumedPackage)
                && YANDEX_HOME.getClassName().equals(resumedClass) ? Foreground.YANDEX_HOME : Foreground.OTHER;
    }

    static void logLaunch(Intent intent) {
        if (intent != null && BOOT_START.equals(intent.getAction())) {
            Log.i(TAG, "MainActivity received the boot launch");
        }
    }
}
