package com.example.holybean.ui.home

import android.annotation.SuppressLint
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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.holybean.data.model.CartItem
import com.example.holybean.data.model.MenuItem
import com.example.holybean.data.model.OrderDialogData
import com.example.holybean.data.repository.MenuDB
import com.example.holybean.databinding.FragmentHomeBinding
import com.example.holybean.interfaces.HomeFunctions
import com.example.holybean.interfaces.MainActivityListener
import com.example.holybean.ui.MenuViewModel
import com.example.holybean.ui.RvCustomDesign
import com.example.holybean.ui.dialog.OrderDialog
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment(), HomeFunctions {

    // Activity 범위의 MenuViewModel 사용 (서버에서 메뉴 데이터를 가져옴)
    private val menuViewModel: MenuViewModel by activityViewModels()
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var binding: FragmentHomeBinding
    private lateinit var context: Context
    private var mainListener: MainActivityListener? = null
    private lateinit var menuBoard: RecyclerView
    private lateinit var menuTab: TabLayout
    private lateinit var basket: RecyclerView
    private var itemList: ArrayList<MenuItem> = ArrayList()
    private var basketList: ArrayList<CartItem> = ArrayList()
    private var orderId: Int = 0
    private var totalPrice: Int = 0

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
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        context = binding.root.context

        mainListener?.let { viewModel.setMainActivityListener(it) }

        initView()
        initObservers()

        menuViewModel.fetchData()

        return binding.root
    }

    private fun initView() {
        setupMenuBoard()
        setupBasket()
        setupTabs()
        setupOrderProcessButton()
        setupCouponAddButton()
        setupOrderNumAsync()
    }

    private fun setupMenuBoard() {
        menuBoard = binding.menuBoard
        // 초기 어댑터 생성 (빈 리스트로 시작)
        val boardAdapter = MenuAdapter(itemList, this)
        menuBoard.apply {
            adapter = boardAdapter
            layoutManager = GridLayoutManager(context, 3)
            addItemDecoration(RvCustomDesign(10, 10, 15, 15))
        }
    }

    private fun setupBasket() {
        basket = binding.basket
        val cartAdapter = CartAdapter(basketList, this)
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
                updateRecyclerViewForCategory(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupOrderProcessButton() {
        binding.orderProcess.setOnClickListener {
            if (basketList.isNotEmpty()) {
                val orderDialog = OrderDialog.newInstance(
                    OrderDialogData(basketList, orderId, totalPrice, viewModel.getCurrentDate())
                )
                orderDialog.setOrderDialogListener(viewModel)
                orderDialog.show(parentFragmentManager, "OrderDialog")
            }
        }
    }

    private fun setupCouponAddButton() {
        binding.couponButton.setOnClickListener {
            val editText = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
            }
            AlertDialog.Builder(context)
                .setTitle("쿠폰 금액을 입력하세요:")
                .setView(editText)
                .setPositiveButton("확인") { _, _ -> handleCouponInput(editText) }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun handleCouponInput(editText: EditText) {
        val enteredAmount = editText.text.toString().toIntOrNull()
        if (enteredAmount != null && enteredAmount > 0) {
            basketList.add(CartItem(999, "쿠폰", enteredAmount, 1, enteredAmount))
            basket.adapter?.notifyDataSetChanged()
            updateTotal()
        } else {
            Toast.makeText(context, "올바른 금액이 아닙니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupOrderNumAsync() {
        lifecycleScope.launch {
            try {
                orderId = viewModel.getCurrentOrderNum()
                binding.orderNum.text = orderId.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "주문 번호를 불러오는 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initObservers() {
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
            }
        }
        menuViewModel.menulist.observe(viewLifecycleOwner) { menuList ->
            if (menuList != null) {
                itemList = menuList.filter { it.inuse }
                    .sortedBy { it.order }
                    .toCollection(ArrayList())
                (menuBoard.adapter as? MenuAdapter)?.updateList(itemList)
            }
        }
    }

    // 카테고리 별로 필터링하여 리사이클러뷰 갱신
    private fun updateRecyclerViewForCategory(category: Int) {
        val filteredItems = if (category == 0) {
            itemList // 전체
        } else {
            itemList.filter { it.id / 1000 == category }
        }
        // 새로운 어댑터를 생성하거나, 기존 어댑터에 업데이트를 요청
        menuBoard.adapter = MenuAdapter(filteredItems as ArrayList<MenuItem>, this)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun addToBasket(id: Int) {
        val item = basketList.find { it.id == id }
        if (item == null) {
            val target = itemList.find { it.id == id } ?: return
            basketList.add(CartItem(id, target.name, target.price, 1, target.price))
            basket.adapter?.notifyItemInserted(basketList.size - 1)
        } else {
            item.count++
            item.total = item.count * item.price
            val index = basketList.indexOf(item)
            basket.adapter?.notifyItemChanged(index)
        }
        updateTotal()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun deleteFromBasket(id: Int) {
        val item = basketList.find { it.id == id }
        if (item != null) {
            if (item.count == 1) {
                val index = basketList.indexOf(item)
                basketList.remove(item)
                basket.adapter?.notifyItemRemoved(index)
            } else {
                item.count--
                item.total = item.count * item.price
                val index = basketList.indexOf(item)
                basket.adapter?.notifyItemChanged(index)
            }
            updateTotal()
        }
    }

    private fun updateTotal() {
        totalPrice = viewModel.getTotal(basketList)
        binding.totalPriceNum.text = totalPrice.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 필요한 리소스 해제 등 처리
    }
}

