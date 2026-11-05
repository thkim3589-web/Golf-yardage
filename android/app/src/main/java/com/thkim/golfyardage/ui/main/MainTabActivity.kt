package com.thkim.golfyardage.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.thkim.golfyardage.R
import com.thkim.golfyardage.databinding.ActivityMainTabBinding
import com.thkim.golfyardage.ui.course.ExploreFragment
import com.thkim.golfyardage.ui.home.HomeFragment
import com.thkim.golfyardage.ui.mypage.MyPageFragment
import com.thkim.golfyardage.ui.notifications.NotificationsFragment
import com.thkim.golfyardage.ui.scorecard.ScorecardFragment

class MainTabActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainTabBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainTabBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            showFragment(HomeFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_explore -> ExploreFragment()
                R.id.nav_scorecard -> ScorecardFragment()
                R.id.nav_notifications -> NotificationsFragment()
                R.id.nav_mypage -> MyPageFragment()
                else -> return@setOnItemSelectedListener false
            }
            showFragment(fragment)
            true
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    /** 홈에서 '탐색으로 가기'처럼 다른 탭으로 코드에서 전환할 때 사용 */
    fun switchToTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }
}
