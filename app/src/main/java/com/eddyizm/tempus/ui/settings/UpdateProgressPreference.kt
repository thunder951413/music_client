package com.eddyizm.tempus.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.eddyizm.tempus.R
import com.google.android.material.progressindicator.LinearProgressIndicator

/** A settings row with a progress bar, used to follow the update download. */
class UpdateProgressPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {
    private var heading: CharSequence = ""
    private var detail: CharSequence = ""
    private var fraction = -1f

    init {
        layoutResource = R.layout.preference_update_progress
    }

    /** [fraction] in 0..1 fills the bar; a negative value shows it as indeterminate. */
    fun setState(heading: CharSequence, detail: CharSequence, fraction: Float) {
        if (this.heading == heading && this.detail == detail && this.fraction == fraction) return
        this.heading = heading
        this.detail = detail
        this.fraction = fraction
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.update_progress_title) as TextView).text = heading
        (holder.findViewById(R.id.update_progress_detail) as TextView).text = detail
        val bar = holder.findViewById(R.id.update_progress_bar) as LinearProgressIndicator
        if (fraction < 0f) {
            if (!bar.isIndeterminate) {
                bar.visibility = android.view.View.INVISIBLE
                bar.isIndeterminate = true
                bar.visibility = android.view.View.VISIBLE
            }
        } else {
            if (bar.isIndeterminate) {
                bar.visibility = android.view.View.INVISIBLE
                bar.isIndeterminate = false
                bar.visibility = android.view.View.VISIBLE
            }
            bar.setProgressCompat((fraction * 1000).toInt(), true)
        }
    }
}
