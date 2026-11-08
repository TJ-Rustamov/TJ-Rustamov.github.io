package uz.camdect.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import uz.camdect.R
import uz.camdect.core.CameraDb
import uz.camdect.core.Control
import uz.camdect.data.TypeLabels

/** "Kamera ma'lumotlari" tab: every named camera type and every extra check, with short descriptions. */
object CameraInfoView {
    fun fill(list: LinearLayout, db: CameraDb?, onClose: () -> Unit) {
        val c = list.context
        list.removeAllViews()
        list.addView(titleBar(c, onClose))
        list.addView(header(c, c.getString(R.string.info_header)))
        for ((code, nameRes, descRes) in TypeLabels.KNOWN) {
            val sub = db?.let { c.getString(R.string.info_points, TypeLabels.count(it, code)) }
            list.addView(row(c, markerIcon(c, police = code == 239), c.getString(nameRes), sub, c.getString(descRes)))
        }
        list.addView(note(c, c.getString(R.string.info_other)))

        list.addView(header(c, c.getString(R.string.info_controls_header)))
        val d = c.resources.displayMetrics.density
        for (ctl in Control.values()) {
            val (icon, desc) = when (ctl) {
                Control.RED_LIGHT -> MapIcons.redLight(d) to R.string.desc_red_light
                Control.CROSSWALK -> MapIcons.crosswalk(d) to R.string.desc_crosswalk
                Control.NO_STOPPING -> MapIcons.noStopping(d) to R.string.desc_no_stopping
            }
            val name = c.getString(TypeLabels.controlRes(ctl)).replaceFirstChar { it.uppercase() }
            list.addView(row(c, icon, name, null, c.getString(desc)))
        }
    }

    /** Page title with a close (✕) button back to the map. */
    private fun titleBar(c: Context, onClose: () -> Unit): View {
        val d = c.resources.displayMetrics.density
        val bar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(TextView(c).apply {
            setText(R.string.tab_info)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(c, R.color.info_text))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        bar.addView(TextView(c).apply {
            text = "✕"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(c, R.color.info_text))
            contentDescription = c.getString(android.R.string.cancel)
            layoutParams = LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt())
            setOnClickListener { onClose() }
        })
        return bar
    }

    /** Same look as the map marker: red circle (blue for points without a speed limit). */
    private fun markerIcon(c: Context, police: Boolean): Bitmap {
        val d = c.resources.displayMetrics.density
        val s = (34 * d).toInt()
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(b)
        val m = s / 2f
        canvas.drawCircle(m, m, m - 1.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (police) 0xFF1E88E5.toInt() else 0xFFE53935.toInt()
        })
        canvas.drawCircle(m, m, m - 1.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt(); strokeWidth = 3 * d
        })
        return b
    }

    private fun header(c: Context, text: String) = TextView(c).apply {
        this.text = text
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(ContextCompat.getColor(c, R.color.info_text))
        val d = c.resources.displayMetrics.density
        setPadding(0, (16 * d).toInt(), 0, (8 * d).toInt())
    }

    private fun note(c: Context, text: String) = TextView(c).apply {
        this.text = text
        textSize = 13f
        setTextColor(ContextCompat.getColor(c, R.color.info_sub))
        val d = c.resources.displayMetrics.density
        setPadding(0, (4 * d).toInt(), 0, (8 * d).toInt())
    }

    private fun row(c: Context, icon: Bitmap, title: String, sub: String?, desc: String): View {
        val d = c.resources.displayMetrics.density
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
        }
        val iv = ImageView(c).apply {
            setImageBitmap(icon)
            layoutParams = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()).apply { rightMargin = (14 * d).toInt() }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(TextView(c).apply {
            text = title
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(c, R.color.info_text))
        })
        if (sub != null) col.addView(TextView(c).apply {
            text = sub
            textSize = 12f
            setTextColor(ContextCompat.getColor(c, R.color.info_sub))
        })
        col.addView(TextView(c).apply {
            text = desc
            textSize = 14f
            setTextColor(ContextCompat.getColor(c, R.color.info_text))
            setPadding(0, (2 * d).toInt(), 0, 0)
        })
        row.addView(iv)
        row.addView(col)
        return row
    }
}
