package com.wetzone.mp3playerboom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class RecordWhilePlaying : AppCompatActivity() {

    private val REQ_CD_FP = 101
    private val timer = Timer()


    private val LANE_HEIGHT_DP = 72


    private var filePath = ""
    private var lastNumber = 0L
    private var currentFile = ""
    private var mpCreated = false
    private var isBlinking = false


    private var selectedClip: AudioClip? = null


    private var playheadMs = 0


    //    private val recordedTakes = ArrayList<HashMap<String, Any>>()
private val recordedClips = mutableListOf<AudioClip>()

    private val filePathsList = ArrayList<String>()

    private lateinit var importBtn: Button
    private lateinit var trackInfoText: TextView
    private lateinit var seekBar: SeekBar
    //private lateinit var playbackLane: LinearLayout
    private lateinit var startRecordBtn: ImageButton
    private lateinit var stopRecordBtn: ImageButton
    //private lateinit var onAirText: TextView
//    private lateinit var takesListView: ListView
    private lateinit var playBtn: ImageButton
    private lateinit var pauseBtn: ImageButton
    private lateinit var rewindBtn: ImageButton



    private lateinit var  gridlayer: FrameLayout


    private lateinit var zoomInBtn: ImageButton
    private lateinit var zoomOutBtn: ImageButton



    // === DAW TIMELINE ===
    private lateinit var timelineScroll: HorizontalScrollView
    private lateinit var timelineContent: FrameLayout

    private lateinit var laneContainer: LinearLayout


//    private lateinit var playbackLane: LinearLayout
//    private lateinit var recordLane: LinearLayout


    private lateinit var playbackLane: FrameLayout
    private lateinit var recordLane: FrameLayout



    private lateinit var playhead: View

    // pixels per millisecond (zoom level)
    //private val PX_PER_MS = 0.1f

    private var pxPerMs = 0.1f
    // --- TIMELINE CONFIG ---
    private val PLAYHEAD_CENTER_RATIO = 0.5f   // center of screen
    private val TIMELINE_START_PADDING_MS = 1000 // 1 second visual padding

    // --- TIMELINE UTILS ---
    private fun pxToMs(px: Int): Int {
        return ((px / pxPerMs) - TIMELINE_START_PADDING_MS).toInt()
    }

    private fun msToPx(ms: Int): Int {
        return ((ms + TIMELINE_START_PADDING_MS) * pxPerMs).toInt()
    }


    // --- GRID / SNAP ---
    private var snapEnabled = true

    // milliseconds per grid line (changes with zoom)
    private fun currentGridMs(): Int {
        return when {
            pxPerMs > 0.5f -> 100     // very zoomed in
            pxPerMs > 0.2f -> 250
            pxPerMs > 0.08f -> 500
            else -> 1000             // zoomed out
        }
    }






    private var mp: MediaPlayer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var timerTask: TimerTask? = null
    private var blinkTask: TimerTask? = null

    private val playbackTrack = mutableListOf<AudioClip>()
    private val recordTrack = mutableListOf<AudioClip>()


    private fun getRecordDir(): File {
        val dir = File(getExternalFilesDir(null), "recordr")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    data class AudioClip(
        val filePath: String,
        val startMs: Int,
        var durationMs: Int = 0,
        var waveform: FloatArray? = null

    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_while_playing)
        initializeViews()

        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (!hasPermissions(permissions.toTypedArray())) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1000)
        } else {
            initializeLogic()
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1000 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initializeLogic()
        } else {
            Toast.makeText(this, "Permissions required for recording and playback", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeViews() {
        importBtn = findViewById(R.id.button2)
        trackInfoText = findViewById(R.id.textview1)
        seekBar = findViewById(R.id.seekbar1)
       // playbackLane = findViewById(R.id.linear1)
        startRecordBtn = findViewById(R.id.button_start)
        stopRecordBtn = findViewById(R.id.button_stop)
        //onAirText = findViewById(R.id.textview_onair)
        //takesListView = findViewById(R.id.listview1)
        playBtn = findViewById(R.id.button1)
        pauseBtn = findViewById(R.id.button3)
        rewindBtn = findViewById(R.id.button4)


        laneContainer = findViewById(R.id.lane_container)

        gridlayer = findViewById(R.id.GRID_LAYER)



        timelineScroll = findViewById(R.id.timeline_scroll)

        gridlayer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {

                val scrollX = gridlayer.scrollX
                val touchX = event.x.toInt()

                val absolutePx = scrollX + touchX
                val targetMs = snapIfEnabled(pxToMs(absolutePx))
                seekTimelineTo(targetMs)


                true
            } else {
                false
            }
        }






        timelineContent = findViewById(R.id.timeline_content)
        playbackLane = findViewById(R.id.playback_lane)
        recordLane = findViewById(R.id.record_lane)
        playhead = findViewById(R.id.playhead)



        zoomInBtn = findViewById(R.id.zoom_in)
        zoomOutBtn = findViewById(R.id.zoom_out)

        zoomInBtn.setOnClickListener {
            pxPerMs = (pxPerMs * 1.25f).coerceAtMost(1.0f)
            renderTimeline()
        }

        zoomOutBtn.setOnClickListener {
            pxPerMs = (pxPerMs / 1.25f).coerceAtLeast(0.02f)
            renderTimeline()
        }




        playbackLane.layoutParams = playbackLane.layoutParams.apply {
            height = dp(LANE_HEIGHT_DP)
        }

        recordLane.layoutParams = recordLane.layoutParams.apply {
            height = dp(LANE_HEIGHT_DP)
        }




        zoomOutBtn.setOnLongClickListener {
            snapEnabled = !snapEnabled
            Toast.makeText(
                this,
                if (snapEnabled) "Snap ON" else "Snap OFF",
                Toast.LENGTH_SHORT
            ).show()
            true
        }



        importBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            }
            startActivityForResult(intent, REQ_CD_FP)
        }
