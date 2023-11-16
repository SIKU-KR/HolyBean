package com.example.holybean

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout

interface MainActivityFunctions{
    fun addToBasket(id:Int)
}

class MainActivity : AppCompatActivity(), MainActivityFunctions {
    private lateinit var menuBoard: RecyclerView
    private lateinit var menuTab: TabLayout
    private lateinit var basket: RecyclerView

    private lateinit var itemList: ArrayList<MenuItem>
    private lateinit var basketList: ArrayList<BasketItem>
    private lateinit var menuMap : Map<Int, Pair<String, Int>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        itemList = readMenu(this) // menu.db 에서 메뉴 목록 가져오기
        basketList = ArrayList<BasketItem>()
        menuMap = createMenuMap();

        initMenuBoard()
        initTabs()
        initBasket()
    }

    private fun initMenuBoard(){
        menuBoard = findViewById<RecyclerView>(R.id.menu_board)
        itemList.sortBy{it.id} // menu.db에서 가져온 itemList를 오름차순으로 정렬해주기
        val boardAdapter = MenuAdapter(itemList, this)
        menuBoard.apply{
            adapter = boardAdapter
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            addItemDecoration(RvCustomDesign(15,15,20,20)) // 20dp의 여백
        }
    }

    private fun initTabs() {
        menuTab = findViewById(R.id.menu_tab)
        menuTab.addTab(menuTab.newTab().setText("전체"))
        menuTab.addTab(menuTab.newTab().setText("ICE커피"))
        menuTab.addTab(menuTab.newTab().setText("HOT커피"))
        menuTab.addTab(menuTab.newTab().setText("티/스무디"))
        menuTab.addTab(menuTab.newTab().setText("기타"))

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

    private fun initBasket(){
        basket = findViewById<RecyclerView>(R.id.basket)
        val basketAdapter = BasketAdapter(basketList)
        basket.apply{
            adapter = basketAdapter
            layoutManager = GridLayoutManager(this@MainActivity, 1)
            addItemDecoration(RvCustomDesign(15,15,0,0)) // 20dp의 여백
        }
    }

    fun createMenuMap(): Map<Int, Pair<String, Int>> {
        return itemList.associate { itemList ->
            itemList.id to Pair(itemList.name, itemList.price)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun addToBasket(id: Int) {
        val item = basketList.find { it.id == id }
        // item이 basketList에 존재하지 않는 경우
        if(item == null) {
            menuMap[id]?.let { menuItem ->
                basketList.add(BasketItem(id, menuItem.first, menuItem.second, 1, menuItem.second))
            } ?: run {
                // menuMap에서 id에 해당하는 항목을 찾지 못한 경우의 처리
            }
        }
        // item이 basketList에 존재하는 경우
        else{
            item.count++
        }
        basket.adapter?.notifyDataSetChanged()
        updateTotal()
    }

    private fun updateTotal() {
        val totalPriceNumTextView: TextView = findViewById(R.id.totalPriceNum)
        var totalSum = 0
        for (item in basketList){
            item.total = item.count * item.price
            totalSum += item.total
        }
        totalPriceNumTextView.text = totalSum.toString()
    }
}