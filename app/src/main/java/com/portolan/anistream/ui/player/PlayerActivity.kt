package com.portolan.anistream.ui.player

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.portolan.anistream.data.models.SkipInterval
import com.portolan.anistream.data.models.StreamSource
import com.portolan.anistream.databinding.ActivityPlayerBinding
import com.portolan.anistream.utils.toTimeString
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class PlayerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlayerBinding
    private lateinit var vm: PlayerViewModel

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null

    private var sources: List<StreamSource> = emptyList()
    private var sourceIndex = 0
    private var skipOp: SkipInterval? = null
    private var skipEd: SkipInterval? = null
    private var controlsShown = true

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() { tickProgress(); progressHandler.postDelayed(this, 500) }
    }
    private var saveExecutor: ScheduledExecutorService? = null

    companion object {
        const val EXTRA_EPISODE_ID = "episode_id"
        const val EXTRA_ANIME_ID = "anime_id"
        const val EXTRA_ANIME_TITLE = "anime_title"
        const val EXTRA_ANIME_IMAGE = "anime_image"
        const val EXTRA_EPISODE_NUMBER = "episode_number"
        const val EXTRA_MAL_ID = "mal_id"
        const val EXTRA_SUB_OR_DUB = "sub_or_dub"
        const val EXTRA_PROVIDER = "provider"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        vm = ViewModelProvider(this)[PlayerViewModel::class.java]

        val epId  = intent.getStringExtra(EXTRA_EPISODE_ID) ?: return
        val anId  = intent.getStringExtra(EXTRA_ANIME_ID) ?: ""
        val title = intent.getStringExtra(EXTRA_ANIME_TITLE) ?: ""
        val image = intent.getStringExtra(EXTRA_ANIME_IMAGE)
        val epNum = intent.getIntExtra(EXTRA_EPISODE_NUMBER, 1)
        val malId = intent.getIntExtra(EXTRA_MAL_ID, -1)
        val sod   = intent.getStringExtra(EXTRA_SUB_OR_DUB) ?: "sub"
        val prov  = intent.getStringExtra(EXTRA_PROVIDER) ?: "animepahe"

        b.tvTitle.text = "$title · Ep $epNum"
        initVlc()
        setupControls()
        observe()
        vm.loadEpisode(epId, malId, epNum, anId, title, image, sod, prov)
    }

    private fun initVlc() {
        libVlc = LibVLC(this, arrayListOf(
            "--network-caching=4000", "--live-caching=4000",
            "--file-caching=4000", "--http-reconnect", "--no-audio-time-stretch",
            "--adaptive-logic=highest"
        ))
        player = MediaPlayer(libVlc)
        player!!.attachViews(b.vlcLayout, null, false, false)
        player!!.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Playing  -> runOnUiThread { b.btnPlay.text = "⏸"; b.progress.visibility = View.GONE }
                MediaPlayer.Event.Paused   -> runOnUiThread { b.btnPlay.text = "▶" }
                MediaPlayer.Event.Buffering -> runOnUiThread {
                    b.progress.visibility = if (ev.buffering < 100f) View.VISIBLE else View.GONE
                }
                MediaPlayer.Event.EndReached -> runOnUiThread { finish() }
                MediaPlayer.Event.EncounteredError -> runOnUiThread { tryNext() }
                MediaPlayer.Event.TimeChanged -> runOnUiThread { checkSkipButtons() }
            }
        }
    }

    private fun setupControls() {
        b.btnPlay.setOnClickListener { togglePlay() }
        b.btnRew.setOnClickListener  { seek(-10_000L); bump() }
        b.btnFwd.setOnClickListener  { seek(30_000L);  bump() }

        b.btnSkipOp.setOnClickListener { skipOp?.let { player?.time = (it.endTime * 1000).toLong() } }
        b.btnSkipEd.setOnClickListener { skipEd?.let { player?.time = (it.endTime * 1000).toLong() } }

        b.btnQuality.setOnClickListener { showQualityPicker() }

        b.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) b.tvCurrent.text = ((player?.length ?: 0) * p / 1000L).toTimeString()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                player?.time = (player?.length ?: 0) * sb.progress / 1000L
            }
        })

        b.vlcLayout.setOnClickListener { if (controlsShown) hideControls() else showControls() }
    }

    private fun observe() {
        vm.loading.observe(this) { b.progress.visibility = if (it) View.VISIBLE else View.GONE }
        vm.error.observe(this)   { it?.let { e -> b.tvError.text = e; b.tvError.visibility = View.VISIBLE } }

        vm.sources.observe(this) { list ->
            sources = list
            // Lock onto 1080p first if present
            val idx1080 = list.indexOfFirst { it.normalizedQuality() == "1080p" }
            sourceIndex = if (idx1080 >= 0) idx1080 else 0
            playSource(list.getOrNull(sourceIndex))
        }

        vm.skipTimes.observe(this) { resp ->
            resp?.results?.forEach {
                when (it.skipType) { "op", "mixed-op" -> skipOp = it.interval; "ed" -> skipEd = it.interval }
            }
        }

        vm.savedProgress.observe(this) { h ->
            if (h != null && h.watchedPosition > 5000L) showResumeDialog(h.watchedPosition)
        }
    }

    private fun playSource(src: StreamSource?) {
        src ?: return
        b.tvError.visibility = View.GONE
        val ref = src.referer ?: "https://kwik.cx/"
        val media = Media(libVlc, Uri.parse(src.url)).apply {
            addOption(":http-referrer=$ref")
            addOption(":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }
        player!!.media = media; media.release(); player!!.play()
        b.btnQuality.text = src.normalizedQuality()
        progressHandler.post(progressRunnable)
        startSaving()
        scheduleHide()
    }

    private fun tryNext() {
        if (++sourceIndex < sources.size) playSource(sources[sourceIndex])
        else b.tvError.apply { text = "All sources failed"; visibility = View.VISIBLE }
    }

    private fun togglePlay() {
        if (player?.isPlaying == true) player?.pause() else player?.play()
        bump()
    }

    private fun seek(ms: Long) {
        val d = player?.length ?: 0
        player?.time = ((player?.time ?: 0) + ms).coerceIn(0L, d)
    }

    private fun tickProgress() {
        val t = player?.time ?: 0L; val d = player?.length ?: 0L
        if (d > 0) b.seekBar.progress = (t * 1000 / d).toInt()
        b.tvCurrent.text = t.toTimeString()
        b.tvTotal.text = d.toTimeString()
    }

    private fun checkSkipButtons() {
        val sec = (player?.time ?: 0L) / 1000.0
        skipOp?.let { b.btnSkipOp.visibility = if (sec >= it.startTime - 3 && sec < it.endTime) View.VISIBLE else View.GONE }
            ?: run { b.btnSkipOp.visibility = View.GONE }
        skipEd?.let { b.btnSkipEd.visibility = if (sec >= it.startTime - 3 && sec < it.endTime) View.VISIBLE else View.GONE }
            ?: run { b.btnSkipEd.visibility = View.GONE }
    }

    private fun showQualityPicker() {
        if (sources.isEmpty()) return
        val labels = sources.map { s ->
            val provTag = s.provider?.uppercase()?.let { " · $it" } ?: ""
            "${s.normalizedQuality()}$provTag"
        }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Quality (1080p / 720p / Auto)")
            .setItems(labels) { _, i ->
                val t = player?.time ?: 0L
                sourceIndex = i; playSource(sources[i])
                progressHandler.postDelayed({ player?.time = t }, 1500)
            }.show()
    }

    private fun showResumeDialog(pos: Long) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Resume?")
            .setMessage("Resume from ${pos.toTimeString()}?")
            .setPositiveButton("Resume") { _, _ -> progressHandler.postDelayed({ player?.time = pos }, 2000) }
            .setNegativeButton("Start Over") { _, _ -> }
            .show()
    }

    private fun showControls() {
        controlsShown = true
        b.topBar.visibility = View.VISIBLE; b.bottomBar.visibility = View.VISIBLE
        scheduleHide()
    }

    private fun hideControls() {
        controlsShown = false
        b.topBar.visibility = View.GONE; b.bottomBar.visibility = View.GONE
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 4000)
    }

    private fun bump() { if (!controlsShown) showControls() else scheduleHide() }

    private fun startSaving() {
        saveExecutor?.shutdown()
        saveExecutor = Executors.newSingleThreadScheduledExecutor()
        saveExecutor?.scheduleAtFixedRate({
            val t = player?.time ?: 0; val d = player?.length ?: 0
            if (t > 0 && d > 0) vm.saveProgress(t, d)
        }, 5, 5, TimeUnit.SECONDS)
    }

    override fun onKeyDown(code: Int, ev: KeyEvent?): Boolean = when (code) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { togglePlay(); true }
        KeyEvent.KEYCODE_DPAD_LEFT  -> { seek(-10_000L); bump(); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { seek(30_000L);  bump(); true }
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { showControls(); true }
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlay(); true }
        KeyEvent.KEYCODE_MEDIA_PLAY  -> { player?.play(); true }
        KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); true }
        KeyEvent.KEYCODE_BACK -> { if (controlsShown && player?.isPlaying == true) { hideControls(); true } else { saveAndExit(); true } }
        else -> super.onKeyDown(code, ev)
    }

    private fun saveAndExit() {
        vm.saveProgress(player?.time ?: 0, player?.length ?: 0); finish()
    }

    override fun onPause()   { super.onPause();  player?.pause();  progressHandler.removeCallbacks(progressRunnable); saveExecutor?.shutdown() }
    override fun onResume()  { super.onResume(); player?.play();   progressHandler.post(progressRunnable); startSaving() }
    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacksAndMessages(null)
        progressHandler.removeCallbacksAndMessages(null)
        saveExecutor?.shutdownNow()
        player?.release(); libVlc?.release()
    }
}
