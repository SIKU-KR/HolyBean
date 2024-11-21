package com.example.holybean.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Database private constructor(
    context: Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {

        // db info
        const val DATABASE_NAME = "databaseV2.db"
        const val DATABASE_VERSION = 3

        // tables
        const val ORDERS = "Orders"
        const val DETAILS = "Details"

        // ORDERS
        const val ORDERS_ID = "id"
        const val ORDERS_UUID = "uuid"
        const val ORDERS_NUM = "number"
        const val ORDERS_DATE = "date"
        const val ORDERS_TOTAL_AMOUNT = "total_amount"
        const val ORDERS_METHOD = "method"
        const val ORDERS_CUSTOMER = "customer"

        // DETAILS
        const val DETAILS_ORDER_ID = "uuid"
        const val DETAILS_PRODUCT_ID = "product_id"
        const val DETAILS_PRODUCT_NAME = "product_name"
        const val DETAILS_QUANTITY = "quantity"
        const val DETAILS_PRICE = "price"

        @Volatile
        private var instance: Database? = null

        fun getInstance(context: Context): Database =
            instance ?: synchronized(this) {
                instance ?: Database(context).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createOrdersTable = """
        CREATE TABLE $ORDERS (
            $ORDERS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $ORDERS_UUID TEXT NOT NULL,
            $ORDERS_NUM INTEGER NOT NULL,
            $ORDERS_DATE TEXT NOT NULL,
            $ORDERS_TOTAL_AMOUNT INTEGER NOT NULL,
            $ORDERS_METHOD TEXT NOT NULL,
            $ORDERS_CUSTOMER TEXT NOT NULL
        )
    """.trimIndent()

        val createDetailsTable = """
        CREATE TABLE $DETAILS (
            $DETAILS_ORDER_ID INTEGER NOT NULL,
            $DETAILS_PRODUCT_ID INTEGER NOT NULL,
            $DETAILS_PRODUCT_NAME TEXT NOT NULL,
            $DETAILS_QUANTITY INTEGER NOT NULL,
            $DETAILS_PRICE INTEGER NOT NULL,
            FOREIGN KEY($DETAILS_ORDER_ID) REFERENCES $ORDERS($ORDERS_UUID)
        )
    """.trimIndent()
        db?.execSQL(createOrdersTable)
        db?.execSQL(createDetailsTable)
    }


    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $ORDERS")
        db?.execSQL("DROP TABLE IF EXISTS $DETAILS")
        onCreate(db)
    }

}
