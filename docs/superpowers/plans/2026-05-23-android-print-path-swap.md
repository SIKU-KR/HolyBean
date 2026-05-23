# Android 인쇄 경로 교체 (Bluetooth → Pi HTTP) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android 앱의 인쇄 경로를 Bluetooth ESC/POS 직접 출력에서 **Pi 프린트 서버로의 HTTP + 구조화 JSON 전송**으로 교체한다. 세 영수증 포매터는 마크업 문자열 대신 `PrintCommand` DTO 리스트를 생성하고, 새 `PiPrintClient`가 직렬화·재시도를 책임진다. AWS(주문/외상/매출/메뉴) 로직은 **무변경**(PRD 비목표).

**Architecture:** 포매터(`HomePrinter`/`OrdersPrinter`/`ReportPrinter`)는 `List<PrintCommandDto>`를 반환한다. `PiPrintClient`는 Mutex로 요청을 직렬화하고 `BackoffRetry`의 지연 정책으로 3회 재시도한 뒤 Retrofit `PrintServerApi.print()`를 호출한다. Pi 서버 base URL은 `BuildConfig.PRINT_SERVER_URL`(핫스팟 게이트웨이). Bluetooth 스택(`PrinterConnectionManager`, `printer/bluetooth/*`, `BluetoothBindings`, `:printer` 레거시 모듈, 매니페스트 BLUETOOTH 권한)은 제거한다. `BackoffRetry`는 재사용한다.

**Tech Stack:** Kotlin, Retrofit 2.11 + Gson 2.13(기존), OkHttp, Hilt(DI), 코루틴. 테스트: JUnit4 + MockK 1.14 + kotlinx-coroutines-test(기존 패턴).

**Dependency:** 이 계획은 `2026-05-23-pi-rust-print-server.md`의 **§JSON 계약**을 소비한다. DTO 필드/타입/명령 종류는 그 계약과 1:1 일치해야 한다. (`text`/`row`/`divider`/`blank`/`cut`, 라인 폭 32칸, EUC-KR은 Pi가 담당.) Pi 서버가 먼저 동작 가능해야 통합 검증이 된다.

---

## 영수증 → 명령 매핑 (수용 기준 레퍼런스)

기존 마크업과 새 명령의 대응. 포매터 테스트가 이 매핑을 고정한다.

| 기존 마크업 | 새 명령 |
|------------|---------|
| `[C]=====...` | `divider(ch='=')` |
| `[C]-----...` | `divider(ch='-')` |
| `[L]` (빈 줄) | `blank()` |
| `[C]<u><font size='big'>X</font></u>` | `text(X, align=CENTER, underline=true, size=BIG)` |
| `[L]<font size='big'>X</font>` | `text(X, align=LEFT, size=BIG)` |
| `[R]X` | `text(X, align=RIGHT)` |
| `[L]<b>name</b>[R]count` | `row(seg(name, bold=true), seg(count, align=RIGHT))` |
| `[L]a[R]b[R]c` (리포트 헤더/행) | `row(seg(a), seg(b, align=RIGHT), seg(c, align=RIGHT))` |
| (기존 `printFormattedTextAndCut`의 암묵적 절단) | 각 영수증 끝에 `cut()` 명시 |

기존 포매터는 절단을 포함하지 않았다(`PrinterConnectionManager`가 출력 시 절단). 새 모델에서는 **영수증 1장 = 명령 배열 1개 = `cut()`으로 종료**한다. 홈 화면은 고객용+POS용 2장이므로 `print()`를 2회 호출한다(각각 cut).

---

## File Structure

신규 패키지 `eloom.holybean.printer.network`.

| 파일 | 책임 |
|------|------|
| `printer/network/PrintCommandDto.kt` | `PrintRequestDto`, `PrintCommandDto`, `PrintSegmentDto`, `PrintAlign`, `PrintSize` (Gson DTO + wire enum) |
| `printer/network/ReceiptBuilder.kt` | 명령 배열 빌더 DSL (`text`/`row`/`seg`/`divider`/`blank`/`cut`/`build`) |
| `printer/network/PrintServerApi.kt` | Retrofit 인터페이스 (`POST print`, `GET health`) |
| `printer/network/PrintServerException.kt` | 인쇄 실패 예외 |
| `printer/PiPrintClient.kt` | Mutex 직렬화 + 3회 재시도 HTTP 클라이언트 (PrinterConnectionManager 대체) |
| `di/PrintNetworkModule.kt` | Pi용 Retrofit + `PrintServerApi` 제공 |
| `printer/polymorphism/HomePrinter.kt` 외 2개 | 반환 타입 `String` → `List<PrintCommandDto>`로 재작성 |
| (삭제) `printer/PrinterConnectionManager.kt`, `printer/bluetooth/*`, `di/BluetoothBindings.kt` | Bluetooth 제거 |

