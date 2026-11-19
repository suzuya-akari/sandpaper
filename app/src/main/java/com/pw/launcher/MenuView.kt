package com.pw.launcher

import android.graphics.*
import android.os.SystemClock
import android.text.StaticLayout
import android.text.TextPaint
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Whole launcher UI drawn on a 1920x1080 virtual canvas, letterboxed to the screen. */
class MenuView(private val act: MainActivity) : View(act) {
    enum class S { TITLE, MAIN, GAME, OPTIONS, LANG, SCREEN, INFO }

    private val W = 1920f
    private val H = 1080f
    private val YEL = 0xFFFFC800.toInt()
    private val GRAY_TXT = 0xFF8C8C8C.toInt()

    private val opts = BitmapFactory.Options().apply { inScaled = false }
    private val bgTitle = BitmapFactory.decodeResource(resources, R.drawable.bg_title, opts)
    private val bgMenu = BitmapFactory.decodeResource(resources, R.drawable.bg_menu, opts)
    private val ring = BitmapFactory.decodeResource(resources, R.drawable.ring, opts)

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private var s = S.TITLE
    private var sel = 0
    private val stack = ArrayList<Pair<S, Int>>()
    private var infoTitle = ""
    private var infoBody = ""
    // animation clock
    private var started = false
    private var enterStart = 0L
    private var lastT = 0L
    private var nowMs = 0L
    private var raw = 1f   // linear 0..1 progress of the current screen's enter animation
    private var en = 1f    // eased version of raw
    private var selY = 0f
    private var snap = true
    private val scan = Paint().apply {
        val b = Bitmap.createBitmap(1, 4, Bitmap.Config.ARGB_8888)
        b.setPixel(0, 0, 0x30000000)
        for (y in 1..3) b.setPixel(0, y, 0)
        shader = BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    init {
        setWillNotDraw(false)
        isFocusable = true
    }

    private val langs = listOf("English", "French", "Italian", "German", "Spanish", "Portuguese (Brazil)")
    private val rowNames = listOf("Resolution Settings", "Internal Resolution", "Internal Upscaling", "Cutscenes", "Restore Defaults")
    private val rowOpts = listOf(
        listOf("Original", "Custom"),
        listOf("Original", "2x", "3x", "4x", "5x"),
        listOf("Default", "Off", "On"),
        listOf("Original", "Upscaled"),
        emptyList<String>()
    )
    private val rowDesc = listOf(
        "Choose Original to leave the game's own resolution alone, or Custom to use the options below.",
        "Rendering scale. Higher looks sharper but needs a faster device.",
        "Chooses how textures are upscaled.",
        "Choose whether cutscenes use the original or the upscaled video.",
        "Reset every option on this page."
    )
    private val mainDesc = "After the loss of The Boss, Big Boss forms a private mercenary army and sets out to acquire a Metal Gear. Infiltrate strongholds, grow Mother Base and take on co-op missions."

    // ---------- state helpers ----------
    private fun anim() {
        enterStart = SystemClock.uptimeMillis(); snap = true
    }

    private fun ease(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)

    private fun fa(col: Int, k: Float): Int =
        (col and 0x00FFFFFF) or ((Color.alpha(col) * k.coerceIn(0f, 1f)).toInt() shl 24)

    private fun mix(a: Int, b: Int, k: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt())

    private fun go(next: S) {
        stack.add(s to sel); s = next; sel = 0; anim(); invalidate()
    }

    private fun info(title: String, body: String) {
        infoTitle = title; infoBody = body; go(S.INFO)
    }

    /** Returns false when already at the title screen (caller should leave the app). */
    fun back(): Boolean {
        if (stack.isEmpty()) return false
        val (ps, psel) = stack.removeAt(stack.size - 1)
        s = ps; sel = psel; anim(); invalidate()
        return true
    }

    private fun rv(i: Int) = act.prefs.getInt("row$i", 0)
    private fun enabled(i: Int) = i == 0 || i == 4 || rv(0) == 1

    private fun items(): List<String> = when (s) {
        S.MAIN -> listOf("Game Selection", "Master Book", "Screenplay Book", "Options", "Legal and Privacy")
        S.OPTIONS -> listOf("Language", "Screen", "Audio", "Notices", "Credits")
        S.GAME -> if (act.hasDisc()) listOf("Start Mission", "Change Disc") else listOf("Select Disc")
        S.LANG -> langs
        else -> emptyList()
    }

    private fun move(d: Int) {
        if (s == S.SCREEN) {
            var i = sel
            do { i = (i + d + 5) % 5 } while (!enabled(i))
            sel = i; invalidate(); return
        }
        val n = items().size
        if (n > 0) { sel = (sel + d + n) % n; invalidate() }
    }

    private fun change(d: Int) {
        if (s != S.SCREEN || sel == 4 || !enabled(sel)) return
        val n = rowOpts[sel].size
        act.prefs.edit().putInt("row$sel", (rv(sel) + d + n) % n).apply()
        invalidate()
    }

    private fun restore() {
        val e = act.prefs.edit()
        for (i in 0..3) e.remove("row$i")
        e.apply(); sel = 0; invalidate()
    }

    private fun activate() {
        when (s) {
            S.TITLE -> go(S.MAIN)
            S.MAIN -> when (sel) {
                0 -> go(S.GAME)
                1 -> info("Master Book", "Not available in this launcher yet.")
                2 -> info("Screenplay Book", "Not available in this launcher yet.")
                3 -> go(S.OPTIONS)
                else -> info("Legal and Privacy", "This launcher only stores a link to your own disc image on this device and sends nothing anywhere. It is an unofficial fan project and is not affiliated with Konami.")
            }
            S.GAME -> if (act.hasDisc() && sel == 0) act.launchGame() else act.pickDisc()
            S.OPTIONS -> when (sel) {
                0 -> { go(S.LANG); sel = act.prefs.getInt("lang", 0) }
                1 -> go(S.SCREEN)
                2 -> info("Audio", "No audio options yet.")
                3 -> info("Notices", "PPSSPP must be installed. Pick your own legally obtained Peace Walker disc image.")
                else -> info("Credits", "Metal Gear Solid: Peace Walker is a trademark and work of Konami. This launcher is an unofficial fan project.")
            }
            S.LANG -> { act.prefs.edit().putInt("lang", sel).apply(); invalidate() }
            S.SCREEN -> if (sel == 4) restore() else change(1)
            S.INFO -> back()
        }
    }

    // ---------- input ----------
    fun onKey(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> move(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(1)
            KeyEvent.KEYCODE_DPAD_LEFT -> change(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> change(1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> activate()
            KeyEvent.KEYCODE_BUTTON_B -> { if (!back()) act.finish() }
            else -> return false
        }
        return true
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_UP) return true
        val sc = min(width / W, height / H)
        val x = (e.x - (width - W * sc) / 2) / sc
        val y = (e.y - (height - H * sc) / 2) / sc
        if (s == S.TITLE) { activate(); return true }
        if (y > 1004f && x > 1400f) { if (!back()) act.finish(); return true }
        when (s) {
            S.SCREEN -> {
                val i = ((y - 132f) / 71f).toInt()
                if (y >= 132f && i in 0..4 && x in 130f..1312f && enabled(i)) {
                    sel = i
                    if (i == 4) restore()
                    else if (x >= 700f) change(if (x > 1067f) 1 else -1)
                }
            }
            S.INFO -> back()
            else -> {
                val list = items()
                val i = ((y - 522f) / 69.5f).toInt()
                if (x >= 1186f && y >= 522f && i in list.indices && y - 522f - i * 69.5f <= 58f) {
                    sel = i; activate()
                }
            }
        }
        invalidate()
        return true
    }

    // ---------- drawing ----------
    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        p.style = Paint.Style.FILL; p.color = color; c.drawRect(l, t, r, b, p)
    }

    private fun txt(c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int,
                    bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        tp.textSize = size; tp.color = color; tp.textAlign = align
        tp.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        c.drawText(t, x, y, tp)
    }

    private fun para(c: Canvas, t: String, x: Float, y: Float, w: Int, size: Float, color: Int) {
        tp.textSize = size; tp.color = color; tp.textAlign = Paint.Align.LEFT; tp.typeface = Typeface.DEFAULT
        val l = StaticLayout.Builder.obtain(t, 0, t.length, tp, w).setLineSpacing(0f, 1.15f).build()
        c.save(); c.translate(x, y); l.draw(c); c.restore()
    }

    private fun tri(c: Canvas, cx: Float, cy: Float, dir: Int, color: Int) {
        val path = Path().apply {
            moveTo(cx + 9f * dir, cy); lineTo(cx - 9f * dir, cy - 11f); lineTo(cx - 9f * dir, cy + 11f); close()
        }
        p.style = Paint.Style.FILL; p.color = color; c.drawPath(path, p)
    }

    private fun footer(c: Canvas, k: Float = en) {
        txt(c, "D-pad  Move cursor      A  Confirm      B  Back", W - 50f, 1048f, 22f, fa(Color.WHITE, k), false, Paint.Align.RIGHT)
    }

    private fun darkBase(c: Canvas) {
        val po = (1f - en) * 160f
        // art: slow drift + slide in
        c.save()
        c.clipRect(0f, 0f, 1194f, H)
        val drift = sin(nowMs / 5000.0).toFloat() * 14f
        c.translate(597f + drift - (1f - en) * 40f, 540f)
        c.scale(1.1f, 1.1f)
        p.alpha = (255 * min(1f, en * 1.5f)).toInt()
        c.drawBitmap(bgMenu, null, RectF(-597f, -540f, 597f, 540f), p)
        p.alpha = 255
        c.restore()
        // panel
        rect(c, 1186f + po, 0f, W + 200f, H, 0xF2090909.toInt())
        // slowly rotating ring behind the list
        c.save()
        c.translate(1700f + po, 700f)
        c.rotate(nowMs / 1000f * 6f)
        p.alpha = 30
        c.drawBitmap(ring, null, RectF(-500f, -500f, 500f, 500f), p)
        p.alpha = 255
        c.restore()
        txt(c, "1974", 1208f + po, 150f, 100f, fa(Color.WHITE, en), true)
        txt(c, "Peace Walker Incident", 1214f + po, 190f, 24f, fa(0xFFBBBBBB.toInt(), en))
        val blink = 0.65f + 0.35f * sin(nowMs / 400.0).toFloat()
        txt(c, "!", 1880f + po, 112f, 76f, fa(YEL, en * blink), true, Paint.Align.CENTER)
        footer(c)
    }

    private fun kItem(i: Int) = ease((raw * 1.6f - i * 0.12f).coerceIn(0f, 1f))

    private fun drawList(c: Canvas, list: List<String>, label: String?) {
        val po = (1f - en) * 160f
        if (label != null) {
            txt(c, label, 1190f + po, 484f, 22f, fa(Color.WHITE, en))
            rect(c, 1186f + po, 494f, 1914f + po, 496f, fa(0x88FFFFFF.toInt(), en))
        }
        // idle item boxes
        list.forEachIndexed { i, _ ->
            val k = kItem(i)
            val top = 522f + i * 69.5f
            val r = RectF(1186f + (1f - k) * 90f, top, 1914f + (1f - k) * 90f, top + 58f)
            p.style = Paint.Style.FILL; p.color = fa(0x99000000.toInt(), k); c.drawRect(r, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = fa(0x88FFFFFF.toInt(), k); c.drawRect(r, p)
        }
        // animated selection bar
        if (list.isNotEmpty()) {
            val k = kItem(sel.coerceIn(0, list.size - 1))
            val xo = (1f - k) * 90f
            val pulse = 0.5f + 0.5f * sin(nowMs / 260.0).toFloat()
            rect(c, 1186f + xo, selY, 1914f + xo, selY + 58f, fa(mix(YEL, 0xFFFFE680.toInt(), pulse * 0.55f), k))
            rect(c, 1186f + xo, selY, 1194f + xo, selY + 58f, fa(Color.WHITE, k))
        }
        // labels
        list.forEachIndexed { i, t ->
            val k = kItem(i)
            val top = 522f + i * 69.5f
            val xo = (1f - k) * 90f
            if (i == sel) txt(c, t, 1208f + xo + 8f, top + 39f, 30f, fa(Color.BLACK, k), true)
            else txt(c, t, 1208f + xo, top + 38f, 28f, fa(Color.WHITE, k))
            if (s == S.LANG && i == act.prefs.getInt("lang", 0)) {
                p.style = Paint.Style.FILL; p.color = fa(if (i == sel) Color.BLACK else YEL, k)
                c.drawCircle(1880f + xo, top + 29f, 8f, p)
            }
        }
    }

    private fun grayBase(c: Canvas, title: String, hint: String) {
        rect(c, 0f, 0f, W, H, 0xFF111111.toInt())
        rect(c, 0f, 58f, W, 100f, Color.WHITE)
        txt(c, title, 100f - (1f - en) * 60f, 89f, 26f, fa(Color.BLACK, en), true)
        rect(c, 0f, 104f, W, 1004f, 0xFFD9D9D9.toInt())
        rect(c, 0f, 104f, W, 107f, 0xFF666666.toInt())
        rect(c, 0f, 1004f, W, H, 0xFF1A1A1A.toInt())
        txt(c, hint, 130f, 1050f, 24f, fa(Color.WHITE, en))
        footer(c)
    }

    private fun drawScreen(c: Canvas) {
        grayBase(c, "Screen", "You can adjust various settings to match your setup.")
        rect(c, 1319f, 107f, 1321f, 1004f, 0xFF999999.toInt())
        // row separators
        rowNames.forEachIndexed { i, _ ->
            val k = kItem(i)
            val top = 132f + i * 71f
            val xo = -(1f - k) * 120f
            rect(c, 130f + xo, top + 62f, 1312f + xo, top + 64f, fa(0xFFAAAAAA.toInt(), k))
        }
        // animated selection bar
        run {
            val k = kItem(sel)
            val xo = -(1f - k) * 120f
            val pulse = 0.5f + 0.5f * sin(nowMs / 260.0).toFloat()
            rect(c, 130f + xo, selY, 1312f + xo, selY + 62f, fa(mix(YEL, 0xFFFFE680.toInt(), pulse * 0.45f), k))
        }
        rowNames.forEachIndexed { i, name ->
            val k = kItem(i)
            val top = 132f + i * 71f
            val xo = -(1f - k) * 120f
            val on = enabled(i)
            val col = fa(if (on) Color.BLACK else GRAY_TXT, k)
            txt(c, name, 150f + xo, top + 41f, 28f, col, i == sel)
            if (i < 4) {
                val nudge = if (i == sel) sin(nowMs / 170.0).toFloat() * 5f else 0f
                tri(c, 861f + xo - nudge, top + 31f, -1, col)
                txt(c, rowOpts[i][rv(i)], 1067f + xo, top + 41f, 28f, col, false, Paint.Align.CENTER)
                tri(c, 1266f + xo + nudge, top + 31f, 1, col)
            }
        }
        val dk = min(1f, raw * 1.4f)
        para(c, rowDesc[sel], 1345f + (1f - dk) * 60f, 135f, 540, 28f, fa(Color.BLACK, dk))
        para(c, "These options are saved by the launcher. Set the same values in PPSSPP's graphics settings to apply them.",
            1345f + (1f - dk) * 60f, 420f, 540, 24f, fa(0xFF444444.toInt(), dk))
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); invalidate() }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); invalidate() }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        if (!started) { started = true; enterStart = now; lastT = now }
        val dt = ((now - lastT) / 1000f).coerceIn(0f, 0.05f)
        lastT = now; nowMs = now
        raw = ((now - enterStart) / 600f).coerceIn(0f, 1f)
        en = ease(raw)

        val ty = if (s == S.SCREEN) 132f + sel * 71f else 522f + sel * 69.5f
        if (snap) { selY = ty; snap = false } else selY += (ty - selY) * (1f - exp(-dt * 22f))

        c.drawColor(Color.BLACK)
        val sc = min(width / W, height / H)
        c.save()
        c.translate((width - W * sc) / 2, (height - H * sc) / 2)
        c.scale(sc, sc)
        c.clipRect(0f, 0f, W, H)

        when (s) {
            S.TITLE -> {
                val z = 1f + 0.05f * (0.5f + 0.5f * sin(now / 6000.0).toFloat())
                c.save()
                c.translate(W / 2, H / 2)
                c.scale(z, z)
                p.alpha = (255 * min(1f, en * 1.3f)).toInt()
                c.drawBitmap(bgTitle, null, RectF(-W / 2, -H / 2, W / 2, H / 2), p)
                p.alpha = 255
                c.restore()
                val a = (150 + 105 * sin(now / 350.0)).toInt()
                txt(c, "TOUCH SCREEN", W / 2, 1010f + (1f - en) * 30f, 34f,
                    Color.argb((a * en).toInt(), 255, 255, 255), true, Paint.Align.CENTER)
            }
            S.MAIN -> {
                darkBase(c)
                para(c, mainDesc, 1210f + (1f - en) * 160f, 215f, 680, 26f, fa(0xFFC8C8C8.toInt(), en))
                drawList(c, items(), null)
            }
            S.GAME -> {
                darkBase(c)
                val msg = if (act.hasDisc()) "Disc linked. Choose Start Mission to open PPSSPP."
                else "No disc linked. Select your Peace Walker disc image to begin."
                para(c, msg, 1210f + (1f - en) * 160f, 215f, 680, 26f, fa(0xFFC8C8C8.toInt(), en))
                drawList(c, items(), "Game Selection")
            }
            S.OPTIONS -> { darkBase(c); drawList(c, items(), "Options") }
            S.LANG -> { darkBase(c); drawList(c, items(), "Language Selection") }
            S.SCREEN -> drawScreen(c)
            S.INFO -> {
                grayBase(c, infoTitle, "")
                para(c, infoBody, 150f, 150f + (1f - en) * 40f, 1600, 34f, fa(Color.BLACK, en))
            }
        }

        // subtle CRT scanlines
        c.drawRect(0f, 0f, W, H, scan)
        // quick fade-through-black on every screen change
        val f = min(1f, raw * 2.5f)
        if (f < 1f) rect(c, 0f, 0f, W, H, Color.argb(((1f - f) * 255).toInt(), 0, 0, 0))
        c.restore()
        postInvalidateOnAnimation()
    }
}
