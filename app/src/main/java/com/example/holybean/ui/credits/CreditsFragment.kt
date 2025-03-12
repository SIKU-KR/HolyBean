package com.example.holybean.ui.credits

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.holybean.interfaces.MainActivityListener
import com.example.holybean.ui.RvCustomDesign
import com.example.holybean.data.repository.CreditsRepository
import com.example.holybean.data.model.CreditItem
import com.example.holybean.databinding.FragmentCreditBinding
import com.example.holybean.interfaces.CreditsFragmentFunction
import com.example.holybean.data.model.OrdersDetailItem
import com.example.holybean.ui.orderlist.OrdersDetailAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CreditsFragment : Fragment(), CreditsFragmentFunction {

    @Inject
    lateinit var repository: CreditsRepository

    private val viewModel by viewModels<CreditsViewModel>()

    private lateinit var binding: FragmentCreditBinding
    private lateinit var context: Context
    private var mainListener: MainActivityListener? = null

    private var orderNumber = 1
    private var orderDate = ""
    private var rowId: String = '0'.toString()

    private lateinit var orderNum: TextView
    private lateinit var totalPrice: TextView
    private lateinit var ordersBoard: RecyclerView
    private lateinit var creditsList: ArrayList<CreditItem>
    private lateinit var basket: RecyclerView
    private var basketList = arrayListOf<OrdersDetailItem>()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle? ): View? {
        binding = FragmentCreditBinding.inflate(inflater, container, false)
        val view = binding.root
        context = view.context

        initUi()
        initBasket()
        initOrdersBoard()
        initViewButton()
        initDeleteButton()

        return view
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

    private fun initUi() {
        orderNum = binding.orderNum
        totalPrice = binding.totalPriceNum
    }

    private fun initDeleteButton() {
        binding.deleteCredit.setOnClickListener {
            repository.deleteCredits(this.rowId)
            mainListener?.replaceCreditsFragment()
        }
    }

    private fun initViewButton() {
        binding.viewThisOrder.setOnClickListener {
            activity?.runOnUiThread {
                basketList.clear()
                basketList = repository.readOrderDetail(this.rowId)
                initBasket()
            }
        }
    }

    private fun initOrdersBoard() {
        ordersBoard = binding.orderBoard
        viewLifecycleOwner.lifecycleScope.launch {
            creditsList = viewModel.fetchCreditsOrders()
            val boardAdapter = CreditsAdapter(creditsList, this@CreditsFragment)
            ordersBoard.apply {
                adapter = boardAdapter
                layoutManager = GridLayoutManager(context, 1)
                addItemDecoration(RvCustomDesign(0, 0, 0, 20))
            }
        }
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

    override fun newOrderSelected(num: Int, total: Int, date:String) {
        this.orderNumber = num
        this.orderDate = date
        orderNum.text = num.toString()
        totalPrice.text = total.toString()
        basketList.clear()
        basket.adapter?.notifyDataSetChanged()
    }
}
