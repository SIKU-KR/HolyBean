package com.example.holybean.home

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
import com.dantsu.escposprinter.EscPosCharsetEncoding
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.example.holybean.common.DatabaseManager
import com.example.holybean.common.MainActivityListener
import com.example.holybean.common.RvCustomDesign
import com.example.holybean.databinding.FragmentHomeBinding
import com.example.holybean.dataclass.BasketItem
import com.example.holybean.dataclass.MenuItem
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.concurrent.thread

class HomeFragment : Fragment(), HomeFunctions, OrderDialogListener {
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


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        val view = binding.root
        context = view.context

        itemList = DatabaseManager.getMenuList()
        itemList.sortBy { it.placement }
        basketList = ArrayList()

        val orderNumTextView: TextView = binding.orderNum
        this.orderId = DatabaseManager.getCurrentOrderNumber(context)
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
                val orderDialog = OrderDialog.newInstance(this.totalPrice)
                orderDialog.setOrderDialogListener(this)
                orderDialog.show(parentFragmentManager, "OrderDialog")
            }
        }

        return view
    }

    private fun initMenuBoard() {
        menuBoard = binding.menuBoard
        val boardAdapter = MenuAdapter(itemList, this)
        menuBoard.apply {
            adapter = boardAdapter
            layoutManager = GridLayoutManager(context, 3)
            addItemDecoration(RvCustomDesign(10, 10, 15, 15)) // 20dp의 여백
        }
    }

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

    private fun updateRecyclerViewForCategory(category: Int) {
        val filteredItems = if (category == 0) {
            itemList // 전체
        } else {
            itemList.filter { it.id / 100 == category } // 카테고리
        }
        menuBoard.adapter = MenuAdapter(filteredItems as ArrayList<MenuItem>, this)
    }

    private fun initBasket() {
        basket = binding.basket
        val basketAdapter = BasketAdapter(basketList, this)
        basket.apply {
            adapter = basketAdapter
            layoutManager = GridLayoutManager(context, 1)
            addItemDecoration(RvCustomDesign(15, 15, 0, 0)) // 20dp의 여백
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun addToBasket(id: Int) {
        val item = basketList.find { it.id == id }
        // item이 basketList에 존재하지 않는 경우
        if (item == null) {
            val target = searchMenuItem(itemList, id) ?: return
            basketList.add(BasketItem(id, target.name, target.price, 1, target.price))
        }
        // item이 basketList에 존재하는 경우
        else {
            item.count++
        }
        basket.adapter?.notifyDataSetChanged()
        updateTotal()
    }

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


    private fun getTotal(): Int {
        this.totalPrice = 0
        for (item in basketList) {
            item.total = item.count * item.price
            this.totalPrice += item.total
        }
        return this.totalPrice
    }

    private fun updateTotal() {
        this.totalPrice = getTotal()
        val totalPriceNumTextView: TextView = binding.totalPriceNum
        totalPriceNumTextView.text = this.totalPrice.toString()
    }

    // menulist binarysearch
    private fun searchMenuItem(menuItems: ArrayList<MenuItem>, itemId: Int): MenuItem? {
        var low = 0
        var high = menuItems.size - 1
        while (low <= high) {
            val mid = (low + high) / 2
            val midVal = menuItems[mid]
            when {
                midVal.id < itemId -> low = mid + 1
                midVal.id > itemId -> high = mid - 1
                else -> return midVal
            }
        }
        return null // itemId not found
    }

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
                    Toast.makeText(view?.context ?: context, "올바른 금액이 아닙니다", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소") { _, _ -> }
            .show()

    }

    private fun printReceipt(takeOption: String, orderMethod: String, ordererName: String) {
        val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm:ss")
        val currentTime = Date()
        val date = dateFormat.format(currentTime)
        val printer = EscPosPrinter(
            BluetoothPrintersConnections.selectFirstPaired(),
            180,
            72f,
            32,
            EscPosCharsetEncoding("EUC-KR", 13)
        )
        thread {
            // 고객용 영수증 인쇄
            printer.printFormattedTextAndCut(receiptTextForCustomer(), 500)
            Thread.sleep(500)
            // 포스용 영수증 인쇄
            printer.printFormattedTextAndCut(
                receiptTextForPOS(takeOption, orderMethod, ordererName, date),
                500
            )
            Thread.sleep(2000)
            printer.disconnectPrinter()
        }
    }

    private fun receiptTextForCustomer(): String {
        var result = "[C]=====================================\n"
        result += "[L]\n"
        result += "[C]<u><font size='big'>주문번호 : ${this.orderId}</font></u>\n"
        result += "[L]\n"
        result += "[C]-------------------------------------\n"
        result += "[L]\n"
        for (item in basketList) {
            result += "[L]<b>${item.name}</b>[R]${item.count}\n"
        }
        result += "[L]\n"
        result += "[C]====================================="
        return result
    }

    private fun receiptTextForPOS(
        takeOption: String,
        orderMethod: String,
        ordererName: String,
        date: String
    ): String {
        var result = "[C]=====================================\n"
        result += "[L]\n"
        result += "[C]<u><font size='big'>주문번호 : ${this.orderId}</font></u>\n"
        result += "[L]\n"
        result += "[L]<font size='big'>${takeOption}</font>\n"
        result += "[L]\n"
        result += "[R]주문자 : ${ordererName}\n"
        result += "[C]-------------------------------------\n"
        result += "[L]\n"
        for (item in basketList) {
            result += "[L]<b>${item.name}</b>[R]${item.count}\n"
        }
        result += "[L]\n"
        result += "[R]합계 : ${this.totalPrice}\n"
        result += "[C]-------------------------------------\n"
        result += "[R]결제수단 : ${orderMethod}\n"
        result += "[R]${date}\n"
        result += "[C]====================================="
        return result
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivityListener) {
            mainListener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        mainListener = null
    }

    override fun onOrderConfirmed(takeOption: String, ordererName: String, orderMethod: String) {
        if (orderMethod == "무료제공") {
            this.totalPrice = 0
            basketList.forEach { it.total = 0 }
        }
        printReceipt(takeOption, orderMethod, ordererName)
        DatabaseManager.orderProcess(
            context,
            this.orderId,
            this.totalPrice,
            orderMethod,
            ordererName,
        )
        DatabaseManager.orderDetailProcess(context, this.orderId, this.basketList)
        mainListener?.replaceHomeFragment()
    }

    override fun onOrderConfirmed(
        takeOption: String,
        ordererName: String,
        firstMethod: String,
        secondMethod: String,
        firstAmount: Int,
        secondAmount: Int
    ) {
        printReceipt(takeOption, "$firstMethod+$secondMethod", ordererName)
        DatabaseManager.orderProcess(context, this.orderId, firstAmount, firstMethod, ordererName)
        DatabaseManager.orderProcess(context, this.orderId, secondAmount, secondMethod, ordererName)
        DatabaseManager.orderDetailProcess(context, this.orderId, this.basketList)
        mainListener?.replaceHomeFragment()
    }
}