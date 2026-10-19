package com.techmodware.tester

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import rikka.shizuku.Shizuku
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class MainActivity : Activity() {

    private val passHash = "35044bdb19c15a8e293ed035b8e9beaba366832a62eebfa86b1e7d48a2725418"
    private val reqCode = 100

    private val C_BG = 0xFF070C2B.toInt()
    private val C_BG2 = 0xFF16206B.toInt()
    private val C_CARD = 0xFF141E4D.toInt()
    private val C_BTN = 0xFF3A3F7A.toInt()
    private val C_SUB = 0xFFB8C0E8.toInt()
    private val C_OK = 0xFF2E7D4F.toInt()
    private val C_BAD = 0xFF8A3B2E.toInt()
    private val C_GREEN = 0xFF6BFF3A.toInt()
    private val C_WARN = 0xFFFFB074.toInt()

    private class Mod(val title: String, val args: Array<String>)
    private class RunCard(val root: LinearLayout, val status: TextView, val out: TextView)

    private val modules = listOf(
        Mod("Graphics", arrayOf("hz")),
        Mod("Performance", arrayOf("perf")),
        Mod("Power", arrayOf("ppa", "batt")),
        Mod("OTG", arrayOf("otg")),
        Mod("Cleanup", arrayOf("clean"))
    )

    private lateinit var pill: TextView
    private lateinit var keyInput: EditText
    private lateinit var msg: TextView
    private lateinit var remember: CheckBox
    private lateinit var logBox: LinearLayout
    private var onGate = false

    private val prefs get() = getSharedPreferences("techmodware", Context.MODE_PRIVATE)

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread { if (onGate) refreshPill() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permListener)
        if (prefs.getBoolean("remember", false) && shizukuReady()) showMenu() else showGate()
    }

    override fun onResume() {
        super.onResume()
        if (onGate) refreshPill()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    // ---------- helper tampilan ----------
    private fun d(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bg(color: Int, radius: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = d(radius).toFloat()
        if (stroke != 0) setStroke(d(1), stroke)
    }

    private fun lp(w: Int, h: Int, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = d(top) }

    private fun tv(t: String, size: Float = 14f, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun btn(label: String, ghost: Boolean = false, onClick: () -> Unit) =
        TextView(this).apply {
            text = label; textSize = 16f; setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = if (ghost) bg(0x22FFFFFF, 16, 0x33FFFFFF) else bg(C_BTN, 16, 0x55FFFFFF)
            setPadding(d(14), d(14), d(14), d(14))
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = bg(C_CARD, 22, 0x33FFFFFF)
        setPadding(d(16), d(16), d(16), d(16))
    }

    private fun newScreen(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(18), d(36), d(18), d(28))
        }
        val sv = ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(C_BG, C_BG2))
            isFillViewport = true
            addView(col)
        }
        setContentView(sv)
        window.statusBarColor = C_BG
        return col
    }

    private fun sha256(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    // ---------- layar login ----------
    private fun showGate() {
        onGate = true
        val col = newScreen()
        col.addView(tv("TECHMODWARE", 32f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.1f; setPadding(0, d(40), 0, 0)
        }, lp(MATCH, WRAP))
        col.addView(tv("TESTER", 14f, C_SUB, true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.6f; setPadding(0, d(4), 0, d(40))
        }, lp(MATCH, WRAP))

        val card = card()
        pill = tv("", 15f, Color.WHITE, true).apply {
            setPadding(d(16), d(12), d(16), d(12))
            setOnClickListener { requestShizuku() }
        }
        card.addView(pill, lp(MATCH, WRAP))

        card.addView(tv("License key", 12f, C_SUB), lp(WRAP, WRAP, 18))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg(0x33000000, 14, 0x66FFFFFF)
            setPadding(d(14), 0, d(6), 0)
        }
        keyInput = EditText(this).apply {
            hint = "License key"
            setHintTextColor(C_SUB)
            setTextColor(Color.WHITE)
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        row.addView(keyInput, LinearLayout.LayoutParams(0, WRAP, 1f))
        var shown = false
        val eye = tv("👁", 20f).apply {
            setPadding(d(10), d(10), d(10), d(10))
            setOnClickListener {
                shown = !shown
                keyInput.inputType = InputType.TYPE_CLASS_TEXT or
                    (if (shown) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                     else InputType.TYPE_TEXT_VARIATION_PASSWORD)
                keyInput.setSelection(keyInput.text.length)
            }
        }
        row.addView(eye, lp(WRAP, WRAP))
        card.addView(row, lp(MATCH, WRAP, 4))

        remember = CheckBox(this).apply {
            text = "Remember me"; setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("remember", false)
        }
        card.addView(remember, lp(WRAP, WRAP, 12))
        card.addView(btn("Connect") { tryEnter() }, lp(MATCH, WRAP, 12))
        msg = tv("", 13f, C_WARN)
        card.addView(msg, lp(WRAP, WRAP, 8))

        col.addView(card, lp(MATCH, WRAP))
        refreshPill()
    }

    private fun refreshPill() {
        val ready = shizukuReady()
        pill.text = when {
            !Shizuku.pingBinder() -> "✕  Shizuku belum jalan (buka Shizuku lalu Start)"
            !ready -> "!  Ketuk di sini untuk authorize Shizuku"
            else -> "✔  Shizuku ready"
        }
        pill.background = bg(if (ready) C_OK else C_BAD, 24)
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) { refreshPill(); return }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(reqCode)
        }
    }

    private fun tryEnter() {
        if (!shizukuReady()) { msg.text = "Authorize Shizuku dulu (ketuk kotak status di atas)."; return }
        val input = keyInput.text.toString().trim().uppercase()
        if (sha256(input) != passHash) { msg.text = "License key salah."; return }
        prefs.edit().putBoolean("remember", remember.isChecked).apply()
        showMenu()
    }

    // ---------- menu utama ----------
    private fun section(t: String) = tv(t, 15f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
    }

    private fun showMenu() {
        onGate = false
        val col = newScreen()
        col.addView(tv("TECHMODWARE", 26f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.1f
        }, lp(MATCH, WRAP))
        col.addView(tv("TESTER", 12f, C_SUB, true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.6f
        }, lp(MATCH, WRAP))

        col.addView(btn("Inject all") { runModules(modules) }, lp(MATCH, WRAP, 20))

        col.addView(section("Inject by module"), lp(MATCH, WRAP, 18))
        modules.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { m ->
                row.addView(btn(m.title) { runModules(listOf(m)) },
                    LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                        setMargins(d(4), d(8), d(4), 0)
                    })
            }
            col.addView(row, lp(MATCH, WRAP))
        }

        col.addView(btn("↻  Revert optimizations", true) {
            runModules(listOf(Mod("Revert", arrayOf("revert"))))
        }, lp(MATCH, WRAP, 14))

        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r1.addView(tile("RAM", ramBig(), ramSub()), tileLp())
        r1.addView(tile("Battery", batteryBig(), batterySub()), tileLp())
        col.addView(r1, lp(MATCH, WRAP, 16))
        val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r2.addView(tile("Storage", storageBig(), storageSub()), tileLp())
        r2.addView(tile("Display", hzNow(), "refresh rate saat ini"), tileLp())
        col.addView(r2, lp(MATCH, WRAP))

        col.addView(section("Display (Resolusi)"), lp(MATCH, WRAP, 18))
        val res = listOf("560p" to "560", "720p" to "720", "1080p" to "1080",
            "1.5K" to "1200", "2K" to "1440", "4K" to "2160")
        res.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { (label, v) ->
                row.addView(btn(label) { runModules(listOf(Mod("Resolusi $label", arrayOf("res", v)))) },
                    LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                        setMargins(d(4), d(8), d(4), 0)
                    })
            }
            col.addView(row, lp(MATCH, WRAP))
        }
        col.addView(btn("Reset resolusi awal", true) {
            runModules(listOf(Mod("Reset resolusi", arrayOf("resreset"))))
        }, lp(MATCH, WRAP, 10))

        logBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(logBox, lp(MATCH, WRAP, 16))

        col.addView(section("Open your game"), lp(MATCH, WRAP, 18))
        col.addView(btn("Free Fire") { launchApp("com.dts.freefireth") }, lp(MATCH, WRAP, 8))
        col.addView(btn("Free Fire MAX") { launchApp("com.dts.freefiremax") }, lp(MATCH, WRAP, 8))
    }

    private fun tileLp() = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
        setMargins(d(4), d(4), d(4), d(4))
    }

    private fun tile(title: String, big: String, sub: String): LinearLayout = card().apply {
        addView(tv(title, 12f, C_SUB), lp(WRAP, WRAP))
        addView(tv(big, 20f, Color.WHITE, true), lp(WRAP, WRAP, 4))
        addView(tv(sub, 12f, C_SUB), lp(WRAP, WRAP, 2))
    }

    // ---------- info asli perangkat ----------
    private fun gb(b: Long) = String.format("%.1fGB", b / 1073741824.0)

    private fun memInfo(): ActivityManager.MemoryInfo {
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return mi
    }
    private fun ramBig() = gb(memInfo().totalMem)
    private fun ramSub() = gb(memInfo().totalMem - memInfo().availMem) + " terpakai"

    private fun batteryIntent() = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    private fun batteryBig(): String {
        val i = batteryIntent() ?: return "-"
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level < 0) "-" else "${level * 100 / scale}%"
    }
    private fun batterySub(): String {
        val t = batteryIntent()?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return String.format("%.1f°C", t / 10.0)
    }

    private fun storageBig(): String {
        val s = StatFs(Environment.getDataDirectory().path)
        return gb(s.blockCountLong * s.blockSizeLong)
    }
    private fun storageSub(): String {
        val s = StatFs(Environment.getDataDirectory().path)
        val total = s.blockCountLong * s.blockSizeLong
        val free = s.availableBlocksLong * s.blockSizeLong
        return "${(total - free) * 100 / total}% terpakai"
    }

    @Suppress("DEPRECATION")
    private fun hzNow() = String.format("%.0f Hz", windowManager.defaultDisplay.refreshRate)

    // ---------- menjalankan modul ----------
    private fun moduleCard(title: String): RunCard {
        val root = card()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(tv(title, 16f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, WRAP, 1f))
        val status = tv("… Berjalan", 13f, C_SUB, true)
        head.addView(status, lp(WRAP, WRAP))
        root.addView(head, lp(MATCH, WRAP))
        val out = tv("", 12f, C_GREEN).apply { typeface = Typeface.MONOSPACE }
        root.addView(out, lp(MATCH, WRAP, 8))
        return RunCard(root, status, out)
    }

    private fun tidy(s: String) = s.lines()
        .filter { it.isNotBlank() && !it.startsWith("Android SDK") && it != "Selesai." }
        .joinToString("\n")

    private fun runModules(list: List<Mod>) {
        if (!shizukuReady()) {
            Toast.makeText(this, "Shizuku belum siap / izin hilang", Toast.LENGTH_SHORT).show()
            return
        }
        logBox.removeAllViews()
        thread {
            var done = 0
            for (m in list) {
                val holder = arrayOfNulls<RunCard>(1)
                val latch = CountDownLatch(1)
                runOnUiThread {
                    val c = moduleCard(m.title)
                    logBox.addView(c.root, lp(MATCH, WRAP, 10))
                    holder[0] = c
                    latch.countDown()
                }
                latch.await()
                val out = try { ShellRunner.runScript(this, *m.args) }
                catch (e: Throwable) { "Error: ${e.message}" }
                val good = !out.startsWith("Error")
                if (good) done++
                runOnUiThread {
                    holder[0]?.status?.text = if (good) "✔ Selesai" else "✕ Gagal"
                    holder[0]?.status?.setTextColor(if (good) C_GREEN else C_WARN)
                    holder[0]?.out?.text = tidy(out)
                }
            }
            runOnUiThread {
                val c = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = bg(C_OK, 22)
                    setPadding(d(16), d(14), d(16), d(14))
                }
                c.addView(tv("Selesai", 18f, Color.WHITE, true), lp(WRAP, WRAP))
                c.addView(tv("$done/${list.size} modul dijalankan", 13f, C_GREEN, true), lp(WRAP, WRAP, 2))
                logBox.addView(c, lp(MATCH, WRAP, 10))
            }
        }
    }

    private fun launchApp(pkg: String) {
        if (!shizukuReady()) {
            Toast.makeText(this, "Shizuku belum siap", Toast.LENGTH_SHORT).show()
            return
        }
        thread {
            val out = try { ShellRunner.runScript(this, "launch", pkg) }
            catch (e: Throwable) { "Error: ${e.message}" }
            val last = tidy(out).lines().lastOrNull() ?: ""
            runOnUiThread { Toast.makeText(this, last, Toast.LENGTH_SHORT).show() }
        }
    }
}