//v1
//        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
//            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {}
//            override fun onStartTrackingTouch(sb: SeekBar?) {}
//            override fun onStopTrackingTouch(sb: SeekBar?) {
//                mp?.seekTo(seekBar.progress)
//            }
//        })

//v2
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    seekTimelineTo(progress)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}

            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })



        startRecordBtn.setOnClickListener {
            startRecording()
        }

        stopRecordBtn.setOnClickListener {
            stopRecording()
        }

        playBtn.setOnClickListener {
            startPlayback()
        }

        pauseBtn.setOnClickListener {
            pausePlayback()
        }

        rewindBtn.setOnClickListener {
            playheadMs = 0
            mp?.seekTo(0)
            seekBar.progress = 0

            playhead.translationX = 0f
            timelineScroll.scrollTo(0, 0)
        }

    }

    private fun initializeLogic() {
       // refreshRecordedList()
        stopRecordBtn.isEnabled = false
        
        val lastFileConfig = File(getRecordDir(), ".lastfile")
        if (lastFileConfig.exists()) {
            lastNumber = FileUtil.readFile(lastFileConfig.absolutePath).trim().toLongOrNull() ?: 1L
        } else {
            FileUtil.writeFile(lastFileConfig.absolutePath, "1")
            lastNumber = 1L
        }

        //v1
       // filePath = File(getRecordDir(), "take_${lastNumber}.mp3").absolutePath


        //v2
        filePath = File(getRecordDir(), "take_${lastNumber}.m4a").absolutePath

    }


    private fun seekTimelineTo(ms: Int) {
        val clampedMs = maxOf(0, ms)
        playheadMs = clampedMs

        mp?.seekTo(clampedMs)
        seekBar.progress = clampedMs

        val timelineWidth = timelineScroll.width
        val centerPx = (timelineWidth * PLAYHEAD_CENTER_RATIO).toInt()
        val playheadPx = msToPx(clampedMs)


        playhead.translationX = playheadPx.toFloat()
        //timelineScroll.scrollTo(0, 0)

//        if (playheadPx < centerPx) {
//            playhead.translationX = playheadPx.toFloat()
//            timelineScroll.scrollTo(0, 0)
//        } else {
//            playhead.translationX = centerPx.toFloat()
//            timelineScroll.scrollTo(playheadPx - centerPx, 0)
//        }
    }


//removed in v3
//    private fun startTransportClock() {
//        timerTask?.cancel()
//        timer.purge()
//        timerTask = object : TimerTask() {
//            override fun run() {
//                runOnUiThread {
//                    mp?.let {
//                        playheadMs = it.currentPosition
//                        seekBar.progress = playheadMs
//                    }
//                }
//            }
//        }
//        timer.scheduleAtFixedRate(timerTask, 0, 33) // ~30fps
//    }








