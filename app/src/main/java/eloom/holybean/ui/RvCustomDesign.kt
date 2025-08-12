package eloom.holybean.ui

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

// 메뉴 목록 내의 item 사이 여백 정의
class RvCustomDesign(
    private val spaceLeft: Int,
    private val spaceRight: Int,
    private val spaceTop: Int,
    private val spaceBot: Int,
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.top = spaceTop
        outRect.left = spaceLeft
        outRect.right = spaceRight
        outRect.bottom = spaceBot
    }
}