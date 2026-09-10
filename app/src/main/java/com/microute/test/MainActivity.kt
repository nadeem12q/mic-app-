package com.microute.test

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.*
import android.os.*
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var audio: AudioManager
    private lateinit var body: LinearLayout
    private lateinit var inputChoices: LinearLayout
    private lateinit var devicesText: TextView
    private lateinit var statusText: TextView
    private lateinit var meterText: TextView
    private lateinit var logText: TextView
    private lateinit var startButton: Button
    private lateinit var playButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val capturing = AtomicBoolean(false)
    private val playing = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var selectedInput: AudioDeviceInfo? = null
    private var clip = ShortArray(0)
    private var operationBusy = false
    private var destroyed = false
    private var lastEvidence = ""
    private var communicationRequest = false
    private val events = ArrayDeque<String>()
    private val observations = mutableListOf<String>()
    private val clockFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val resetCommunication = Runnable { clearCommunication("Two-minute request expired") }
    private val communicationListener = AudioManager.OnCommunicationDeviceChangedListener {
        log("Communication output changed: ${label(it)} (not proof of another app's mic)")
        updateStatus()
    }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            log("Devices added: ${addedDevices.joinToString { label(it) }}")
            refreshDevices()
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            log("Devices removed: ${removedDevices.joinToString { label(it) }}")
            // Do not silently replace a disconnected selection with system default.
            if (removedDevices.any { it.id == selectedInput?.id }) {
                log("Selected mic disconnected. Stop test; select an available input.")
                stopAudio()
            }
            // Avoid continuing our sample on the phone speaker after headset loss.
            if (removedDevices.any { it.isSink } && playing.get()) stopAudio()
            refreshDevices()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = getSystemService(AudioManager::class.java)
        window.statusBarColor = Color.rgb(20, 24, 31)
        window.navigationBarColor = Color.rgb(20, 24, 31)
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
            setBackgroundColor(Color.rgb(20, 24, 31))
        }
        scroll.addView(body)
        setContentView(scroll)
        // Respect system bars, including Android 15 edge-to-edge enforcement.
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        heading("Mic Route Test")
        text("Phone mic + earbuds audio\nAndroid routing feasibility • v0.1", 16)
        text("This tests real routes. Selecting a mic here does not force other apps to use it. Other-app results stay unverified until you check them.")
        button("Allow microphone & Bluetooth") { requestAudioPermissions() }
        heading("1 · Select test microphone")
        inputChoices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(inputChoices)
        button("Refresh connected devices") { refreshDevices() }
        devicesText = text("")
        statusText = text("").apply { setTextColor(Color.rgb(118, 228, 183)) }
        startButton = button("Record 10-second test") { startCapture() }
        meterText = text("Level: idle")
        playButton = button("Listen to last test on earbuds") { choosePlaybackOutput() }
        button("Stop audio") { stopAudio() }
        text("Recording stays in memory. Speak near the phone, then near the earbuds. You can switch the selected input during this test. Audio stops when you leave this screen.")
        heading("2 · Communication routing experiment")
        text("This requests an OUTPUT and its Android-selected matching mic. It cannot independently request phone mic + earbuds output. Request lasts up to 2 minutes while this process lives; another app can override it. No microphone is kept recording in the background.")
        button("Request communication output…") { chooseCommunicationOutput() }
        button("Reset routing & clear test audio") { resetAll() }
        heading("3 · Other-app results")
        text("Stop test audio, try a call or voice note in another app, then record what you actually heard. Use a consenting test partner for calls. The app cannot confirm another app's microphone.")
        button("Add manual test result") { addObservation() }
        button("Export diagnostic report") { exportReport() }
        heading("Session log")
        logText = text("").apply { setTextIsSelectable(true); textSize = 12f }
        audio.registerAudioDeviceCallback(deviceCallback, handler)
        audio.addOnCommunicationDeviceChangedListener(mainExecutor, communicationListener)
        log("Device: ${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}; build ${Build.DISPLAY}")
        log("Cross-app microphone: UNVERIFIED. No privileged routing backend installed.")
        refreshDevices()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Int = 14): TextView = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(Color.rgb(215, 222, 232))
        setPadding(0, dp(6), 0, dp(10))
        body.addView(this)
    }
    private fun heading(value: String) = text(value, 23)
    private fun button(value: String, action: () -> Unit): Button = Button(this).apply {
        text = value; isAllCaps = false
        setOnClickListener { safely(action) }
        body.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun safely(action: () -> Unit) {
        try { action() } catch (e: Exception) { log("ERROR: ${e.javaClass.simpleName}: ${e.message}") }
    }
    private fun log(message: String) {
        if (destroyed) return
        events.addLast("${clockFormat.format(Date())} $message")
        while (events.size > 250) events.removeFirst()
        if (::logText.isInitialized) logText.text = events.takeLast(18).joinToString("\n")
    }
    private fun label(device: AudioDeviceInfo?): String {
        if (device == null) return "Unknown / no route reported"
        val type = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Phone mic"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone earpiece"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth call audio"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth media"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
            else -> "Audio type ${device.type}"
        }
        return "$type · ${device.productName} (#${device.id})"
    }
    private fun requestAudioPermissions() {
        val missing = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) { log("Permissions already granted"); refreshDevices() }
        else requestPermissions(missing.toTypedArray(), 10)
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        log("Permissions: ${permissions.mapIndexed { i, p -> "$p=${results.getOrNull(i) == PackageManager.PERMISSION_GRANTED}" }}")
        refreshDevices()
    }
    private fun refreshDevices() = safely {
        val inputs = audio.getDevices(AudioManager.GET_DEVICES_INPUTS)
        inputChoices.removeAllViews()
        val group = RadioGroup(this)
        val choices = listOf<AudioDeviceInfo?>(null) + inputs.toList()
        choices.forEach { device ->
            val radio = RadioButton(this).apply {
                id = View.generateViewId()
                text = if (device == null) "System default" else label(device)
                setTextColor(Color.WHITE)
            }
            group.addView(radio)
            radio.isChecked = device?.id == selectedInput?.id
            radio.setOnClickListener { safely {
                selectedInput = device
                log("Requested test input: ${device?.let { label(it) } ?: "System default"}")
                recorder?.let { log("setPreferredDevice accepted=${it.setPreferredDevice(device)}; actual route must be checked") }
                updateStatus()
            } }
        }
        inputChoices.addView(group)
        devicesText.text = "Available outputs:\n" + audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).joinToString("\n") { label(it) }
        updateStatus()
    }
    private fun updateStatus() = safely {
        val record = recorder
        val active = record != null && capturing.get() && record.recordingState == AudioRecord.RECORDSTATE_RECORDING
        val actual = if (active) record?.routedDevice else null
        val silenced = if (active) record?.activeRecordingConfiguration?.isClientSilenced ?: false else false
        val evidence = RouteEvidence.describe(selectedInput?.id, actual?.id, active, silenced)
        statusText.text = "Requested mic: ${selectedInput?.let { label(it) } ?: "System default"}\n" +
            "Actual test mic: ${label(actual)}\n$evidence\n" +
            "Communication output: ${label(audio.communicationDevice)}\nOther-app mic: UNVERIFIED"
        val evidenceKey = "$evidence / ${actual?.id}"
        if (lastEvidence != evidenceKey) { log("$evidence; actual=${label(actual)}"); lastEvidence = evidenceKey }
        startButton.isEnabled = !operationBusy
        playButton.isEnabled = !operationBusy && clip.isNotEmpty()
    }

    private fun startCapture() {
        if (operationBusy) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermissions(); return
        }
        if (selectedInput != null && audio.getDevices(AudioManager.GET_DEVICES_INPUTS).none { it.id == selectedInput?.id }) {
            log("Selected microphone unavailable. Choose another input."); return
        }
        val bufferSize = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(bufferSize > 0) { "16 kHz mono capture is not supported on this device" }
        val record = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(bufferSize * 2, 6400)).build()
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord could not initialize" }
            log("Capture request accepted=${record.setPreferredDevice(selectedInput)}; source=MIC rate=$RATE")
            record.addOnRoutingChangedListener({ handler.post { if (!destroyed && recorder === record) updateStatus() } }, handler)
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Android did not start recording" }
        } catch (e: Exception) { record.release(); throw e }
        recorder = record
        capturing.set(true)
        operationBusy = true
        clip = ShortArray(0)
        clearClipWhenStopped = false
        updateStatus()
        log("Recording started; maximum 10 seconds. Audio stays in memory.")
        worker.execute {
            val samples = ShortArray(RATE * 10)
            val chunk = ShortArray(1600)
            var count = 0
            val deadline = SystemClock.elapsedRealtime() + 10_000
            try {
                while (capturing.get() && count < samples.size && SystemClock.elapsedRealtime() < deadline) {
                    val read = record.read(chunk, 0, minOf(chunk.size, samples.size - count), AudioRecord.READ_NON_BLOCKING)
                    if (read < 0) error("AudioRecord.read returned $read")
                    if (read == 0) { Thread.sleep(20); continue }
                    chunk.copyInto(samples, count, 0, read)
                    count += read
                    val peak = (0 until read).maxOf { abs(chunk[it].toInt()) } * 100 / 32768
                    val seconds = count.toFloat() / RATE
                    handler.post {
                        if (!destroyed && recorder === record) {
                            meterText.text = "Level: $peak% · ${String.format(Locale.US, "%.1f", seconds)} / 10 s"
                            updateStatus()
                        }
                    }
                }
            } catch (e: Exception) { handler.post { log("Capture error: ${e.message}") } }
            finally {
                capturing.set(false)
                // Release on the UI thread so route queries cannot race with release.
                val recorded = samples.copyOf(count)
                handler.post {
                    if (recorder === record) recorder = null
                    runCatching { record.stop() }
                    record.release()
                    if (!destroyed) {
                        clip = if (clearClipWhenStopped) ShortArray(0) else recorded
                        operationBusy = false
                        log("Recording stopped: ${recorded.size} samples. Other-app mic remains unverified.")
                        meterText.text = "Level: idle"
                        updateStatus()
                    }
                }
            }
        }
    }

    private fun choosePlaybackOutput() {
        if (operationBusy || clip.isEmpty()) return
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
            it.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET)
        }
        if (outputs.isEmpty()) { log("No headset output available. Connect earbuds and refresh."); return }
        AlertDialog.Builder(this).setTitle("Play test through headset")
            .setItems(outputs.map { label(it) }.toTypedArray()) { _, index -> safely { playClip(outputs[index]) } }
            .setNegativeButton("Cancel", null).show()
    }
    private fun playClip(output: AudioDeviceInfo) {
        if (operationBusy || clip.isEmpty()) return
        val samples = clip.copyOf()
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(samples.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED) { "Playback initialization failed" }
            check(track.setPreferredDevice(output)) { "Headset output request rejected" }
            check(track.write(samples, 0, samples.size) == samples.size) { "Could not load test audio" }
            // Start muted until the actual route has been observed.
            track.setVolume(0f)
            track.play()
        } catch (e: Exception) { track.release(); throw e }
        player = track
        playing.set(true)
        operationBusy = true
        updateStatus()
        log("Playback requested: ${label(output)}; muted pending actual route")
        val started = SystemClock.elapsedRealtime()
        var verified = false
        val poll = object : Runnable {
            override fun run() {
                if (player !== track) return
                if (!playing.get() || destroyed) { finishPlayback(track); return }
                safely {
                    val actual = track.routedDevice
                    if (actual?.id == output.id) {
                        if (!verified) {
                            verified = true
                            track.setVolume(0.5f)
                            log("Observed playback output: ${label(actual)}")
                        }
                    } else if (verified || actual != null || SystemClock.elapsedRealtime() - started > 1000) {
                        log("Playback stopped: selected headset route not active (${label(actual)})")
                        finishPlayback(track); return@safely
                    }
                    if (track.playbackHeadPosition >= samples.size) finishPlayback(track)
                    else handler.postDelayed(this, 50)
                }
            }
        }
        track.addOnRoutingChangedListener({ handler.post {
            if (player === track && track.routedDevice?.id != output.id) {
                track.setVolume(0f)
                log("Headset playback route changed; stopping sample")
                finishPlayback(track)
            }
        } }, handler)
        handler.post(poll)
    }
    private fun finishPlayback(track: AudioTrack) {
        if (player !== track) return
        player = null
        playing.set(false)
        runCatching { track.stop() }
        track.release()
        operationBusy = false
        if (!destroyed) { log("Playback stopped"); updateStatus() }
    }
    private fun stopAudio() {
        capturing.set(false)
        playing.set(false)
        player?.let { finishPlayback(it) }
        // The non-blocking capture loop releases its recorder on completion.
    }
    private fun chooseCommunicationOutput() {
        if (operationBusy) { log("Stop test audio and wait for it to finish first."); return }
        val outputs = audio.availableCommunicationDevices
        if (outputs.isEmpty()) { log("No communication devices available"); return }
        AlertDialog.Builder(this).setTitle("Communication OUTPUT (also affects mic)")
            .setItems(outputs.map { label(it) }.toTypedArray()) { _, index -> safely {
                val accepted = audio.setCommunicationDevice(outputs[index])
                log("Communication request: ${label(outputs[index])}; accepted=$accepted; cross-app mic UNVERIFIED")
                if (accepted) {
                    communicationRequest = true
                    handler.removeCallbacks(resetCommunication)
                    handler.postDelayed(resetCommunication, 120_000)
                }
                updateStatus()
            } }.setNegativeButton("Cancel", null).show()
    }
    private fun clearCommunication(reason: String) {
        handler.removeCallbacks(resetCommunication)
        if (communicationRequest) {
            safely { audio.clearCommunicationDevice() }
            communicationRequest = false
            log("$reason; cleared this app's communication request")
        }
        if (!destroyed) updateStatus()
    }
    private fun resetAll() {
        stopAudio()
        selectedInput = null
        clip = ShortArray(0)
        // A worker finishing after reset must not restore the discarded clip.
        clearClipWhenStopped = true
        clearCommunication("Reset")
        refreshDevices()
        log("System default selected. Test clip cleared; diagnostic log retained.")
    }
    private var clearClipWhenStopped = false

    private fun addObservation() {
        stopAudio()
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        val scenario = EditText(this).apply { hint = "App + activity (e.g. WhatsApp voice note)" }
        val result = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("UNVERIFIED", "PASS — phone mic + earbuds audio", "FAIL — wrong mic or output"))
        }
        val details = EditText(this).apply { hint = "Actual mic, output, before/during switch, what you heard"; minLines = 3 }
        panel.addView(scenario); panel.addView(result); panel.addView(details)
        val dialog = AlertDialog.Builder(this).setTitle("Manual observation, not API proof")
            .setView(panel).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (scenario.text.isBlank()) { scenario.error = "Enter app and activity"; return@setOnClickListener }
                if (result.selectedItemPosition != 0 && details.text.isBlank()) {
                    details.error = "Describe the evidence for this result"; return@setOnClickListener
                }
                val observation = "${Date()} | MANUAL | ${scenario.text} | ${result.selectedItem} | ${details.text}"
                observations.add(observation)
                log(observation)
                dialog.dismiss()
            }
        }
        dialog.show()
    }
    private fun exportReport() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "mic-route-${System.currentTimeMillis()}.txt")
        }, 20)
    }
    @Deprecated("Activity result API retained to avoid an unnecessary AndroidX dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 20 && resultCode == RESULT_OK) data?.data?.let { uri -> safely {
            val report = buildString {
                appendLine("Mic Route Test 0.1 — diagnostic report")
                appendLine("${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}; SDK ${Build.VERSION.SDK_INT}; ${Build.DISPLAY}")
                appendLine("Requested test mic: ${selectedInput?.let { label(it) } ?: "System default"}")
                appendLine("Other-app actual microphone: UNVERIFIED by this app. Manual observations below are user-reported.")
                appendLine("No root / Shizuku backend. Audio clips are not included.")
                appendLine("\nMANUAL RESULTS")
                appendLine(observations.joinToString("\n").ifEmpty { "No physical-device results entered." })
                appendLine("\nLAST 250 EVENTS")
                appendLine(events.joinToString("\n"))
            }
            checkNotNull(contentResolver.openOutputStream(uri, "wt")) { "Cannot open export destination" }
                .bufferedWriter().use { it.write(report) }
            log("Report exported. No audio included.")
        } }
    }
    override fun onResume() {
        super.onResume()
        if (::devicesText.isInitialized) refreshDevices()
    }
    override fun onStop() {
        stopAudio()
        super.onStop()
    }
    override fun onDestroy() {
        stopAudio()
        clearCommunication("Activity destroyed")
        destroyed = true
        audio.unregisterAudioDeviceCallback(deviceCallback)
        audio.removeOnCommunicationDeviceChangedListener(communicationListener)
        worker.shutdown()
        super.onDestroy()
    }
    companion object { private const val RATE = 16000 }
}
