package com.portolan.anistream.ui.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.portolan.anistream.R
import com.portolan.anistream.data.models.AniListAnime
import com.portolan.anistream.databinding.ItemAnimeCardBinding

class AnimeCardAdapter(
    private val list: List<AniListAnime>,
    private val onClick: (AniListAnime) -> Unit
) : RecyclerView.Adapter<AnimeCardAdapter.VH>() {

    inner class VH(val b: ItemAnimeCardBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemAnimeCardBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val a = list[pos]
        Glide.with(h.b.root).load(a.image).placeholder(R.drawable.placeholder).into(h.b.ivCover)
        h.b.tvTitle.text = a.title?.display() ?: "Unknown"
        h.b.tvRating.text = a.rating?.let { "★ ${it}%" } ?: ""
        h.b.root.setOnClickListener { onClick(a) }
        h.b.root.setOnFocusChangeListener { v, has ->
            v.scaleX = if (has) 1.08f else 1f
            v.scaleY = if (has) 1.08f else 1f
        }
    }

    override fun getItemCount() = list.size
}
