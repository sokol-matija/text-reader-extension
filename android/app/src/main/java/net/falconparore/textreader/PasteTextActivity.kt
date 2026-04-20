package net.falconparore.textreader

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.material.button.MaterialButton
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import java.io.File

@UnstableApi
class PasteTextActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var tts: KokoroTtsClient

    private lateinit var textInput: EditText
    private lateinit var btnRead: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnClear: MaterialButton
    private lateinit var voiceSpinner: Spinner
    private lateinit var playbackPanel: PlaybackPanel

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingSaveFile: File? = null

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mpeg")
    ) { uri: Uri? ->
        val source = pendingSaveFile
        pendingSaveFile = null
        if (uri == null || source == null) return@registerForActivityResult
        val ok = AudioExport.copyToUri(contentResolver, source, uri)
        val msg = if (ok) {
            getString(R.string.save_success, uri.lastPathSegment ?: "audio.mp3")
        } else {
            getString(R.string.save_failure, "write failed")
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paste)
        title = getString(R.string.paste_title)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        settings = Settings(this)
        tts = KokoroTtsClient(settings)

        textInput = findViewById(R.id.textInput)
        voiceSpinner = findViewById(R.id.voiceSpinner)
        btnRead = findViewById(R.id.btnRead)
        btnStop = findViewById(R.id.btnStop)
        btnClear = findViewById(R.id.btnClear)

        playbackPanel = PlaybackPanel(findViewById(R.id.playbackPanel), lifecycleScope).apply {
            onSaveRequested = { file ->
                pendingSaveFile = file
                createDocument.launch(AudioExport.suggestedFilename())
            }
        }

        voiceSpinner.adapter = VoiceSpinnerAdapter(this)
        voiceSpinner.setSelection(Voices.groupedIndexOf(settings.voice))
        voiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val entry = Voices.grouped[pos]
                if (entry is VoiceEntry.Item) settings.voice = entry.id
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        btnRead.setOnClickListener {
            val text = textInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.paste_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnRead.isEnabled = false
            btnStop.isEnabled = true
            fetchAndPlay(text)
        }
        btnStop.setOnClickListener {
            controller?.stop()
            btnRead.isEnabled = true
            btnStop.isEnabled = false
            playbackPanel.stopPolling()
        }
        btnClear.setOnClickListener {
            textInput.setText("")
            playbackPanel.reset()
        }
    }

    override fun onStart() {
        super.onStart()
        connectController()
    }

    override fun onStop() {
        playbackPanel.stopPolling()
        releaseController()
        super.onStop()
    }

    private fun fetchAndPlay(text: String) {
        lifecycleScope.launch {
            when (val result = tts.synthesise(text, cacheDir)) {
                is KokoroTtsClient.Result.Success -> {
                    val c = controller
                    if (c == null) {
                        Toast.makeText(
                            this@PasteTextActivity,
                            "Playback not ready yet",
                            Toast.LENGTH_SHORT
                        ).show()
                        btnRead.isEnabled = true
                        btnStop.isEnabled = false
                        return@launch
                    }
                    playbackPanel.showForFile(result.mp3File)
                    playbackPanel.attachController(c)
                    c.setMediaItem(MediaItem.fromUri(result.mp3File.toUri()))
                    c.prepare()
                    c.play()
                    playbackPanel.startPolling()
                }
                is KokoroTtsClient.Result.Failure -> {
                    val msg = if (result.cause is java.net.UnknownHostException ||
                        result.cause is java.net.ConnectException
                    ) {
                        getString(R.string.tts_error_network)
                    } else {
                        getString(R.string.tts_error_generic, result.message)
                    }
                    Toast.makeText(this@PasteTextActivity, msg, Toast.LENGTH_LONG).show()
                    btnRead.isEnabled = true
                    btnStop.isEnabled = false
                }
            }
        }
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, TtsPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (future.isCancelled) return@addListener
            val c = try {
                future.get()
            } catch (_: Exception) {
                return@addListener
            }
            controller = c
            playbackPanel.attachController(c)
            c.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        btnRead.isEnabled = true
                        btnStop.isEnabled = false
                        playbackPanel.stopPolling()
                    }
                }
            })
        }, ContextCompat.getMainExecutor(this))
    }

    private fun releaseController() {
        controller?.release()
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
