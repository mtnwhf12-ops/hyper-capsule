package com.moamen.hypercapsule;
import android.app.Notification;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class NotifListener extends NotificationListenerService {
  static final String TAG="HyperCapsule";
  public void onCreate(){ super.onCreate(); Log.i(TAG,"NotifListener onCreate"); }
  public void onListenerConnected(){ Log.i(TAG,"NotifListener CONNECTED"); }
  public void onListenerDisconnected(){ Log.w(TAG,"NotifListener DISCONNECTED"); }

  public void onNotificationPosted(StatusBarNotification sbn){ handle(sbn); }
  public void onNotificationRemoved(StatusBarNotification sbn){
    if(sbn==null) return;
    IslandService s=IslandService.inst;
    if(s!=null) s.onNotifRemoved(sbn.getPackageName());
  }

  private void handle(StatusBarNotification sbn){
    if(sbn==null || sbn.getNotification()==null) return;
    Log.i(TAG,"notif posted pkg="+sbn.getPackageName()+" ongoing="+sbn.isOngoing());
    if(sbn.getPackageName().equals(getPackageName())) return;
    Notification n=sbn.getNotification();
    Bundle e=n.extras;
    if(e==null) return;

    boolean isMedia = e.containsKey("android.mediaSession") || "transport".equals(n.category);
    Log.i(TAG,"isMedia="+isMedia+" pkg="+sbn.getPackageName());

    if(isMedia){
      CharSequence t=e.getCharSequence("android.title");
      CharSequence x=e.getCharSequence("android.text");
      Drawable art=null;
      try{ if(n.getLargeIcon()!=null) art=n.getLargeIcon().loadDrawable(this); }catch(Exception ex){}
      android.app.PendingIntent prev=null, play=null, next=null;
      if(n.actions!=null){
        for(Notification.Action a : n.actions){
          if(a==null || a.actionIntent==null) continue;
          String ttl=a.title==null?"":a.title.toString().toLowerCase();
          if(prev==null && (ttl.contains("prev")||ttl.contains("السابق"))) prev=a.actionIntent;
          else if(next==null && (ttl.contains("next")||ttl.contains("التالي"))) next=a.actionIntent;
          else if(play==null) play=a.actionIntent; // middle/remaining action toggles
        }
        // if 3 actions and ordering guess failed, use positional fallback
        if(n.actions.length==3 && (play==null)){
          prev=n.actions[0].actionIntent; play=n.actions[1].actionIntent; next=n.actions[2].actionIntent;
        }
      }
      IslandService s=IslandService.inst;
      Log.i(TAG,"showMediaNotif svc="+(s!=null));
      if(s!=null) s.showMediaNotif(sbn.getPackageName(), t==null?"":t.toString(), x==null?"":x.toString(), art, prev, play, next, sbn.isOngoing());
      return;
    }

    if(sbn.isOngoing()) return;
    CharSequence t=e.getCharSequence("android.title");
    CharSequence x=e.getCharSequence("android.text");
    Drawable icon=null;
    try{ if(n.getSmallIcon()!=null) icon=n.getSmallIcon().loadDrawable(this); }catch(Exception ex){}
    IslandService s=IslandService.inst;
    Log.i(TAG,"showNotif svc="+(s!=null));
    if(s!=null) s.showNotif(t==null?"":t.toString(), x==null?"":x.toString(), icon);
  }
}