**작업 디렉터리는 `android/`** (Gradle 루트). 모든 `./gradlew` 명령은 `android/`에서 실행.

---

## Task 1: 인쇄 명령 DTO + 빌더 DSL

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/printer/network/PrintCommandDto.kt`
- Create: `android/app/src/main/java/eloom/holybean/printer/network/ReceiptBuilder.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/printer/network/ReceiptBuilderTest.kt`

- [ ] **Step 1: DTO 작성** — `PrintCommandDto.kt`

```kotlin
package eloom.holybean.printer.network

/**
 * Pi 프린트 서버 JSON 계약과 1:1 매핑되는 DTO.
 * Gson 기본 동작(널 필드 생략)으로 직렬화하면 계약의 최소 형태가 된다.
 */
data class PrintRequestDto(
    val commands: List<PrintCommandDto>,
)

data class PrintCommandDto(
    val type: String,                       // "text" | "row" | "divider" | "blank" | "cut"
    val content: String? = null,
    val align: String? = null,              // "left" | "center" | "right"
    val bold: Boolean? = null,
    val underline: Boolean? = null,
    val size: String? = null,               // "normal" | "big"
    val columns: List<PrintSegmentDto>? = null,
    val ch: String? = null,                 // divider 문자(첫 글자만 사용)
)

data class PrintSegmentDto(
    val content: String,
    val align: String? = null,
    val bold: Boolean? = null,
    val underline: Boolean? = null,
    val size: String? = null,
)

enum class PrintAlign(val wire: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    /** 기본값(LEFT)은 null로 만들어 JSON에서 생략 → 계약 최소 형태 유지. */
    fun wireOrNull(): String? = if (this == LEFT) null else wire
}

enum class PrintSize(val wire: String) {
    NORMAL("normal"),
    BIG("big");

    fun wireOrNull(): String? = if (this == NORMAL) null else wire
}
```

- [ ] **Step 2: 빌더 작성** — `ReceiptBuilder.kt`

```kotlin
package eloom.holybean.printer.network

/**
 * 영수증 명령 배열을 선언적으로 구성하는 빌더.
 * 기본값(left/normal/false)은 DTO에서 null로 남겨 JSON 직렬화 시 생략된다.
 */
class ReceiptBuilder {
    private val commands = mutableListOf<PrintCommandDto>()

    fun text(
        content: String,
        align: PrintAlign = PrintAlign.LEFT,
        bold: Boolean = false,
        underline: Boolean = false,
        size: PrintSize = PrintSize.NORMAL,
    ) = apply {
        commands += PrintCommandDto(
            type = "text",
            content = content,
            align = align.wireOrNull(),
            bold = if (bold) true else null,
            underline = if (underline) true else null,
            size = size.wireOrNull(),
        )
    }

    fun row(vararg segments: PrintSegmentDto) = apply {
        commands += PrintCommandDto(type = "row", columns = segments.toList())
    }

    fun divider(ch: Char = '=') = apply {
        commands += PrintCommandDto(type = "divider", ch = ch.toString())
    }

    fun blank() = apply {
        commands += PrintCommandDto(type = "blank")
    }

    fun cut() = apply {
        commands += PrintCommandDto(type = "cut")
    }

    fun build(): List<PrintCommandDto> = commands.toList()

    companion object {
        /** row()에 넣을 세그먼트 헬퍼. */
        fun seg(
            content: String,
            align: PrintAlign = PrintAlign.LEFT,
            bold: Boolean = false,
            underline: Boolean = false,
            size: PrintSize = PrintSize.NORMAL,
        ): PrintSegmentDto = PrintSegmentDto(
            content = content,
            align = align.wireOrNull(),
            bold = if (bold) true else null,
            underline = if (underline) true else null,
            size = size.wireOrNull(),
        )
    }
}
```

- [ ] **Step 3: 실패하는 테스트 작성** — `ReceiptBuilderTest.kt`

```kotlin
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
```

- [ ] **Step 4: 테스트 실행**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.printer.network.ReceiptBuilderTest"`
Expected: 3개 통과.

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/printer/network/ android/app/src/test/kotlin/eloom/holybean/printer/network/
git commit -m "feat(android): add print command DTOs and receipt builder"
```

---

## Task 2: PrintServerApi + Pi Retrofit DI + BuildConfig URL

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/printer/network/PrintServerApi.kt`
- Create: `android/app/src/main/java/eloom/holybean/printer/network/PrintServerException.kt`
- Create: `android/app/src/main/java/eloom/holybean/di/PrintNetworkModule.kt`
- Modify: `android/app/build.gradle.kts` (BuildConfig 필드 추가)

- [ ] **Step 1: API 인터페이스 + 예외 작성**

