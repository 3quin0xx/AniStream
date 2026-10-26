package com.portolan.anistream.ui.adapters

import android.graphics.Color
import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.portolan.anistream.data.db.WatchHistory
import com.portolan.anistream.data.models.EpisodeItem
import com.portolan.anistream.databinding.ItemEpisodeBinding
import com.portolan.anistream.utils.toTimeString

class EpisodeAdapter(
    private val episodes: List<EpisodeItem>,
    private val watchMap: Map<String, WatchHistory> = emptyMap(),
    private val onClick: (EpisodeItem) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

    inner class VH(val b: ItemEpisodeBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemEpisodeBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val ep = episodes[pos]
        h.b.tvNum.text = ep.title?.let { "Ep ${ep.number}: $it" } ?: "Episode ${ep.number}"
        val hist = watchMap[ep.id]
        if (hist != null) {
            h.b.pbProgress.visibility = View.VISIBLE
            h.b.pbProgress.progress = hist.progressPercent()
            h.b.tvTime.visibility = View.VISIBLE
            h.b.tvTime.text = hist.watchedPosition.toTimeString()
        } else {
            h.b.pbProgress.visibility = View.GONE
            h.b.tvTime.visibility = View.GONE
        }
        h.b.root.setOnClickListener { onClick(ep) }
        h.b.root.setOnFocusChangeListener { v, has ->
            v.setBackgroundColor(if (has) 0xFF7B2FBE.toInt() else Color.TRANSPARENT)
        }
    }

    override fun getItemCount() = episodes.size
}
