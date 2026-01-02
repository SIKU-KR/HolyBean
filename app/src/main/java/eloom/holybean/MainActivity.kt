package eloom.holybean

import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import eloom.holybean.PermissionCoordinator.PermissionResult
import eloom.holybean.interfaces.MainActivityListener
import eloom.holybean.ui.credits.CreditsFragment
import eloom.holybean.ui.home.HomeFragment
import eloom.holybean.ui.menumanagement.MenuManagementFragment
import eloom.holybean.ui.orderlist.OrdersFragment
import eloom.holybean.ui.report.ReportFragment

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), MainActivityListener {
    private val permissionCoordinator by lazy { PermissionCoordinator(this) }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun replaceHomeFragment() {
        loadFragment(HomeFragment())
    }

    override fun replaceOrdersFragment() {
        loadFragment(OrdersFragment())
    }

    override fun replaceCreditsFragment() {
        loadFragment(CreditsFragment())
    }

    override fun replaceMenuManagementFragment() {
        loadFragment(MenuManagementFragment())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        configureSystemBars()
        initializeViews()
        setupInitialFragment(savedInstanceState)
        setupNavigationDrawer()
        requestBluetoothPermissions()
    }

    private fun configureSystemBars() {
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
    }

    private fun setupInitialFragment(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }
    }

    private fun setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener { drawerItem ->
            val handled = handleNavigationSelection(drawerItem.itemId)
            if (handled) {
                drawerItem.isChecked = true
                drawerLayout.closeDrawers()
            }
            handled
        }
    }

    private fun handleNavigationSelection(itemId: Int): Boolean {
        val fragment = when (itemId) {
            R.id.nav_home -> HomeFragment()
            R.id.nav_orders -> OrdersFragment()
            R.id.nav_report -> ReportFragment()
            R.id.nav_credit -> CreditsFragment()
            R.id.menu_management -> MenuManagementFragment()
            else -> null
        }

        return fragment?.let {
            loadFragment(it)
            true
        } ?: false
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
