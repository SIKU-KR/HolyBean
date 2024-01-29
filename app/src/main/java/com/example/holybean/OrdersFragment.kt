package com.example.holybean

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.holybean.databinding.FragmentOrdersBinding

class OrdersFragment : Fragment(), OrdersFragmentFunction {
    private lateinit var binding: FragmentOrdersBinding
    private lateinit var context: Context

    private var orderNumber = 1

    private lateinit var orderNum: TextView
    private lateinit var totalPrice: TextView

    private lateinit var ordersBoard: RecyclerView
    private lateinit var ordersList: ArrayList<OrderItem>

    private lateinit var basket: RecyclerView
    private lateinit var basketList: ArrayList<OrdersDetailItem>

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle? ): View? {
        binding = FragmentOrdersBinding.inflate(inflater, container, false)
        val view = binding.root
        context = view.context

        orderNum = binding.orderNum
        totalPrice = binding.totalPriceNum

        val today = getCurrentDate()

        ordersList = DatabaseManager.getOrderList(view.context, today)
        basketList = ArrayList()

        initBasket()

        ordersBoard = binding.orderBoard
        val boardAdapter = OrdersAdapter(ordersList, this)
        ordersBoard.apply{
            adapter = boardAdapter
            layoutManager = GridLayoutManager(context, 1)
            addItemDecoration(RvCustomDesign(0,0,0,20))
        }

        binding.viewThisOrder.setOnClickListener{
            activity?.runOnUiThread {
                basketList.clear()
                basketList = DatabaseManager.getOrderDetail(view.context, orderNumber, today)
                initBasket()
            }
        }

        return view
    }

    private fun initBasket(){
        basket = binding.basket
        val ordersDetailAdapter = OrdersDetailAdapter(basketList)
        basket.apply{
            adapter = ordersDetailAdapter
            layoutManager = GridLayoutManager(context, 1)
            addItemDecoration(RvCustomDesign(15,15,0,0)) // 20dp의 여백
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun newOrderSelected(num: Int, total: Int) {
        orderNumber = num
        orderNum.text = num.toString()
        totalPrice.text = total.toString()
        basketList.clear()
        basket.adapter?.notifyDataSetChanged()
    }
}
