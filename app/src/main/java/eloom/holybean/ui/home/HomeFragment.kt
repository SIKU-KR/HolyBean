package eloom.holybean.ui.home

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import eloom.holybean.data.model.OrderDialogData
import eloom.holybean.databinding.FragmentHomeBinding
import eloom.holybean.interfaces.MainActivityListener
import eloom.holybean.ui.RvCustomDesign
import eloom.holybean.ui.dialog.OrderDialog
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var contextRef: Context
    private var mainListener: MainActivityListener? = null
    private lateinit var menuBoard: RecyclerView
    private lateinit var menuTab: TabLayout
    private lateinit var basket: RecyclerView

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivityListener) {
            mainListener = context
        } else {
            throw RuntimeException("$context must implement MainActivityListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        contextRef = requireContext()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        observeViewModel()
    }

    private fun initView() {
        setupMenuBoard()
        setupBasket()
        setupTabs()
        setupOrderProcessButton()
        setupCouponAddButton()
    }

    private fun setupMenuBoard() {
        menuBoard = binding.menuBoard
        val boardAdapter = MenuAdapter { item -> viewModel.addToBasket(item.id) }
        menuBoard.apply {
            adapter = boardAdapter
            layoutManager = GridLayoutManager(context, 3)
            addItemDecoration(RvCustomDesign(10, 10, 15, 15))
        }
    }

    private fun setupBasket() {
        basket = binding.basket
        val cartAdapter = CartAdapter { item -> viewModel.deleteFromBasket(item.id) }
        basket.apply {
            adapter = cartAdapter
            layoutManager = GridLayoutManager(context, 1)
            addItemDecoration(RvCustomDesign(15, 15, 0, 0))
        }
    }

    private fun setupTabs() {
        menuTab = binding.menuTab
        val categories = listOf("전체", "ICE커피", "HOT커피", "에이드/스무디", "티/음료", "베이커리")
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

    private fun setupOrderProcessButton() {
        binding.orderProcess.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.basketItems.isNotEmpty()) {
                val orderDialog = OrderDialog.newInstance(
                    OrderDialogData(
                        cartItems = state.basketItems,
                        orderNum = state.orderId,
                        totalPrice = state.totalPrice,
                        date = state.currentDate
                    )
                )
                orderDialog.setOrderDialogListener(viewModel)
                orderDialog.show(parentFragmentManager, "OrderDialog")
            }
        }
    }

    private fun setupCouponAddButton() {
        binding.couponButton.setOnClickListener {
            val editText = EditText(contextRef).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
            }
            AlertDialog.Builder(contextRef)
                .setTitle("쿠폰 금액을 입력하세요:")
                .setView(editText)
                .setPositiveButton("확인") { _, _ ->
                    val enteredAmount = editText.text.toString().toIntOrNull()
                    if (enteredAmount != null) viewModel.addCoupon(enteredAmount) else
                        Toast.makeText(contextRef, "올바른 금액이 아닙니다", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
                launch { viewModel.uiEvent.collect { handleEvent(it) } }
            }
        }
    }

    private fun render(state: HomeViewModel.UiState) {
        (menuBoard.adapter as? MenuAdapter)?.submitList(state.filteredMenuItems)
        (basket.adapter as? CartAdapter)?.submitList(state.basketItems)
        binding.orderNum.text = state.orderId.toString()
        binding.totalPriceNum.text = state.totalPrice.toString()
    }

    private fun handleEvent(event: HomeViewModel.UiEvent) {
        when (event) {
            is HomeViewModel.UiEvent.ShowToast ->
                Toast.makeText(contextRef, event.message, Toast.LENGTH_SHORT).show()

            is HomeViewModel.UiEvent.NavigateHome ->
                mainListener?.replaceHomeFragment()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

