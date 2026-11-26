#!/usr/bin/env bash
# Runtime verification for Hyper Capsule on an emulator (CI).
# FAILS LOUDLY when a setup step does not take effect; screenshots alone are not proof.
set -u
PKG=com.moamen.hypercapsule
INJ=com.test.mediainj
LSN=$PKG/$PKG.NotifListener
mkdir -p shots

adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do sleep 5; done
echo "== boot complete =="
adb logcat -c

adb install -r app/build/outputs/apk/debug/app-debug.apk || exit 1
adb install -r verification/mediainj/app/build/outputs/apk/debug/app-debug.apk || exit 1

echo "== grant overlay + listener =="
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
OV=$(adb shell appops get $PKG SYSTEM_ALERT_WINDOW | tr -d '\r')
echo "appops: $OV"
echo "$OV" | grep -q "allow" || { echo "FATAL: overlay op not allowed"; exit 1; }

adb shell settings put secure enabled_notification_listeners $LSN
sleep 2
CUR=$(adb shell settings get secure enabled_notification_listeners | tr -d '\r')
echo "listeners now: $CUR"
echo "$CUR" > shots/listeners.txt
echo "$CUR" | grep -q "$LSN" || { echo "FATAL: notification listener not enabled"; exit 1; }

adb shell pm grant $PKG android.permission.READ_PHONE_STATE 2>/dev/null
adb shell pm grant $INJ android.permission.POST_NOTIFICATIONS 2>/dev/null

echo "== 01 setup UI =="
adb shell am start -n $PKG/.MainActivity
sleep 4
adb shell screencap -p /sdcard/01_setup.png

echo "== start IslandService (adb root bypasses exported=false; root only AFTER secure-settings writes) =="
adb root
sleep 2
adb wait-for-device
adb shell am start-foreground-service -n $PKG/.IslandService
sleep 3
adb shell dumpsys activity services $PKG > shots/services.txt
grep -q "IslandService" shots/services.txt || { echo "FATAL: IslandService not running"; cat shots/services.txt; exit 1; }
echo "IslandService running"

echo "== 02 idle island over launcher =="
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
  adb pull /sdcard/$f.png shots/$f.png || { echo "FATAL: missing $f"; exit 1; }
done
adb shell dumpsys notification > shots/dumpsys_notification.txt 2>/dev/null
adb shell dumpsys media_session > shots/dumpsys_media_session.txt 2>/dev/null
adb logcat -d > shots/logcat_full.txt
ls -la shots/
echo "== verification script completed =="
