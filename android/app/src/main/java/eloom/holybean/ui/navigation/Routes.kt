package eloom.holybean.ui.navigation

import kotlinx.serialization.Serializable

// 라우트(목적지) object는 *Dest, Composable 진입점은 *Route 로 명명 → 이름 충돌/alias 방지.
@Serializable object OrderFlow      // 주문+결제 공유 그래프
@Serializable object HomeDest
@Serializable object PaymentDest
@Serializable object OrdersDest
@Serializable object MenuMgmtDest
@Serializable object CreditsDest
@Serializable object ReportDest
@Serializable object DevToolsDest
