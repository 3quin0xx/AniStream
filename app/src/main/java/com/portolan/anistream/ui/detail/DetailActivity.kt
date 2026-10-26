package com.portolan.anistream.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.portolan.anistream.databinding.ActivityDetailBinding
import com.portolan.anistream.ui.adapters.EpisodeAdapter
import com.portolan.anistream.ui.player.PlayerActivity

class DetailActivity : AppCompatActivity() {
    private lateinit var b: ActivityDetailBinding
    private lateinit var vm: DetailViewModel

    private var currentSubOrDub = "sub"
    private var anilistId = ""
    private var malId = -1

    companion object {
        const val EXTRA_ANIME_ID = "anime_id"
        const val EXTRA_ANIME_TITLE = "anime_title"
        const val EXTRA_ANIME_IMAGE = "anime_image"
        const val EXTRA_MAL_ID = "mal_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        vm = ViewModelProvider(this)[DetailViewModel::class.java]

        anilistId = intent.getStringExtra(EXTRA_ANIME_ID) ?: return
        malId = intent.getIntExtra(EXTRA_MAL_ID, -1)

        b.btnSub.alpha = 1f; b.btnDub.alpha = 0.45f
        b.btnSub.setOnClickListener {
            if (currentSubOrDub != "sub") {
                currentSubOrDub = "sub"
                b.btnSub.alpha = 1f; b.btnDub.alpha = 0.45f
                vm.load(anilistId)
            }
        }
        b.btnDub.setOnClickListener {
            val firstEp = vm.episodes.value?.firstOrNull()
            if (currentSubOrDub != "dub" && firstEp != null) {
                currentSubOrDub = "dub"
                b.btnSub.alpha = 0.45f; b.btnDub.alpha = 1f
                vm.loadDubEpisodes(firstEp.id)
            }
        }

        vm.loading.observe(this) { b.progress.visibility = if (it) View.VISIBLE else View.GONE }

        vm.info.observe(this) { info ->
            Glide.with(this).load(info.cover ?: info.image).into(b.ivBanner)
            Glide.with(this).load(info.image).into(b.ivCover)
            b.tvTitle.text = info.title?.display() ?: "Unknown"
            b.tvDesc.text = info.description?.replace(Regex("<[^>]*>"), "") ?: ""
            b.tvGenres.text = info.genres?.joinToString(" · ") ?: ""
            b.tvRating.text = info.rating?.let { "★ ${it}%" } ?: ""
            b.tvStatus.text = info.status ?: ""
            b.btnDub.visibility = if (info.hasDub == true) View.VISIBLE else View.GONE
            malId = info.malId ?: malId
        }

        vm.episodes.observe(this) { eps ->
            val watchMap = vm.watchMap.value ?: emptyMap()
            b.rvEpisodes.layoutManager = GridLayoutManager(this, 1)
            b.rvEpisodes.adapter = EpisodeAdapter(eps, watchMap) { ep -> openPlayer(ep.id, ep.number) }
        }

        vm.watchMap.observe(this) { map ->
            val eps = vm.episodes.value ?: return@observe
            b.rvEpisodes.adapter = EpisodeAdapter(eps, map) { ep -> openPlayer(ep.id, ep.number) }
        }

        vm.lastWatched.observe(this) { h ->
            if (h != null) {
                b.tvContinue.visibility = View.VISIBLE
                b.tvContinue.text = "▶ Resume Episode ${h.episodeNumber} — ${h.progressPercent()}%"
            }
        }

        vm.error.observe(this) { e ->
            if (e != null) b.tvError.apply { text = e; visibility = View.VISIBLE }
        }

        vm.load(anilistId)
    }

    private fun openPlayer(epId: String, epNum: Int) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_EPISODE_ID, epId)
            putExtra(PlayerActivity.EXTRA_ANIME_ID, anilistId)
            putExtra(PlayerActivity.EXTRA_ANIME_TITLE, vm.info.value?.title?.display())
            putExtra(PlayerActivity.EXTRA_ANIME_IMAGE, vm.info.value?.image)
            putExtra(PlayerActivity.EXTRA_EPISODE_NUMBER, epNum)
            putExtra(PlayerActivity.EXTRA_MAL_ID, malId)
            putExtra(PlayerActivity.EXTRA_SUB_OR_DUB, currentSubOrDub)
        })
    }
}
