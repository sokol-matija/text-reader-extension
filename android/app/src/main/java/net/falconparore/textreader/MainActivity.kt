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
import android.provider.Settings as AndroidSettings
import android.view.View
import android.widget.AdapterView
import android.widget.ImageButton
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import java.io.File

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var appBar: View
    private lateinit var bottomBar: View
    private lateinit var shutterButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var pasteButton: ImageButton
    private lateinit var galleryButton: ImageButton
    private lateinit var flashButton: ImageButton
    private lateinit var statusPill: TextView
    private lateinit var loadingOverlay: View
    private lateinit var permissionOverlay: View
    private lateinit var btnGrantCamera: MaterialButton
    private lateinit var btnOpenSettings: MaterialButton

    private var cameraController: LifecycleCameraController? = null
    private var torchOn: Boolean = false

    private lateinit var settings: Settings
    private lateinit var ocr: OcrRepository
    private lateinit var tts: KokoroTtsClient
    private lateinit var translation: TranslationRepository

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private var reviewSheet: BottomSheetDialog? = null
    private var reviewPlaybackPanel: PlaybackPanel? = null
    private var reviewVoiceId: String = Voices.DEFAULT_ID
    private var currentText: String = ""
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

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            hidePermissionOverlay()
            if (cameraController == null) startCamera()
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.CAMERA
            )
        ) {
            // Permanently denied — offer the app-settings escape hatch.
            btnGrantCamera.visibility = View.GONE
            btnOpenSettings.visibility = View.VISIBLE
        }
    }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.notifications_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            loadingOverlay.visibility = View.VISIBLE
            runOcr(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        appBar = findViewById(R.id.appBar)
        bottomBar = findViewById(R.id.bottomBar)
        shutterButton = findViewById(R.id.shutterButton)
        settingsButton = findViewById(R.id.settingsButton)
        pasteButton = findViewById(R.id.pasteButton)
        galleryButton = findViewById(R.id.galleryButton)
        flashButton = findViewById(R.id.flashButton)
        statusPill = findViewById(R.id.statusPill)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        permissionOverlay = findViewById(R.id.permissionOverlay)
        btnGrantCamera = findViewById(R.id.btnGrantCamera)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)

        val root = findViewById<View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.updatePadding(top = bars.top)
            bottomBar.updatePadding(bottom = bars.bottom + (32 * resources.displayMetrics.density).toInt())
            insets
        }

        settings = Settings(this)
        ocr = OcrRepository()
        tts = KokoroTtsClient(settings)
        translation = TranslationRepository()
        reviewVoiceId = settings.voice

        shutterButton.setOnClickListener { capturePhoto() }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        pasteButton.setOnClickListener {
            startActivity(Intent(this, PasteTextActivity::class.java))
        }
        galleryButton.setOnClickListener {
            pickImage.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
        flashButton.setOnClickListener { toggleFlash() }

        btnGrantCamera.setOnClickListener {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
        btnOpenSettings.setOnClickListener {
            startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }

        setStatus(R.string.status_ready, R.color.status_success)

        TextReaderApp.readAndClear(this)?.let { trace ->
            AlertDialog.Builder(this)
                .setTitle("Previous crash")
                .setMessage(trace)
                .setPositiveButton("Copy") { _, _ ->
                    val clip = ClipData.newPlainText("crash", trace)
                    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                }
                .setNegativeButton("Dismiss", null)
                .show()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectController()
    }

    override fun onResume() {
        super.onResume()
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            hidePermissionOverlay()
            if (cameraController == null) startCamera()
        } else {
            showPermissionOverlay()
        }
    }

    override fun onStop() {
        releaseController()
        super.onStop()
    }

    private fun showPermissionOverlay() {
        permissionOverlay.visibility = View.VISIBLE
        // Reset to the primary CTA; the launcher's callback will swap to
        // "Open app settings" if we detect a permanent denial.
        btnGrantCamera.visibility = View.VISIBLE
        btnOpenSettings.visibility = View.GONE
    }

    private fun hidePermissionOverlay() {
        permissionOverlay.visibility = View.GONE
    }

    private fun startCamera() {
        val controller = LifecycleCameraController(this).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            bindToLifecycle(this@MainActivity)
        }
        previewView.controller = controller
        cameraController = controller
        // Reset flash state when (re)binding.
        torchOn = false
        flashButton.setImageResource(R.drawable.ic_flash_off)
    }

    private fun toggleFlash() {
        val controller = cameraController ?: return
        torchOn = !torchOn
        controller.enableTorch(torchOn)
        flashButton.setImageResource(
            if (torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )
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
            if (text.isBlank()) {
                loadingOverlay.visibility = View.GONE
                setStatus(R.string.status_error, R.color.status_error)
                Toast.makeText(
                    this@MainActivity,
                    R.string.no_text_found,
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            // Try to detect language and translate to English — Kokoro voices
            // are English-only, so this is what makes the app actually useful
            // for non-English signage/menus.
            val detectedLang = translation.detect(text)
            val translated: String? = if (detectedLang != null && detectedLang != "en") {
                setStatus(R.string.status_translating, R.color.status_info)
                translation.translateToEnglish(text, detectedLang)
            } else {
                null
            }

            loadingOverlay.visibility = View.GONE
            currentText = translated ?: text
            setStatus(R.string.status_ready, R.color.status_success)
            openReviewSheet(original = text, translated = translated, detectedLang = detectedLang)
        }
    }

    private fun openReviewSheet(original: String, translated: String?, detectedLang: String?) {
        val text = translated ?: original
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
        var activeText: String = text

        val chipGroup = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.translationChipGroup)
        val chipEnglish = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipEnglish)
        val chipOriginal = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipOriginal)
        if (translated != null) {
            chipGroup.visibility = View.VISIBLE
            chipOriginal.text = if (!detectedLang.isNullOrBlank()) {
                getString(R.string.chip_original_with_lang, detectedLang.uppercase())
            } else {
                getString(R.string.chip_original)
            }
            chipEnglish.isChecked = true
            chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
                val showEnglish = checkedIds.contains(R.id.chipEnglish)
                val next = if (showEnglish) translated else original
                extractedText.text = next
                activeText = next
            }
        }

        try {
            val panelRoot = view.findViewById<View>(R.id.playbackPanel)
            if (panelRoot != null) {
                val panel = PlaybackPanel(panelRoot, lifecycleScope).apply {
                    onSaveRequested = { file ->
                        pendingSaveFile = file
                        createDocument.launch(AudioExport.suggestedFilename())
                    }
                }
                reviewPlaybackPanel = panel
                controller?.let { panel.attachController(it) }
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Playback panel init failed: ${e.javaClass.simpleName}: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }

        spinner.adapter = VoiceSpinnerAdapter(this)
        spinner.setSelection(Voices.groupedIndexOf(reviewVoiceId))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val entry = Voices.grouped[pos]
                if (entry is VoiceEntry.Item) {
                    reviewVoiceId = entry.id
                    settings.voice = reviewVoiceId
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        btnRead.setOnClickListener {
            btnRead.isEnabled = false
            btnStop.isEnabled = true
            fetchAndPlay(activeText)
        }
        btnStop.setOnClickListener {
            controller?.stop()
            btnRead.isEnabled = true
            btnStop.isEnabled = false
            reviewPlaybackPanel?.stopPolling()
            setStatus(R.string.status_ready, R.color.status_success)
        }
        btnCopy.setOnClickListener {
            val clip = ClipData.newPlainText("extracted", activeText)
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
        btnRetake.setOnClickListener { sheet.dismiss() }

        reviewSheet = sheet
        sheet.setOnDismissListener {
            reviewPlaybackPanel?.stopPolling()
            reviewPlaybackPanel = null
            reviewSheet = null
        }
        sheet.show()
    }

    private fun fetchAndPlay(text: String) {
        setStatus(R.string.status_fetching, R.color.status_info)
        lifecycleScope.launch {
            when (val result = tts.synthesise(text, cacheDir)) {
                is KokoroTtsClient.Result.Success -> {
                    reviewPlaybackPanel?.showForFile(result.mp3File)
                    play(result.mp3File)
                    reviewPlaybackPanel?.startPolling()
                }
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
            reviewPlaybackPanel?.attachController(c)
            c.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> setStatus(R.string.status_playing, R.color.status_success)
                        Player.STATE_ENDED -> {
                            setStatus(R.string.status_ready, R.color.status_success)
                            reviewSheet?.findViewById<MaterialButton>(R.id.btnRead)?.isEnabled = true
                            reviewSheet?.findViewById<MaterialButton>(R.id.btnStop)?.isEnabled = false
                            reviewPlaybackPanel?.stopPolling()
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
