package eloom.holybean.ui.dialog

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import eloom.holybean.R

class PasswordDialog(
    private val context: Context,
    private val onPasswordCorrect: () -> Unit
) {
    // 모든 UI에서 사용할 통일된 비밀번호
    private val correctPassword: String = "1031"

    fun show() {
        val builder = AlertDialog.Builder(context)

        // LinearLayout을 사용하여 여백을 적용
        val container = LinearLayout(context).apply {
            setPadding(50, 0, 50, 0)  // 좌우 여백 적용
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val inputField = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "비밀번호를 입력하세요"

            // Pretendard SemiBold 폰트 설정
            typeface = ResourcesCompat.getFont(context, R.font.pretendard_semibold)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(50, 20, 50, 20) // EditText 내부 여백 추가
            }
        }

        container.addView(inputField)
        builder.setTitle("비밀번호 확인")
        builder.setView(container)

        // Dialog의 버튼 텍스트도 같은 폰트로 설정 가능
        val typeface = ResourcesCompat.getFont(context, R.font.pretendard_semibold)

        builder.setPositiveButton("확인", null)  // 나중에 클릭 리스너 설정

        builder.setNegativeButton("취소") { dialog, _ ->
            dialog.cancel()
        }

        val dialog = builder.create()

        // 다이얼로그 버튼 폰트 적용 (show 이후 접근해야 합니다)
        dialog.setOnShowListener {
            // PositiveButton의 기본 동작을 재정의
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton?.typeface = typeface
            positiveButton?.setOnClickListener {
                val enteredPassword = inputField.text.toString()

                if (enteredPassword == correctPassword) {
                    onPasswordCorrect()  // 비밀번호가 맞으면 콜백 호출
                    dialog.dismiss()  // 비밀번호가 맞을 때만 다이얼로그 닫음
                } else {
                    Toast.makeText(context, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                    // 다이얼로그는 닫히지 않음
                }
            }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.typeface = typeface
        }

        dialog.show()
    }
}
