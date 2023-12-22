package com.example.holybean

import DatabaseManager
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout

interface HomeFunctions{
    fun addToBasket(id:Int)
}

class HomeFragment : Fragment(), HomeFunctions {
    private lateinit var menuBoard: RecyclerView
    private lateinit var menuTab: TabLayout
    private lateinit var basket: RecyclerView

    private lateinit var itemList: ArrayList<MenuItem>
    private lateinit var basketList: ArrayList<BasketItem>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        itemList = DatabaseManager.getMenuList(view.context)// menu.db 에서 메뉴 목록 가져오기
        basketList = ArrayList()

        initMenuBoard(view)
        initTabs(view)
        initBasket(view)

        val finishbutton = view.findViewById<Button>(R.id.orderProcess)
        finishbutton.setOnClickListener {
            val intent = Intent(view.context, ProcessOrder::class.java)
            intent.putExtra("basket", basketList)
            startActivity(intent)
        }


        return view
    }

    private fun initMenuBoard(view: View){
        menuBoard = view.findViewById(R.id.menu_board)
        val boardAdapter = MenuAdapter(itemList, this)
        menuBoard.apply{
            adapter = boardAdapter
            layoutManager = GridLayoutManager(view.context, 4)
            addItemDecoration(RvCustomDesign(10,10,15,15)) // 20dp의 여백
        }
    }

    private fun initTabs(view: View) {
        menuTab = view.findViewById(R.id.menu_tab)
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

    private fun initBasket(view: View){
        basket = view.findViewById(R.id.basket)
        val basketAdapter = BasketAdapter(basketList)
        basket.apply{
            adapter = basketAdapter
            layoutManager = GridLayoutManager(view.context, 1)
            addItemDecoration(RvCustomDesign(15,15,0,0)) // 20dp의 여백
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun addToBasket(id: Int) {
        val item = basketList.find { it.id == id }
        // item이 basketList에 존재하지 않는 경우
        if(item == null) {
            val target = searchMenuItem(itemList, id) ?: return
            basketList.add(BasketItem(id, target.name, target.price, 1, target.price))
        }
        // item이 basketList에 존재하는 경우
        else{
            item.count++
        }
        basket.adapter?.notifyDataSetChanged()
        updateTotal()
    }

    private fun updateTotal() {
        view?.let { currentView ->
            val totalPriceNumTextView: TextView = currentView.findViewById(R.id.totalPriceNum)
            var totalSum = 0
            for (item in basketList) {
                item.total = item.count * item.price
                totalSum += item.total
            }
            totalPriceNumTextView.text = totalSum.toString()
        }
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
}