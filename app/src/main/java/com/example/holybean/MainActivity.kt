package com.example.holybean

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        // 초기 화면을 HomeFragment로 설정
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }
        navigationView.setNavigationItemSelectedListener { drawerItem ->
            // 드로어 메뉴 아이템 클릭 시 해당 Fragment로 전환
            when (drawerItem.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment())
                // 다른 메뉴 아이템에 대한 처리를 여기에 추가
            }
            drawerItem.isChecked = true
            drawerLayout.closeDrawers()
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