//added in v3
private fun startTransportClock() {
    timerTask?.cancel()
    timer.purge()

    timerTask = object : TimerTask() {
        override fun run() {
            runOnUiThread {
                mp?.let { player ->
                    playheadMs = player.currentPosition
                    seekBar.progress = playheadMs

                    val timelineWidth = timelineScroll.width
                    val centerPx = (timelineWidth * PLAYHEAD_CENTER_RATIO).toInt()

                    val playheadPx =
                        ((playheadMs + TIMELINE_START_PADDING_MS) * pxPerMs).toInt()

                    // Move playhead until it reaches center
                    if (playheadPx < centerPx) {
                        playhead.translationX = playheadPx.toFloat()
                        timelineScroll.scrollTo(0, 0)
                    } else {
                        playhead.translationX = centerPx.toFloat()
                        timelineScroll.scrollTo(playheadPx - centerPx, 0)
                    }
                }
            }
        }
    }
    timer.scheduleAtFixedRate(timerTask, 0, 33)
}


    private fun snapIfEnabled(ms: Int): Int {
        if (!snapEnabled) return ms
        val grid = currentGridMs()
        return (ms / grid) * grid
    }



//v1
//    private fun startRecording() {
//        try {
//            // Ensure the directory exists before starting
//            getRecordDir()
//
//            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                MediaRecorder(this)
//            } else {
//                MediaRecorder()
//            }.apply {
//                setAudioSource(MediaRecorder.AudioSource.MIC)
//                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
//                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
//                setOutputFile(filePath)
//                prepare()
//                start()
//            }
//
//            startRecordBtn.isEnabled = false
//            stopRecordBtn.isEnabled = true
//            onAirText.visibility = View.VISIBLE
//
//            if (mpCreated) {
//                startPlayback()
//            }
//        } catch (e: Exception) {
//            Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
//            e.printStackTrace()
//        }
//    }





    private fun renderGrid() {
        // Remove old grid lines
        timelineContent.findViewWithTag<FrameLayout>("GRID_LAYER")?.let {
            timelineContent.removeView(it)
        }


        val gridLayer = FrameLayout(this).apply {
            tag = "GRID_LAYER"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = dp(14)
                bottomMargin = dp(14)
            }

        }

        val gridMs = currentGridMs()
        val timelineWidthPx = timelineContent.layoutParams.width

        var ms = 0
        while (true) {
            val xPx = msToPx(ms)
            if (xPx > timelineWidthPx) break

            val line = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    if (ms % (gridMs * 4) == 0) 3 else 1,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    leftMargin = xPx
                }
                setBackgroundColor(
                    if (ms % (gridMs * 4) == 0)
                        Color.parseColor("#44FFFFFF")
                    else
                        Color.parseColor("#22FFFFFF")
                )
            }

            gridLayer.addView(line)
            ms += gridMs
        }

        timelineContent.addView(gridLayer, 0)
    }





    //v2
    private fun startRecording() {
        try {
//            val clipStart = playheadMs
            val clipStart = snapIfEnabled(playheadMs)



            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(filePath)
                prepare()
                start()
            }

            startRecordBtn.isEnabled = false
            stopRecordBtn.isEnabled = true
           // onAirText.visibility = View.VISIBLE

            recordTrack.add(
                AudioClip(
                    filePath = filePath,
                    startMs = clipStart
                )
            )

            if (mpCreated) startPlayback()

        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }
//v1
//    private fun stopRecording() {
//        try {
//            mediaRecorder?.apply {
//                stop()
//                release()
//            }
//            mediaRecorder = null
//            Toast.makeText(this, "Saved: $filePath", Toast.LENGTH_SHORT).show()
//
//            lastNumber++
//            val lastFileConfig = File(getRecordDir(), ".lastfile")
//            FileUtil.writeFile(lastFileConfig.absolutePath, lastNumber.toString())
//            filePath = File(getRecordDir(), "take_${lastNumber}.mp3").absolutePath
//
//            startRecordBtn.isEnabled = true
//            stopRecordBtn.isEnabled = false
//            onAirText.visibility = View.GONE
//
//            refreshRecordedList()
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//v1
//    private fun startPlayback() {
//        if (mp == null && currentFile.isNotEmpty()) {
//            createMediaPlayer()
//        }
//
//        mp?.let { player ->
//            player.start()
//            seekBar.max = player.duration
//
//            timerTask?.cancel()
//            timerTask = object : TimerTask() {
//                override fun run() {
//                    runOnUiThread {
//                        if (player.isPlaying) {
//                            seekBar.progress = player.currentPosition
//                        } else if (!player.isPlaying && player.currentPosition >= player.duration - 100) {
//                            pausePlayback()
//                        }
//                    }
//                }
//            }
//            timer.scheduleAtFixedRate(timerTask, 1, 500)
//
//            blinkTask?.cancel()
//            blinkTask = object : TimerTask() {
//                override fun run() {
//                    runOnUiThread {
//                        isBlinking = !isBlinking
//                        playBtn.setColorFilter(if (isBlinking) Color.parseColor("#CDDC39") else Color.parseColor("#4CAF50"))
//                    }
//                }
//            }
//            timer.scheduleAtFixedRate(blinkTask, 0, 1000)
//
//            pauseBtn.isEnabled = true
//            playBtn.isEnabled = false
//        }
//    }



