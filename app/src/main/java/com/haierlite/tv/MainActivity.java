package com.haierlite.tv;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private final int INK=0xff442e4e, WHITE=0xffffffff, PINK=0xffffb5d4;
    private LinearLayout root, favoritesRow;
    private TextView clock, timerLabel;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<AppEntry> apps=new ArrayList<>();
    private final LinkedHashSet<String> favorites=new LinkedHashSet<>();
    private long deadline=0;
    private final Runnable tick=new Runnable(){public void run(){
        if(clock!=null)clock.setText(new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
        if(deadline>0 && SystemClock.elapsedRealtime()>=deadline){deadline=0;getPreferences(0).edit().remove("deadline").apply();new AlertDialog.Builder(MainActivity.this).setTitle("Таймер завершён").setMessage("Время сна наступило. Выключите телевизор кнопкой питания на пульте.").setPositiveButton("Хорошо",null).show();}
        if(timerLabel!=null)timerLabel.setText(deadline>0?"Сон через "+Math.max(1,(deadline-SystemClock.elapsedRealtime()+59999)/60000)+" мин":"Таймер сна");
        handler.postDelayed(this,1000);
    }};
    static class AppEntry {String pkg, title; Drawable icon; Intent intent; AppEntry(String p,String t,Drawable d,Intent i){pkg=p;title=t;icon=d;intent=i;}}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextColor(color);v.setTextSize(size);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    @Override public void onCreate(Bundle b){super.onCreate(b);favorites.addAll(getPreferences(0).getStringSet("favorites",new HashSet<>()));deadline=getPreferences(0).getLong("deadline",0);loadApps();draw();handler.post(tick);}
    @Override protected void onDestroy(){handler.removeCallbacks(tick);super.onDestroy();}
    @Override protected void onResume(){super.onResume();loadApps();if(favoritesRow!=null)renderFavorites();}
    private void loadApps(){
        apps.clear();PackageManager pm=getPackageManager();Map<String,AppEntry> map=new LinkedHashMap<>();
        for(String cat:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            Intent q=new Intent(Intent.ACTION_MAIN);q.addCategory(cat);
            for(ResolveInfo r:pm.queryIntentActivities(q,0)){
                if(r.activityInfo==null)continue;String pkg=r.activityInfo.packageName;
                if(pkg.equals(getPackageName())||map.containsKey(pkg))continue;
                CharSequence label=r.loadLabel(pm);String title=label==null?"":label.toString().trim();
                if(title.isEmpty()||title.equals(pkg)||title.equals(r.activityInfo.name))continue;
                Intent launch=new Intent(Intent.ACTION_MAIN);launch.addCategory(cat);launch.setClassName(pkg,r.activityInfo.name);launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Drawable icon=null;try{icon=r.loadIcon(pm);}catch(Exception ignored){}
                map.put(pkg,new AppEntry(pkg,title,icon,launch));
            }
        }
        apps.addAll(map.values());Collections.sort(apps,(a,b)->a.title.compareToIgnoreCase(b.title));
        if(!getPreferences(0).contains("favorites") && favorites.isEmpty())for(String key:new String[]{"com.google.android.youtube.tv","com.netflix.ninja","com.android.vending"})if(map.containsKey(key))favorites.add(key);
    }
    private void draw(){
        FrameLayout base=new FrameLayout(this);
        base.setBackground(new ScenicBackground());
        setContentView(base);

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(42),dp(20),dp(42),dp(10));
        FrameLayout.LayoutParams topLp=new FrameLayout.LayoutParams(-1,dp(88),Gravity.TOP);
        base.addView(top,topLp);
        TextView brand=text("♡  Haier Lite TV",26,WHITE);
        brand.setTypeface(null,Typeface.BOLD);
        top.addView(brand,new LinearLayout.LayoutParams(0,dp(54),1));
        top.addView(action("Приложения",()->showAllApps()));
        top.addView(action("Настройки",()->showSettings()));
        clock=text("",23,WHITE);
        clock.setGravity(Gravity.CENTER);
        top.addView(clock,new LinearLayout.LayoutParams(dp(115),dp(52)));

        // The only app row on the home screen, vertically centered.
        HorizontalScrollView hs=new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFillViewport(true);
        FrameLayout.LayoutParams centerLp=new FrameLayout.LayoutParams(-1,dp(210),Gravity.CENTER);
        centerLp.leftMargin=dp(36);
        centerLp.rightMargin=dp(36);
        base.addView(hs,centerLp);
        favoritesRow=new LinearLayout(this);
        favoritesRow.setOrientation(LinearLayout.HORIZONTAL);
        favoritesRow.setGravity(Gravity.CENTER);
        favoritesRow.setPadding(dp(8),dp(12),dp(8),dp(12));
        hs.addView(favoritesRow,new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        renderFavorites();

        LinearLayout bottom=new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(20),dp(8),dp(20),dp(14));
        FrameLayout.LayoutParams bottomLp=new FrameLayout.LayoutParams(-1,dp(76),Gravity.BOTTOM);
        base.addView(bottom,bottomLp);
        timerLabel=action("Таймер сна",()->showTimer());
        bottom.addView(timerLabel,new LinearLayout.LayoutParams(-2,dp(50)));
        TextView help=text("    ◀ ▶  Выбор    •    OK  Открыть",14,WHITE);
        bottom.addView(help);
        if(favoritesRow.getChildCount()>0)favoritesRow.getChildAt(0).requestFocus();
    }
    private TextView action(String title,Runnable click){TextView v=text(title,17,WHITE);v.setGravity(Gravity.CENTER);v.setPadding(dp(18),0,dp(18),0);v.setBackground(shape(0x884b2b54,18));v.setFocusable(true);v.setOnClickListener(w->click.run());v.setOnFocusChangeListener((w,f)->{v.setBackground(shape(f?0xffff9dc9:0x884b2b54,18));v.setTextColor(f?INK:WHITE);});return v;}
    private void renderFavorites(){if(favoritesRow==null)return;favoritesRow.removeAllViews();int count=0;for(AppEntry a:apps)if(favorites.contains(a.pkg)){favoritesRow.addView(tile(a),tileParams());count++;}TextView add=action("＋\nДобавить",()->showPicker());add.setTextSize(20);favoritesRow.addView(add,tileParams());}
    private LinearLayout.LayoutParams tileParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(164),dp(164));p.leftMargin=dp(8);p.rightMargin=dp(8);return p;}
    private View tile(AppEntry a){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(10),dp(12),dp(10),dp(10));card.setBackground(shape(0xc9ffe5f0,24));card.setFocusable(true);card.setClickable(true);
        ImageView iv=new ImageView(this);if(a.icon!=null)iv.setImageDrawable(a.icon);card.addView(iv,new LinearLayout.LayoutParams(dp(84),dp(84)));
        TextView label=text(a.title,15,INK);label.setGravity(Gravity.CENTER);label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(42));lp.topMargin=dp(8);card.addView(label,lp);
        card.setOnClickListener(v->{try{startActivity(a.intent);}catch(Exception e){Toast.makeText(this,"Не удалось запустить "+a.title,Toast.LENGTH_SHORT).show();}});
        card.setOnFocusChangeListener((v,f)->{card.setBackground(shape(f?0xffffa8d1:0xc9ffe5f0,24));card.setScaleX(f?1.06f:1f);card.setScaleY(f?1.06f:1f);});return card;}
    private void showAllApps(){loadApps();ScrollView s=new ScrollView(this);GridLayout grid=new GridLayout(this);grid.setColumnCount(4);grid.setPadding(dp(18),dp(18),dp(8),dp(18));s.addView(grid);for(AppEntry a:apps){GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=dp(145);p.height=dp(145);p.setMargins(dp(8),dp(8),dp(8),dp(8));grid.addView(tile(a),p);}new AlertDialog.Builder(this).setTitle("Все приложения").setView(s).setPositiveButton("Закрыть",null).setNeutralButton("Добавить в избранное",(d,w)->showPicker()).show();}
    private void showPicker(){loadApps();String[] names=new String[apps.size()];boolean[] checks=new boolean[apps.size()];for(int i=0;i<apps.size();i++){names[i]=apps.get(i).title;checks[i]=favorites.contains(apps.get(i).pkg);}new AlertDialog.Builder(this).setTitle("Выбери приложения для главного экрана").setMultiChoiceItems(names,checks,(d,which,checked)->checks[which]=checked).setPositiveButton("Сохранить",(d,w)->{favorites.clear();for(int i=0;i<apps.size();i++)if(checks[i])favorites.add(apps.get(i).pkg);getPreferences(0).edit().putStringSet("favorites",new HashSet<>(favorites)).apply();renderFavorites();}).setNegativeButton("Отмена",null).show();}
    private void showSettings(){new AlertDialog.Builder(this).setTitle("Настройки Haier Lite TV").setItems(new String[]{"Добавить / убрать приложения","Таймер сна","Настройки Android","Выбрать другой домашний экран"},(d,n)->{if(n==0)showPicker();if(n==1)showTimer();if(n==2)openSettings(Settings.ACTION_SETTINGS);if(n==3)openSettings(Settings.ACTION_HOME_SETTINGS);}).setNegativeButton("Закрыть",null).show();}
    private void openSettings(String action){try{startActivity(new Intent(action));}catch(Exception e){Toast.makeText(this,"Недоступно на этой прошивке",Toast.LENGTH_LONG).show();}}
    private void showTimer(){final int[] minutes={10,20,30,40,60,90,120,240};String[] items=new String[minutes.length+1];for(int i=0;i<minutes.length;i++)items[i]=minutes[i]+" минут";items[minutes.length]="Отменить таймер";new AlertDialog.Builder(this).setTitle("Таймер сна · напоминание").setItems(items,(d,n)->{if(n==minutes.length){deadline=0;getPreferences(0).edit().remove("deadline").apply();}else{deadline=SystemClock.elapsedRealtime()+minutes[n]*60000L;getPreferences(0).edit().putLong("deadline",deadline).apply();}Toast.makeText(this,deadline==0?"Таймер отменён":"Таймер установлен",Toast.LENGTH_SHORT).show();}).setNegativeButton("Закрыть",null).show();}
    @Override public void onBackPressed(){} // Do not close home screen accidentally
    @Override public boolean onKeyDown(int key,KeyEvent e){if(key==KeyEvent.KEYCODE_MENU){showSettings();return true;}return super.onKeyDown(key,e);}
    class ScenicBackground extends Drawable {
        Paint p=new Paint(3);@Override public void draw(Canvas c){android.graphics.Rect r=getBounds();int w=r.width(),h=r.height();p.setShader(new LinearGradient(0,0,0,h,new int[]{0xffb879a6,0xffe7a2bf,0xffa77eae},null,Shader.TileMode.CLAMP));c.drawRect(r,p);p.setShader(null);
            mountain(c,w,h,0.61f,0xffb98eae,0.045f);mountain(c,w,h,0.72f,0xff97799c,0.09f);mountain(c,w,h,0.87f,0xff806c90,0.04f);
        }
        void mountain(Canvas c,int w,int h,float level,int color,float variation){p.setColor(color);Path path=new Path();path.moveTo(0,h);path.lineTo(0,h*level);for(int i=0;i<=12;i++){float x=w*i/12f;float y=h*(level+(float)Math.sin(i*1.8)*variation);path.lineTo(x,y);}path.lineTo(w,h);path.close();c.drawPath(path,p);}
        @Override public void setAlpha(int a){}@Override public void setColorFilter(android.graphics.ColorFilter f){}@Override public int getOpacity(){return android.graphics.PixelFormat.OPAQUE;}
    }
}
