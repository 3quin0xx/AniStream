package com.portolan.anistream

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.portolan.anistream.databinding.ActivityMainBinding
import com.portolan.anistream.ui.home.HomeFragment
import com.portolan.anistream.ui.search.SearchActivity

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        loadFragment(HomeFragment())
        b.btnSearch.setOnClickListener { startActivity(Intent(this, SearchActivity::class.java)) }
        b.btnHome.setOnClickListener { loadFragment(HomeFragment()) }
    }

    private fun loadFragment(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, f)
            .commit()
    }
}