`PrintServerApi.kt`:

```kotlin
package eloom.holybean.printer.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface PrintServerApi {
    @POST("print")
    suspend fun print(@Body body: PrintRequestDto): Response<Unit>

    @GET("health")
    suspend fun health(): Response<Unit>
}
```

`PrintServerException.kt`:

```kotlin
package eloom.holybean.printer.network

class PrintServerException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

- [ ] **Step 2: BuildConfig 필드 추가** — `android/app/build.gradle.kts`

`defaultConfig {}` 블록 안, 기존 `buildConfigField("String", "BASE_URL", ...)` 줄 **아래**에 추가:

```kotlin
        buildConfigField("String", "PRINT_SERVER_URL", "\"http://192.168.4.1:9100/\"")
```

> 주의: Retrofit baseUrl은 **반드시 끝에 `/`** 가 있어야 한다. `192.168.4.1`은 Pi 핫스팟 게이트웨이 기본값(PRD §8에서 SSID/주소 확정 시 교체). 포트 9100은 Pi 서버 `HOLYBEAN_PRINT_BIND` 기본값과 일치.

- [ ] **Step 3: DI 모듈 작성** — `PrintNetworkModule.kt`

```kotlin
package eloom.holybean.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eloom.holybean.BuildConfig
import eloom.holybean.printer.network.PrintServerApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Pi 프린트 서버 전용 Retrofit. apikey 헤더 없음(AWS와 무관한 격리 경로).
 */
@Module
@InstallIn(SingletonComponent::class)
object PrintNetworkModule {

