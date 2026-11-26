package com.test.mediainj;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RemoteViews;

public class MainActivity extends Activity {
  static MainActivity instance;
  static MediaSession ms;
  static boolean playing=false;
  NotificationManager nm;

  public void onCreate(Bundle b){
    super.onCreate(b);
    instance=this;
    nm=getSystemService(NotificationManager.class);
    nm.createNotificationChannel(new NotificationChannel("m","Media",NotificationManager.IMPORTANCE_LOW));
    LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
    Button b1=new Button(this); b1.setText("Session: play/pause");
    b1.setOnClickListener(v->toggleSession());
    Button b2=new Button(this); b2.setText("MediaStyle notif: post/remove");
    b2.setOnClickListener(v->toggleNotif());
    l.addView(b1); l.addView(b2);
    setContentView(l);
    ensureSession();
  }

  void ensureSession(){
    if(ms==null){
      ms=new MediaSession(this,"inj");
      ms.setMetadata(new MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE,"Test Song Title")
        .putString(MediaMetadata.METADATA_KEY_ARTIST,"Test Artist")
        .putLong(MediaMetadata.METADATA_KEY_DURATION,240000).build());
      ms.setActive(true);
    }
  }

  void setState(boolean play){
    playing=play;
    ms.setPlaybackState(new PlaybackState.Builder()
      .setState(play?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,0,1f).build());
  }

  void toggleSession(){ setState(!playing); }

  PendingIntent act(String a){
    Intent i=new Intent(this,ActReceiver.class); i.setAction(a);
    return PendingIntent.getBroadcast(this,a.hashCode(),i,PendingIntent.FLAG_IMMUTABLE);
  }

  void toggleNotif(){ if(nm.getActiveNotifications().length>0){ removeNotif(); return; } postNotif(); }
  void removeNotif(){ nm.cancel(7); nm.cancel(9); }
  void plainNotif(){
    Notification n=new Notification.Builder(this,"m")
      .setSmallIcon(android.R.drawable.ic_dialog_email)
      .setContentTitle("Test Notification Title")
      .setContentText("Test notification body text")
      .build();
    nm.notify(9,n);
  }
  void postNotif(){
    Notification.Action prev=new Notification.Action.Builder(android.R.drawable.ic_media_previous,"Previous",act("prev")).build();
    Notification.Action play=new Notification.Action.Builder(android.R.drawable.ic_media_play,"Play",act("play")).build();
    Notification.Action next=new Notification.Action.Builder(android.R.drawable.ic_media_next,"Next",act("next")).build();
    Notification.MediaStyle style=new Notification.MediaStyle();
    style.setMediaSession((MediaSession.Token)ms.getSessionToken());
    style.setShowActionsInCompactView(0,1,2);
    Notification n=new Notification.Builder(this,"m")
      .setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle("Notif Song Title")
      .setContentText("Notif Artist")
      .setOngoing(true)
      .addAction(prev).addAction(play).addAction(next)
      .setStyle(style)
      .build();
    nm.notify(7,n);
  }
}