//v2
    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null

            val lastClip = recordTrack.last()
            //v1
            //lastClip.durationMs = playheadMs - lastClip.startMs

            //v2
            lastClip.durationMs = maxOf(
                100,
                playheadMs - lastClip.startMs
            )


            lastNumber++
            FileUtil.writeFile(
                File(getRecordDir(), ".lastfile").absolutePath,
                lastNumber.toString()
            )

            filePath = File(getRecordDir(), "take_$lastNumber.m4a").absolutePath

            startRecordBtn.isEnabled = true
            stopRecordBtn.isEnabled = false
           // onAirText.visibility = View.GONE

           // refreshRecordedList()
            renderTimeline()


        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
//added v2
//    private fun renderTrackLanes() {
//        playbackLane.removeAllViews()
//
//        recordTrack.forEach { clip ->
//            val clipView = View(this).apply {
//                setBackgroundColor(Color.parseColor("#F44336"))
//                alpha = 0.6f
//                layoutParams = LinearLayout.LayoutParams(
//                    (clip.durationMs / 10).coerceAtLeast(20),
//                    LinearLayout.LayoutParams.MATCH_PARENT
//                ).apply {
//                    leftMargin = clip.startMs / 10
//                }
//            }
//            playbackLane.addView(clipView)
//        }
//    }




    private fun updateTimelineWidth() {
        val maxMs =
            (playbackTrack + recordTrack)
                .maxOfOrNull { it.startMs + it.durationMs }
                ?: 0

        val totalMs = maxMs + TIMELINE_START_PADDING_MS * 2
        val totalPx = maxOf((totalMs * pxPerMs).toInt(), timelineScroll.width)

        timelineContent.layoutParams = timelineContent.layoutParams.apply {
            width = totalPx
        }

        playbackLane.layoutParams = playbackLane.layoutParams.apply {
            width = totalPx
        }

        recordLane.layoutParams = recordLane.layoutParams.apply {
            width = totalPx
        }
    }









    //added in v3
    private fun renderTimeline() {
        updateTimelineWidth()
    renderGrid()

    playbackLane.removeAllViews()
        recordLane.removeAllViews()

        fun renderClip(
            parent: FrameLayout,
            clip: AudioClip,
            color: String
        ) {
            val widthPx = maxOf((clip.durationMs * pxPerMs).toInt(), 40)
            val startPx = ((clip.startMs + TIMELINE_START_PADDING_MS) * pxPerMs).toInt()


            val clipView = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    widthPx,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    leftMargin = startPx
                    topMargin = dp(6)
                    bottomMargin = dp(6)
                }
//                setBackgroundColor(Color.parseColor(color))
                setBackgroundColor(
                    if (clip == selectedClip)
                        Color.parseColor("#FFD54F")
                    else
                        Color.parseColor(color)
                )

                alpha = 0.85f
            }

            val label = TextView(this).apply {
                text = File(clip.filePath).name
                textSize = 10f
                setTextColor(Color.WHITE)
                setPadding(8, 4, 8, 4)
                maxLines = 1
            }

            clipView.setOnClickListener {
                selectedClip = clip
                renderTimeline()
            }


// Generate waveform once
            if (clip.waveform == null) {
                clip.waveform = extractWaveform(File(clip.filePath))
            }

