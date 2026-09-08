package com.thkim.golfyardage.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.thkim.golfyardage.data.ClubSettingsRepository
import com.thkim.golfyardage.databinding.ActivityClubSettingsBinding

class ClubSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClubSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClubSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val clubs = ClubSettingsRepository.loadAll(this)
        val adapter = ClubDistanceAdapter(clubs) { clubName, meters ->
            ClubSettingsRepository.save(this, clubName, meters)
        }
        binding.recyclerClubs.layoutManager = LinearLayoutManager(this)
        binding.recyclerClubs.adapter = adapter
    }
}
