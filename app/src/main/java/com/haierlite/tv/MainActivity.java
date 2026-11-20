package com.haierlite.tv;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.ComponentName;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.KeyEvent;
import android.widget.*;
import java.util.*;
import java.text.SimpleDateFormat;

public class MainActivity extends Activity {
    private LinearLayout appRow;
    private TextView clock;
    private final android.os.Handler clockHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable clockTick = new Runnable(){ public void run(){ if(clock != null) clock.setText(new SimpleDateFormat("HH:mm  •  dd.MM",Locale.getDefault()).format(new Date())); clockHandler.postDelayed(this,60000); } };
    private final int WHITE=Color.WHITE, MUTED=0xffa6b1c8;
    private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g;}
    private TextView label(String text,int sp,int color){TextView v=new TextView(this);v.setText(text);v.setTextSize(sp);v.setTextColor(color);return v;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle b){super.onCreate(b);draw();}
    private void draw(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(42),dp(28),dp(42),dp(22));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff0a101d,0xff1b2440,0xff080d16}));setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=label("●  HAIER LITE",22,WHITE);brand.setTypeface(null,Typeface.BOLD);top.addView(brand,new LinearLayout.LayoutParams(0,dp(44),1));
        clock=label("",18,MUTED);top.addView(clock);root.addView(top);updateClock();
        TextView heading=label("Ваш телевизор. Ваш стиль.",30,WHITE);heading.setTypeface(null,Typeface.BOLD);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(32);root.addView(heading,hp);
        TextView sub=label("Приложения и развлечения — в одном месте",15,MUTED);root.addView(sub);
        TextView title=label("ПРИЛОЖЕНИЯ",14,MUTED);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(38);tp.bottomMargin=dp(15);root.addView(title,tp);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);appRow=new LinearLayout(this);appRow.setOrientation(LinearLayout.HORIZONTAL);appRow.setPadding(dp(3),dp(8),dp(3),dp(12));scroll.addView(appRow);root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(150)));
        TextView hint=label("◀ ▶  Выбор     OK  Открыть     ⟳  Обновить список",13,MUTED);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(20);root.addView(hint,pp);
        TextView exit=label("⚙  Настройки / Выход",15,WHITE);
        exit.setFocusable(true);exit.setClickable(true);exit.setPadding(dp(20),dp(12),dp(20),dp(12));
        exit.setBackground(bg(0xff26334d,dp(14)));
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-2,-2);ep.topMargin=dp(12);root.addView(exit,ep);
        exit.setOnClickListener(v->showExitMenu());
        exit.setOnFocusChangeListener((v,focused)->v.setBackground(bg(focused?0xff4269ad:0xff26334d,dp(14))));
        loadApps();
    }
    private void updateClock(){clockHandler.removeCallbacks(clockTick);clockTick.run();}
    @Override protected void onDestroy(){clockHandler.removeCallbacks(clockTick);super.onDestroy();}
    @Override protected void onResume(){super.onResume();if(appRow!=null)loadApps();}
    private void loadApps(){
        appRow.removeAllViews();PackageManager pm=getPackageManager();
        Map<String,ResolveInfo> found=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            Intent query=new Intent(Intent.ACTION_MAIN);query.addCategory(category);
            for(ResolveInfo r:pm.queryIntentActivities(query,0)){
                if(r.activityInfo==null||getPackageName().equals(r.activityInfo.packageName))continue;
                String key=r.activityInfo.packageName;
                if(!found.containsKey(key))found.put(key,r);
            }
        }
        ArrayList<ResolveInfo> apps=new ArrayList<>(found.values());
        Collections.sort(apps,(a,b)->String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));
        for(ResolveInfo r:apps){
            final String pkg=r.activityInfo.packageName;
            LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setFocusable(true);tile.setClickable(true);tile.setBackground(bg(0xff26334d,dp(18)));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(170),dp(118));lp.rightMargin=dp(16);appRow.addView(tile,lp);
            ImageView icon=new ImageView(this);try{icon.setImageDrawable(r.loadIcon(pm));}catch(Exception ignored){}
            tile.addView(icon,new LinearLayout.LayoutParams(dp(50),dp(50)));
            TextView name=label(String.valueOf(r.loadLabel(pm)),14,WHITE);name.setGravity(Gravity.CENTER);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(dp(152),dp(32));np.topMargin=dp(8);tile.addView(name,np);
            tile.setOnFocusChangeListener((v,focused)->{v.setBackground(bg(focused?0xff4269ad:0xff26334d,dp(18)));v.setScaleX(focused?1.05f:1f);v.setScaleY(focused?1.05f:1f);});
            tile.setOnClickListener(v->{try{Intent launch=pm.getLeanbackLaunchIntentForPackage(pkg);if(launch==null)launch=pm.getLaunchIntentForPackage(pkg);if(launch==null){launch=new Intent(Intent.ACTION_MAIN);launch.setComponent(new ComponentName(pkg,r.activityInfo.name));}startActivity(launch);}catch(Exception ex){Toast.makeText(this,"Не удалось открыть приложение",Toast.LENGTH_SHORT).show();}});
        }
        if(appRow.getChildCount()>0)appRow.getChildAt(0).requestFocus();
        else {TextView empty=label("Приложения не найдены",18,WHITE);appRow.addView(empty);}
    }
    private void showExitMenu(){
        new AlertDialog.Builder(this).setTitle("Haier Lite TV")
          .setItems(new String[]{"Настройки Android", "Выбрать другой главный экран", "Закрыть лаунчер"},(d,which)->{
              if(which==0){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception e){Toast.makeText(this,"Настройки недоступны",Toast.LENGTH_SHORT).show();}}
              else if(which==1){try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Exception e){Toast.makeText(this,"Выбор главного экрана недоступен на этой прошивке",Toast.LENGTH_LONG).show();}}
              else {finish();}
          }).setNegativeButton("Отмена",null).show();
    }
    @Override public void onBackPressed(){
        // Home launcher: Back must not accidentally close the home screen.
    }
    @Override public boolean onKeyDown(int keyCode,KeyEvent event){if(keyCode==KeyEvent.KEYCODE_MENU||keyCode==KeyEvent.KEYCODE_REFRESH){loadApps();return true;}return super.onKeyDown(keyCode,event);}
}
