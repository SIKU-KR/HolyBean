package eloom.holybean

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import eloom.holybean.interfaces.MainActivityListener
import eloom.holybean.ui.credits.CreditsFragment
import eloom.holybean.ui.home.HomeFragment
import eloom.holybean.ui.menumanagement.MenuManagementFragment
import eloom.holybean.ui.orderlist.OrdersFragment
import eloom.holybean.ui.report.ReportFragment

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), MainActivityListener {
    companion object {
        const val PERMISSION_BLUETOOTH = 1
        const val PERMISSION_BLUETOOTH_ADMIN = 2
        const val PERMISSION_BLUETOOTH_CONNECT = 3
        const val PERMISSION_BLUETOOTH_SCAN = 4
    }

    private lateinit var drawerLayout: DrawerLayout

    override fun replaceHomeFragment() {
        val homeFragment = HomeFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, homeFragment)
            .commit()
    }

    override fun replaceOrdersFragment() {
        val ordersFragment = OrdersFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ordersFragment)
            .commit()
    }

    override fun replaceCreditsFragment() {
        val creditsController = CreditsFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, creditsController)
            .commit()
    }

    override fun replaceMenuManagementFragment() {
        val menuManagementFragment = MenuManagementFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, menuManagementFragment)
            .commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 몰입 모드 활성화
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.nav_view)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }
        navigationView.setNavigationItemSelectedListener { drawerItem ->
            // 드로어 메뉴 아이템 클릭 시 해당 Fragment로 전환
            when (drawerItem.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment())
                R.id.nav_orders -> loadFragment(OrdersFragment())
                R.id.nav_report -> loadFragment(ReportFragment())
                R.id.nav_credit -> loadFragment(CreditsFragment())
                R.id.menu_management -> loadFragment(MenuManagementFragment())
//                R.id.nav_credit -> Toast.makeText(this, "업데이트 중 입니다.", Toast.LENGTH_SHORT).show()
                // 다른 메뉴 아이템에 대한 처리를 여기에 추가
            }
            drawerItem.isChecked = true
            drawerLayout.closeDrawers()
            true
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH),
                PERMISSION_BLUETOOTH
            )
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADMIN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_ADMIN),
                PERMISSION_BLUETOOTH_ADMIN
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                PERMISSION_BLUETOOTH_CONNECT
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                PERMISSION_BLUETOOTH_SCAN
            )
        } else {
        }

    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
