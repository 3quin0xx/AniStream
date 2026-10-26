package com.portolan.anistream.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.portolan.anistream.databinding.FragmentHomeBinding
import com.portolan.anistream.ui.adapters.AnimeCardAdapter
import com.portolan.anistream.ui.adapters.ContinueWatchingAdapter
import com.portolan.anistream.ui.detail.DetailActivity

class HomeFragment : Fragment() {
    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private lateinit var vm: HomeViewModel

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHomeBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        vm = ViewModelProvider(this)[HomeViewModel::class.java]
        b.rvTrending.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        b.rvPopular.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        b.rvRecent.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        b.rvContinue.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        vm.loading.observe(viewLifecycleOwner) { b.progress.visibility = if (it) View.VISIBLE else View.GONE }

        vm.trending.observe(viewLifecycleOwner) { list ->
            b.rvTrending.adapter = AnimeCardAdapter(list) { openDetail(it.id, it.title?.display(), it.image, it.malId) }
        }
        vm.popular.observe(viewLifecycleOwner) { list ->
            b.rvPopular.adapter = AnimeCardAdapter(list) { openDetail(it.id, it.title?.display(), it.image, it.malId) }
        }
        vm.continueWatching.observe(viewLifecycleOwner) { list ->
            b.rowContinue.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            b.rvContinue.adapter = ContinueWatchingAdapter(list) { h ->
                openDetail(h.animeId, h.animeTitle, h.animeImage, null)
            }
        }
    }

    private fun openDetail(id: String, title: String?, image: String?, malId: Int?) {
        startActivity(Intent(requireContext(), DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_ANIME_ID, id)
            putExtra(DetailActivity.EXTRA_ANIME_TITLE, title)
            putExtra(DetailActivity.EXTRA_ANIME_IMAGE, image)
            putExtra(DetailActivity.EXTRA_MAL_ID, malId ?: -1)
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