// Add waveform view
            clip.waveform?.let {
                val waveformView = WaveformView(this, it).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    alpha = 0.6f
                }
                clipView.addView(waveformView)
            }

            clipView.addView(label)
            parent.addView(clipView)



        }

        playbackTrack.forEach {
            renderClip(playbackLane, it, "#2E7D32")
        }

        recordTrack.forEach {
            renderClip(recordLane, it, "#B71C1C")
        }
    }



    private fun extractWaveform(file: File, samples: Int = 1000): FloatArray {
        val extractor = android.media.MediaExtractor()
        extractor.setDataSource(file.absolutePath)

        var trackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                trackIndex = i
                break
            }
        }

        if (trackIndex == -1) return FloatArray(0)

        extractor.selectTrack(trackIndex)

        val buffer = ByteArray(4096)
        val amplitudes = ArrayList<Float>()

        while (true) {
            val size = extractor.readSampleData(java.nio.ByteBuffer.wrap(buffer), 0)
            if (size < 0) break

            var sum = 0f
            for (i in 0 until size step 2) {
                val sample =
                    ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF))
                sum += kotlin.math.abs(sample) / 32768f
            }
            amplitudes.add(sum / (size / 2))
            extractor.advance()
        }

        extractor.release()

        if (amplitudes.isEmpty()) return FloatArray(0)

        val downsample = amplitudes.size / samples
        return FloatArray(samples) { i ->
            amplitudes[(i * downsample).coerceAtMost(amplitudes.size - 1)]
        }
    }



    //v2
    private fun startPlayback() {
        if (mp == null && currentFile.isNotEmpty()) {
            createMediaPlayer()
        }

        mp?.let { player ->
            //player.seekTo(playheadMs)
            player.start()
            //v1
            //seekBar.max = player.duration

            //v2
            seekBar.max = maxOf(player.duration, 1)

            startTransportClock()

            playBtn.isEnabled = false
            pauseBtn.isEnabled = true
        }
    }


    //--------------
//v1
//    private fun pausePlayback() {
//        mp?.pause()
//        blinkTask?.cancel()
//        timerTask?.cancel()
//        playBtn.isEnabled = true
//        pauseBtn.isEnabled = false
//        playBtn.setColorFilter(Color.parseColor("#4CAF50"))
//    }


    //v2
    private fun pausePlayback() {
        mp?.let {
            playheadMs = it.currentPosition
            it.pause()
        }
        timerTask?.cancel()
        playBtn.isEnabled = true
        pauseBtn.isEnabled = false
    }


    private fun createMediaPlayer() {
        try {
            mp?.release()
            mp = MediaPlayer.create(this, Uri.fromFile(File(currentFile)))
            mpCreated = true
            playbackLane.visibility = View.VISIBLE
            trackInfoText.text = File(currentFile).name
            playBtn.isEnabled = true
            pauseBtn.isEnabled = false
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading audio", Toast.LENGTH_SHORT).show()
        }
    }
//v1
//    private fun refreshRecordedList() {
//        recordedTakes.clear()
//        filePathsList.clear()
//        val dir = getRecordDir().absolutePath
//        FileUtil.listDir(dir, filePathsList)
//
//        filePathsList.forEach { path ->
//            val item = HashMap<String, Any>()
//            item["file"] = path
//            recordedTakes.add(item)
//        }
//        takesListView.adapter = TakesAdapter(this, recordedTakes)
//    }


    //v2

//    private fun refreshRecordedList() {
//        filePathsList.clear()
//        val dir = getRecordDir().absolutePath
//        FileUtil.listDir(dir, filePathsList)
//
//        val data = ArrayList<HashMap<String, Any>>()
//
//        filePathsList.forEach { path ->
//            if (!path.endsWith(".m4a") && !path.endsWith(".mp3")) return@forEach
//            data.add(hashMapOf("file" to path))
//        }
//
//        //takesListView.adapter = TakesAdapter(this, data)
//    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CD_FP && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                currentFile = FileUtil.convertUriToFilePath(this, uri) ?: ""
                createMediaPlayer()
                mp?.setOnPreparedListener { player ->
                    playbackTrack.add(
                        AudioClip(
                            filePath = currentFile,
                            startMs = 0,
                            durationMs = maxOf(player.duration, 100)
                        )
                    )
                    renderTimeline()

                }


            }
        }
    }

    inner class TakesAdapter(context: Context, private val data: ArrayList<HashMap<String, Any>>) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = data.size
        override fun getItem(position: Int) = data[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            val path = data[position]["file"].toString()
            val file = File(path)
            
            if (file.name == ".lastfile") {
                view.visibility = View.GONE
                return view
            } else {
                view.visibility = View.VISIBLE
            }

            view.findViewById<TextView>(android.R.id.text1).apply {
                text = file.name
                setTextColor(Color.WHITE)
            }
            view.findViewById<TextView>(android.R.id.text2).apply {
                text = path
                setTextColor(Color.GRAY)
                textSize = 10f
            }
            
            view.setOnClickListener {
                currentFile = path
                createMediaPlayer()
            }
            
            return view
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mp?.release()
        mediaRecorder?.release()
        timer.cancel()
    }
}
