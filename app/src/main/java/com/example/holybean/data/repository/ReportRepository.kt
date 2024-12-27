package com.example.holybean.data.repository

import android.content.Context
import androidx.annotation.WorkerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileWriter
import java.io.IOException
import javax.inject.Inject

class ReportRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val database = Database.getInstance(context)

    /**
     * 모든 ORDERS 및 DETAILS 테이블 데이터를 각각 orders1.csv와 details1.csv 파일로 내보냅니다.
     */
    @WorkerThread
    fun exportToCsv() {
        val ordersFile = File(context.getExternalFilesDir(null), "orders1.csv")
        val detailsFile = File(context.getExternalFilesDir(null), "details1.csv")

        // ORDERS 테이블 내보내기
        val cursorOrders = database.getAllOrders()
        try {
            FileWriter(ordersFile).use { writer ->
                // 헤더 작성
                val headers = cursorOrders.columnNames.joinToString(",")
                writer.write(headers + "\n")

                // 데이터 행 작성
                while (cursorOrders.moveToNext()) {
                    val row = (0 until cursorOrders.columnCount)
                        .map { escapeCsv(cursorOrders.getString(it)) }
                        .joinToString(",")
                    writer.write(row + "\n")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            cursorOrders.close()
        }

        // DETAILS 테이블 내보내기
        val cursorDetails = database.getAllDetails()
        try {
            FileWriter(detailsFile).use { writer ->
                // 헤더 작성
                val headers = cursorDetails.columnNames.joinToString(",")
                writer.write(headers + "\n")

                // 데이터 행 작성
                while (cursorDetails.moveToNext()) {
                    val row = (0 until cursorDetails.columnCount)
                        .map { escapeCsv(cursorDetails.getString(it)) }
                        .joinToString(",")
                    writer.write(row + "\n")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            cursorDetails.close()
        }
    }

    /**
     * CSV 형식에서 특수 문자를 처리하기 위한 이스케이프 함수
     */
    private fun escapeCsv(value: String?): String {
        if (value == null) return ""
        var result = value
        if (result.contains("\"")) {
            result = result.replace("\"", "\"\"")
        }
        if (result.contains(",") || result.contains("\n") || result.contains("\"")) {
            result = "\"$result\""
        }
        return result
    }
}
