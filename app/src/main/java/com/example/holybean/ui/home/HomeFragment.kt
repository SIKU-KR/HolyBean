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
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.holybean.interfaces.MainActivityListener
import com.example.holybean.data.repository.MenuDB
import com.example.holybean.ui.RvCustomDesign
import com.example.holybean.databinding.FragmentHomeBinding
import com.example.holybean.data.model.BasketItem
import com.example.holybean.data.model.MenuItem
import com.example.holybean.data.model.OrderDialogData
import com.example.holybean.interfaces.HomeFunctions
import com.example.holybean.ui.dialog.OrderDialog
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment(), HomeFunctions {

    @Inject
    lateinit var service: HomeViewModel

    private lateinit var binding: FragmentHomeBinding
    private lateinit var context: Context

    private var mainListener: MainActivityListener? = null

    private lateinit var menuBoard: RecyclerView
    private lateinit var menuTab: TabLayout
    private lateinit var basket: RecyclerView

    private lateinit var itemList: ArrayList<MenuItem>
    private lateinit var basketList: ArrayList<BasketItem>

    private var orderId: Int = 0
    private var totalPrice: Int = 0

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivityListener) {
            mainListener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        val view = binding.root
        context = view.context

        mainListener?.let {
            service.setMainActivityListener(it)
        } ?: run {
            throw IllegalStateException("MainActivityListener is not set")
        }

        itemList = MenuDB.getMenuList(context).filter {
            it.inuse == true
        }.toCollection(ArrayList())

        itemList.sortBy { it.placement }
        basketList = ArrayList()

        val orderNumTextView: TextView = binding.orderNum
        this.orderId = service.getCurrentOrderNum()
        orderNumTextView.text = orderId.toString()

        initMenuBoard()
        initTabs()
        initBasket()

        // 쿠폰 추가 버튼
        binding.couponButton.setOnClickListener {
            addCouponListener()
        }

        // 주문 담기 완료
        binding.orderProcess.setOnClickListener {
            if (basketList.isNotEmpty()) {
                val orderDialog = OrderDialog.newInstance(
                    OrderDialogData(this.basketList, this.orderId, this.totalPrice, service.getCurrentDate())
                )
                orderDialog.setOrderDialogListener(service)
                orderDialog.show(parentFragmentManager, "OrderDialog")
            }
        }

        return view
    }

    override fun onDetach() {
        super.onDetach()
        mainListener = null
    }

    // Menu recycler view init
    private fun initMenuBoard() {
        menuBoard = binding.menuBoard
        val boardAdapter = MenuAdapter(itemList, this)
        menuBoard.apply {
            adapter = boardAdapter
            layoutManager = GridLayoutManager(context, 3)
            addItemDecoration(RvCustomDesign(10, 10, 15, 15)) // 20dp의 여백
        }
    }

    // Basket recycler view init
    private fun initBasket() {
        basket = binding.basket
        val basketAdapter = BasketAdapter(basketList, this)
        basket.apply {
            adapter = basketAdapter
            layoutManager = GridLayoutManager(context, 1)
            addItemDecoration(RvCustomDesign(15, 15, 0, 0)) // 20dp의 여백
        }
    }

    // Menu tabs init
    private fun initTabs() {
        menuTab = binding.menuTab
        menuTab.addTab(menuTab.newTab().setText("전체"))
        menuTab.addTab(menuTab.newTab().setText("ICE커피"))
        menuTab.addTab(menuTab.newTab().setText("HOT커피"))
        menuTab.addTab(menuTab.newTab().setText("에이드/스무디"))
        menuTab.addTab(menuTab.newTab().setText("티/음료"))
        menuTab.addTab(menuTab.newTab().setText("베이커리"))

        menuTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateRecyclerViewForCategory(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // Function to set recycler view by categories(tab)
    private fun updateRecyclerViewForCategory(category: Int) {
        val filteredItems = if (category == 0) {
            itemList // 전체
        } else {
            itemList.filter { it.id / 1000 == category } // 카테고리
        }
        menuBoard.adapter = MenuAdapter(filteredItems as ArrayList<MenuItem>, this)
    }

    // Add item to basket
    @SuppressLint("NotifyDataSetChanged")
    override fun addToBasket(id: Int) {
        val item = basketList.find { it.id == id }
        // item이 basketList에 존재하지 않는 경우
        if (item == null) {
            val target = itemList.find { it.id == id } ?: return
            basketList.add(BasketItem(id, target.name, target.price, 1, target.price))
        }
        // item이 basketList에 존재하는 경우
        else {
            item.count++
        }
        basket.adapter?.notifyDataSetChanged()
        updateTotal()
    }

    // Remove item from basket
    @SuppressLint("NotifyDataSetChanged")
    override fun deleteFromBasket(id: Int) {
        val item = basketList.find { it.id == id }
        if (item != null) {
            if (item.count == 1) {
                basketList.remove(item)
            } else {
                item.count--
            }
        }
        basket.adapter?.notifyDataSetChanged()
        updateTotal()
    }

    // Update total textview
    private fun updateTotal() {
        this.totalPrice = service.getTotal(this.basketList)
        val totalPriceNumTextView: TextView = binding.totalPriceNum
        totalPriceNumTextView.text = this.totalPrice.toString()
    }

    // Adding coupon(Any Amount) to basket
    private fun addCouponListener() {
        val editText = EditText(context)
        editText.inputType = InputType.TYPE_CLASS_NUMBER
        val builder = AlertDialog.Builder(context)
        builder.setTitle("쿠폰 금액을 입력하세요:")
            .setView(editText)
            .setPositiveButton("확인") { _, _ ->
                val enteredAmount = editText.text.toString().toIntOrNull()
                if (enteredAmount != null && enteredAmount > 0) {
                    basketList.add(BasketItem(999, "쿠폰", enteredAmount, 1, enteredAmount))
                    basket.adapter?.notifyDataSetChanged()
                    updateTotal()
                } else {
                    Toast.makeText(view?.context ?: context, "올바른 금액이 아닙니다", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton("취소") { _, _ -> }
            .show()
    }

}