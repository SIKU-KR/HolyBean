package com.example.holybean

import DatabaseManager
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
import java.text.SimpleDateFormat
import java.util.Date

class OrdersFragment : Fragment() {
    private lateinit var binding: FragmentOrdersBinding
    private lateinit var context: Context

    private lateinit var title: TextView
    private lateinit var ordersBoard: RecyclerView
    private lateinit var ordersList: ArrayList<OrderItem>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle? ): View? {
        binding = FragmentOrdersBinding.inflate(inflater, container, false)
        val view = binding.root
        context = view.context

        title = binding.todayTitle
        val dateFormat = SimpleDateFormat("yyyy.MM.dd")
        val currentDate = Date()
        title.text = dateFormat.format(currentDate)

        ordersList = DatabaseManager.getOrderList(view.context)

        ordersBoard = binding.orderBoard
        val boardAdapter = OrdersAdapter(ordersList)
        ordersBoard.apply{
            adapter = boardAdapter
            layoutManager = GridLayoutManager(context, 1)
            addItemDecoration(RvCustomDesign(0,0,0,20))
        }

        return view
    }
}
