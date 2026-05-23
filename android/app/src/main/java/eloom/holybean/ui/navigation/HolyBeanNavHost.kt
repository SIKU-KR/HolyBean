package eloom.holybean.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import eloom.holybean.ui.home.HomeRoute

@Composable
fun HolyBeanNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = OrderFlow) {
        navigation<OrderFlow>(startDestination = HomeDest) {
            composable<HomeDest> { entry ->
                HomeRoute(
                    sharedViewModel = entry.sharedOrderViewModel(navController),
                    onNavigateToPayment = { navController.navigate(PaymentDest) },
                    onNavigateToOrders = { navController.navigate(OrdersDest) },
                    onNavigateToSettings = { /* Task 15: 설정 시트 */ },
                )
            }
            composable<PaymentDest> { /* Task 11 에서 연결 */ }
        }
        composable<OrdersDest> { /* Task 14 */ }
        composable<MenuMgmtDest> { /* Task 19 */ }
        composable<CreditsDest> { /* Task 18 */ }
        composable<ReportDest> { /* Task 17 */ }
        composable<DevToolsDest> { /* Task 16 */ }
    }
}

/** 주문 플로우(Home+Payment)가 동일 HomeViewModel 인스턴스를 공유하도록 부모 그래프 스코프로 가져온다. */
@Composable
fun NavBackStackEntry.sharedOrderViewModel(nav: NavHostController): eloom.holybean.ui.home.HomeViewModel {
    val parentEntry = androidx.compose.runtime.remember(this) {
        nav.getBackStackEntry(OrderFlow)
    }
    return hiltViewModel(parentEntry)
}
