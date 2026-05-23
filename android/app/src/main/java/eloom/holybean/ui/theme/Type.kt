package eloom.holybean.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import eloom.holybean.R

val Pretendard = FontFamily(
    Font(R.font.pretendard_medium,    FontWeight.Medium),
    Font(R.font.pretendard_bold,      FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)

val HolyBeanTypography = Typography(
    titleLarge  = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold,      fontSize = 18.sp),
    bodyLarge   = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium,    fontSize = 16.sp),
    bodyMedium  = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium,    fontSize = 15.sp),
    labelSmall  = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium,    fontSize = 14.sp),
)
