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
import android.media.tv.TvContract;
import android.net.Uri;
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
    private static final int ACCENT = 0xffff2448;
    private static final int OPEN_FILE = 1;
    private static final String[][] APP_SLOTS = {
        {"com.google.android.youtube.tv"},
        {"ru.kinopoisk.tv", "ru.kinopoisk"},
        {"ru.ivi.client"},
        {"ru.rutube.app", "ru.rutube"},
        {"ru.yandex.searchplugin", "com.yandex.tv.home"},
        {"org.telegram.messenger"},
        {"com.android.vending"}
    };
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
    private TextView clock, date;
    private View returnFocus;
    private Drawable wallpaper;
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

    private void place(View view, int x, int y, int width, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(px(width), px(height));
        params.leftMargin = px(x);
        params.topMargin = px(y);
        root.addView(view, params);
    }

    private TextView control(String title, boolean showTitle, int icon, int size, Runnable action) {
        TextView view = label(showTitle ? title : "", 25, WHITE);
        view.setId(View.generateViewId());
        view.setContentDescription(title);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setFocusable(true);
        view.setGravity(Gravity.CENTER);
        Drawable drawable = getDrawable(icon);
        drawable.setBounds(0, 0, px(size), px(size));
        if (showTitle) {
            view.setGravity(Gravity.CENTER_VERTICAL);
            int padding = title.equals("Алиса") ? 24 : 40;
            view.setPadding(px(padding), 0, px(12), 0);
            view.setCompoundDrawables(drawable, null, null, null);
            view.setCompoundDrawablePadding(px(title.equals("Алиса") ? 16 : 27));
        } else {
            // Draw a compound image centered in an otherwise empty button.
            view.setPadding(px(16), 0, px(16), 0);
            view.setCompoundDrawables(drawable, null, null, null);
        }
        view.setBackground(controlBackground(false));
        view.setOnFocusChangeListener((v, focused) -> v.setBackground(controlBackground(focused)));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private Drawable controlBackground(boolean focused) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                focused ? new int[]{0xff840b27, 0xff280c11, 0xff960721}
                        : new int[]{0xf46e0715, 0xf51a0d0d, 0xf51a0d0d, 0xf5740818});
        drawable.setCornerRadius(px(44));
        drawable.setStroke(px(focused ? 3 : 1), focused ? WHITE : 0xffff092b);
        return drawable;
    }

    private void buildHome() {
        root = new FrameLayout(this);
        setContentView(root);
        applyScreen();

        View clockShade = new View(this);
        clockShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xd30e080a, 0xa20e080a, 0x000e080a}));
        place(clockShade, 0, 113, 384, 267);

        ImageView crest = new ImageView(this);
        crest.setImageResource(R.drawable.dxd_crest);
        place(crest, 40, 15, 72, 80);
        TextView name = label("Haier Lite TV", 32, WHITE);
        name.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        name.setShadowLayer(px(4), 0, px(2), 0xff000000);
        place(name, 127, 21, 199, 41);
        TextView edition = label("DxD Edition", 23, ACCENT);
        edition.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        place(edition, 128, 59, 182, 33);
        TextView badge = label("Яндекс ТВ", 21, WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(surface(0xe04d0739, 18, 0xffec174b));
        place(badge, 330, 26, 122, 33);

        place(control("Алиса", true, R.drawable.ic_mic, 31, this::openAlice), 1220, 23, 178, 63);
        place(control("Wi-Fi", false, R.drawable.ic_wifi, 31, this::showNetwork), 1415, 23, 63, 63);
        place(control("Bluetooth", false, R.drawable.ic_bluetooth, 31, this::openBluetooth), 1494, 23, 63, 63);
        place(control("Настройки", false, R.drawable.ic_settings, 31, this::openPanel), 1573, 23, 63, 63);

        View line = new View(this);
        line.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ACCENT, WHITE, ACCENT, ACCENT}));
        place(line, 56, 137, 3, 234);
        clock = label("", 84, WHITE);
        clock.setShadowLayer(px(5), 0, px(2), 0xff000000);
        place(clock, 95, 118, 264, 102);
        date = label("", 28, WHITE);
        date.setShadowLayer(px(4), 0, px(2), 0xff000000);
        place(date, 96, 224, 264, 38);
        weather = new LinearLayout(this);
        weather.setGravity(Gravity.CENTER_VERTICAL);
        place(weather, 91, 272, 260, 62);
        TextView city = label("Екатеринбург", 25, WHITE);
        city.setShadowLayer(px(4), 0, px(2), 0xff000000);
        Drawable pin = getDrawable(R.drawable.ic_pin);
        pin.setBounds(0, 0, px(22), px(22));
        city.setCompoundDrawables(pin, null, null, null);
        city.setCompoundDrawablePadding(px(5));
        place(city, 94, 342, 249, 36);
        showWeather();

        shelf = new HorizontalScrollView(this);
        shelf.setFillViewport(true);
        shelf.setHorizontalScrollBarEnabled(false);
        shelf.setOverScrollMode(View.OVER_SCROLL_NEVER);
        shelf.setSmoothScrollingEnabled(true);
        place(shelf, 72, 501, 1528, 200);
        row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        shelf.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));

        place(control("ТВ-каналы", true, R.drawable.ic_tv, 43, this::openChannels),
                377, 807, 288, 79);
        place(control("Приложения", true, R.drawable.ic_apps, 38, this::showApps),
                692, 807, 288, 79);
        place(control("Файлы", true, R.drawable.ic_folder, 43, this::openFiles),
                1007, 807, 288, 79);
        renderFavorites();
    }

    private boolean tryOpen(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (android.content.ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    private void openAlice() {
        // Limit assistant intents to Yandex packages. Remote microphone events remain system-owned.
        for (String pkg : new String[]{"ru.yandex.searchplugin", "com.yandex.tv.home",
                "ru.yandex.yandexnavi", "com.yandex.browser"}) {
            if (tryOpen(new Intent(Intent.ACTION_VIEW, Uri.parse("yandex://alice")).setPackage(pkg))) return;
        }
        for (ResolveInfo handler : getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_VOICE_COMMAND), 0)) {
            String pkg = handler.activityInfo.packageName;
            if ((pkg.startsWith("com.yandex.") || pkg.startsWith("ru.yandex."))
                    && tryOpen(new Intent(Intent.ACTION_VOICE_COMMAND).setPackage(pkg))) return;
        }
        toast("Откройте Алису кнопкой микрофона на пульте");
    }

    private void openChannels() {
        Intent intent = new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI);
        if (!tryOpen(intent) && !tryOpen(new Intent(Intent.ACTION_MAIN).setPackage("com.android.tv"))) {
            toast("Приложение ТВ-каналов недоступно");
        }
    }

    private void openFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        try {
            startActivityForResult(intent, OPEN_FILE);
        } catch (android.content.ActivityNotFoundException | SecurityException ignored) {
            toast("Приложение для просмотра файлов недоступно");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != OPEN_FILE || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, getContentResolver().getType(uri))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (!tryOpen(view)) toast("Нет приложения для этого файла");
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

    private Set<String> defaultFavorites() {
        Set<String> selected = new HashSet<>();
        for (String[] candidates : APP_SLOTS) {
            for (String pkg : candidates) {
                if (!apps.containsKey(pkg)) continue;
                selected.add(pkg);
                break;
            }
        }
        return selected;
    }

    private void renderFavorites() {
        row.removeAllViews();
        Bitmap tvIcon = Bitmap.createBitmap(px(132), px(132), Bitmap.Config.ARGB_8888);
        Drawable tv = getDrawable(R.drawable.ic_play_tile);
        tv.setBounds(0, 0, tvIcon.getWidth(), tvIcon.getHeight());
        tv.draw(new Canvas(tvIcon));
        addTile(new Tile("Открыть ТВ-каналы", tvIcon, 66), this::openChannels);
        Set<String> selected = prefs.contains("favorites") ? favorites : defaultFavorites();
        Set<String> shown = new HashSet<>();
        for (String[] candidates : APP_SLOTS) {
            String pkg = null;
            for (String candidate : candidates) {
                if (!apps.containsKey(candidate) || !selected.contains(candidate)) continue;
                pkg = candidate;
                break;
            }
            if (pkg == null) {
                row.addView(new Space(this), new LinearLayout.LayoutParams(px(169), -1));
            } else {
                shown.add(pkg);
                addFavorite(pkg);
            }
        }
        for (Map.Entry<String, AppEntry> item : sortedApps()) {
            if (selected.contains(item.getKey()) && !shown.contains(item.getKey())) {
                addFavorite(item.getKey());
            }
        }
        addTile(new Tile("Все приложения", null, 63), this::showApps);
        row.getChildAt(0).requestFocus();
    }

    private void addFavorite(String pkg) {
        AppEntry entry = apps.get(pkg);
        addTile(new Tile(entry.title, icon(pkg, entry), 59), () -> launch(entry));
    }

    private void addTile(Tile tile, Runnable action) {
        tile.setId(View.generateViewId());
        tile.setFocusable(true);
        tile.setOnClickListener(v -> action.run());
        tile.setOnLongClickListener(v -> { showFavorites(); return true; });
        row.addView(tile, new LinearLayout.LayoutParams(px(169), -1));
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

        private final int iconHalfSize;

        Tile(String description, Bitmap icon, int iconHalfSize) {
            super(MainActivity.this);
            this.icon = icon;
            this.iconHalfSize = iconHalfSize;
            setContentDescription(description);
        }

        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            float x = w / 2f, y = h / 2f, half = px(iconHalfSize);
            iconBounds.set(x - half, y - half, x + half, y + half);
            corners.set(px(6), px(14), w - px(6), h - px(14));
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
                for (int i = 0; i < 4; i++) {
                    canvas.save();
                    canvas.scale(i % 2 == 0 ? 1 : -1, i < 2 ? 1 : -1,
                            corners.centerX(), corners.centerY());
                    float x = corners.left, y = corners.top, r = px(31), arm = px(13);
                    Path corner = new Path();
                    corner.moveTo(x, y + r + arm);
                    corner.lineTo(x, y + r);
                    corner.arcTo(x, y, x + 2 * r, y + 2 * r, 180, 90, false);
                    corner.lineTo(x + r + arm, y);
                    paint.setColor(0x92ff0033);
                    paint.setStrokeWidth(px(10));
                    canvas.drawPath(corner, paint);
                    paint.setColor(WHITE);
                    paint.setStrokeWidth(px(4));
                    canvas.drawPath(corner, paint);
                    canvas.restore();
                }
            }
            paint.setStyle(Paint.Style.FILL);
            if (icon != null) {
                canvas.drawBitmap(icon, null, iconBounds, paint);
            } else {
                paint.setColor(0x99403235);
                canvas.drawRoundRect(iconBounds, px(29), px(29), paint);
                Drawable grid = getDrawable(R.drawable.ic_apps);
                int x = (int) iconBounds.centerX(), y = (int) iconBounds.centerY(), half = px(31);
                grid.setBounds(x - half, y - half, x + half, y + half);
                grid.draw(canvas);
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
        Set<String> selected = prefs.contains("favorites") ? favorites : defaultFavorites();
        for (int i = 0; i < list.size(); i++) {
            names[i] = list.get(i).getValue().title;
            enabled[i] = selected.contains(list.get(i).getKey());
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
        shelf.setVisibility(View.INVISIBLE);
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
        shelf.setVisibility(View.VISIBLE);
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
                .setSingleChoiceItems(new String[]{"DxD Edition", "Однотонный графит"},
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
        if (prefs.getInt("background", 0) == 0) {
            if (wallpaper == null) wallpaper = getDrawable(R.drawable.dxd_wallpaper);
            root.setBackground(wallpaper);
        } else root.setBackgroundColor(0xff11141a);
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
        weather.addView(new WeatherGlyph(kind, day), new LinearLayout.LayoutParams(px(85), px(72)));
        TextView reading = label(temperature, 51, WHITE);
        reading.setPadding(px(12), 0, 0, 0);
        reading.setShadowLayer(px(5), 0, px(2), 0xff000000);
        weather.addView(reading);
        weather.setContentDescription(detail + ", " + temperature + ". " + feels);
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

}
