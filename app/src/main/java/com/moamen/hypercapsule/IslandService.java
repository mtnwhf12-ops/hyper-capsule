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
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Rect;
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
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class IslandService extends Service {
  static IslandService inst;

  static final int M_IDLE=0, M_FLASH=1, M_MUSIC=2, M_CALL=3, M_CHARGE=4, M_UNLOCK=5, M_TIMER=6;

  WindowManager wm; FrameLayout pill; WindowManager.LayoutParams lp;
  GradientDrawable pillBg;
  Handler h=new Handler(Looper.getMainLooper());
  int mode=-1; boolean musicExpanded=false;
  String lastTitle="",lastText=""; Drawable lastIcon=null;
  long flashUntil=0, chargeFlashUntil=0, unlockFlashUntil=0, timerEnd=0, swStart=0;
  boolean swRunning=false;

  // punch-hole geometry
  int holeCx,holeCy,holeW,holeH,baseTop,screenW;
  int W0,H0;

  LinearLayout root;
  LinearLayout rowCompact; ImageView imgIcon; TextView txtTitle2, txtSub;
  LinearLayout boxMusic; ImageView imgArt; TextView txtTitle, txtArtist;
  TextView btnPrev, btnPlay, btnNext;

  int callState=TelephonyManager.CALL_STATE_IDLE;
  boolean phoneRegistered=false;
  MediaSessionManager msm; MediaController mc;
  boolean musicPlaying=false; String songTitle="",songArtist=""; Bitmap art=null;
  boolean charging=false; int battLevel=0;
  float downY=0; boolean moved=false;

  public void onCreate(){
    super.onCreate(); inst=this;
    NotificationChannel ch=new NotificationChannel("island","Island",NotificationManager.IMPORTANCE_MIN);
    getSystemService(NotificationManager.class).createNotificationChannel(ch);
    Notification n=new Notification.Builder(this,"island")
      .setContentTitle("Hyper Capsule شغالة")
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE))
      .build();
    startForeground(1,n);

    wm=getSystemService(WindowManager.class);
    computeHole();
    buildViews();
    lp=new WindowManager.LayoutParams(W0,H0,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
      PixelFormat.TRANSLUCENT);
    lp.gravity=Gravity.TOP|Gravity.LEFT;
    lp.x=holeCx-W0/2; lp.y=baseTop;
    pill.setOnTouchListener(touch);
    wm.addView(pill,lp);

    IntentFilter bf=new IntentFilter();
    bf.addAction(Intent.ACTION_POWER_CONNECTED);
    bf.addAction(Intent.ACTION_POWER_DISCONNECTED);
    bf.addAction(Intent.ACTION_BATTERY_CHANGED);
    if(Build.VERSION.SDK_INT>=33) registerReceiver(batt,bf,Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(batt,bf);

    IntentFilter uf=new IntentFilter(Intent.ACTION_USER_PRESENT);
    if(Build.VERSION.SDK_INT>=33) registerReceiver(unlockRx,uf,Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(unlockRx,uf);

    try{
      msm=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
      msm.addOnActiveSessionsChangedListener(sessListener,new ComponentName(this,NotifListener.class));
      pickSession();
    }catch(Exception e){}

    h.post(tick);
    refresh();
  }

  // Find the camera punch-hole and anchor the island on it, like the native one.
  void computeHole(){
    Rect r=null;
    try{
      if(Build.VERSION.SDK_INT>=30){
        WindowMetrics m=wm.getCurrentWindowMetrics();
        screenW=m.getBounds().width();
        WindowInsets in=m.getWindowInsets();
        DisplayCutout c=in.getDisplayCutout();
        if(c!=null && !c.getBoundingRects().isEmpty()) r=c.getBoundingRects().get(0);
      }
    }catch(Exception e){}
    if(screenW<=0) screenW=getResources().getDisplayMetrics().widthPixels;
    if(r!=null && r.width()>0){
      holeCx=r.centerX(); holeCy=r.centerY(); holeW=r.width(); holeH=r.height();
    }else{
      holeCx=screenW/2; holeW=dp(26); holeH=dp(26); holeCy=dp(8)+holeH/2;
    }
    W0=Math.max(holeW+dp(18),dp(104));
    H0=Math.max(holeH+dp(8),dp(30));
    baseTop=holeCy-H0/2;
    if(baseTop<0) baseTop=0;
  }

  void buildViews(){
    pill=new FrameLayout(this);
    pillBg=new GradientDrawable();
    pillBg.setColor(Color.BLACK); pillBg.setCornerRadius(H0/2f);
    pill.setBackground(pillBg);
    pill.setElevation(dp(4));

    root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER);
    pill.addView(root,new FrameLayout.LayoutParams(-1,-1));

    // compact row: icon + two lines (bold title / gray sub)
    rowCompact=new LinearLayout(this);
    rowCompact.setOrientation(LinearLayout.HORIZONTAL);
    rowCompact.setGravity(Gravity.CENTER_VERTICAL);
    rowCompact.setPadding(dp(12),0,dp(14),0);
    imgIcon=new ImageView(this);
    LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(dp(22),dp(22));
    rowCompact.addView(imgIcon,ilp);
    LinearLayout col=new LinearLayout(this);
    col.setOrientation(LinearLayout.VERTICAL);
    txtTitle2=new TextView(this);
    txtTitle2.setTextColor(Color.WHITE); txtTitle2.setTextSize(12.5f);
    txtTitle2.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    txtTitle2.setSingleLine(true); txtTitle2.setEllipsize(TextUtils.TruncateAt.END);
    txtSub=new TextView(this);
    txtSub.setTextColor(0xFFB0B0B0); txtSub.setTextSize(11f);
    txtSub.setSingleLine(true); txtSub.setEllipsize(TextUtils.TruncateAt.END);
    col.addView(txtTitle2); col.addView(txtSub);
    LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,-2,1f);
    clp.setMargins(dp(8),0,0,0);
    rowCompact.addView(col,clp);
    root.addView(rowCompact,new LinearLayout.LayoutParams(-1,-1));

    // music card
    boxMusic=new LinearLayout(this);
    boxMusic.setOrientation(LinearLayout.VERTICAL);
    boxMusic.setGravity(Gravity.CENTER_HORIZONTAL);
    boxMusic.setPadding(dp(16),dp(12),dp(16),dp(12));

    LinearLayout mrow=new LinearLayout(this);
    mrow.setOrientation(LinearLayout.HORIZONTAL);
    mrow.setGravity(Gravity.CENTER_VERTICAL);
    imgArt=new ImageView(this);
    imgArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
    imgArt.setOutlineProvider(new ViewOutlineProvider(){
      public void getOutline(View v,Outline o){ o.setRoundRect(0,0,v.getWidth(),v.getHeight(),dp(10)); }
    });
    imgArt.setClipToOutline(true);
    mrow.addView(imgArt,new LinearLayout.LayoutParams(dp(52),dp(52)));
    LinearLayout mcol=new LinearLayout(this);
    mcol.setOrientation(LinearLayout.VERTICAL);
    txtTitle=new TextView(this); txtTitle.setTextColor(Color.WHITE); txtTitle.setTextSize(14f);
    txtTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    txtTitle.setSingleLine(true); txtTitle.setEllipsize(TextUtils.TruncateAt.END);
    txtArtist=new TextView(this); txtArtist.setTextColor(0xFFB0B0B0); txtArtist.setTextSize(12f);
    txtArtist.setSingleLine(true); txtArtist.setEllipsize(TextUtils.TruncateAt.END);
    mcol.addView(txtTitle); mcol.addView(txtArtist);
    LinearLayout.LayoutParams mclp=new LinearLayout.LayoutParams(0,-2,1f);
    mclp.setMargins(dp(12),0,0,0);
    mrow.addView(mcol,mclp);
    boxMusic.addView(mrow,new LinearLayout.LayoutParams(-1,-2));

    LinearLayout brow=new LinearLayout(this);
    brow.setOrientation(LinearLayout.HORIZONTAL);
    brow.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-2,-2);
    blp.setMargins(dp(20),dp(10),dp(20),0);
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
    b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(24);
    b.setGravity(Gravity.CENTER);
    b.setPadding(dp(8),dp(2),dp(8),dp(2));
    return b;
  }

  // tap = expand/dismiss, swipe up = dismiss
  View.OnTouchListener touch=new View.OnTouchListener(){
    public boolean onTouch(View v,MotionEvent e){
      switch(e.getAction()){
        case MotionEvent.ACTION_DOWN: downY=e.getRawY(); moved=false; return true;
        case MotionEvent.ACTION_MOVE:
          if(downY-e.getRawY()>dp(24)){ dismissCurrent(); moved=true; return true; }
          if(Math.abs(e.getRawY()-downY)>dp(8)) moved=true;
          return true;
        case MotionEvent.ACTION_UP:
          if(!moved) onTap();
          return true;
      }
      return false;
    }
  };

  void dismissCurrent(){
    if(mode==M_FLASH) flashUntil=0;
    if(mode==M_MUSIC && musicExpanded) musicExpanded=false;
    if(mode==M_TIMER){ timerEnd=0; swRunning=false; }
    if(mode==M_UNLOCK) unlockFlashUntil=0;
    if(mode==M_CHARGE) chargeFlashUntil=0;
    refresh();
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
    flashUntil=System.currentTimeMillis()+6000;
    refresh();
  }

  void onTap(){
    if(mode==M_MUSIC){ musicExpanded=!musicExpanded; applyViews(true); }
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
    else if(now<flashUntil) m=M_FLASH;
    else if(now<chargeFlashUntil) m=M_CHARGE;
    else if(now<unlockFlashUntil) m=M_UNLOCK;
    else m=M_IDLE;
    if(m!=M_MUSIC) musicExpanded=false;
    mode=m;
    applyViews(m!=M_IDLE);
  }

  void setCompact(String title,String sub){
    txtTitle2.setText(title==null?"":title);
    txtSub.setText(sub==null?"":sub);
    txtSub.setVisibility(txtSub.getText().length()==0?View.GONE:View.VISIBLE);
    txtTitle2.setVisibility(txtTitle2.getText().length()==0?View.GONE:View.VISIBLE);
  }

  void applyViews(){ applyViews(false); }

  void applyViews(boolean animate){
    int w=W0, hgt=H0;
    boxMusic.setVisibility(View.GONE);
    rowCompact.setVisibility(View.VISIBLE);
    imgIcon.setVisibility(View.GONE);
    switch(mode){
      case M_IDLE:
        setCompact("","");
        if(charging){ setCompact("⚡",""); }
        break;
      case M_FLASH:
        w=(int)(screenW*0.72f); hgt=Math.max(H0+dp(26),dp(64));
        if(lastIcon!=null){ imgIcon.setVisibility(View.VISIBLE); imgIcon.setImageDrawable(lastIcon); }
        setCompact(lastTitle,lastText);
        break;
      case M_CHARGE:
        w=Math.max(W0+dp(80),dp(200)); hgt=Math.max(H0+dp(10),dp(44));
        setCompact("⚡ جاري الشحن",battLevel+"%");
        break;
      case M_UNLOCK:
        w=Math.max(W0+dp(60),dp(170)); hgt=Math.max(H0+dp(6),dp(40));
        setCompact("تم الفتح ✓","");
        break;
      case M_TIMER: {
        w=Math.max(W0+dp(70),dp(190)); hgt=Math.max(H0+dp(12),dp(48));
        long now=System.currentTimeMillis();
        if(timerEnd>now){
          long r=(timerEnd-now+500)/1000;
          setCompact("⏳ مؤقت",(r/60)+":"+String.format("%02d",r%60));
        }else if(swRunning){
          long r=(now-swStart)/1000;
          setCompact("⏱ ساعة إيقاف",(r/60)+":"+String.format("%02d",r%60));
        }
        break;
      }
      case M_CALL:
        w=Math.max(W0+dp(100),dp(230)); hgt=Math.max(H0+dp(12),dp(50));
        setCompact(callState==TelephonyManager.CALL_STATE_RINGING?"📞 مكالمة واردة":"📞 مكالمة جارية","");
        break;
      case M_MUSIC:
        if(musicExpanded){
          w=(int)(screenW*0.86f); hgt=dp(168);
          rowCompact.setVisibility(View.GONE);
          boxMusic.setVisibility(View.VISIBLE);
          if(art!=null) imgArt.setImageBitmap(art); else imgArt.setImageResource(android.R.drawable.ic_media_play);
          txtTitle.setText(songTitle); txtArtist.setText(songArtist);
          btnPlay.setText(musicPlaying?"⏸":"⏯");
        }else{
          w=Math.max(W0+dp(110),dp(230)); hgt=Math.max(H0+dp(18),dp(56));
          if(art!=null){ imgIcon.setVisibility(View.VISIBLE); imgIcon.setImageBitmap(art); }
          setCompact(songTitle.length()==0?"🎵 موسيقى":songTitle,songArtist);
        }
        break;
    }
    if(lp.width!=w || lp.height!=hgt) animateTo(w,hgt,animate);
  }

  // grow/shrink around the hole: center-x pinned to the hole, top edge pinned under it
  void animateTo(final int w,final int hgt,boolean springy){
    final int sw=lp.width, sh=lp.height, sx=lp.x;
    final int tx=holeCx-w/2;
    ValueAnimator a=ValueAnimator.ofFloat(0f,1f);
    a.setDuration(springy?340:260);
    a.setInterpolator(springy?new OvershootInterpolator(0.9f):new AccelerateDecelerateInterpolator());
    a.addUpdateListener(an->{
      float f=(Float)an.getAnimatedValue();
      if(f>1f) f=1f;
      lp.width=sw+(int)((w-sw)*f);
      lp.height=sh+(int)((hgt-sh)*f);
      lp.x=sx+(int)((tx-sx)*f);
      lp.y=baseTop;
      pillBg.setCornerRadius(Math.min(lp.height/2f,dp(30)));
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
