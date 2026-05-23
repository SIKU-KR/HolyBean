package eloom.holybean.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import eloom.holybean.ui.home.HomeRoute
import eloom.holybean.ui.home.HomeViewModel

@Composable
fun HolyBeanNavHost(navController: NavHostController = rememberNavController()) {
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) {
        eloom.holybean.ui.settings.SettingsSheet(
            onDismiss = { showSettings = false },
            onMenuMgmt = { showSettings = false; navController.navigate(MenuMgmtDest) },
            onCredits = { showSettings = false; navController.navigate(CreditsDest) },
            onReport = { showSettings = false; navController.navigate(ReportDest) },
            onDevTools = { showSettings = false; navController.navigate(DevToolsDest) },
        )
    }
    NavHost(navController = navController, startDestination = OrderFlow) {
        navigation<OrderFlow>(startDestination = HomeDest) {
            composable<HomeDest> { entry ->
                HomeRoute(
                    sharedViewModel = entry.sharedOrderViewModel(navController),
                    onNavigateToPayment = { navController.navigate(PaymentDest) },
                    onNavigateToOrders = { navController.navigate(OrdersDest) },
                    onNavigateToSettings = { showSettings = true },
                )
            }
            composable<PaymentDest> { entry ->
                eloom.holybean.ui.payment.PaymentRoute(
                    sharedViewModel = entry.sharedOrderViewModel(navController),
                    onClose = { navController.popBackStack() },
                    onPaid = { navController.popBackStack(HomeDest, inclusive = false) },
                )
            }
        }
        composable<OrdersDest> {
            eloom.holybean.ui.orders.OrdersRoute(onClose = { navController.popBackStack() })
        }
        composable<MenuMgmtDest> { /* Task 19 */ }
        composable<CreditsDest> {
            eloom.holybean.ui.credits.CreditsRoute(onClose = { navController.popBackStack() })
        }
        composable<ReportDest> {
            eloom.holybean.ui.report.ReportRoute(onClose = { navController.popBackStack() })
        }
        composable<DevToolsDest> {
            eloom.holybean.ui.settings.DevToolsRoute(onClose = { navController.popBackStack() })
        }
    }
}

/**
 * 주문 플로우(Home+Payment)가 동일 HomeViewModel 인스턴스를 공유하도록 부모 그래프 스코프로 가져온다.
 *
 * 주의: 이 ViewModel 의 uiEvent 는 Home 과 Payment 가 함께 구독하는 fan-out SharedFlow(replay=0) 이다.
 * 이를 collect 하는 모든 라우트는 반드시 repeatOnLifecycle(Lifecycle.State.RESUMED) 로 수집을 게이트해야
 * 백그라운드 화면이 상대 화면의 일회성 이벤트(ShowToast, NavigateToPayment 등)를 가로채거나 중복 처리하지 않는다.
 * (HomeRoute 참고. Task 11 PaymentRoute 도 동일 규칙을 따를 것.)
 */
@Composable
fun NavBackStackEntry.sharedOrderViewModel(nav: NavHostController): HomeViewModel {
    val parentEntry = remember(this) {
        nav.getBackStackEntry(OrderFlow)
    }
    return hiltViewModel(parentEntry)
}
