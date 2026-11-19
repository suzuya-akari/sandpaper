package com.pw.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast

class MainActivity : Activity() {
    val prefs by lazy { getSharedPreferences("pw", MODE_PRIVATE) }
    private val pickCode = 42
    private val targets = listOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold")
    private lateinit var menu: MenuView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        menu = MenuView(this)
        setContentView(menu)
        immersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun savedUri(): Uri? = prefs.getString("uri", null)?.let(Uri::parse)

    fun hasDisc(): Boolean = savedUri() != null

    fun pickDisc() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(i, pickCode)
    }

    @Deprecated("fine for a tiny launcher")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (req != pickCode || res != RESULT_OK) return
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit().putString("uri", uri.toString()).apply()
        menu.invalidate()
    }

    fun launchGame() {
        val uri = savedUri() ?: return
        for (pkg in targets) {
            val i = Intent(Intent.ACTION_VIEW).apply {
                setClassName(pkg, "org.ppsspp.ppsspp.PpssppActivity")
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try { startActivity(i); return }
            catch (_: ActivityNotFoundException) {}
            catch (_: SecurityException) {}
        }
        Toast.makeText(this, "PPSSPP not found or disc not readable", Toast.LENGTH_LONG).show()
    }

    @Deprecated("fine for a tiny launcher")
    override fun onBackPressed() {
        if (!menu.back()) super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (menu.onKey(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }
}
