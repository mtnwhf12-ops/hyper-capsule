package com.moamen.hypercapsule;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i){
    try{
      if(Settings.canDrawOverlays(c)){
        c.startForegroundService(new Intent(c, IslandService.class));
      }
    }catch(Exception e){}
  }
}
