package com.example.holybean

import com.example.holybean.dataclass.MenuItem
import com.opencsv.CSVReaderBuilder
import java.io.InputStreamReader

class Menu {

    companion object {
        fun getMenuList(): ArrayList<MenuItem> {
            val menuList = ArrayList<MenuItem>()

            try {
                val inputStream = javaClass.classLoader.getResourceAsStream("assets/menu.csv")
                val reader = CSVReaderBuilder(InputStreamReader(inputStream))
                    .withSkipLines(1) // Skip the header row
                    .build()
                reader.readAll().forEach { line ->
                    if (line.size == 4) {
                        val id = line[0].toInt()
                        val name = line[1]
                        val price = line[2].toInt()
                        val placement = line[3].toInt()
                        val menuItem = MenuItem(id, name, price, placement)
                        menuList.add(menuItem)
                    }
                }
                reader.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            menuList.sortBy { it.id }
            return menuList
        }
    }

}