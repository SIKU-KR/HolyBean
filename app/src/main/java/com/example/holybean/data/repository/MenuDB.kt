package com.example.holybean.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.holybean.data.model.MenuItem

class MenuDB private constructor(
    context: Context
) : SQLiteOpenHelper(context, MENUDB_NAME, null, MENUDB_VER) {

    companion object {
        @Volatile
        private var INSTANCE: MenuDB? = null
        // db info
        const val MENUDB_NAME = "menuDB.db"
        const val MENUDB_VER = 1
        const val MENUDB_TABLE = "menu"
        const val MENU_ID = "id"
        const val MENU_NAME = "name"
        const val MENU_PRICE = "price"
        const val MENU_PLACEMENT = "placement"
        const val MENU_INUSE = "inuse"

        fun getInstance(context: Context): MenuDB {
            return INSTANCE ?: synchronized(this) {
                val instance = MenuDB(context)
                INSTANCE = instance
                instance
            }
        }

        fun getMenuList(context: Context): ArrayList<MenuItem> {
            val menuList = ArrayList<MenuItem>()
            val db = getInstance(context).readableDatabase

            try {
                val cursor = db.rawQuery("SELECT id, name, price, placement, inuse FROM menu", null)

                if (cursor.moveToFirst()) {
                    do {
                        val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                        val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        val price = cursor.getInt(cursor.getColumnIndexOrThrow("price"))
                        val placement = cursor.getInt(cursor.getColumnIndexOrThrow("placement"))
                        val inuse = cursor.getInt(cursor.getColumnIndexOrThrow("inuse")) == 1  // Convert to boolean

                        val menuItem = MenuItem(id, name, price, placement, inuse)
                        menuList.add(menuItem)
                    } while (cursor.moveToNext())
                }
                cursor.close()

            } catch (e: Exception) {
                e.printStackTrace()
            }

            menuList.sortBy { it.id }
            return menuList
        }

        fun printAllMenus(context: Context) {
            val db = getInstance(context).readableDatabase
            val query = "SELECT * FROM $MENUDB_TABLE"
            val cursor = db.rawQuery(query, null)
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow(MENU_ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(MENU_NAME))
                    val price = cursor.getInt(cursor.getColumnIndexOrThrow(MENU_PRICE))
                    val placement = cursor.getInt(cursor.getColumnIndexOrThrow(MENU_PLACEMENT))
                    val inuse = cursor.getInt(cursor.getColumnIndexOrThrow(MENU_INUSE))
                    println("MenuItem: id=$id, name=$name, price=$price, placement=$placement, inuse=$inuse")
                } while (cursor.moveToNext())
            } else {
                println("메뉴 항목이 없습니다.")
            }
            cursor.close()
            db.close()
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(
            """
        CREATE TABLE $MENUDB_TABLE (
            $MENU_ID INTEGER PRIMARY KEY,
            $MENU_NAME TEXT NOT NULL,
            $MENU_PRICE INTEGER NOT NULL,
            $MENU_PLACEMENT INTEGER NOT NULL,
            $MENU_INUSE INTEGER NOT NULL DEFAULT 1
        )
    """.trimIndent()
        )
        db?.execSQL(
            """
        INSERT INTO $MENUDB_TABLE ($MENU_ID, $MENU_NAME, $MENU_PRICE, $MENU_PLACEMENT, $MENU_INUSE) VALUES
        (1001, '아이스 아메리카노', 2000, 1001, 1),
        (1002, '아이스 카페라떼', 2500, 1002, 1),
        (1003, '아이스 바닐라라떼', 2500, 1003, 1),
        (1004, '아이스 카라멜마끼야또', 2500, 1004, 1),
        (1005, '아이스 카페모카', 2500, 1005, 1),
        (1006, '아이스 헤이즐넛', 2500, 1006, 1),
        (1007, '아이스 녹차라떼', 2500, 1007, 1),
        (1008, '아이스 초코라떼', 2500, 1008, 1),
        (1009, '아몬드크림라떼', 4000, 1009, 1),
        (1010, 'ㄴ 덜달게', 0, 1010, 1),
        (1011, 'ㄴ 달게', 0, 1011, 1),
        (1012, 'ㄴ 샷추가', 0, 1012, 1),
        (1013, 'ㄴ 연하게', 0, 1013, 1),
        (1014, 'ㄴ 우유조금', 0, 1014, 1),
        (2001, '핫 아메리카노', 2000, 2001, 1),
        (2002, '에스프레소', 2000, 2002, 1),
        (2003, '핫 카페라떼', 2500, 2003, 1),
        (2004, '핫 바닐라라떼', 2500, 2004, 1),
        (2005, '핫 카라멜마끼야또', 2500, 2005, 1),
        (2006, '핫 카페모카', 2500, 2006, 1),
        (2007, '핫 헤이즐넛', 2500, 2007, 1),
        (2008, '핫 녹차라떼', 2500, 2008, 1),
        (2009, '핫 초코라떼', 2500, 2009, 1),
        (2010, '핫 헤이즐넛라떼', 2500, 2010, 1),
        (2011, '꿀 생강라떼', 3500, 2011, 1),
        (2012, 'ㄴ 덜달게', 0, 2012, 1),
        (2013, 'ㄴ 달게', 0, 2013, 1),
        (2014, 'ㄴ 샷추가', 0, 2014, 1),
        (2015, 'ㄴ 연하게', 0, 2015, 1),
        (2016, 'ㄴ 우유조금', 0, 2016, 1),
        (3001, '자몽에이드 (생과일)', 3000, 3001, 1),
        (3002, '레몬에이드 (생과일)', 3000, 3002, 1),
        (3003, '청포도에이드', 3000, 3003, 1),
        (3004, '자두에이드', 2500, 3004, 1),
        (3005, '수제아로니아 베리에이드', 3000, 3005, 1),
        (3006, '플레인요거트스무디', 3000, 3006, 1),
        (3007, '딸기스무디', 4000, 3007, 1),
        (3008, '딸기요거트스무디', 4000, 3008, 1),
        (3009, '유자스무디', 3000, 3009, 1),
        (4001, '복숭아아이스티 (소)', 1500, 4001, 1),
        (4002, '레몬아이스티 (소)', 1500, 4002, 1),
        (4003, '복숭아아이스티 (대)', 2000, 4003, 1),
        (4004, '레몬아이스티 (대)', 2000, 4004, 1),
        (4005, '자몽차', 2000, 4005, 1),
        (4006, '유자차', 2000, 4006, 1),
        (4007, '레몬차', 2000, 4007, 1),
        (4008, '생강차', 2000, 4008, 1),
        (4009, '골드메달 사과주스', 3000, 4009, 1),
        (4010, '과일사이다 (복숭아)', 2500, 4010, 1),
        (4011, '과일사이다 (수박)', 2500, 4011, 1),
        (4012, '과일사이다 (메론)', 2500, 4012, 1),
        (4013, '과일사이다 (사과)', 2500, 4013, 1),
        (4014, '분다버그 (자몽)', 3500, 4014, 1),
        (4015, '분다버그 (레몬)', 3500, 4015, 1),
        (4016, '분다버그 (망고)', 3500, 4016, 1),
        (4017, 'ㄴ 아이스컵', 0, 4017, 1),
        (5001, '크루아상', 2000, 5001, 1),
        (5002, '베이글', 2000, 5002, 1),
        (5003, '베이글세트', 5000, 5003, 1),
        (5004, '크림치즈 (플레인)', 1500, 5004, 1),
        (5005, '크림치즈 (바질)', 1500, 5005, 1),
        (5006, '크림치즈 (블루베리)', 1500, 5006, 1),
        (5007, '미니바게트', 2000, 5007, 1),
        (5008, '호두파이', 3000, 5008, 1),
        (5009, '벨지움초코타르트', 3000, 5009, 1),
        (5010, '마카롱 (초코)', 1500, 5010, 1),
        (5011, '마카롱 (바닐라)', 1500, 5011, 1),
        (5012, '마카롱 (라즈베리)', 1500, 5012, 1),
        (5013, '하겐다즈 바닐라', 4800, 5013, 1),
        (5014, '하겐다즈 그린티', 4800, 5014, 1),
        (5015, '하겐다즈 스트로베리', 4800, 5015, 1),
        (5016, '하겐다즈 벨지안초콜릿', 4800, 5016, 1),
        (5017, '하겐다즈 마카다미아넛', 4800, 5017, 1),
        (5018, '하겐다즈 쿠키&크림', 4800, 5018, 1),
        (5019, '하겐다즈 카라멜샌드', 4800, 5019, 1),
        (5020, '하겐다즈 스트로베리샌드', 4800, 5020, 1),
        (5021, '퐁당 오 쇼콜라', 3000, 5021, 1),
        (5022, '하겐다즈 파인트 초콜릿', 13000, 5022, 1),
        (5023, '하겐다즈 파인트 바닐라', 13000, 5023, 1),
        (5024, '하겐다즈 파인트 스트로베리', 13000, 5024, 1);
    """.trimIndent()
        )
    }


    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }


}