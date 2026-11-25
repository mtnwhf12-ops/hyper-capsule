package com.moamen.hypercapsule

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private val BG = 0xFF0B0B0D.toInt()
    private val CARD = 0xFF17171B.toInt()
    private val ACCENT = 0xFFFF6900.toInt()
    private val GREEN = 0xFF34C759.toInt()
    private val GRAY = 0xFF9A9AA0.toInt()

    private lateinit var stOverlay: TextView
    private lateinit var stNotif: TextView
    private lateinit var btnOverlay: TextView
    private lateinit var btnNotif: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        scroll.addView(root)
        setContentView(scroll)

        // respect status bar / nav bar insets (fixes clipped top control)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        root.addView(TextView(this).apply {
            text = "Hyper Capsule"
            setTextColor(Color.WHITE); textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "الإصدار ${appVersion()} — جزيرة تفاعلية حوالين الكاميرا"
            setTextColor(GRAY); textSize = 13f; gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(18))
        })

        // permissions card
        root.addView(card().apply {
            addView(cardTitle("الصلاحيات المطلوبة"))
            stOverlay = statusLine()
            btnOverlay = smallBtn("تفعيل صلاحية الظهور فوق التطبيقات") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            addView(stOverlay); addView(btnOverlay)
            stNotif = statusLine()
            btnNotif = smallBtn("تفعيل صلاحية قراءة الإشعارات") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            addView(stNotif); addView(btnNotif)
        })

        // main controls
        root.addView(bigBtn("تشغيل الجزيرة ▶", ACCENT, Color.BLACK) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startForegroundService(Intent(this, IslandService::class.java))
                finish()
            }
        })
        root.addView(bigBtn("إيقاف الجزيرة ⏹", CARD, Color.WHITE) {
            stopService(Intent(this, IslandService::class.java))
        })

        // try-it card
        root.addView(card().apply {
            addView(cardTitle("جرّب الجزيرة"))
            addView(smallBtn("مؤقت دقيقة واحدة") { startSvc("TIMER", 60) })
            addView(smallBtn("مؤقت ٥ دقائق") { startSvc("TIMER", 300) })
            addView(smallBtn("ساعة إيقاف ⏱ (تشغيل/إيقاف)") { startSvc("STOPWATCH", 0) })
        })

        // xiaomi note
        root.addView(card().apply {
            addView(cardTitle("مهم في شاومي"))
            addView(TextView(this@MainActivity).apply {
                text = "من الإعدادات ← التطبيقات ← Hyper Capsule: فعّل التشغيل التلقائي، وخلي توفير البطارية «بدون قيود»، وفعّل «إظهار النوافذ المنبثقة في الخلفية» — وإلا النظام هيوقف الجزيرة."
                setTextColor(GRAY); textSize = 13f; setPadding(0, 0, 0, dp(4))
            })
        })

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS", "android.permission.READ_PHONE_STATE"), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val ov = Settings.canDrawOverlays(this)
        stOverlay.text = if (ov) "الظهور فوق التطبيقات: مفعّلة ✓" else "الظهور فوق التطبيقات: غير مفعّلة"
        stOverlay.setTextColor(if (ov) GREEN else GRAY)
        btnOverlay.visibility = if (ov) View.GONE else View.VISIBLE
        val nl = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val ok = nl.contains(packageName)
        stNotif.text = if (ok) "قراءة الإشعارات: مفعّلة ✓" else "قراءة الإشعارات: غير مفعّلة"
        stNotif.setTextColor(if (ok) GREEN else GRAY)
        btnNotif.visibility = if (ok) View.GONE else View.VISIBLE
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(18).toFloat() }
        setPadding(dp(16), dp(14), dp(16), dp(8))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    }

    private fun cardTitle(t: String): TextView = TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, dp(8))
    }

    private fun statusLine(): TextView = TextView(this).apply {
        textSize = 13f; setPadding(0, dp(4), 0, dp(2))
    }

    private fun smallBtn(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
        background = GradientDrawable().apply { setColor(0xFF26262C.toInt()); cornerRadius = dp(14).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(6); bottomMargin = dp(6) }
        setOnClickListener { onClick() }
    }

    private fun bigBtn(label: String, bg: Int, fg: Int, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label; setTextColor(fg); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(24).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14) }
        setOnClickListener { onClick() }
    }

    private fun startSvc(act: String, sec: Int) {
        val i = Intent(this, IslandService::class.java)
        i.action = act
        i.putExtra("sec", sec)
        startService(i)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
