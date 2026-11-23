package com.haierlite.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int WHITE = 0xfff7f8fc, MUTED = 0xffb1b7c8;
    private static final int ACCENT = 0xffff8c9d;
    private static final long WEATHER_INTERVAL = 30 * 60_000L;
    private static final Locale RUSSIAN = new Locale("ru", "RU");
    private static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=56.84&longitude=60.61&current=temperature_2m,apparent_temperature,weather_code,is_day";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService weatherWorker = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> icons = new LruCache<>(24);
    private final Map<String, AppEntry> apps = new LinkedHashMap<>();
    private final Set<String> favorites = new HashSet<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", RUSSIAN);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMMM", RUSSIAN);
    private SharedPreferences prefs;
    private FrameLayout root;
    private HorizontalScrollView shelf;
    private LinearLayout row, panel, weather;
    private TextView clock, date, settingsButton;
    private View returnFocus;
    private float scale;
    private long lastWeatherAttempt;
    private boolean offline, refreshing;
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            Date instant = new Date(now);
            clock.setText(timeFormat.format(instant));
            String stamp = dateFormat.format(instant);
            date.setText(stamp.substring(0, 1).toUpperCase(RUSSIAN) + stamp.substring(1));
            handler.postDelayed(this, 60_000L - now % 60_000L);
        }
    };
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            refreshWeather();
            handler.postDelayed(this, WEATHER_INTERVAL);
        }
    };
    private final Runnable timerTick = this::checkTimer;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getPreferences(0);
        favorites.addAll(prefs.getStringSet("favorites", Collections.emptySet()));
        int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        scale = getResources().getDisplayMetrics().widthPixels / 1672f;
        loadApps();
        buildHome();
    }

    @Override protected void onStart() {
        super.onStart();
        timeFormat.setTimeZone(TimeZone.getDefault());
        dateFormat.setTimeZone(TimeZone.getDefault());
        clockTick.run();
        weatherTick.run();
        checkTimer();
        if (loadApps()) renderFavorites();
    }

    @Override protected void onStop() {
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(timerTick);
        super.onStop();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        weatherWorker.shutdownNow();
        super.onDestroy();
    }

    private int px(float units) { return Math.round(units * scale); }

    private TextView label(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable surface(int color, int radius, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(px(radius));
        if (stroke != 0) background.setStroke(Math.max(1, px(1)), stroke);
        return background;
    }

    private TextView button(String value, int size, Runnable action) {
        TextView view = label(value, size, WHITE);
        view.setId(View.generateViewId());
        view.setFocusable(true);
        view.setGravity(Gravity.CENTER);
        view.setOnClickListener(v -> action.run());
        view.setOnFocusChangeListener((v, focused) ->
                v.setBackground(focused ? surface(0xff4c2635, 18, ACCENT) : null));
        return view;
    }

    private void buildHome() {
        root = new FrameLayout(this);
        setContentView(root);
        applyScreen();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams headerPosition = new FrameLayout.LayoutParams(-1, px(72), Gravity.TOP);
        headerPosition.setMargins(px(70), px(34), px(54), 0);
        root.addView(header, headerPosition);
        TextView name = label("Haier Lite TV", 32, WHITE);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(name);
        TextView version = label(getString(R.string.edition_name), 20, ACCENT);
        version.setPadding(px(15), 0, px(15), 0);
        version.setBackground(surface(0xff3a1928, 22, 0));
        LinearLayout.LayoutParams badge = new LinearLayout.LayoutParams(-2, px(36));
        badge.leftMargin = px(30);
        header.addView(version, badge);
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView search = button("⌕", 49, this::showApps);
        search.setContentDescription("Найти приложение");
        header.addView(search, new LinearLayout.LayoutParams(px(76), px(68)));
        settingsButton = button("⚙", 40, this::openPanel);
        settingsButton.setContentDescription("Настройки");
        header.addView(settingsButton, new LinearLayout.LayoutParams(px(76), px(68)));
        TextView sleep = button("⏻", 38, this::showTimer);
        sleep.setContentDescription("Таймер сна");
        header.addView(sleep, new LinearLayout.LayoutParams(px(76), px(68)));

        LinearLayout timeBlock = new LinearLayout(this);
        timeBlock.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams timePosition = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        timePosition.setMargins(px(70), px(176), 0, 0);
        root.addView(timeBlock, timePosition);
        clock = label("", 156, WHITE);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        timeBlock.addView(clock);
        date = label("", 34, MUTED);
        date.setPadding(px(15), 0, 0, 0);
        timeBlock.addView(date);

        shelf = new HorizontalScrollView(this);
        shelf.setFillViewport(true);
        shelf.setHorizontalScrollBarEnabled(false);
        shelf.setOverScrollMode(View.OVER_SCROLL_NEVER);
        shelf.setSmoothScrollingEnabled(true);
        FrameLayout.LayoutParams shelfPosition = new FrameLayout.LayoutParams(-1, px(178), Gravity.CENTER);
        shelfPosition.leftMargin = px(70);
        shelfPosition.rightMargin = px(70);
        root.addView(shelf, shelfPosition);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        shelf.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));
        renderFavorites();

        weather = new LinearLayout(this);
        weather.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams weatherPosition = new FrameLayout.LayoutParams(-2, px(105), Gravity.BOTTOM | Gravity.LEFT);
        weatherPosition.setMargins(px(70), 0, 0, px(72));
        root.addView(weather, weatherPosition);
        showWeather();
    }

    private boolean loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, AppEntry> detected = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                if (info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || detected.containsKey(pkg)) continue;
                CharSequence title = info.loadLabel(pm);
                if (title == null || title.toString().trim().isEmpty() || pkg.contentEquals(title)) continue;
                Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(category)
                        .setClassName(pkg, info.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                detected.put(pkg, new AppEntry(title.toString(), info, launch));
            }
        }
        boolean changed = !apps.equals(detected);
        for (String pkg : apps.keySet()) {
            if (!Objects.equals(apps.get(pkg), detected.get(pkg))) icons.remove(pkg);
        }
        apps.clear();
        apps.putAll(detected);
        if (!prefs.contains("favorites") && favorites.isEmpty()) {
            for (String pkg : new String[]{"com.google.android.youtube.tv", "com.netflix.ninja", "com.android.vending"}) {
                if (apps.containsKey(pkg)) favorites.add(pkg);
            }
        }
        return changed;
    }

    private static final class AppEntry {
        final String title;
        final ResolveInfo info;
        final Intent launch;
        final long updated;

        AppEntry(String title, ResolveInfo info, Intent launch) {
            this.title = title;
            this.info = info;
            this.launch = launch;
            updated = new java.io.File(info.activityInfo.applicationInfo.sourceDir).lastModified();
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof AppEntry)) return false;
            AppEntry entry = (AppEntry) other;
            return title.equals(entry.title) && launch.filterEquals(entry.launch) && updated == entry.updated;
        }

        @Override public int hashCode() { return Objects.hash(title, launch.getComponent(), updated); }
    }

    private List<Map.Entry<String, AppEntry>> sortedApps() {
        List<Map.Entry<String, AppEntry>> list = new ArrayList<>(apps.entrySet());
        list.sort(Comparator.comparing(entry -> entry.getValue().title, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private void renderFavorites() {
        row.removeAllViews();
        for (Map.Entry<String, AppEntry> item : sortedApps()) {
            if (!favorites.contains(item.getKey())) continue;
            AppEntry entry = item.getValue();
            addTile(new Tile(entry.title, icon(item.getKey(), entry)), () -> launch(entry));
        }
        addTile(new Tile("Добавить приложения", null), this::showFavorites);
        row.getChildAt(0).requestFocus();
    }

    private void addTile(Tile tile, Runnable action) {
        tile.setId(View.generateViewId());
        tile.setFocusable(true);
        tile.setOnClickListener(v -> action.run());
        tile.setOnLongClickListener(v -> { showFavorites(); return true; });
        tile.setNextFocusUpId(settingsButton.getId());
        row.addView(tile, new LinearLayout.LayoutParams(px(164), -1));
    }

    private Bitmap icon(String pkg, AppEntry entry) {
        Bitmap cached = icons.get(pkg);
        if (cached != null) return cached;
        Drawable drawable = entry.info.loadIcon(getPackageManager());
        if (drawable == null) drawable = getPackageManager().getDefaultActivityIcon();
        int size = Math.min(px(136), 192);
        Bitmap bitmap = Bitmap.createBitmap(Math.max(size, 1), Math.max(size, 1), Bitmap.Config.ARGB_8888);
        int w = Math.max(1, drawable.getIntrinsicWidth()), h = Math.max(1, drawable.getIntrinsicHeight());
        int halfW = Math.round(size * w / (2f * Math.max(w, h)));
        int halfH = Math.round(size * h / (2f * Math.max(w, h)));
        drawable.setBounds(size / 2 - halfW, size / 2 - halfH, size / 2 + halfW, size / 2 + halfH);
        drawable.draw(new Canvas(bitmap));
        icons.put(pkg, bitmap);
        return bitmap;
    }

    private final class Tile extends View {
        private final Bitmap icon;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF iconBounds = new RectF(), corners = new RectF();

        Tile(String description, Bitmap icon) {
            super(MainActivity.this);
            this.icon = icon;
            setContentDescription(description);
        }

        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            float x = w / 2f, y = h / 2f, half = px(61);
            iconBounds.set(x - half, y - half, x + half, y + half);
            corners.set(px(5), px(10), w - px(5), h - px(10));
        }

        @Override protected void onFocusChanged(boolean focused, int direction, Rect previous) {
            super.onFocusChanged(focused, direction, previous);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            paint.setColor(isFocused() ? ACCENT : WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(1, px(3)));
            if (isFocused()) {
                canvas.save();
                for (int i = 0; i < 4; i++) {
                    canvas.save();
                    canvas.scale(i % 2 == 0 ? 1 : -1, i < 2 ? 1 : -1, corners.centerX(), corners.centerY());
                    float x = corners.left, y = corners.top, r = px(27);
                    canvas.drawArc(x, y, x + 2 * r, y + 2 * r, 180, 90, false, paint);
                    canvas.restore();
                }
                canvas.restore();
            }
            if (icon != null) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawBitmap(icon, null, iconBounds, paint);
            } else {
                float x = iconBounds.centerX(), y = iconBounds.centerY(), d = px(23);
                canvas.drawLine(x - d, y, x + d, y, paint);
                canvas.drawLine(x, y - d, x, y + d, paint);
            }
        }
    }

    private void launch(AppEntry app) {
        try { startActivity(app.launch); }
        catch (RuntimeException ignored) { toast("Не удалось открыть " + app.title); }
    }

    private void showApps() {
        loadApps();
        List<Map.Entry<String, AppEntry>> list = sortedApps();
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).getValue().title;
        new AlertDialog.Builder(this).setTitle("Приложения")
                .setItems(names, (dialog, index) -> launch(list.get(index).getValue()))
                .setNeutralButton("Избранное", (dialog, which) -> showFavorites())
                .setNegativeButton("Закрыть", null).show();
    }

    private void showFavorites() {
        loadApps();
        List<Map.Entry<String, AppEntry>> list = sortedApps();
        String[] names = new String[list.size()];
        boolean[] enabled = new boolean[list.size()];
        for (int i = 0; i < list.size(); i++) {
            names[i] = list.get(i).getValue().title;
            enabled[i] = favorites.contains(list.get(i).getKey());
        }
        new AlertDialog.Builder(this).setTitle("Приложения на главном экране")
                .setMultiChoiceItems(names, enabled, (dialog, index, checked) -> enabled[index] = checked)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    favorites.clear();
                    for (int i = 0; i < list.size(); i++) if (enabled[i]) favorites.add(list.get(i).getKey());
                    prefs.edit().putStringSet("favorites", new HashSet<>(favorites)).apply();
                    renderFavorites();
                    if (panel != null) panel.getChildAt(1).requestFocus();
                }).setNegativeButton("Отмена", null).show();
    }

    private void openPanel() {
        if (panel != null) { closePanel(); return; }
        returnFocus = getCurrentFocus();
        FrameLayout.LayoutParams shelfPosition = (FrameLayout.LayoutParams) shelf.getLayoutParams();
        shelfPosition.rightMargin = px(424);
        shelf.setLayoutParams(shelfPosition);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(17), px(18), px(17), px(16));
        panel.setBackground(surface(0xfa21121d, 32, 0xff5f3348));
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(px(382), -2, Gravity.RIGHT | Gravity.TOP);
        position.setMargins(0, px(158), px(27), 0);
        root.addView(panel, position);
        TextView heading = label("Настройки", 30, WHITE);
        heading.setPadding(px(18), 0, 0, 0);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, px(67)));
        panelItem("▱", "Экран", "Яркость и фон", this::showScreen);
        panelItem("♫", "Звук", "Системная громкость", this::showVolume);
        panelItem("⌁", "Сеть", "Wi-Fi, Ethernet", this::showNetwork);
        panelItem("ᛒ", "Bluetooth", "Подключённые устройства", this::openBluetooth);
        panelItem("▦", "Приложения", "Управление избранным", this::showFavorites);
        panelItem("☾", "Таймер сна", "Напоминание", this::showTimer);
        panelItem("☷", "Система", "Настройки Android TV",
                () -> openSystem(Settings.ACTION_SETTINGS));
        for (int i = 1; i < panel.getChildCount(); i++) {
            View item = panel.getChildAt(i);
            item.setNextFocusLeftId(item.getId());
            item.setNextFocusRightId(item.getId());
            item.setNextFocusUpId(panel.getChildAt(Math.max(1, i - 1)).getId());
            item.setNextFocusDownId(panel.getChildAt(Math.min(panel.getChildCount() - 1, i + 1)).getId());
        }
        panel.getChildAt(1).requestFocus();
    }

    private void panelItem(String glyph, String title, String subtitle, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setId(View.generateViewId());
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(px(25), 0, px(17), 0);
        item.setFocusable(true);
        item.setOnClickListener(v -> action.run());
        item.setOnFocusChangeListener((v, focused) ->
                v.setBackground(focused ? surface(0xff4c2635, 20, ACCENT) : null));
        TextView symbol = label(glyph, 32, WHITE);
        symbol.setGravity(Gravity.CENTER);
        item.addView(symbol, new LinearLayout.LayoutParams(px(52), -1));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(px(17), 0, 0, 0);
        copy.addView(label(title, 21, WHITE));
        copy.addView(label(subtitle, 15, MUTED));
        item.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        item.addView(label("›", 28, WHITE));
        panel.addView(item, new LinearLayout.LayoutParams(-1, px(72)));
    }

    private void closePanel() {
        root.removeView(panel);
        panel = null;
        FrameLayout.LayoutParams shelfPosition = (FrameLayout.LayoutParams) shelf.getLayoutParams();
        shelfPosition.rightMargin = px(70);
        shelf.setLayoutParams(shelfPosition);
        if (returnFocus != null && !returnFocus.requestFocus()) row.getChildAt(0).requestFocus();
        returnFocus = null;
    }

    @Override public void onBackPressed() {
        if (panel != null) closePanel();
        else super.onBackPressed();
    }

    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if ((key == KeyEvent.KEYCODE_MENU || key == KeyEvent.KEYCODE_SETTINGS) && event.getRepeatCount() == 0) {
            openPanel();
            return true;
        }
        return super.onKeyDown(key, event);
    }

    private void showScreen() {
        new AlertDialog.Builder(this).setTitle("Экран")
                .setItems(new String[]{"Яркость интерфейса", "Оформление и фон"}, (dialog, index) -> {
                    if (index == 0) showBrightness();
                    else showBackground();
                }).setNegativeButton("Закрыть", null).show();
    }

    private void showBrightness() {
        new AlertDialog.Builder(this).setTitle("Яркость только в Haier Lite TV")
                .setSingleChoiceItems(new String[]{"Системная", "30%", "55%", "80%", "100%"},
                        prefs.getInt("brightness_index", 0), (dialog, index) -> {
                            prefs.edit().putInt("brightness_index", index).apply();
                            applyScreen();
                            dialog.dismiss();
                        }).setNegativeButton("Закрыть", null).show();
    }

    private void showBackground() {
        new AlertDialog.Builder(this).setTitle("Фон")
                .setSingleChoiceItems(new String[]{"Бордовые волны", "Однотонный графит"},
                        prefs.getInt("background", 0), (dialog, index) -> {
                            prefs.edit().putInt("background", index).apply();
                            applyScreen();
                            dialog.dismiss();
                        }).setNegativeButton("Закрыть", null).show();
    }

    private void applyScreen() {
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.screenBrightness = new float[]{-1f, .30f, .55f, .80f, 1f}[prefs.getInt("brightness_index", 0)];
        getWindow().setAttributes(attrs);
        if (prefs.getInt("background", 0) == 0) root.setBackground(new Waves());
        else root.setBackgroundColor(0xff11141a);
    }

    private void showVolume() {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        int stream = AudioManager.STREAM_MUSIC;
        if (audio == null || audio.isVolumeFixed()) {
            toast("Громкость регулируется подключённым устройством");
            return;
        }
        SeekBar seek = new SeekBar(this);
        seek.setMax(audio.getStreamMaxVolume(stream));
        seek.setProgress(audio.getStreamVolume(stream));
        seek.setKeyProgressIncrement(1);
        seek.setPadding(px(26), px(16), px(26), px(16));
        seek.setContentDescription("Системная громкость мультимедиа");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int volume, boolean fromUser) {
                if (fromUser) {
                    try { audio.setStreamVolume(stream, volume, 0); }
                    catch (SecurityException ignored) {
                        toast("Управление громкостью ограничено");
                        bar.setProgress(audio.getStreamVolume(stream));
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        new AlertDialog.Builder(this).setTitle("Системная громкость")
                .setView(seek).setPositiveButton("Закрыть", null).show();
        seek.requestFocus();
    }

    private void showNetwork() {
        new AlertDialog.Builder(this).setTitle("Сеть")
                .setItems(new String[]{"Wi-Fi", "Ethernet и другие сети"}, (dialog, index) -> {
                    if (index == 0) openSystem(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_SETTINGS);
                    else openSystem(Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS);
                }).setNegativeButton("Закрыть", null).show();
    }

    private void openBluetooth() {
        openSystem("android.settings.BLUETOOTH_SETTINGS", Settings.ACTION_SETTINGS);
    }

    private void openSystem(String... actions) {
        for (String action : actions) {
            try { startActivity(new Intent(action)); return; }
            catch (android.content.ActivityNotFoundException | SecurityException ignored) {}
        }
        toast("Этот раздел недоступен на телевизоре");
    }

    private void showTimer() {
        int[] minutes = {10, 20, 30, 40, 60, 90, 120, 240};
        long until = prefs.getLong("sleep_at", 0);
        String[] items = new String[minutes.length + 1];
        for (int i = 0; i < minutes.length; i++) items[i] = minutes[i] + " мин";
        items[minutes.length] = "Отменить таймер";
        long left = Math.max(0, (until - System.currentTimeMillis() + 59_999L) / 60_000L);
        String title = left == 0 ? "Таймер сна · напоминание" : "Таймер сна · осталось " + left + " мин";
        new AlertDialog.Builder(this).setTitle(title).setItems(items, (dialog, index) -> {
            if (index == minutes.length) {
                prefs.edit().remove("sleep_at").apply();
                toast("Таймер отменён");
            } else {
                prefs.edit().putLong("sleep_at", System.currentTimeMillis() + minutes[index] * 60_000L).apply();
                toast("Напоминание через " + minutes[index] + " мин");
            }
            checkTimer();
        }).setNegativeButton("Закрыть", null).show();
    }

    private void checkTimer() {
        handler.removeCallbacks(timerTick);
        long until = prefs.getLong("sleep_at", 0);
        if (until == 0) return;
        long remaining = until - System.currentTimeMillis();
        if (remaining > 0) {
            handler.postDelayed(timerTick, remaining);
            return;
        }
        prefs.edit().remove("sleep_at").apply();
        new AlertDialog.Builder(this).setTitle("Таймер сна завершён")
                .setMessage("Чтобы выключить телевизор, нажмите кнопку питания на пульте.")
                .setPositiveButton("Понятно", null).show();
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    private void refreshWeather() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (refreshing || (lastWeatherAttempt != 0 && now - lastWeatherAttempt < 60_000L)) return;
        refreshing = true;
        lastWeatherAttempt = now;
        weatherWorker.execute(() -> {
            JSONObject result = readWeather();
            handler.post(() -> {
                if (isDestroyed()) return;
                refreshing = false;
                offline = result == null;
                if (result != null) prefs.edit().putString("weather", result.toString())
                        .putLong("weather_at", System.currentTimeMillis()).apply();
                showWeather();
            });
        });
    }

    private JSONObject readWeather() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(WEATHER_URL).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            if (connection.getResponseCode() != 200) return null;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[2048];
                for (int len; (len = input.read(chunk)) != -1;) {
                    if (bytes.size() + len > 32_768) return null;
                    bytes.write(chunk, 0, len);
                }
                JSONObject current = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8))
                        .getJSONObject("current");
                current.getDouble("temperature_2m");
                current.getDouble("apparent_temperature");
                current.getInt("weather_code");
                return current;
            }
        } catch (Exception ignored) { return null; }
        finally { if (connection != null) connection.disconnect(); }
    }

    private void showWeather() {
        weather.removeAllViews();
        String temperature = "—", detail = "Ожидаем данные", feels = "";
        int kind = 1;
        boolean day = true;
        try {
            JSONObject current = new JSONObject(prefs.getString("weather", ""));
            temperature = degrees(current.getDouble("temperature_2m"));
            int code = current.getInt("weather_code");
            day = current.optInt("is_day", 1) == 1;
            if (code == 0) { kind = 0; detail = "Ясно"; }
            else if (code <= 3) { detail = code == 3 ? "Пасмурно" : "Облачно"; }
            else if (code <= 48) { kind = 2; detail = "Туман"; }
            else if ((code >= 71 && code <= 77) || code == 85 || code == 86) { kind = 3; detail = "Снег"; }
            else if (code >= 95) { kind = 5; detail = "Гроза"; }
            else { kind = 4; detail = "Дождь"; }
            feels = "Ощущается " + degrees(current.getDouble("apparent_temperature"));
            long cachedAt = prefs.getLong("weather_at", 0);
            if (offline || System.currentTimeMillis() - cachedAt > 2 * WEATHER_INTERVAL) {
                SimpleDateFormat stamp = new SimpleDateFormat("d MMM, HH:mm", RUSSIAN);
                feels += "\nДанные на " + stamp.format(new Date(cachedAt));
            }
        } catch (org.json.JSONException ignored) {
            if (offline) detail = "Погода недоступна";
        }
        weather.addView(new WeatherGlyph(kind, day), new LinearLayout.LayoutParams(px(86), px(82)));
        View line = new View(this);
        line.setBackgroundColor(0xff525869);
        LinearLayout.LayoutParams separator = new LinearLayout.LayoutParams(Math.max(1, px(1)), px(70));
        separator.setMargins(px(17), 0, px(26), 0);
        weather.addView(line, separator);
        LinearLayout readings = new LinearLayout(this);
        readings.setOrientation(LinearLayout.VERTICAL);
        readings.addView(label("Екатеринбург", 19, WHITE));
        readings.addView(label(temperature, 58, WHITE));
        weather.addView(readings);
        LinearLayout description = new LinearLayout(this);
        description.setOrientation(LinearLayout.VERTICAL);
        description.setPadding(px(36), px(13), 0, 0);
        description.addView(label(detail, 19, MUTED));
        if (!feels.isEmpty()) description.addView(label(feels, 18, MUTED));
        weather.addView(description);
    }

    private String degrees(double value) {
        return String.valueOf(Math.round(value)).replace('-', '−') + "°";
    }

    private final class WeatherGlyph extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int kind;
        private final boolean day;
        WeatherGlyph(int kind, boolean day) {
            super(MainActivity.this);
            this.kind = kind;
            this.day = day;
        }

        @Override protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / 86f, getHeight() / 82f);
            if (kind == 0) {
                paint.setColor(day ? 0xffead89d : 0xffdee5fc);
                canvas.drawCircle(43, 36, 19, paint);
                if (!day) {
                    paint.setColor(0xff171a23);
                    canvas.drawCircle(53, 27, 17, paint);
                }
            } else {
                paint.setColor(0xffdce1ee);
                canvas.drawCircle(43, 31, 20, paint);
                canvas.drawCircle(23, 42, 15, paint);
                canvas.drawCircle(62, 43, 14, paint);
                canvas.drawRoundRect(13, 39, 72, 57, 9, 9, paint);
                paint.setColor(0xffa2caec);
                paint.setStrokeWidth(2f);
                for (int x = 25; x <= 61; x += 18) {
                    if (kind == 3) {
                        canvas.drawLine(x - 4, 69, x + 4, 69, paint);
                        canvas.drawLine(x - 3, 65, x + 3, 73, paint);
                        canvas.drawLine(x + 3, 65, x - 3, 73, paint);
                    } else if (kind == 4 || kind == 5) canvas.drawLine(x, 65, x - 4, 74, paint);
                }
                if (kind == 2) {
                    canvas.drawLine(20, 64, 67, 64, paint);
                    canvas.drawLine(29, 71, 59, 71, paint);
                }
                if (kind == 5) {
                    paint.setColor(0xfff3dc8c);
                    canvas.drawLine(47, 57, 39, 68, paint);
                    canvas.drawLine(39, 68, 47, 68, paint);
                    canvas.drawLine(47, 68, 41, 76, paint);
                }
            }
            canvas.restore();
        }
    }

    private static final class Waves extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path[] shapes = {new Path(), new Path(), new Path()};
        private final Shader[] fills = {
            new RadialGradient(722, 74, 690, new int[]{0xffa64a64, 0xff4b1b2f, 0xff21121d},
                    new float[]{0, .36f, 1}, Shader.TileMode.CLAMP),
            new LinearGradient(715, 271, 903, 515, new int[]{0xff804263, 0xff421a35, 0xff160e19},
                    new float[]{0, .27f, .83f}, Shader.TileMode.CLAMP),
            new RadialGradient(1103, 697, 540, new int[]{0xff8e3c52, 0xff3c1726, 0xff1b1019},
                    new float[]{0, .31f, .92f}, Shader.TileMode.CLAMP)
        };
        private final Shader base = new LinearGradient(0, 0, 1672, 941, 0xff25121f, 0xff100b13, Shader.TileMode.CLAMP);
        private final Shader edge = new LinearGradient(250, 250, 1100, 650,
                new int[]{0x09934c61, 0xb1e99eaf, 0x07934c61, 0xa5e99eaf},
                new float[]{0, .29f, .67f, 1}, Shader.TileMode.CLAMP);
        Waves() {
            shapes[0].moveTo(-20, 500);
            shapes[0].cubicTo(240, 495, 508, 430, 580, 334);
            shapes[0].cubicTo(622, 278, 625, 149, 704, -2);
            shapes[0].lineTo(-20, -10);
            shapes[0].close();
            shapes[1].moveTo(407, 627);
            shapes[1].cubicTo(672, 470, 764, 302, 1042, 333);
            shapes[1].cubicTo(1133, 343, 1250, 256, 1370, 302);
            shapes[1].cubicTo(1510, 347, 1612, 441, 1680, 480);
            shapes[1].lineTo(1680, 951);
            shapes[1].lineTo(-20, 951);
            shapes[1].close();
            shapes[2].moveTo(780, 951);
            shapes[2].cubicTo(934, 776, 1140, 709, 1312, 654);
            shapes[2].cubicTo(1465, 608, 1604, 612, 1680, 565);
            shapes[2].lineTo(1680, 951);
            shapes[2].close();
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / 1672f, bounds.height() / 941f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(base);
            canvas.drawRect(0, 0, 1672, 941, paint);
            for (int i = 0; i < shapes.length; i++) {
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(fills[i]);
                canvas.drawPath(shapes[i], paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setShader(edge);
                paint.setStrokeWidth(1.5f);
                canvas.drawPath(shapes[i], paint);
            }
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
