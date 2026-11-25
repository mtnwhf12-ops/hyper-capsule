package com.moamen.hypercapsule;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotifListener extends NotificationListenerService {
  public void onNotificationPosted(StatusBarNotification sbn){
    if(sbn.getPackageName().equals(getPackageName())) return;
    if(sbn.isOngoing()) return;
    Bundle e=sbn.getNotification().extras;
    CharSequence t=e.getCharSequence("android.title");
    CharSequence x=e.getCharSequence("android.text");
    Drawable icon=null;
    try{
      android.app.Notification n=sbn.getNotification();
      if(n.getSmallIcon()!=null) icon=n.getSmallIcon().loadDrawable(this);
    }catch(Exception ex){}
    IslandService s=IslandService.inst;
    if(s!=null) s.showNotif(t==null?"":t.toString(), x==null?"":x.toString(), icon);
  }
}
