package com.example.holybean.ui.orderlist

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.holybean.interfaces.MainActivityListener
import com.example.holybean.ui.RvCustomDesign
import com.example.holybean.databinding.FragmentOrdersBinding
import com.example.holybean.interfaces.OrdersFragmentFunction
import com.example.holybean.data.model.OrderItem
import com.example.holybean.data.model.OrdersDetailItem
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OrdersController : Fragment(), OrdersFragmentFunction {

    @Inject
    lateinit var service: OrdersService

    private lateinit var binding: FragmentOrdersBinding
    private lateinit var context: Context

    private var mainListener: MainActivityListener? = null

    private var orderNumber = 1
    private var rowId: String = '1'.toString()

    private lateinit var orderNum: TextView
    private lateinit var totalPrice: TextView

    private lateinit var ordersBoard: RecyclerView
    private lateinit var ordersList: ArrayList<OrderItem>

    private lateinit var basket: RecyclerView
    private lateinit var basketList: ArrayList<OrdersDetailItem>

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOrdersBinding.inflate(inflater, container, false)
        val view = binding.root
        context = view.context

        orderNum = binding.orderNum
        totalPrice = binding.totalPriceNum

        val today = service.getCurrentDate()

        ordersList = service.getOrderList()
        basketList = ArrayList()

        initBasket()

        ordersBoard = binding.orderBoard
        val boardAdapter = OrdersAdapter(ordersList, this)
        ordersBoard.apply {
            adapter = boardAdapter
            layoutManager = GridLayoutManager(context, 1)
            addItemDecoration(RvCustomDesign(0, 0, 0, 20))
        }

        binding.viewThisOrder.setOnClickListener {
            activity?.runOnUiThread {
                basketList.clear()
                basketList = service.getOrderDetail(rowId)
                initBasket()
            }
        }

        // 영수증 재출력 버튼
        binding.reprint.setOnClickListener {
            if (this.basketList.isNotEmpty()) {
                service.reprint(this.orderNumber, this.basketList)
            }
        }

        // 주문 내역 삭제 버튼
        binding.deleteButton.setOnClickListener {
            if (this.basketList.isNotEmpty()) {
                val builder = AlertDialog.Builder(context)
                builder.setTitle("주문 내역을 삭제하시겠습니까?")
                    .setMessage("주문번호 ${this.orderNumber}번이 삭제되며 복구할 수 없습니다")
                    .setPositiveButton("확인") { _, _ ->
                        service.deleteOrder(rowId, orderNumber)
                        mainListener?.replaceOrdersFragment()
                    }
                    .setNegativeButton("취소") { _, _ -> }
                    .show()
            } else {
                Toast.makeText(context, "주문 조회 후 클릭해주세요", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private fun initBasket() {
        basket = binding.basket
        val ordersDetailAdapter = OrdersDetailAdapter(basketList)
        basket.apply {
            adapter = ordersDetailAdapter
            layoutManager = GridLayoutManager(context, 1)
            addItemDecoration(RvCustomDesign(15, 15, 0, 0)) // 20dp의 여백
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun newOrderSelected(id: String, num: Int, total: Int) {
        rowId = id
        orderNumber = num
        orderNum.text = num.toString()
        totalPrice.text = total.toString()
        basketList.clear()
        basket.adapter?.notifyDataSetChanged()
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
}
