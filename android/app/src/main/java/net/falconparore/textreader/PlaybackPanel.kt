package net.falconparore.textreader

import android.media.MediaMetadataRetriever
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Wraps the view_playback_panel include. Shows MB size + duration,
 * live-polls the MediaController for position while playing, and exposes
 * a Save button callback.
 */
@UnstableApi
class PlaybackPanel(
    private val root: View,
    private val scope: LifecycleCoroutineScope
) {
    private val timeLabel: TextView = root.findViewById(R.id.timeLabel)
    private val sizeLabel: TextView = root.findViewById(R.id.sizeLabel)
    private val seekBar: SeekBar = root.findViewById(R.id.seekBar)
    private val btnSave: MaterialButton = root.findViewById(R.id.btnSaveAudio)

    private var controller: MediaController? = null
    private var pollJob: Job? = null
    private var currentFile: File? = null
    private var currentDurationMs: Long = 0L

    /** Caller wires this to launch an ACTION_CREATE_DOCUMENT result contract. */
    var onSaveRequested: ((File) -> Unit)? = null

    init {
        btnSave.setOnClickListener {
            currentFile?.let { onSaveRequested?.invoke(it) }
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) controller?.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = Unit
        })
    }

    /** Call after a TTS fetch succeeds. Reveals the panel with size + duration. */
    fun showForFile(file: File) {
        currentFile = file
        currentDurationMs = readDurationMs(file)

        val sizeMB = file.length() / 1024.0 / 1024.0
        sizeLabel.text = String.format(Locale.US, "%.2f MB", sizeMB)
        seekBar.max = currentDurationMs.toInt().coerceAtLeast(1)
        seekBar.progress = 0
        timeLabel.text = "0:00 / ${formatTime(currentDurationMs)}"
        root.visibility = View.VISIBLE
    }

    fun attachController(c: MediaController) {
        controller = c
    }

    fun startPolling() {
        stopPolling()
        val c = controller ?: return
        pollJob = scope.launch {
            while (isActive) {
                val pos = c.currentPosition.coerceAtLeast(0)
                val dur = c.duration.takeIf { it > 0 } ?: currentDurationMs
                if (dur > 0) {
                    seekBar.max = dur.toInt()
                    seekBar.progress = pos.toInt().coerceAtMost(dur.toInt())
                }
                timeLabel.text = "${formatTime(pos)} / ${formatTime(dur.coerceAtLeast(0))}"
                delay(250)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun reset() {
        stopPolling()
        root.visibility = View.GONE
        currentFile = null
        currentDurationMs = 0L
        seekBar.progress = 0
        timeLabel.text = "0:00 / 0:00"
    }

    private fun readDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%d:%02d", m, s)
    }
}
