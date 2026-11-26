package com.test.mediainj;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class CmdReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i){
    String cmd = i.getStringExtra("cmd");
    MainActivity a = MainActivity.instance;
    if (a == null || cmd == null) return;
    switch (cmd) {
      case "session_play": a.setState(true); break;
      case "session_pause": a.setState(false); break;
      case "notif_post": a.postNotif(); break;
      case "notif_remove": a.removeNotif(); break;
      case "plain_notif": a.plainNotif(); break;
    }
  }
}
