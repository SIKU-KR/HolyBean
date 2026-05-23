package eloom.holybean.printer.network

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptBuilderTest {

    @Test
    fun `builds command list in order`() {
        val commands = ReceiptBuilder()
            .divider('=')
            .blank()
            .text("주문번호 : 42", align = PrintAlign.CENTER, underline = true, size = PrintSize.BIG)
            .cut()
            .build()

        assertEquals(4, commands.size)
        assertEquals("divider", commands[0].type)
        assertEquals("=", commands[0].ch)
        assertEquals("blank", commands[1].type)
        assertEquals("text", commands[2].type)
        assertEquals("center", commands[2].align)
        assertEquals(true, commands[2].underline)
        assertEquals("big", commands[2].size)
        assertEquals("cut", commands[3].type)
    }

    @Test
    fun `default fields are null so gson omits them`() {
        val command = ReceiptBuilder().text("hi").build().single()
        // 기본값(left/normal/false)은 null
        assertEquals(null, command.align)
        assertEquals(null, command.bold)
        assertEquals(null, command.underline)
        assertEquals(null, command.size)

        val json = Gson().toJson(PrintRequestDto(listOf(command)))
        assertTrue(json.contains("\"type\":\"text\""))
        assertTrue(json.contains("\"content\":\"hi\""))
        assertFalse("기본값 필드는 직렬화에서 생략되어야 함", json.contains("align"))
        assertFalse(json.contains("bold"))
        assertFalse(json.contains("size"))
    }

    @Test
    fun `row builds columns with segments`() {
        val command = ReceiptBuilder()
            .row(
                ReceiptBuilder.seg("아메리카노", bold = true),
                ReceiptBuilder.seg("2", align = PrintAlign.RIGHT),
            )
            .build()
            .single()

        assertEquals("row", command.type)
        assertEquals(2, command.columns!!.size)
        assertEquals("아메리카노", command.columns!![0].content)
        assertEquals(true, command.columns!![0].bold)
        assertEquals("right", command.columns!![1].align)
    }
}
