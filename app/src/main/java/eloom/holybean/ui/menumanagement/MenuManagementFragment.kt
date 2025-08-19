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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import eloom.holybean.R
import eloom.holybean.databinding.FragmentMenuManagementBinding
import eloom.holybean.interfaces.MainActivityListener
import eloom.holybean.ui.RvCustomDesign
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MenuManagementFragment : Fragment() {

    private val viewModel: MenuManagementViewModel by viewModels()
    private var _binding: FragmentMenuManagementBinding? = null
    private val binding get() = _binding!!

    private var mainListener: MainActivityListener? = null
    private lateinit var menuBoard: RecyclerView
    private lateinit var menuAdapter: MenuAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mainListener = context as? MainActivityListener
            ?: throw RuntimeException("$context must implement MainActivityListener")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        initListeners()
        observeViewModel()
    }

    private fun initViews() {
        setupMenuBoard()
        setupTabs()
    }

    private fun initListeners() {
        binding.returnButton.setOnClickListener {
            mainListener?.replaceHomeFragment()
        }
        binding.saveButton.setOnClickListener {
            PasswordDialog(requireContext()) {
                viewModel.saveMenuOrder()
            }.show()
        }
        binding.addButton.setOnClickListener {
            MenuAddDialog().show(parentFragmentManager, "MenuAddDialog")
        }
        binding.deviceToServer.setOnClickListener {
            PasswordDialog(requireContext()) {
                viewModel.saveMenuListToServer()
            }.show()
        }
        binding.serverToDevice.setOnClickListener {
            PasswordDialog(requireContext()) {
                viewModel.getMenuListFromServer()
            }.show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { render(it) }
                }
                launch {
                    viewModel.uiEvent.collect { handleEvent(it) }
                }
            }
        }
    }

    private fun render(state: MenuManagementViewModel.UiState) {
        menuAdapter.submitList(state.filteredMenuItems)
    }

    private fun handleEvent(event: MenuManagementViewModel.UiEvent) {
        when (event) {
            is MenuManagementViewModel.UiEvent.ShowToast -> {
                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
            }

            is MenuManagementViewModel.UiEvent.RefreshMenu -> {
                mainListener?.replaceMenuManagementFragment()
            }
        }
    }

    private fun setupMenuBoard() {
        menuAdapter = MenuAdapter()
        menuBoard = binding.menulistView
        menuBoard.apply {
            adapter = menuAdapter
            layoutManager = GridLayoutManager(context, 1)
            addItemDecoration(RvCustomDesign(10, 10, 5, 5))
        }
        setupItemTouchHelper()
    }

    private fun setupItemTouchHelper() {
        val callback = object : ItemTouchHelper.Callback() {
            override fun onMove(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                viewModel.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val menuItem = menuAdapter.getItemAt(position)
                MenuEditDialog(menuItem).show(parentFragmentManager, "MenuEditDialog")
                menuAdapter.notifyItemChanged(position)
            }

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
        ItemTouchHelper(callback).attachToRecyclerView(menuBoard)
    }

    private fun setupTabs() {
        val menuTab = binding.menuTab
        val categories = listOf("ICE커피", "HOT커피", "에이드/스무디", "티/음료", "베이커리")
        categories.forEach { category ->
            menuTab.addTab(menuTab.newTab().setText(category))
        }

        menuTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.onCategorySelected(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        mainListener = null
    }
}