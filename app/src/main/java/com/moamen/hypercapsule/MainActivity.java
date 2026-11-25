package com.moamen.hypercapsule;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    LinearLayout l=new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    int p=dp(24); l.setPadding(p,p,p,p);
    TextView t=new TextView(this);
    t.setText("جزيرة مؤمن 🏝");
    t.setTextSize(26); t.setGravity(Gravity.CENTER); t.setTextColor(Color.BLACK);
    l.addView(t);
    TextView d=new TextView(this);
    d.setText("جزيرة تفاعلية فوق الشاشة، بتوصلها الإشعارات وتكبر معاها.\n\n١) فعّل صلاحية الظهور فوق التطبيقات.\n٢) فعّل صلاحية قراءة الإشعارات.\n٣) اضغط تشغيل الجزيرة.\n\nمهم في شاومي: الإعدادات ← التطبيقات ← جزيرة مؤمن، فعّل التشغيل التلقائي (Autostart) وخلي توفير البطارية «بدون قيود»، وفعّل «إظهار النوافذ المنبثقة في الخلفية»، عشان النظام ما يقفلهاش.");
    d.setTextSize(15); d.setTextColor(Color.DKGRAY);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
    lp.setMargins(0,dp(16),0,dp(16)); d.setLayoutParams(lp);
    l.addView(d);
    Button b1=new Button(this); b1.setText("صلاحية الظهور فوق التطبيقات");
    b1.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))));
    l.addView(b1);
    Button b2=new Button(this); b2.setText("صلاحية قراءة الإشعارات");
    b2.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
    l.addView(b2);
    Button b3=new Button(this); b3.setText("تشغيل الجزيرة ▶");
    b3.setOnClickListener(v->{
      if(!Settings.canDrawOverlays(this)){
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName())));
        return;
      }
      startForegroundService(new Intent(this, IslandService.class));
      finish();
    });
    l.addView(b3);
    Button b5=new Button(this); b5.setText("مؤقت ١ دقيقة");
    b5.setOnClickListener(v->startSvc("TIMER",60));
    l.addView(b5);
    Button b6=new Button(this); b6.setText("مؤقت ٥ دقائق");
    b6.setOnClickListener(v->startSvc("TIMER",300));
    l.addView(b6);
    Button b7=new Button(this); b7.setText("ساعة إيقاف ⏱ (تشغيل/إيقاف)");
    b7.setOnClickListener(v->startSvc("STOPWATCH",0));
    l.addView(b7);
    Button b4=new Button(this); b4.setText("إيقاف الجزيرة ⏹");
    b4.setOnClickListener(v->stopService(new Intent(this, IslandService.class)));
    l.addView(b4);
    setContentView(l);
    if(android.os.Build.VERSION.SDK_INT>=33){
      requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS","android.permission.READ_PHONE_STATE"},1);
    }
  }
  void startSvc(String act,int sec){
    Intent i=new Intent(this, IslandService.class);
    i.setAction(act); i.putExtra("sec",sec);
    startService(i);
  }
  int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
}
