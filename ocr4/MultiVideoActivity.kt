package com.goodwy.gallery.activities

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.gallery.R
import com.goodwy.gallery.databinding.ActivityMultiVideoBinding

class MultiVideoActivity : BaseSimpleActivity() {

    companion object {
        const val EXTRA_PATHS = "multi_video_paths"
    }

    private lateinit var binding: ActivityMultiVideoBinding

    private val players = arrayOfNulls<MediaPlayer>(4)
    private val textures = arrayOfNulls<TextureView>(4)
    private val playBtns = arrayOfNulls<ImageButton>(4)
    private val posTvs = arrayOfNulls<TextView>(4)
    private val isReady = BooleanArray(4)
    private val paths = ArrayList<String>()

    private var isSyncMode = false
    private var isMutedAll = false
    private var isAllPlaying = true

    private val handler = Handler(Looper.getMainLooper())
    private val posRunnable = object : Runnable {
        override fun run() {
            updatePositions()
            handler.postDelayed(this, 500)
        }
    }

    override fun getAppIconIDs() = arrayListOf(R.mipmap.ic_launcher)
    override fun getAppLauncherName() = getString(R.string.app_launcher_name)
    override fun getRepositoryName() = "Gallery"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMultiVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val incoming = intent.getStringArrayListExtra(EXTRA_PATHS)
        if (incoming.isNullOrEmpty()) { finish(); return }
        paths.addAll(incoming.take(4))

        setupGrid()
        setupControls()
        handler.post(posRunnable)
    }

    private fun setupGrid() {
        textures[0] = binding.multiVideoTexture0
        textures[1] = binding.multiVideoTexture1
        textures[2] = binding.multiVideoTexture2
        textures[3] = binding.multiVideoTexture3
        playBtns[0] = binding.multiVideoPlay0
        playBtns[1] = binding.multiVideoPlay1
        playBtns[2] = binding.multiVideoPlay2
        playBtns[3] = binding.multiVideoPlay3
        posTvs[0] = binding.multiVideoPos0
        posTvs[1] = binding.multiVideoPos1
        posTvs[2] = binding.multiVideoPos2
        posTvs[3] = binding.multiVideoPos3

        if (paths.size > 2) {
            binding.multiVideoDividerH.visibility = View.VISIBLE
            binding.multiVideoRow2.visibility = View.VISIBLE
            val p = binding.multiVideoRow2.layoutParams as android.widget.LinearLayout.LayoutParams
            p.weight = 1f
            binding.multiVideoRow2.layoutParams = p
        }

        if (paths.size < 4) binding.multiVideoCell3.visibility = View.GONE
        if (paths.size < 3) binding.multiVideoCell2.visibility = View.GONE

        paths.forEachIndexed { i, path -> initPlayer(i, path) }
    }

    private fun initPlayer(index: Int, path: String) {
        val texture = textures[index] ?: return
        playBtns[index]?.setOnClickListener { toggleSingle(index) }

        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                try {
                    val mp = MediaPlayer()
                    mp.setDataSource(path)
                    mp.setSurface(Surface(st))
                    mp.isLooping = true
                    mp.setOnPreparedListener {
                        isReady[index] = true
                        it.start()
                        updatePlayBtn(index, true)
                    }
                    mp.prepareAsync()
                    players[index] = mp
                } catch (_: Exception) {}
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    private fun setupControls() {
        binding.multiVideoBack.setOnClickListener { finish() }

        binding.multiVideoPlayAll.setOnClickListener {
            isAllPlaying = !isAllPlaying
            players.forEachIndexed { i, mp ->
                if (mp != null && isReady[i]) {
                    if (isAllPlaying) mp.start() else mp.pause()
                    updatePlayBtn(i, isAllPlaying)
                }
            }
            binding.multiVideoPlayAll.setImageResource(
                if (isAllPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector
            )
        }

        binding.multiVideoSync.setOnCheckedChangeListener { _, checked ->
            isSyncMode = checked
            if (isSyncMode) syncAllToFirst()
        }

        binding.multiVideoMuteAll.setOnClickListener {
            isMutedAll = !isMutedAll
            players.forEach { it?.setVolume(if (isMutedAll) 0f else 1f, if (isMutedAll) 0f else 1f) }
            binding.multiVideoMuteAll.setImageResource(
                if (isMutedAll) R.drawable.ic_vector_speaker_off else R.drawable.ic_vector_speaker_on
            )
        }
    }

    private fun toggleSingle(index: Int) {
        if (isSyncMode) {
            isAllPlaying = !isAllPlaying
            players.forEachIndexed { i, mp ->
                if (mp != null && isReady[i]) {
                    if (isAllPlaying) mp.start() else mp.pause()
                    updatePlayBtn(i, isAllPlaying)
                }
            }
        } else {
            val mp = players[index] ?: return
            if (!isReady[index]) return
            if (mp.isPlaying) { mp.pause(); updatePlayBtn(index, false) }
            else { mp.start(); updatePlayBtn(index, true) }
        }
    }

    private fun syncAllToFirst() {
        val firstPos = players[0]?.currentPosition ?: return
        players.forEachIndexed { i, mp ->
            if (i > 0 && mp != null && isReady[i]) mp.seekTo(firstPos)
        }
    }

    private fun updatePlayBtn(index: Int, playing: Boolean) {
        playBtns[index]?.setImageResource(
            if (playing) R.drawable.ic_pause_vector else R.drawable.ic_play_vector
        )
    }

    private fun updatePositions() {
        players.forEachIndexed { i, mp ->
            if (mp != null && isReady[i]) {
                val ms = mp.currentPosition
                val s = (ms / 1000) % 60
                val m = (ms / 60000) % 60
                val h = ms / 3600000
                posTvs[i]?.text = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
            }
        }
    }

    fun getCurrentPosition(): Long = players[0]?.currentPosition?.toLong() ?: 0L

    override fun onPause() {
        super.onPause()
        players.forEach { it?.pause() }
    }

    override fun onResume() {
        super.onResume()
        if (isAllPlaying) players.forEachIndexed { i, mp -> if (isReady[i]) mp?.start() }
    }

    override fun onDestroy() {
        handler.removeCallbacks(posRunnable)
        players.forEach { it?.release() }
        super.onDestroy()
    }
}
