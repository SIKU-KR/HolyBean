package eloom.holybean.ui.menumanagement

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import eloom.holybean.R
import eloom.holybean.data.model.MenuItem
import eloom.holybean.data.repository.LambdaRepository
import eloom.holybean.data.repository.MenuDB
import eloom.holybean.databinding.FragmentMenuManagementBinding
import eloom.holybean.interfaces.MainActivityListener
import eloom.holybean.ui.RvCustomDesign
import eloom.holybean.ui.dialog.MenuAddDialog
import eloom.holybean.ui.dialog.MenuEditDialog
import eloom.holybean.ui.dialog.PasswordDialog
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MenuManagementFragment : Fragment() {

    @Inject
    lateinit var menuDB: MenuDB

    @Inject
    lateinit var lambdaRepository: LambdaRepository

    private var mainListener: MainActivityListener? = null

    private lateinit var binding: FragmentMenuManagementBinding

    private lateinit var menuBoard: RecyclerView
    private lateinit var menuTab: TabLayout

    private lateinit var itemList: ArrayList<MenuItem>
    private var category: Int = 1

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivityListener) {
            mainListener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentMenuManagementBinding.inflate(inflater, container, false)
        val view = binding.root
        itemList = readMenuList()
        initMenuBoard()
        initTabs()
        initAddButton()
        initSaveButton()
        initReturnButton()
        initSaveToServerButton()
        initGetFromServerButton()
        updateRecyclerViewForCategory()
        return view
    }

    private fun initReturnButton() {
        binding.returnButton.setOnClickListener {
            mainListener?.replaceMenuManagementFragment()
        }
    }

    private fun initSaveButton() {
        binding.saveButton.setOnClickListener {
            val passwordDialog = PasswordDialog(requireContext()) {
                menuDB.saveMenuOrders(itemList.filter { it.id / 1000 == category })
                mainListener?.replaceMenuManagementFragment()
            }
            passwordDialog.show()
        }
    }

    private fun initAddButton() {
        binding.addButton.setOnClickListener {
            val id = menuDB.getNextAvailableIdForCategory(this.category)
            val placement = menuDB.getNextAvailablePlacementForCategory(this.category)
            val dialog = MenuAddDialog(id, placement, mainListener)
            dialog.show(parentFragmentManager, "MenuAddDialog")
        }
    }

    private fun initSaveToServerButton() {
        binding.deviceToServer.setOnClickListener {
            val passwordDialog = PasswordDialog(requireContext()) {
                lifecycleScope.launch {
                    lambdaRepository.saveMenuListToServer(readMenuList())
                    Toast.makeText(binding.root.context, "서버에 저장 완료", Toast.LENGTH_SHORT).show()
                }
            }
            passwordDialog.show()
        }
    }

    private fun initGetFromServerButton() {
        binding.serverToDevice.setOnClickListener {
            val passwordDialog = PasswordDialog(requireContext()) {
                lifecycleScope.launch {
                    val response = lambdaRepository.getLastedSavedMenuList()
                    if (response.size == 0) {
                        throw Exception("데이터가 올바르지 않습니다.")
                    }
                    menuDB.overwriteMenuList(response)
                    Toast.makeText(binding.root.context, "태블릿에 저장 완료", Toast.LENGTH_SHORT).show()
                    mainListener?.replaceMenuManagementFragment()
                }
            }
            passwordDialog.show()
        }
    }

    override fun onDetach() {
        super.onDetach()
        mainListener = null
    }

    private fun initMenuBoard() {
        menuBoard = binding.menulistView
        val boardAdapter = MenuAdapter(itemList)
        menuBoard.apply {
            adapter = boardAdapter
            layoutManager = GridLayoutManager(context, 1)
            addItemDecoration(RvCustomDesign(10, 10, 5, 5))
        }

        // RecyclerView Item Movement Logic
        val callback = object : ItemTouchHelper.Callback() {
            private val editBackground = ColorDrawable(Color.parseColor("#FFD700"))
            private val paint = Paint().apply {
                color = Color.WHITE
                textSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 22f, resources.displayMetrics
                )
                textAlign = Paint.Align.CENTER
                typeface = ResourcesCompat.getFont(requireContext(), R.font.pretendard_bold)
            }
            private val maxSwipeDistance = 120f

            override fun onMove(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                // 현재 카테고리의 메뉴 아이템들을 새 ArrayList로 생성
                val filteredItems = ArrayList(itemList.filter { it.id / 1000 == category })
                moveItem(filteredItems, fromPosition, toPosition)

                // 변경된 순서에 맞춰 placement 값 업데이트
                filteredItems.forEachIndexed { index, menuItem ->
                    menuItem.order = (category * 1000) + (index + 1)
                }

                // 다른 카테고리의 아이템들과 합쳐서 전체 리스트를 재정렬 후 ArrayList로 생성
                val otherItems = itemList.filter { it.id / 1000 != category }
                itemList = ArrayList((otherItems + filteredItems).sortedBy { it.order })

                recyclerView.adapter?.notifyItemMoved(fromPosition, toPosition)
                return true
            }


            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                showSwipeMenuDialog(position)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val limitedDX = if (dX < -maxSwipeDistance) -maxSwipeDistance else dX

                if (limitedDX < 0) {
                    editBackground.setBounds(
                        itemView.right + limitedDX.toInt(), itemView.top, itemView.right, itemView.bottom
                    )
                    editBackground.draw(c)
                    val textX = itemView.right - 50f
                    val fontMetrics = paint.fontMetrics
                    val textY = (itemView.top + itemView.bottom) / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2
                    c.drawText("수정", textX, textY, paint)
                }

                super.onChildDraw(
                    c, recyclerView, viewHolder, limitedDX, dY, actionState, isCurrentlyActive
                )
            }

            override fun getMovementFlags(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
            ): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                val swipeFlags = ItemTouchHelper.LEFT
                return makeMovementFlags(dragFlags, swipeFlags)
            }
        }

        // ItemTouchHelper를 RecyclerView에 연결
        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(menuBoard)
    }


    private fun showSwipeMenuDialog(position: Int) {
        val filteredItems = itemList.filter { it.id / 1000 == category }
        val menuItem = filteredItems[position]
        val dialog = MenuEditDialog(menuItem, mainListener)
        dialog.show(parentFragmentManager, "MenuEditDialog")
    }

    private fun initTabs() {
        menuTab = binding.menuTab
        menuTab.addTab(menuTab.newTab().setText("ICE커피"))
        menuTab.addTab(menuTab.newTab().setText("HOT커피"))
        menuTab.addTab(menuTab.newTab().setText("에이드/스무디"))
        menuTab.addTab(menuTab.newTab().setText("티/음료"))
        menuTab.addTab(menuTab.newTab().setText("베이커리"))

        menuTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                category = tab.position + 1
                updateRecyclerViewForCategory()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun updateRecyclerViewForCategory() {
        val filteredItems = ArrayList(itemList.filter { it.id / 1000 == this.category })
        menuBoard.adapter = MenuAdapter(filteredItems)
    }

    // 아이템을 이동시키는 로직
    private fun moveItem(list: MutableList<MenuItem>, fromPosition: Int, toPosition: Int) {
        val item = list.removeAt(fromPosition)
        list.add(toPosition, item)
    }

    private fun readMenuList(): ArrayList<MenuItem> {
        return ArrayList(menuDB.getMenuList().sortedBy { it.order })
    }


}