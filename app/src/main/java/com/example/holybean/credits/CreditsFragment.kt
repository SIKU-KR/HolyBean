package com.example.holybean.credits

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
import com.example.holybean.common.DatabaseManager
import com.example.holybean.common.MainActivityListener
import com.example.holybean.common.RvCustomDesign
import com.example.holybean.databinding.FragmentCreditBinding
import com.example.holybean.dataclass.CreditItem
import com.example.holybean.dataclass.OrdersDetailItem
import com.example.holybean.orders.OrdersDetailAdapter

class CreditsFragment : Fragment(), CreditsFragmentFunction {
    private lateinit var binding: FragmentCreditBinding
    private lateinit var context: Context

    private var mainListener: MainActivityListener? = null

    private var orderNumber = 1
    private var orderDate = ""

    private lateinit var orderNum: TextView
    private lateinit var totalPrice: TextView

    private lateinit var ordersBoard: RecyclerView
    private lateinit var creditsList: ArrayList<CreditItem>

    private lateinit var basket: RecyclerView
    private lateinit var basketList: ArrayList<OrdersDetailItem>

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle? ): View? {
        binding = FragmentCreditBinding.inflate(inflater, container, false)
        val view = binding.root
        context = view.context

        orderNum = binding.orderNum
        totalPrice = binding.totalPriceNum

        creditsList = DatabaseManager.getCreditList(view.context)
        basketList = ArrayList()

        initBasket()

        ordersBoard = binding.orderBoard
        val boardAdapter = CreditsAdapter(creditsList, this)
        ordersBoard.apply{
            adapter = boardAdapter
            layoutManager = GridLayoutManager(context, 1)
            addItemDecoration(RvCustomDesign(0,0,0,20))
        }

        binding.viewThisOrder.setOnClickListener{
            activity?.runOnUiThread {
                basketList.clear()
                basketList = DatabaseManager.getOrderDetail(view.context, orderNumber, orderDate)
                initBasket()
            }
        }

        binding.deleteCredit.setOnClickListener{
            DatabaseManager.deleteCreditRecord(view.context, orderNumber, orderDate)
            mainListener?.replaceCreditsFragment()
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
    override fun newOrderSelected(num: Int, total: Int, date:String) {
        this.orderNumber = num
        this.orderDate = date
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
