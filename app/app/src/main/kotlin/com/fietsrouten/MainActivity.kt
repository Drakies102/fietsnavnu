package com.fietsrouten

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fietsrouten.databinding.ActivityMainBinding
import com.fietsrouten.ui.map.MapFragment
import com.fietsrouten.ui.profile.ProfileFragment
import com.fietsrouten.ui.rides.RidesFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var mapFragment: MapFragment
    private lateinit var ridesFragment: RidesFragment
    private lateinit var profileFragment: ProfileFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        AppPreferences.applyPersistedTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapFragment = supportFragmentManager.findFragmentByTag(TAG_MAP) as? MapFragment ?: MapFragment()
        ridesFragment = supportFragmentManager.findFragmentByTag(TAG_RIDES) as? RidesFragment ?: RidesFragment()
        profileFragment = supportFragmentManager.findFragmentByTag(TAG_PROFILE) as? ProfileFragment ?: ProfileFragment()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, profileFragment, TAG_PROFILE).hide(profileFragment)
                .add(R.id.fragmentContainer, ridesFragment, TAG_RIDES).hide(ridesFragment)
                .add(R.id.fragmentContainer, mapFragment, TAG_MAP)
                .commit()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.tabRitten -> ridesFragment
                R.id.tabProfiel -> profileFragment
                else -> mapFragment
            }
            supportFragmentManager.beginTransaction()
                .hide(mapFragment).hide(ridesFragment).hide(profileFragment)
                .show(target)
                .commitAllowingStateLoss()
            true
        }
    }

    /** MapFragment hides the tab bar during full-screen states (search, planner, navigation). */
    fun setTabBarVisible(visible: Boolean) {
        binding.bottomNav.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
    }

    companion object {
        private const val TAG_MAP = "map"
        private const val TAG_RIDES = "rides"
        private const val TAG_PROFILE = "profile"
    }
}
