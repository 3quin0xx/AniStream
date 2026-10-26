package com.portolan.anistream.ui.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.portolan.anistream.R
import com.portolan.anistream.data.db.WatchHistory
import com.portolan.anistream.databinding.ItemAnimeCardBinding

class ContinueWatchingAdapter(
    private val list: List<WatchHistory>,
    private val onClick: (WatchHistory) -> Unit
) : RecyclerView.Adapter<ContinueWatchingAdapter.VH>() {

    inner class VH(val b: ItemAnimeCardBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemAnimeCardBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val h_ = list[pos]
        Glide.with(h.b.root).load(h_.animeImage).placeholder(R.drawable.placeholder).into(h.b.ivCover)
        h.b.tvTitle.text = h_.animeTitle
        h.b.tvRating.text = "Ep ${h_.episodeNumber} · ${h_.progressPercent()}%"
        h.b.root.setOnClickListener { onClick(h_) }
        h.b.root.setOnFocusChangeListener { v, has ->
            v.scaleX = if (has) 1.08f else 1f
            v.scaleY = if (has) 1.08f else 1f
        }
    }

    override fun getItemCount() = list.size
}
