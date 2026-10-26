package com.eddyizm.tempus.ui.browse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.eddyizm.tempus.R
import com.eddyizm.tempus.discovery.ChartMatches
import com.eddyizm.tempus.discovery.ChartTrack
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Hands songs missing from the library to QQ Music or NetEase as text: one "title - artist" line
 * each, copied to the clipboard or shared as a .txt file.
 */
object MissingExport {
    @JvmStatic
    fun show(fragment: Fragment, title: String, fileStem: String, rows: List<ChartTrack>) {
        if (rows.isEmpty()) return
        val context = fragment.requireContext()
        val text = ChartMatches.exportText(rows)
        val fileName = fileStem.replace(" · ", "-").replace(' ', '_') + "-" + context.getString(R.string.discovery_export_file_suffix) + ".txt"
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(R.string.discovery_export_message)
            .setPositiveButton(R.string.discovery_export_copy) { _, _ ->
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(fileStem, text))
                Toast.makeText(context, context.getString(R.string.discovery_export_copied, rows.size), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.discovery_export_share) { _, _ -> share(fragment, fileName, text) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun share(fragment: Fragment, fileName: String, text: String) {
        val context = fragment.requireContext()
        try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            file.writeText(text)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".updates.provider", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, text)
                .putExtra(Intent.EXTRA_SUBJECT, fileName)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            fragment.startActivity(Intent.createChooser(send, context.getString(R.string.discovery_export_share)))
        } catch (e: Exception) {
            Toast.makeText(context, R.string.discovery_export_failed, Toast.LENGTH_LONG).show()
        }
    }
}
