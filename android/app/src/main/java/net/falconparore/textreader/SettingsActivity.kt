package net.falconparore.textreader

import android.media.MediaPlayer
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private var testPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        val settings = Settings(this)
        val urlInput = findViewById<EditText>(R.id.urlInput)
        val modelInput = findViewById<EditText>(R.id.modelInput)
        val voiceSpinner = findViewById<Spinner>(R.id.voiceSpinner)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        val btnTest = findViewById<MaterialButton>(R.id.btnTest)

        urlInput.setText(settings.baseUrl)
        modelInput.setText(settings.model)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            Voices.all.map { it.label }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        voiceSpinner.adapter = adapter
        voiceSpinner.setSelection(Voices.indexOf(settings.voice))

        btnSave.setOnClickListener {
            settings.baseUrl = urlInput.text.toString().trim()
            settings.model = modelInput.text.toString().trim()
            settings.voice = Voices.all[voiceSpinner.selectedItemPosition].id
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnTest.setOnClickListener {
            val tempSettings = Settings(this).also {
                it.baseUrl = urlInput.text.toString().trim()
                it.model = modelInput.text.toString().trim()
                it.voice = Voices.all[voiceSpinner.selectedItemPosition].id
            }
            val client = KokoroTtsClient(tempSettings)
            Toast.makeText(this, "Sending test request…", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                when (val result = client.synthesise("Hello from Text Reader.", cacheDir)) {
                    is KokoroTtsClient.Result.Success -> {
                        Toast.makeText(
                            this@SettingsActivity,
                            "OK \u2014 ${result.mp3File.length()} bytes",
                            Toast.LENGTH_SHORT
                        ).show()
                        playTestFile(result.mp3File.absolutePath)
                    }
                    is KokoroTtsClient.Result.Failure -> {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Failed: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun playTestFile(path: String) {
        testPlayer?.release()
        testPlayer = MediaPlayer().apply {
            setOnCompletionListener {
                it.release()
                if (testPlayer === it) testPlayer = null
            }
            setOnErrorListener { mp, what, extra ->
                mp.release()
                if (testPlayer === mp) testPlayer = null
                Toast.makeText(
                    this@SettingsActivity,
                    "Playback error ($what, $extra)",
                    Toast.LENGTH_LONG
                ).show()
                true
            }
            setDataSource(path)
            prepare()
            start()
        }
    }

    override fun onDestroy() {
        testPlayer?.release()
        testPlayer = null
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
