package com.moamen.hypercapsule;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class IslandService extends Service {
  static IslandService inst;

  static final int M_IDLE=0, M_FLASH=1, M_MUSIC=2, M_CALL=3, M_CHARGE=4, M_UNLOCK=5, M_TIMER=6;

  WindowManager wm; FrameLayout pill; WindowManager.LayoutParams lp;
  Handler h=new Handler(Looper.getMainLooper());
  int W0,H0,W1,H1,W2,H2;
  int mode=-1; boolean musicExpanded=false;
  String lastTitle="",lastText=""; Drawable lastIcon=null;
  long flashUntil=0, chargeFlashUntil=0, unlockFlashUntil=0, timerEnd=0, swStart=0;
  boolean swRunning=false;

  LinearLayout root;
  LinearLayout rowCompact; ImageView imgIcon; TextView txtCompact;
  LinearLayout boxMusic; ImageView imgArt; TextView txtTitle, txtArtist;
  TextView btnPrev, btnPlay, btnNext;

  int callState=TelephonyManager.CALL_STATE_IDLE;
  boolean phoneRegistered=false;
  MediaSessionManager msm; MediaController mc;
  boolean musicPlaying=false; String songTitle="",songArtist=""; Bitmap art=null;
  boolean charging=false; int battLevel=0;

  public void onCreate(){
    super.onCreate(); inst=this;
    NotificationChannel ch=new NotificationChannel("island","Island",NotificationManager.IMPORTANCE_MIN);
    getSystemService(NotificationManager.class).createNotificationChannel(ch);
    Notification n=new Notification.Builder(this,"island")
      .setContentTitle("جزيرة مؤمن شغالة")
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE))
      .build();
    startForeground(1,n);

    wm=getSystemService(WindowManager.class);
    W0=dp(120); H0=dp(32); W1=dp(300); H1=dp(84); W2=dp(320); H2=dp(160);
    buildViews();
    lp=new WindowManager.LayoutParams(W0,H0,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
      PixelFormat.TRANSLUCENT);
    lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL; lp.y=dp(6);
    pill.setOnClickListener(v->onTap());
    wm.addView(pill,lp);

    // battery / charging
    IntentFilter bf=new IntentFilter();
    bf.addAction(Intent.ACTION_POWER_CONNECTED);
    bf.addAction(Intent.ACTION_POWER_DISCONNECTED);
    bf.addAction(Intent.ACTION_BATTERY_CHANGED);
    if(Build.VERSION.SDK_INT>=33) registerReceiver(batt,bf,Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(batt,bf);

    IntentFilter uf=new IntentFilter(Intent.ACTION_USER_PRESENT);
    if(Build.VERSION.SDK_INT>=33) registerReceiver(unlockRx,uf,Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(unlockRx,uf);

    // media sessions (uses the granted notification-listener permission)
    try{
      msm=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
      msm.addOnActiveSessionsChangedListener(sessListener,new ComponentName(this,NotifListener.class));
      pickSession();
    }catch(Exception e){}

    h.post(tick);
    refresh();
  }

  void buildViews(){
    pill=new FrameLayout(this);
    GradientDrawable bg=new GradientDrawable();
    bg.setColor(Color.BLACK); bg.setCornerRadius(dp(28));
    pill.setBackground(bg);

    root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER);
    pill.addView(root,new FrameLayout.LayoutParams(-1,-1));

    rowCompact=new LinearLayout(this);
    rowCompact.setOrientation(LinearLayout.HORIZONTAL);
    rowCompact.setGravity(Gravity.CENTER_VERTICAL);
    rowCompact.setPadding(dp(10),0,dp(12),0);
    imgIcon=new ImageView(this);
    rowCompact.addView(imgIcon,new LinearLayout.LayoutParams(dp(20),dp(20)));
    txtCompact=new TextView(this);
    txtCompact.setTextColor(Color.WHITE); txtCompact.setTextSize(12);
    txtCompact.setSingleLine(true); txtCompact.setEllipsize(TextUtils.TruncateAt.MARQUEE);
    txtCompact.setSelected(true);
    LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(0,-2,1f);
    tlp.setMargins(dp(8),0,0,0);
    rowCompact.addView(txtCompact,tlp);
    root.addView(rowCompact,new LinearLayout.LayoutParams(-1,-1));

    boxMusic=new LinearLayout(this);
    boxMusic.setOrientation(LinearLayout.VERTICAL);
    boxMusic.setGravity(Gravity.CENTER_HORIZONTAL);
    boxMusic.setPadding(dp(14),dp(10),dp(14),dp(10));

    LinearLayout mrow=new LinearLayout(this);
    mrow.setOrientation(LinearLayout.HORIZONTAL);
    mrow.setGravity(Gravity.CENTER_VERTICAL);
    imgArt=new ImageView(this);
    mrow.addView(imgArt,new LinearLayout.LayoutParams(dp(44),dp(44)));
    LinearLayout mcol=new LinearLayout(this);
    mcol.setOrientation(LinearLayout.VERTICAL);
    txtTitle=new TextView(this); txtTitle.setTextColor(Color.WHITE); txtTitle.setTextSize(14); txtTitle.setSingleLine(true); txtTitle.setEllipsize(TextUtils.TruncateAt.END);
    txtArtist=new TextView(this); txtArtist.setTextColor(Color.LTGRAY); txtArtist.setTextSize(12); txtArtist.setSingleLine(true); txtArtist.setEllipsize(TextUtils.TruncateAt.END);
    mcol.addView(txtTitle); mcol.addView(txtArtist);
    LinearLayout.LayoutParams mclp=new LinearLayout.LayoutParams(0,-2,1f);
    mclp.setMargins(dp(10),0,0,0);
    mrow.addView(mcol,mclp);
    boxMusic.addView(mrow,new LinearLayout.LayoutParams(-1,-2));

    LinearLayout brow=new LinearLayout(this);
    brow.setOrientation(LinearLayout.HORIZONTAL);
    brow.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-2,-2);
    blp.setMargins(dp(18),dp(8),dp(18),0);
    btnPrev=mkBtn("⏮"); btnPlay=mkBtn("⏯"); btnNext=mkBtn("⏭");
    brow.addView(btnPrev,blp); brow.addView(btnPlay,blp); brow.addView(btnNext,blp);
    boxMusic.addView(brow,new LinearLayout.LayoutParams(-1,-2));

    btnPrev.setOnClickListener(v->{ if(mc!=null) mc.getTransportControls().skipToPrevious(); });
    btnNext.setOnClickListener(v->{ if(mc!=null) mc.getTransportControls().skipToNext(); });
    btnPlay.setOnClickListener(v->{
      if(mc==null) return;
      if(musicPlaying) mc.getTransportControls().pause(); else mc.getTransportControls().play();
    });
    root.addView(boxMusic,new LinearLayout.LayoutParams(-1,-1));
  }

  TextView mkBtn(String s){
    TextView b=new TextView(this);
    b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(22);
    b.setGravity(Gravity.CENTER);
    b.setPadding(dp(8),dp(4),dp(8),dp(4));
    return b;
  }

  // ---- media sessions ----
  MediaSessionManager.OnActiveSessionsChangedListener sessListener=new MediaSessionManager.OnActiveSessionsChangedListener(){
    public void onActiveSessionsChanged(List<MediaController> sessions){ pickSession(); }
  };
  MediaController.Callback cb=new MediaController.Callback(){
    public void onMetadataChanged(MediaMetadata md){ readMeta(); refresh(); }
    public void onPlaybackStateChanged(PlaybackState st){ readState(); refresh(); }
    public void onSessionDestroyed(){ mc=null; musicPlaying=false; pickSession(); refresh(); }
  };
  void pickSession(){
    try{
      List<MediaController> ss=msm.getActiveSessions(new ComponentName(this,NotifListener.class));
      MediaController best=null;
      if(ss!=null){
        for(MediaController c:ss){ PlaybackState st=c.getPlaybackState(); if(st!=null && st.getState()==PlaybackState.STATE_PLAYING){ best=c; break; } }
        if(best==null && !ss.isEmpty()) best=ss.get(0);
      }
      if(best==mc) return;
      if(mc!=null) try{ mc.unregisterCallback(cb);}catch(Exception e){}
      mc=best;
      if(mc!=null){ try{ mc.registerCallback(cb);}catch(Exception e){} readMeta(); readState(); }
      else musicPlaying=false;
      refresh();
    }catch(Exception e){}
  }
  void readMeta(){
    if(mc==null) return;
    MediaMetadata md=mc.getMetadata();
    if(md!=null){
      songTitle=md.getString(MediaMetadata.METADATA_KEY_TITLE);
      if(songTitle==null) songTitle="";
      songArtist=md.getString(MediaMetadata.METADATA_KEY_ARTIST);
      if(songArtist==null) songArtist="";
      art=md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
      if(art==null) art=md.getBitmap(MediaMetadata.METADATA_KEY_ART);
    }
  }
  void readState(){
    if(mc==null){ musicPlaying=false; return; }
    PlaybackState st=mc.getPlaybackState();
    musicPlaying = st!=null && st.getState()==PlaybackState.STATE_PLAYING;
  }

  // ---- battery ----
  BroadcastReceiver batt=new BroadcastReceiver(){
    public void onReceive(Context c,Intent i){
      String a=i.getAction();
      if(Intent.ACTION_POWER_CONNECTED.equals(a)){ charging=true; chargeFlashUntil=System.currentTimeMillis()+4000; }
      else if(Intent.ACTION_POWER_DISCONNECTED.equals(a)){ charging=false; chargeFlashUntil=0; }
      else if(Intent.ACTION_BATTERY_CHANGED.equals(a)){
        int lv=i.getIntExtra(BatteryManager.EXTRA_LEVEL,0), sc=i.getIntExtra(BatteryManager.EXTRA_SCALE,100);
        if(sc>0) battLevel=lv*100/sc;
        charging=i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
      }
      refresh();
    }
  };

  BroadcastReceiver unlockRx=new BroadcastReceiver(){
    public void onReceive(Context c,Intent i){
      if(Intent.ACTION_USER_PRESENT.equals(i.getAction())){
        unlockFlashUntil=System.currentTimeMillis()+2500;
        refresh();
      }
    }
  };

  // ---- calls ----
  PhoneStateListener pl=new PhoneStateListener(){
    public void onCallStateChanged(int state,String num){ callState=state; refresh(); }
  };
  void maybeRegisterPhone(){
    if(phoneRegistered) return;
    if(checkSelfPermission("android.permission.READ_PHONE_STATE")!=PackageManager.PERMISSION_GRANTED) return;
    try{
      TelephonyManager tm=(TelephonyManager)getSystemService(TELEPHONY_SERVICE);
      tm.listen(pl,PhoneStateListener.LISTEN_CALL_STATE);
      phoneRegistered=true;
    }catch(Exception e){}
  }

  // ---- notifications ----
  void showNotif(String title,String text,Drawable icon){
    lastTitle=title==null?"":title; lastText=text==null?"":text; lastIcon=icon;
    flashUntil=System.currentTimeMillis()+5000;
    refresh();
  }

  void onTap(){
    if(mode==M_MUSIC){ musicExpanded=!musicExpanded; applyViews(); }
    else if(mode==M_TIMER){ timerEnd=0; swRunning=false; refresh(); }
    else if(mode==M_FLASH){ flashUntil=0; refresh(); }
    else if(mode==M_IDLE && lastText.length()>0){ flashUntil=System.currentTimeMillis()+10000; refresh(); }
  }

  Runnable tick=new Runnable(){ public void run(){
    maybeRegisterPhone();
    long now=System.currentTimeMillis();
    if(timerEnd>0 && now>=timerEnd){ timerEnd=0; lastTitle="انتهى المؤقت ⏰"; lastText=""; lastIcon=null; flashUntil=now+8000; }
    refresh(); h.postDelayed(this,1000); } };

  void refresh(){
    int m;
    long now=System.currentTimeMillis();
    if(callState!=TelephonyManager.CALL_STATE_IDLE) m=M_CALL;
    else if(timerEnd>now || swRunning) m=M_TIMER;
    else if(musicPlaying) m=M_MUSIC;
    else if(System.currentTimeMillis()<flashUntil) m=M_FLASH;
    else if(System.currentTimeMillis()<chargeFlashUntil) m=M_CHARGE;
    else if(System.currentTimeMillis()<unlockFlashUntil) m=M_UNLOCK;
    else m=M_IDLE;
    if(m!=M_MUSIC) musicExpanded=false;
    if(m!=mode){ mode=m; }
    applyViews();
  }

  void applyViews(){
    int w=W0, hgt=H0;
    boxMusic.setVisibility(View.GONE);
    rowCompact.setVisibility(View.VISIBLE);
    imgIcon.setVisibility(View.GONE);
    switch(mode){
      case M_IDLE:
        txtCompact.setText("");
        if(charging){ imgIcon.setVisibility(View.VISIBLE); imgIcon.setImageResource(android.R.drawable.ic_lock_idle_charging); txtCompact.setText(battLevel+"%"); }
        break;
      case M_FLASH:
        w=W1; hgt=H1;
        if(lastIcon!=null){ imgIcon.setVisibility(View.VISIBLE); imgIcon.setImageDrawable(lastIcon); }
        txtCompact.setText(lastTitle.length()==0?lastText:lastTitle+" · "+lastText);
        break;
      case M_CHARGE:
        w=dp(210); hgt=H0;
        imgIcon.setVisibility(View.VISIBLE); imgIcon.setImageResource(android.R.drawable.ic_lock_idle_charging);
        txtCompact.setText("جاري الشحن "+battLevel+"%");
        break;
      case M_UNLOCK:
        w=dp(180); hgt=H0;
        txtCompact.setText("تم الفتح ✓");
        break;
      case M_TIMER: {
        w=dp(200); hgt=dp(48);
        long now=System.currentTimeMillis();
        if(timerEnd>now){
          long r=(timerEnd-now+500)/1000;
          txtCompact.setText("⏳ "+(r/60)+":"+String.format("%02d",r%60));
        }else if(swRunning){
          long r=(now-swStart)/1000;
          txtCompact.setText("⏱ "+(r/60)+":"+String.format("%02d",r%60));
        }
        break;
      }
      case M_CALL:
        w=dp(240); hgt=dp(48);
        imgIcon.setVisibility(View.VISIBLE); imgIcon.setImageResource(android.R.drawable.sym_call_incoming);
        txtCompact.setText(callState==TelephonyManager.CALL_STATE_RINGING?"مكالمة واردة…":"مكالمة جارية");
        break;
      case M_MUSIC:
        if(musicExpanded){
          w=W2; hgt=H2;
          rowCompact.setVisibility(View.GONE);
          boxMusic.setVisibility(View.VISIBLE);
          if(art!=null) imgArt.setImageBitmap(art); else imgArt.setImageResource(android.R.drawable.ic_media_play);
          txtTitle.setText(songTitle); txtArtist.setText(songArtist);
          btnPlay.setText(musicPlaying?"⏸":"⏯");
        }else{
          w=dp(230); hgt=dp(56);
          if(art!=null){ imgIcon.setVisibility(View.VISIBLE); imgIcon.setImageBitmap(art); }
          txtCompact.setText(songTitle.length()==0?"موسيقى":songTitle);
        }
        break;
    }
    if(lp.width!=w || lp.height!=hgt) animateTo(w,hgt);
  }

  void animateTo(final int w,final int hgt){
    final int sw=lp.width, sh=lp.height;
    ValueAnimator a=ValueAnimator.ofFloat(0f,1f);
    a.setDuration(250); a.setInterpolator(new DecelerateInterpolator());
    a.addUpdateListener(an->{
      float f=(Float)an.getAnimatedValue();
      lp.width=sw+(int)((w-sw)*f); lp.height=sh+(int)((hgt-sh)*f);
      try{ wm.updateViewLayout(pill,lp);}catch(Exception e){}
    });
    a.start();
  }

  public int onStartCommand(Intent i,int flags,int id){
    if(i!=null && i.getAction()!=null){
      long now=System.currentTimeMillis();
      if("TIMER".equals(i.getAction())){ timerEnd=now+i.getIntExtra("sec",60)*1000L; swRunning=false; }
      else if("STOPWATCH".equals(i.getAction())){ if(swRunning){ swRunning=false; } else { swStart=now; swRunning=true; } timerEnd=0; }
      refresh();
    }
    return START_STICKY;
  }

  public IBinder onBind(Intent i){ return null; }
  public void onDestroy(){
    super.onDestroy(); inst=null;
    h.removeCallbacksAndMessages(null);
    try{ unregisterReceiver(batt);}catch(Exception e){}
    try{ unregisterReceiver(unlockRx);}catch(Exception e){}
    try{ if(msm!=null) msm.removeOnActiveSessionsChangedListener(sessListener);}catch(Exception e){}
    try{ if(mc!=null) mc.unregisterCallback(cb);}catch(Exception e){}
    try{ if(phoneRegistered){ ((TelephonyManager)getSystemService(TELEPHONY_SERVICE)).listen(pl,PhoneStateListener.LISTEN_NONE); } }catch(Exception e){}
    try{ wm.removeView(pill);}catch(Exception e){}
  }
  int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
}
