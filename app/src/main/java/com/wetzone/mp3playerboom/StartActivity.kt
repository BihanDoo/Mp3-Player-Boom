package com.wetzone.mp3playerboom

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class StartActivity : AppCompatActivity() {

    private val timer = Timer()
    private var currentMusicPath = ""
    private var currentPos = -1
    private var mpCreated = false
    private var seekIsPlaying = false
    private var isExpanded = false

    private val allMusic = ArrayList<HashMap<String, Any>>()

    private lateinit var proglin: LinearLayout
    private lateinit var linearPlayer: MaterialCardView
    private lateinit var linearArtwork: LinearLayout
    private lateinit var listView1: ListView
    private lateinit var currentMusicText: TextView
    private lateinit var artistNameText: TextView
    private lateinit var time1: TextView
    private lateinit var time2: TextView
    private lateinit var seekBar1: SeekBar
    private lateinit var playPauseBtn: ImageView
    private lateinit var expandBtn: ImageView
    private lateinit var searchBtn: ImageView
    private lateinit var bottomSpacer: View

    private lateinit var prefs: SharedPreferences
    private var mp: MediaPlayer? = null
    private var timerTask: TimerTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.start)
        initialize()

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 1000)
        } else {
            initializeLogic()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1000 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeLogic()
        } else {
            Toast.makeText(this, "Permission denied. Cannot scan for music.", Toast.LENGTH_LONG).show()
        }
    }

    private fun initialize() {
        proglin = findViewById(R.id.proglin)
        linearPlayer = findViewById(R.id.linear_player)
        linearArtwork = findViewById(R.id.linear19)
        listView1 = findViewById(R.id.listview1)
        currentMusicText = findViewById(R.id.currentmusictext)
        artistNameText = findViewById(R.id.artist_name)
        time1 = findViewById(R.id.time1)
        time2 = findViewById(R.id.time2)
        seekBar1 = findViewById(R.id.seekbar1)
        playPauseBtn = findViewById(R.id.playpausebtn)
        expandBtn = findViewById(R.id.imageview6)
        searchBtn = findViewById(R.id.imageview_search)
        bottomSpacer = findViewById(R.id.bottom_spacer)

        prefs = getSharedPreferences("favs", Context.MODE_PRIVATE)

        listView1.setOnItemClickListener { _, _, position, _ ->
            currentMusicPath = allMusic[position]["file"].toString()
            currentPos = position
            createAndPlay()
            (listView1.adapter as BaseAdapter).notifyDataSetChanged()
            marqueeText(Uri.parse(currentMusicPath).lastPathSegment ?: "Unknown Song", currentMusicText)
            artistNameText.text = "Now Playing"
        }

        seekBar1.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val currentSec = progress / 1000
                    time1.text = String.format("%d:%02d", currentSec / 60, currentSec % 60)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (mpCreated) {
                    seekIsPlaying = mp?.isPlaying ?: false
                    timerTask?.cancel()
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                mp?.seekTo(seekBar1.progress)
                if (seekIsPlaying) {
                    playMusic()
                }
            }
        })

        playPauseBtn.setOnClickListener {
            handlePlayPauseClick()
        }

        expandBtn.setOnClickListener {
            toggleExpand()
        }
        
        searchBtn.setOnClickListener {
            Toast.makeText(this, "Search feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeLogic() {
        startScanning()
        mpCreated = false
        linearPlayer.visibility = View.GONE
        linearArtwork.visibility = View.GONE
        isExpanded = false
    }

    private fun startScanning() {
        proglin.visibility = View.VISIBLE
        Thread {
            allMusic.clear()
            scanDirectory(File(FileUtil.getExternalStorageDir()))
            
            Handler(Looper.getMainLooper()).post {
                if (allMusic.isEmpty()) {
                    Toast.makeText(this, "No music found in storage", Toast.LENGTH_SHORT).show()
                }
                listView1.adapter = MusicAdapter(allMusic)
                proglin.visibility = View.GONE
            }
        }.start()
    }

    private fun scanDirectory(dir: File) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.lowercase().startsWith(".") && !file.name.lowercase().startsWith("sys")) {
                    scanDirectory(file)
                }
            } else {
                if (file.name.lowercase().endsWith(".mp3")) {
                    val item = HashMap<String, Any>()
                    item["file"] = file.absolutePath
                    allMusic.add(item)
                }
            }
        }
    }

    private fun playMusic() {
        if (currentMusicPath.isEmpty()) {
            Toast.makeText(this, "Please pick a song to play", Toast.LENGTH_SHORT).show()
            return
        }

        if (mpCreated) {
            mp?.start()
            playPauseBtn.setImageResource(R.drawable.ic_pause_circle_fill_white)
            startTimer()
            linearPlayer.visibility = View.VISIBLE
            bottomSpacer.visibility = View.VISIBLE
        } else {
            createAndPlay()
        }
    }

    private fun pauseMusic() {
        if (mpCreated) {
            mp?.pause()
            playPauseBtn.setImageResource(R.drawable.ic_play_circle_fill_white)
            timerTask?.cancel()
        }
    }

    private fun createAndPlay() {
        if (currentMusicPath.isEmpty()) return
        
        try {
            mp?.stop()
            mp?.release()
            mp = MediaPlayer().apply {
                setDataSource(currentMusicPath)
                prepare()
                setOnCompletionListener {
                    pauseMusic()
                    playNext()
                }
            }
            mpCreated = true
            
            mp?.let { player ->
                seekBar1.max = player.duration
                seekBar1.progress = 0
                time1.text = "0:00"
                
                val durationSec = player.duration / 1000
                val minutes = durationSec / 60
                val seconds = durationSec % 60
                time2.text = String.format("%d:%02d", minutes, seconds)
                
                linearPlayer.visibility = View.VISIBLE
                bottomSpacer.visibility = View.VISIBLE
                playMusic()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error playing file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playNext() {
        if (allMusic.isNotEmpty()) {
            currentPos = (currentPos + 1) % allMusic.size
            currentMusicPath = allMusic[currentPos]["file"].toString()
            createAndPlay()
            (listView1.adapter as BaseAdapter).notifyDataSetChanged()
            marqueeText(Uri.parse(currentMusicPath).lastPathSegment ?: "Unknown Song", currentMusicText)
        }
    }

    private fun startTimer() {
        timerTask?.cancel()
        timerTask = object : TimerTask() {
            override fun run() {
                Handler(Looper.getMainLooper()).post {
                    mp?.let { player ->
                        if (player.isPlaying) {
                            seekBar1.progress = player.currentPosition
                            val currentSec = player.currentPosition / 1000
                            time1.text = String.format("%d:%02d", currentSec / 60, currentSec % 60)
                        }
                    }
                }
            }
        }
        timer.scheduleAtFixedRate(timerTask, 0, 1000)
    }

    private fun marqueeText(text: String, textView: TextView) {
        textView.text = text
        textView.ellipsize = TextUtils.TruncateAt.MARQUEE
        textView.isSelected = true
        textView.isSingleLine = true
        textView.marqueeRepeatLimit = -1
    }

    private fun handlePlayPauseClick() {
        if (mpCreated) {
            if (mp?.isPlaying == true) {
                pauseMusic()
            } else {
                playMusic()
            }
        } else if (currentMusicPath.isNotEmpty()) {
            createAndPlay()
        }
    }

    private fun toggleExpand() {
        if (isExpanded) {
            isExpanded = false
            linearArtwork.visibility = View.GONE
            expandBtn.setImageResource(R.drawable.ic_open_in_new_white)
        } else {
            isExpanded = true
            linearArtwork.visibility = View.VISIBLE
            expandBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        }
    }

    inner class MusicAdapter(private val data: ArrayList<HashMap<String, Any>>) : BaseAdapter() {
        override fun getCount(): Int = data.size
        override fun getItem(position: Int) = data[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@StartActivity).inflate(R.layout.custmahm, parent, false)
            
            val itemLinear = view.findViewById<LinearLayout>(R.id.linear1)
            val titleText = view.findViewById<TextView>(R.id.textview1)
            val subTitleText = view.findViewById<TextView>(R.id.linear3)
            
            val filePath = data[position]["file"].toString()
            titleText.text = Uri.parse(filePath).lastPathSegment ?: "Unknown"
            subTitleText.text = "MP3 Audio"
            
            if (position == currentPos) {
                titleText.setTextColor(Color.parseColor("#4CAF50"))
                itemLinear.setBackgroundResource(R.drawable.rounded_corner_bg)
            } else {
                titleText.setTextColor(Color.BLACK)
                itemLinear.setBackgroundColor(Color.TRANSPARENT)
            }
            
            return view
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mp?.release()
        timer.cancel()
    }
}
