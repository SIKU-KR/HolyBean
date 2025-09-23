package eloom.holybean

import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import eloom.holybean.interfaces.MainActivityListener
import eloom.holybean.permission.PermissionCoordinator
import eloom.holybean.permission.PermissionCoordinator.PermissionResult
import eloom.holybean.ui.credits.CreditsFragment
import eloom.holybean.ui.home.HomeFragment
import eloom.holybean.ui.menumanagement.MenuManagementFragment
import eloom.holybean.ui.orderlist.OrdersFragment
import eloom.holybean.ui.report.ReportFragment

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), MainActivityListener {
    private val permissionCoordinator by lazy { PermissionCoordinator(this) }

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
            when (drawerItem.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment())
                R.id.nav_orders -> loadFragment(OrdersFragment())
                R.id.nav_report -> loadFragment(ReportFragment())
                R.id.nav_credit -> loadFragment(CreditsFragment())
                R.id.menu_management -> loadFragment(MenuManagementFragment())
            }
            drawerItem.isChecked = true
            drawerLayout.closeDrawers()
            true
        }

        requestBluetoothPermissions()

    }

    private fun requestBluetoothPermissions() {
        permissionCoordinator.requestBluetoothPermissions { result ->
            when (result) {
                PermissionResult.Granted -> Unit
                is PermissionResult.ShowRationale -> {
                    // Surface an educational UI before re-requesting if needed.
                }

                is PermissionResult.Denied -> {
                    // Consider guiding users to Settings when permission is essential.
                }
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment).commit()
    }
}
