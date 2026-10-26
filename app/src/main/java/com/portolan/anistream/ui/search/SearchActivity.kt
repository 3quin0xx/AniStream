package com.portolan.anistream.ui.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.portolan.anistream.databinding.ActivitySearchBinding
import com.portolan.anistream.ui.adapters.AnimeCardAdapter
import com.portolan.anistream.ui.detail.DetailActivity

class SearchActivity : AppCompatActivity() {
    private lateinit var b: ActivitySearchBinding
    private lateinit var vm: SearchViewModel

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivitySearchBinding.inflate(layoutInflater); setContentView(b.root)
        vm = ViewModelProvider(this)[SearchViewModel::class.java]
        b.rvResults.layoutManager = GridLayoutManager(this, 4)
        vm.loading.observe(this) { b.progress.visibility = if (it) View.VISIBLE else View.GONE }
        vm.results.observe(this) { list ->
            b.rvResults.adapter = AnimeCardAdapter(list) { a ->
                startActivity(Intent(this, DetailActivity::class.java).apply {
                    putExtra(DetailActivity.EXTRA_ANIME_ID, a.id)
                    putExtra(DetailActivity.EXTRA_ANIME_TITLE, a.title?.display())
                    putExtra(DetailActivity.EXTRA_ANIME_IMAGE, a.image)
                    putExtra(DetailActivity.EXTRA_MAL_ID, a.malId ?: -1)
                })
            }
        }
        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { vm.search(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b_: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b_: Int, c: Int) {}
        })
        b.etSearch.requestFocus()
    }
}