    @Provides
    @Singleton
    @Named("PrintServer")
    fun providePrintServerOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("PrintServer")
    fun providePrintServerRetrofit(
        @Named("PrintServer") client: OkHttpClient,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.PRINT_SERVER_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun providePrintServerApi(
        @Named("PrintServer") retrofit: Retrofit,
    ): PrintServerApi = retrofit.create(PrintServerApi::class.java)
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (`BuildConfig.PRINT_SERVER_URL` 미생성 에러가 나면 `./gradlew :app:generateDebugBuildConfig` 후 재시도.)

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/printer/network/PrintServerApi.kt \
        android/app/src/main/java/eloom/holybean/printer/network/PrintServerException.kt \
        android/app/src/main/java/eloom/holybean/di/PrintNetworkModule.kt \
        android/app/build.gradle.kts
git commit -m "feat(android): add Pi print server API and DI"
```

---

## Task 3: PiPrintClient (직렬화 + 재시도)

`PrinterConnectionManager`를 대체. Mutex로 출력을 직렬화하고, `BackoffRetry`의 검증된 `nextDelay` 정책을 코루틴 `delay()`와 결합해 3회 재시도한다.

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/printer/PiPrintClient.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/printer/PiPrintClientTest.kt`

- [ ] **Step 1: 구현 작성** — `PiPrintClient.kt`

```kotlin
package eloom.holybean.printer

import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.network.PrintRequestDto
import eloom.holybean.printer.network.PrintServerApi
import eloom.holybean.printer.network.PrintServerException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Pi 프린트 서버로 구조화 JSON을 전송하는 클라이언트.
 * 모든 출력은 내부 Mutex로 직렬화되어 영수증이 섞이지 않는다.
 * 일시적 실패는 BackoffRetry 정책으로 최대 3회 재시도한다.
 */
@Singleton
class PiPrintClient @Inject constructor(
    private val api: PrintServerApi,
    @Named("Printer") private val printerDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val retry = BackoffRetry(
        maxAttempts = 3,
        initialDelayMs = 300,
        multiplier = 2.0,
        maxDelayMs = 1_500,
    )

    /**
     * 명령 배열 1개(영수증 1장)를 출력한다. 실패 시 PrintServerException.
     */
    suspend fun print(commands: List<PrintCommandDto>) = withContext(printerDispatcher) {
        mutex.withLock {
            withRetry {
                val response = api.print(PrintRequestDto(commands))
                if (!response.isSuccessful) {
                    throw PrintServerException("print server returned HTTP ${response.code()}")
                }
            }
        }
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (error: Exception) {
                if (attempt >= retry.maxAttempts) {
                    throw error
                }
                delay(retry.nextDelay(attempt))
                attempt++
            }
        }
    }
}
```

- [ ] **Step 2: 실패하는 테스트 작성** — `PiPrintClientTest.kt`

```kotlin
package eloom.holybean.printer

import eloom.holybean.printer.network.PrintRequestDto
import eloom.holybean.printer.network.PrintServerApi
import eloom.holybean.printer.network.PrintServerException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response

@ExperimentalCoroutinesApi
class PiPrintClientTest {

    private val api: PrintServerApi = mockk()

    private fun client() = PiPrintClient(api, StandardTestDispatcher())

    @Test
    fun `posts commands and succeeds on 200`() = runTest {
        coEvery { api.print(any()) } returns Response.success(Unit)
        client().print(emptyList())
        coVerify(exactly = 1) { api.print(any<PrintRequestDto>()) }
    }

    @Test
    fun `retries transient failures up to three attempts`() = runTest {
        coEvery { api.print(any()) } throws RuntimeException("network") andThenThrows
            RuntimeException("network") andThen Response.success(Unit)
        client().print(emptyList())
        coVerify(exactly = 3) { api.print(any<PrintRequestDto>()) }
    }

    @Test
    fun `throws after exhausting retries`() = runTest {
        coEvery { api.print(any()) } returns Response.error(503, okhttp3.ResponseBody.create(null, ""))
        assertThrows(PrintServerException::class.java) {
            kotlinx.coroutines.runBlocking { client().print(emptyList()) }
        }
        coVerify(exactly = 3) { api.print(any<PrintRequestDto>()) }
    }
}
```

> 참고: `StandardTestDispatcher` + `runTest`에서 `delay()`는 가상시간으로 즉시 진행된다. 세 번째 테스트의 `assertThrows`는 동기 람다를 요구하므로 내부에서 `runBlocking`으로 감싼다(가상시간이 아니지만 재시도 지연 총합 ~1.2s로 허용 범위). 만약 느리면 `retry` 정책을 생성자 주입으로 빼서 테스트에서 0ms 지연을 주입하도록 리팩터링한다.

- [ ] **Step 3: 테스트 실행**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.printer.PiPrintClientTest"`
Expected: 3개 통과.

- [ ] **Step 4: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/printer/PiPrintClient.kt \
        android/app/src/test/kotlin/eloom/holybean/printer/PiPrintClientTest.kt
git commit -m "feat(android): add PiPrintClient with serialization and retry"
```

---

## Task 4: HomePrinter 명령 생성으로 재작성

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/printer/polymorphism/HomePrinter.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/printer/polymorphism/HomePrinterTest.kt`

- [ ] **Step 1: HomePrinter 재작성** (반환 타입 `String` → `List<PrintCommandDto>`)

```kotlin
package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.Order
import eloom.holybean.printer.network.PrintAlign
import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.network.PrintSize
import eloom.holybean.printer.network.ReceiptBuilder
import eloom.holybean.printer.network.ReceiptBuilder.Companion.seg
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 홈 화면 영수증 명령 생성기. 고객용/POS용 명령 배열을 만든다.
 * ESC/POS 변환은 Pi 서버가 담당한다.
 */
@Singleton
class HomePrinter @Inject constructor() {

    fun receiptForCustomer(data: Order): List<PrintCommandDto> = ReceiptBuilder()
        .divider('=')
        .blank()
        .text("주문번호 : ${data.orderNum}", align = PrintAlign.CENTER, underline = true, size = PrintSize.BIG)
        .blank()
        .divider('-')
        .blank()
        .also { builder ->
            data.orderItems.forEach { item ->
                builder.row(seg(item.name, bold = true), seg(item.count.toString(), align = PrintAlign.RIGHT))
            }
        }
        .blank()
        .divider('=')
        .cut()
        .build()

    fun receiptForPOS(data: Order, option: String): List<PrintCommandDto> = ReceiptBuilder()
        .divider('=')
        .blank()
        .text("주문번호 : ${data.orderNum}", align = PrintAlign.CENTER, underline = true, size = PrintSize.BIG)
        .blank()
        .text(option, align = PrintAlign.LEFT, size = PrintSize.BIG)
        .blank()
        .text("주문자 : ${data.customerName}", align = PrintAlign.RIGHT)
        .divider('-')
        .blank()
        .also { builder ->
            data.orderItems.forEach { item ->
                builder.row(seg(item.name, bold = true), seg(item.count.toString(), align = PrintAlign.RIGHT))
            }
        }
        .blank()
        .text("합계 : ${data.totalAmount}", align = PrintAlign.RIGHT)
        .text(data.orderDate, align = PrintAlign.RIGHT)
        .divider('=')
        .cut()
        .build()
}
```

> 메서드명을 `receiptTextForCustomer`→`receiptForCustomer`, `receiptTextForPOS`→`receiptForPOS`로 변경(반환이 더 이상 텍스트가 아니므로). ViewModel은 Task 6에서 갱신.

- [ ] **Step 2: 실패하는 테스트 작성** — `HomePrinterTest.kt`

```kotlin
package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePrinterTest {

    private val printer = HomePrinter()

    private fun order() = Order(
        orderDate = "2026-05-23",
        orderNum = 42,
        creditStatus = 0,
        customerName = "홍길동",
        orderItems = listOf(
            CartItem(id = 1, name = "아메리카노", price = 4000, count = 2, total = 8000),
        ),
        paymentMethods = listOf(PaymentMethod("현금", 8000)),
        totalAmount = 8000,
    )

    @Test
    fun `customer receipt has order number title and cut`() {
        val commands = printer.receiptForCustomer(order())

        assertEquals("divider", commands.first().type)
        assertEquals("cut", commands.last().type)

        val title = commands.first { it.type == "text" }
        assertEquals("주문번호 : 42", title.content)
        assertEquals("center", title.align)
        assertEquals("big", title.size)
        assertEquals(true, title.underline)

        val itemRow = commands.first { it.type == "row" }
        assertEquals("아메리카노", itemRow.columns!![0].content)
        assertEquals(true, itemRow.columns!![0].bold)
        assertEquals("2", itemRow.columns!![1].content)
        assertEquals("right", itemRow.columns!![1].align)
    }

    @Test
    fun `pos receipt includes option, customer, total, date`() {
        val commands = printer.receiptForPOS(order(), "포장")

        val texts = commands.filter { it.type == "text" }.mapNotNull { it.content }
        assertTrue(texts.any { it == "포장" })
        assertTrue(texts.any { it == "주문자 : 홍길동" })
        assertTrue(texts.any { it == "합계 : 8000" })
        assertTrue(texts.any { it == "2026-05-23" })
        assertEquals("cut", commands.last().type)
    }
}
```

- [ ] **Step 3: 테스트 실행**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.printer.polymorphism.HomePrinterTest"`
Expected: 2개 통과. (이 시점에 `HomeViewModel`은 옛 메서드명을 참조해 **컴파일 실패**할 수 있다 — Task 6에서 함께 고친다. 단위 테스트 컴파일이 막히면 Task 6을 Task 4와 한 묶음으로 실행하라.)

- [ ] **Step 4: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/printer/polymorphism/HomePrinter.kt \
        android/app/src/test/kotlin/eloom/holybean/printer/polymorphism/HomePrinterTest.kt
git commit -m "feat(android): rewrite HomePrinter to emit print commands"
```

---

## Task 5: OrdersPrinter + ReportPrinter 재작성

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/printer/polymorphism/OrdersPrinter.kt`
- Modify: `android/app/src/main/java/eloom/holybean/printer/polymorphism/ReportPrinter.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/printer/polymorphism/OrdersPrinterTest.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/printer/polymorphism/ReportPrinterTest.kt`

- [ ] **Step 1: OrdersPrinter 재작성**

```kotlin
package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.printer.network.PrintAlign
import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.network.PrintSize
import eloom.holybean.printer.network.ReceiptBuilder
import eloom.holybean.printer.network.ReceiptBuilder.Companion.seg
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 주문 목록 화면 영수증 재출력 명령 생성기.
 */
@Singleton
class OrdersPrinter @Inject constructor() {

    fun makeCommands(orderNum: Int, basketList: List<OrdersDetailItem>): List<PrintCommandDto> =
        ReceiptBuilder()
            .text("영수증 재출력", align = PrintAlign.RIGHT)
            .divider('=')
            .blank()
            .text("주문번호 : $orderNum", align = PrintAlign.CENTER, underline = true, size = PrintSize.BIG)
            .blank()
            .divider('-')
            .also { builder ->
                basketList.forEach { item ->
                    builder.row(seg(item.name, bold = true), seg(item.count.toString(), align = PrintAlign.RIGHT))
                }
            }
            .blank()
            .divider('=')
            .cut()
            .build()
}
```

- [ ] **Step 2: ReportPrinter 재작성**

```kotlin
package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.PrinterDTO
import eloom.holybean.printer.network.PrintAlign
import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.network.PrintSize
import eloom.holybean.printer.network.ReceiptBuilder
import eloom.holybean.printer.network.ReceiptBuilder.Companion.seg
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 리포트 화면 매출 영수증 명령 생성기.
 */
@Singleton
class ReportPrinter @Inject constructor() {

    fun makeCommands(data: PrinterDTO): List<PrintCommandDto> = ReceiptBuilder()
        .blank()
        .text("${data.startdate}~", align = PrintAlign.CENTER, underline = true, size = PrintSize.BIG)
        .text(data.enddate, align = PrintAlign.CENTER, underline = true, size = PrintSize.BIG)
        .divider('-')
        .text("총 판매금액 : ${data.reportData["총합"] ?: 0}")
        .text("현금 판매금액 : ${data.reportData["현금"] ?: 0}")
        .text("쿠폰 판매금액 : ${data.reportData["쿠폰"] ?: 0}")
        .text("계좌이체 판매금액 : ${data.reportData["계좌이체"] ?: 0}")
        .text("외상 판매금액 : ${data.reportData["외상"] ?: 0}")
        .text("무료쿠폰 판매금액 : ${data.reportData["무료쿠폰"] ?: 0}")
        .text("무료제공 판매금액 : ${data.reportData["무료제공"] ?: 0}")
        .divider('-')
        .row(seg("이름"), seg("수량", align = PrintAlign.RIGHT), seg("판매액", align = PrintAlign.RIGHT))
        .also { builder ->
            data.reportDetailItem.forEach { item ->
                builder.row(
                    seg(item.name),
                    seg(item.quantity.toString(), align = PrintAlign.RIGHT),
                    seg(item.subtotal.toString(), align = PrintAlign.RIGHT),
                )
            }
        }
        .blank()
        .cut()
        .build()
}
```

- [ ] **Step 3: 테스트 작성** — `OrdersPrinterTest.kt`

```kotlin
package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.OrdersDetailItem
import org.junit.Assert.assertEquals
import org.junit.Test

class OrdersPrinterTest {

    @Test
    fun `reprint receipt starts with reprint label and ends with cut`() {
        val commands = OrdersPrinter().makeCommands(
            orderNum = 7,
            basketList = listOf(OrdersDetailItem(name = "라떼", count = 3, subtotal = 12000)),
        )

        val first = commands.first()
        assertEquals("text", first.type)
        assertEquals("영수증 재출력", first.content)
        assertEquals("right", first.align)
        assertEquals("cut", commands.last().type)

        val itemRow = commands.first { it.type == "row" }
        assertEquals("라떼", itemRow.columns!![0].content)
        assertEquals("3", itemRow.columns!![1].content)
    }
}
```

`ReportPrinterTest.kt`:

```kotlin
package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.PrinterDTO
import eloom.holybean.data.model.ReportDetailItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPrinterTest {

    @Test
    fun `report has date title, totals and three-column detail rows`() {
        val dto = PrinterDTO(
            startdate = "2026-05-01",
            enddate = "2026-05-23",
            reportData = mapOf("총합" to 100000, "현금" to 60000),
            reportDetailItem = listOf(ReportDetailItem(name = "아메리카노", quantity = 10, subtotal = 40000)),
        )
        val commands = ReportPrinter().makeCommands(dto)

        val texts = commands.filter { it.type == "text" }.mapNotNull { it.content }
        assertTrue(texts.any { it == "2026-05-01~" })
        assertTrue(texts.any { it == "총 판매금액 : 100000" })

        val headerRow = commands.first { it.type == "row" }
        assertEquals(3, headerRow.columns!!.size)
        assertEquals("이름", headerRow.columns!![0].content)
        assertEquals("right", headerRow.columns!![1].align)

        val detailRow = commands.last { it.type == "row" }
        assertEquals("아메리카노", detailRow.columns!![0].content)
        assertEquals("10", detailRow.columns!![1].content)
        assertEquals("40000", detailRow.columns!![2].content)
        assertEquals("cut", commands.last().type)
    }
}
```

- [ ] **Step 4: 테스트 실행**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.printer.polymorphism.*"`
Expected: OrdersPrinterTest·ReportPrinterTest·HomePrinterTest 통과. (ViewModel 컴파일 의존이 있으면 Task 6과 묶어 실행.)

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/printer/polymorphism/OrdersPrinter.kt \
        android/app/src/main/java/eloom/holybean/printer/polymorphism/ReportPrinter.kt \
        android/app/src/test/kotlin/eloom/holybean/printer/polymorphism/OrdersPrinterTest.kt \
        android/app/src/test/kotlin/eloom/holybean/printer/polymorphism/ReportPrinterTest.kt
git commit -m "feat(android): rewrite Orders/Report printers to emit commands"
```

---

## Task 6: ViewModel 3개 + DI 배선 갱신

`PrinterConnectionManager` 의존을 `PiPrintClient`로 교체하고, 포매터 호출을 새 시그니처로 갱신한다. `print`/`printAndDisconnect` 구분은 사라진다(연결 개념 없음) — 모두 `piPrintClient.print(commands)`.

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/orderlist/OrdersViewModel.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/report/ReportViewModel.kt`
- Modify: 대응 ViewModel 테스트 3개

- [ ] **Step 1: HomeViewModel 갱신**

`import eloom.holybean.printer.PrinterConnectionManager` → `import eloom.holybean.printer.PiPrintClient`로 교체. 생성자 파라미터 `private val printerConnectionManager: PrinterConnectionManager,` → `private val piPrintClient: PiPrintClient,`. `printReceipt`를 다음으로 교체:

```kotlin
    // 영수증 출력은 독립적으로 실행 (Network 완료와 무관)
    private suspend fun printReceipt(data: Order, takeOption: String) {
        val customerCommands = homePrinter.receiptForCustomer(data)
        val posCommands = homePrinter.receiptForPOS(data, takeOption)
        piPrintClient.print(customerCommands)
        piPrintClient.print(posCommands)
    }
```

- [ ] **Step 2: OrdersViewModel 갱신**

`PrinterConnectionManager` import/파라미터를 `PiPrintClient`로 교체. `reprint()`의 출력부를 교체:

```kotlin
        val orderDetailsArrayList = ArrayList(currentState.orderDetails)
        val commands = ordersPrinter.makeCommands(currentState.selectedOrderNumber, orderDetailsArrayList)

        // Printer I/O - Application Scope에서 실행 (ViewModel 생명주기와 독립)
        applicationScope.launch {
            val result = runCatching {
                piPrintClient.print(commands)
            }
            result.onFailure { error ->
                _uiEvent.tryEmit(OrdersUiEvent.ShowToast("Printer error: ${error.message}"))
            }
        }
```

- [ ] **Step 3: ReportViewModel 갱신**

`PrinterConnectionManager` import/파라미터를 `PiPrintClient`로 교체. 출력부를 교체:

```kotlin
        applicationScope.launch {
            val result = runCatching {
                val dateParts = title.split(" ~ ")
                val printerDTO = PrinterDTO(dateParts[0], dateParts[1], summary, details)
                val commands = reportPrinter.makeCommands(printerDTO)
                piPrintClient.print(commands)
            }
            result
                .onSuccess {
                    _uiEvent.tryEmit(ReportUiEvent.ShowToast("리포트 인쇄가 완료되었습니다"))
                }
                .onFailure { error ->
                    _uiEvent.tryEmit(ReportUiEvent.ShowError("인쇄 실패 : ${error.localizedMessage}"))
                }
        }
```

- [ ] **Step 4: ViewModel 테스트 갱신**

세 테스트 파일에서 `PrinterConnectionManager` mock을 `PiPrintClient`로 교체하고, 포매터 mock의 반환 타입을 `List<PrintCommandDto>`로 맞춘다. 예 — `HomeViewModelTest.kt`:

- `import eloom.holybean.printer.PrinterConnectionManager` → `import eloom.holybean.printer.PiPrintClient`
- `private val printerConnectionManager: PrinterConnectionManager = mockk(relaxed = true)` → `private val piPrintClient: PiPrintClient = mockk(relaxed = true)`
- ViewModel 생성 시 인자 교체.
- 인쇄 검증이 있던 곳: `coVerify { printerConnectionManager.print(any()) }` → `coVerify { piPrintClient.print(any<List<PrintCommandDto>>()) }` (필요한 import 추가: `eloom.holybean.printer.network.PrintCommandDto`).
- `homePrinter.receiptTextForCustomer(...)`를 stub하던 부분이 있으면 `homePrinter.receiptForCustomer(any())` 로 변경하고 반환을 `emptyList()`로.

OrdersViewModelTest / ReportViewModelTest도 동일 패턴(`makeText`→`makeCommands`, `getPrintingText`→`makeCommands`, 반환 `emptyList()`).

- [ ] **Step 5: 단위 테스트 실행**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.*"`
Expected: Home/Orders/Report ViewModel 테스트 통과.

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt \
        android/app/src/main/java/eloom/holybean/ui/orderlist/OrdersViewModel.kt \
        android/app/src/main/java/eloom/holybean/ui/report/ReportViewModel.kt \
        android/app/src/test/kotlin/eloom/holybean/ui/
git commit -m "feat(android): switch ViewModels from Bluetooth to PiPrintClient"
```

---

## Task 7: Bluetooth 스택 제거

**Files:**
- Delete: `android/app/src/main/java/eloom/holybean/printer/PrinterConnectionManager.kt`
- Delete: `android/app/src/main/java/eloom/holybean/printer/bluetooth/` (디렉터리 전체)
- Delete: `android/app/src/main/java/eloom/holybean/di/BluetoothBindings.kt`
- Modify: `android/app/src/main/AndroidManifest.xml` (BLUETOOTH 권한 제거)
- Modify: `android/settings.gradle.kts` (`:printer` 모듈 include 제거)
- Modify: `android/app/build.gradle.kts` (`implementation(project(":printer"))` 제거)

- [ ] **Step 1: 코드/모듈 삭제**

```bash
cd /Users/benn/dev/personal/HolyBean
git rm android/app/src/main/java/eloom/holybean/printer/PrinterConnectionManager.kt
git rm -r android/app/src/main/java/eloom/holybean/printer/bluetooth
git rm android/app/src/main/java/eloom/holybean/di/BluetoothBindings.kt
```

- [ ] **Step 2: 매니페스트 권한 제거** — `android/app/src/main/AndroidManifest.xml`에서 `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` `<uses-permission>` 두 줄 삭제(있다면 `<uses-feature>` bluetooth 항목도).

- [ ] **Step 3: Gradle에서 레거시 모듈 제거**

`android/settings.gradle.kts`에서 두 줄 삭제:
```
include(":app", ":printer")          →  include(":app")
project(":printer").projectDir = file("../_legacy/escpos")   →  (삭제)
```

`android/app/build.gradle.kts`에서 삭제:
```kotlin
implementation(project(":printer"))
```

- [ ] **Step 4: 잔존 참조 확인**

Run: `cd android && grep -rn "PrinterConnectionManager\|escpos\|BluetoothPrintersConnections\|BluetoothConnection\|project(\":printer\")" app/src settings.gradle.kts app/build.gradle.kts`
Expected: **결과 없음**. (있으면 해당 참조를 제거/교체.)

- [ ] **Step 5: 컴파일 + 전체 단위 테스트**

Run: `cd android && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 전체 단위 테스트 통과.

- [ ] **Step 6: 커밋**

```bash
git add -A android/
git commit -m "refactor(android): remove Bluetooth printing stack and legacy escpos module"
```

---

## Task 8: 최종 빌드 + 통합 스모크 (Pi 서버 연동)

**Files:** (없음 — 검증만)

- [ ] **Step 1: 디버그 APK 빌드**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Pi 서버 대상 통합 스모크** (Pi 서버 실행 중일 때)

로컬 PC에서 Pi 서버를 임시 파일 디바이스로 띄우고(`2026-05-23-pi-rust-print-server.md` Task 10 참조), `BuildConfig.PRINT_SERVER_URL`을 그 주소로 일시 변경한 디버그 빌드를 실기기/에뮬레이터에서 실행해 주문 1건을 출력한다. 출력 후 Pi 서버의 디바이스 파일에 `1b 40 1b 74 0d ...` ESC/POS 바이트가 누적되는지 확인.

> 실제 프린터(세우 SLK-TS400B) 출력 검증과 핫스팟/NAT/ systemd는 **별도 후속 계획**(PRD 마일스톤 1·3·4·6) 소관이다.

- [ ] **Step 3: 최종 커밋(필요 시)**

```bash
git commit --allow-empty -m "chore(android): verify build after print path swap"
```

---

## Self-Review (작성자 점검 결과)

**Spec coverage (PRD §5):**
- FR-1 (출력 요청): Task 2·3 (`PiPrintClient`→`POST /print`). ✓
- FR-2a (구조화 JSON 전송): Task 1·4·5 (포매터가 DTO 생성). ✓
- FR-4 (실패 자동 재시도): Task 3 (`withRetry` 3회). ✓
- FR-5 (실패를 사용자에게 알림): Task 6 (Orders/Report `ShowToast`/`ShowError`). ✓ (Home은 기존대로 백그라운드 출력이라 toast 미표시 — 기존 동작 보존; 알림 강화가 필요하면 별도 안건.)
- FR-7 (인쇄 경로 AWS와 독립): Task 2 (apikey 없는 별도 Retrofit), Task 6 (postOrder와 print가 분리된 스코프). ✓
- NFR-4 (영수증 직렬화): Task 3 Mutex. ✓
- FR-3 (`/health`): `PrintServerApi.health` 정의(Task 2). 호출 UI 연동은 선택 — 현재 범위에선 API만 제공.
- FR-6·NFR-1·2·5 (핫스팟/NAT): 네트워크 후속 계획 소관.

**Placeholder scan:** TODO/TBD 없음. 모든 코드 단계에 완전한 코드. 삭제 단계는 정확한 경로/grep 검증 포함.

**Type consistency:** `PrintCommandDto`/`PrintSegmentDto`/`PrintRequestDto`/`PrintAlign`/`PrintSize`(network), `ReceiptBuilder.seg`, `PrintServerApi.print`, `PiPrintClient.print(List<PrintCommandDto>)`, 포매터 신규 시그니처(`receiptForCustomer`/`receiptForPOS`/`makeCommands`)가 태스크 전반에 일관. JSON 필드명은 Pi 계획 §JSON 계약과 일치.

**알려진 주의점:**
1. Pi 계획이 먼저 동작해야 Task 8 통합이 가능. DTO/계약 변경 시 양 계획 동시 수정.
2. 포매터 메서드명 변경으로 Task 4~6은 컴파일상 한 묶음(서브에이전트 실행 시 4·5·6을 연속 처리 권장).
3. `PiPrintClientTest` 세 번째 케이스의 `runBlocking`이 느리면 `BackoffRetry`를 생성자 주입으로 빼고 테스트에서 0ms 정책 주입(주석에 명시).
