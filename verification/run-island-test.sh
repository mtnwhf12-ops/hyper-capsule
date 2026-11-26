#!/usr/bin/env bash
# Runtime verification for Hyper Capsule on an emulator (CI).
# Produces screenshots + logs under shots/ as artifacts.
set -u
PKG=com.moamen.hypercapsule
INJ=com.test.mediainj
mkdir -p shots

adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do sleep 5; done
echo "== boot complete =="

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r verification/mediainj/app/build/outputs/apk/debug/app-debug.apk

adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
CUR=$(adb shell settings get secure enabled_notification_listeners | tr -d '\r')
LSN=$PKG/$PKG.NotifListener
case "$CUR" in
  *$LSN*) ;;
  ""|null) adb shell settings put secure enabled_notification_listeners $LSN ;;
  *) adb shell settings put secure enabled_notification_listeners "$CUR:$LSN" ;;
esac
adb shell pm grant $PKG android.permission.READ_PHONE_STATE 2>/dev/null
adb shell pm grant $INJ android.permission.POST_NOTIFICATIONS 2>/dev/null
sleep 2
adb shell settings get secure enabled_notification_listeners > shots/listeners.txt

echo "== 01 setup UI =="
adb shell am start -n $PKG/.MainActivity
sleep 4
adb shell screencap -p /sdcard/01_setup.png

echo "== 02 idle island over launcher =="
adb shell am start-foreground-service -n $PKG/.IslandService
sleep 2
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell screencap -p /sdcard/02_idle.png

echo "== launch injector once (creates MediaSession) =="
adb shell am start -n $INJ/.MainActivity
sleep 3
adb shell input keyevent KEYCODE_HOME
sleep 2

echo "== 03 music via MediaSession path =="
adb shell am broadcast -a com.test.mediainj.CMD --es cmd session_play
sleep 5
adb shell screencap -p /sdcard/03_music_session.png

adb shell am broadcast -a com.test.mediainj.CMD --es cmd session_pause
sleep 3

echo "== 04 music via MediaStyle notification fallback =="
adb shell am broadcast -a com.test.mediainj.CMD --es cmd notif_post
sleep 5
adb shell screencap -p /sdcard/04_music_notif.png

echo "== 05 plain notification flash =="
adb shell cmd notification post -S bigtext -t "Test Notification Title" testapp tag1 "Test notification body text" 2>/dev/null
sleep 3
adb shell screencap -p /sdcard/05_notif_flash.png

echo "== 06 after collapse =="
sleep 6
adb shell screencap -p /sdcard/06_after_collapse.png

adb shell am broadcast -a com.test.mediainj.CMD --es cmd notif_remove 2>/dev/null

for f in 01_setup 02_idle 03_music_session 04_music_notif 05_notif_flash 06_after_collapse; do
  adb pull /sdcard/$f.png shots/$f.png || echo "MISSING $f"
done
adb logcat -d -t 800 > shots/logcat_tail.txt
ls -la shots/
