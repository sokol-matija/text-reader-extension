package net.falconparore.textreader

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Spinner adapter that renders Kokoro voices grouped by category with
 * disabled, styled header rows — mirroring the <optgroup> layout used by
 * popup.html in the Chrome extension.
 */
class VoiceSpinnerAdapter(
    private val context: Context,
    private val entries: List<VoiceEntry> = Voices.grouped
) : BaseAdapter() {

    override fun getCount(): Int = entries.size
    override fun getItem(position: Int): VoiceEntry = entries[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getItemViewType(position: Int): Int =
        if (entries[position] is VoiceEntry.Header) TYPE_HEADER else TYPE_ITEM

    override fun getViewTypeCount(): Int = 2

    override fun isEnabled(position: Int): Boolean = entries[position] !is VoiceEntry.Header

    override fun areAllItemsEnabled(): Boolean = false

    /** Compact single-line for the collapsed spinner state. */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val entry = entries[position]
        val tv = (convertView as? TextView) ?: TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        tv.text = when (entry) {
            is VoiceEntry.Header -> entry.label
            is VoiceEntry.Item -> entry.display
        }
        tv.setTypeface(null, Typeface.NORMAL)
        return tv
    }

    /** Expanded dropdown: headers look like section titles, items are indented. */
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val entry = entries[position]
        val tv = (convertView as? TextView) ?: TextView(context).apply {
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
        }
        when (entry) {
            is VoiceEntry.Header -> {
                tv.text = entry.label
                tv.setTypeface(null, Typeface.BOLD)
                tv.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                tv.setBackgroundColor(ContextCompat.getColor(context, R.color.bg_elevated))
                tv.setPadding(dp(16), dp(12), dp(16), dp(6))
                tv.textSize = 12f
                tv.isAllCaps = true
                tv.letterSpacing = 0.06f
            }
            is VoiceEntry.Item -> {
                tv.text = entry.display
                tv.setTypeface(null, Typeface.NORMAL)
                tv.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                tv.setBackgroundColor(ContextCompat.getColor(context, R.color.bg_elevated))
                tv.setPadding(dp(28), dp(10), dp(16), dp(10))
                tv.textSize = 15f
                tv.isAllCaps = false
                tv.letterSpacing = 0f
            }
        }
        return tv
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
}
