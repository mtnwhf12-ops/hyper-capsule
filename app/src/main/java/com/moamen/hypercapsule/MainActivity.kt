package com.moamen.hypercapsule

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Island controls, added under the original Hyper Capsule title
        val root = (findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as ViewGroup)
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        val p = dp(24)
        panel.setPadding(p, 0, p, p)
        root.addView(panel, ViewGroup.LayoutParams(-1, -2))

        fun btn(label: String, onClick: () -> Unit) {
            val b = Button(this)
            b.text = label
            b.setOnClickListener { onClick() }
            panel.addView(b)
        }

        btn("صلاحية الظهور فوق التطبيقات") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("صلاحية قراءة الإشعارات") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        btn("تشغيل الجزيرة ▶") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startForegroundService(Intent(this, IslandService::class.java))
                finish()
            }
        }
        btn("مؤقت ١ دقيقة") { startSvc("TIMER", 60) }
        btn("مؤقت ٥ دقائق") { startSvc("TIMER", 300) }
        btn("ساعة إيقاف ⏱ (تشغيل/إيقاف)") { startSvc("STOPWATCH", 0) }
        btn("إيقاف الجزيرة ⏹") {
            stopService(Intent(this, IslandService::class.java))
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS", "android.permission.READ_PHONE_STATE"), 1)
        }
    }

    private fun startSvc(act: String, sec: Int) {
        val i = Intent(this, IslandService::class.java)
        i.action = act
        i.putExtra("sec", sec)
        startService(i)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
