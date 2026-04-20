package net.falconparore.textreader

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import java.io.File

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var shutterButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var pasteButton: ImageButton
    private lateinit var statusPill: TextView
    private lateinit var loadingOverlay: View

    private var cameraController: LifecycleCameraController? = null

    private lateinit var settings: Settings
    private lateinit var ocr: OcrRepository
    private lateinit var tts: KokoroTtsClient

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private var reviewSheet: BottomSheetDialog? = null
    private var reviewVoiceId: String = Voices.DEFAULT_ID
    private var currentText: String = ""

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
    }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.notifications_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        shutterButton = findViewById(R.id.shutterButton)
        settingsButton = findViewById(R.id.settingsButton)
        pasteButton = findViewById(R.id.pasteButton)
        statusPill = findViewById(R.id.statusPill)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        settings = Settings(this)
        ocr = OcrRepository()
        tts = KokoroTtsClient(settings)
        reviewVoiceId = settings.voice

        shutterButton.setOnClickListener { capturePhoto() }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        pasteButton.setOnClickListener {
            startActivity(Intent(this, PasteTextActivity::class.java))
        }

        setStatus(R.string.status_ready, R.color.status_success)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onStart() {
        super.onStart()
        connectController()
    }

    override fun onStop() {
        releaseController()
        super.onStop()
    }

    private fun startCamera() {
        val controller = LifecycleCameraController(this).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            bindToLifecycle(this@MainActivity)
        }
        previewView.controller = controller
        cameraController = controller
    }

    private fun capturePhoto() {
        val controller = cameraController ?: return
        setStatus(R.string.status_capturing, R.color.status_info)
        loadingOverlay.visibility = View.VISIBLE

        val file = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(file).build()

        controller.takePicture(
            output,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    runOcr(file.toUri())
                }
                override fun onError(exc: ImageCaptureException) {
                    loadingOverlay.visibility = View.GONE
                    setStatus(R.string.status_error, R.color.status_error)
                    Toast.makeText(
                        this@MainActivity,
                        "Capture failed: ${exc.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun runOcr(imageUri: Uri) {
        setStatus(R.string.status_ocr, R.color.status_info)
        lifecycleScope.launch {
            val text = try {
                ocr.recognise(this@MainActivity, imageUri)
            } catch (e: Exception) {
                loadingOverlay.visibility = View.GONE
                setStatus(R.string.status_error, R.color.status_error)
                Toast.makeText(
                    this@MainActivity,
                    "OCR failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            loadingOverlay.visibility = View.GONE
            if (text.isBlank()) {
                setStatus(R.string.status_error, R.color.status_error)
                Toast.makeText(
                    this@MainActivity,
                    R.string.no_text_found,
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            currentText = text
            setStatus(R.string.status_ready, R.color.status_success)
            openReviewSheet(text)
        }
    }

    private fun openReviewSheet(text: String) {
        val sheet = BottomSheetDialog(this, R.style.Theme_TextReader_BottomSheet)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_review, null)
        sheet.setContentView(view)

        val extractedText = view.findViewById<TextView>(R.id.extractedText)
        val spinner = view.findViewById<Spinner>(R.id.voiceSpinner)
        val btnRead = view.findViewById<MaterialButton>(R.id.btnRead)
        val btnStop = view.findViewById<MaterialButton>(R.id.btnStop)
        val btnCopy = view.findViewById<MaterialButton>(R.id.btnCopy)
        val btnRetake = view.findViewById<MaterialButton>(R.id.btnRetake)

        extractedText.text = text

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            Voices.all.map { it.label }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(Voices.indexOf(reviewVoiceId))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                reviewVoiceId = Voices.all[pos].id
                settings.voice = reviewVoiceId
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        btnRead.setOnClickListener {
            btnRead.isEnabled = false
            btnStop.isEnabled = true
            fetchAndPlay(text)
        }
        btnStop.setOnClickListener {
            controller?.stop()
            btnRead.isEnabled = true
            btnStop.isEnabled = false
            setStatus(R.string.status_ready, R.color.status_success)
        }
        btnCopy.setOnClickListener {
            val clip = ClipData.newPlainText("extracted", text)
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
        btnRetake.setOnClickListener { sheet.dismiss() }

        reviewSheet = sheet
        sheet.setOnDismissListener {
            reviewSheet = null
        }
        sheet.show()
    }

    private fun fetchAndPlay(text: String) {
        setStatus(R.string.status_fetching, R.color.status_info)
        lifecycleScope.launch {
            when (val result = tts.synthesise(text, cacheDir)) {
                is KokoroTtsClient.Result.Success -> play(result.mp3File)
                is KokoroTtsClient.Result.Failure -> {
                    setStatus(R.string.status_error, R.color.status_error)
                    val msg = if (result.cause is java.net.UnknownHostException ||
                        result.cause is java.net.ConnectException
                    ) {
                        getString(R.string.tts_error_network)
                    } else {
                        getString(R.string.tts_error_generic, result.message)
                    }
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                    reviewSheet?.findViewById<MaterialButton>(R.id.btnRead)?.isEnabled = true
                    reviewSheet?.findViewById<MaterialButton>(R.id.btnStop)?.isEnabled = false
                }
            }
        }
    }

    private fun play(file: File) {
        val c = controller ?: run {
            Toast.makeText(this, "Playback not ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        c.setMediaItem(MediaItem.fromUri(file.toUri()))
        c.prepare()
        c.play()
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
            c.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> setStatus(R.string.status_playing, R.color.status_success)
                        Player.STATE_ENDED -> {
                            setStatus(R.string.status_ready, R.color.status_success)
                            reviewSheet?.findViewById<MaterialButton>(R.id.btnRead)?.isEnabled = true
                            reviewSheet?.findViewById<MaterialButton>(R.id.btnStop)?.isEnabled = false
                        }
                        else -> Unit
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

    private fun setStatus(textRes: Int, colorRes: Int) {
        statusPill.setText(textRes)
        statusPill.setTextColor(ContextCompat.getColor(this, colorRes))
    }
}
