# 런타임 Pi 인쇄서버 주소 해석 (mDNS 하이브리드) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android 앱이 Pi 인쇄서버 주소를 빌드 고정값이 아니라 런타임에 해석(수동 override > 영속 캐시 > mDNS)하도록 바꿔, 핫스팟/공유기 토폴로지에 무관하게 동작시킨다.

**Architecture:** OkHttp 인터셉터가 매 요청에서 `PrinterAddressResolver`가 보유한 `host:port`로 요청 URL을 치환한다(방식 A). Retrofit baseUrl은 더미. 리졸버는 `수동 override > 영속화된 마지막 성공 IP > mDNS 재탐색` 우선순위로 주소를 해석하며, 평상시 인쇄는 인메모리 캐시로 지연 0이고 IOException 발생 시에만 1회 재탐색한다.

**Tech Stack:** Kotlin, Hilt, Retrofit2 + OkHttp, Android `NsdManager`(mDNS), SharedPreferences(영속), JUnit4 + mockk + kotlinx-coroutines-test. Pi 측은 avahi(mDNS 광고).

설계 출처: `docs/superpowers/specs/2026-05-30-runtime-pi-address-discovery-design.md`

---

## File Structure

신규 (모두 `android/app/src/main/java/eloom/holybean/printer/network/` 아래):
- `PrinterAddress.kt` — `host:port` 값 객체 + 파싱/직렬화. 순수 Kotlin.
- `PrinterStatus.kt` — UI 표시용 상태 sealed class.
- `PrinterAddressStore.kt` — 영속 인터페이스 + SharedPreferences 구현.
- `MdnsDiscovery.kt` — mDNS 탐색 인터페이스 + `NsdManager` 구현.
- `PrinterAddressResolver.kt` — 우선순위/캐시/재탐색 단일 책임 컴포넌트.
- `PrinterUrlRewriter.kt` — 원본 URL의 host/port를 치환하는 순수 함수.
- `PrinterHostInterceptor.kt` — OkHttp 인터셉터(리졸버 캐시 → 요청 URL 치환).

수정:
- `android/app/build.gradle.kts:30` — `PRINT_SERVER_URL` 라인 제거.
- `android/app/src/main/java/eloom/holybean/di/PrintNetworkModule.kt` — 더미 baseUrl + 인터셉터 + 인터페이스 바인딩.
- `android/app/src/main/java/eloom/holybean/printer/PiPrintClient.kt` — IOException 시 1회 재탐색.
- `android/app/src/main/java/eloom/holybean/ui/settings/DevToolsViewModel.kt` — 프린터 연결 상태/수동입력/재탐색.
- `android/app/src/main/java/eloom/holybean/ui/startup/StartupViewModel.kt` — 시작 시 비차단 워밍업.
- DevTools 화면 Compose(아래 Task 11에서 경로 확정).

신규 (Pi):
- `pi/deploy/holybean-print.service` — avahi 서비스 정의(XML).
- `pi/deploy/README.md` — 셋업/핫스팟 은퇴 안내.

테스트 (`android/app/src/test/kotlin/eloom/holybean/printer/`):
- `PrinterAddressTest.kt`, `PrinterAddressResolverTest.kt`, `PrinterUrlRewriterTest.kt`, `PrinterHostInterceptorTest.kt`
- `PiPrintClientTest.kt`(수정), `network/FakePrinterAddressStore.kt`(테스트용 fake)

---

## Task 1: `PrinterAddress` 값 객체

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/printer/network/PrinterAddress.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/printer/PrinterAddressTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// PrinterAddressTest.kt
package eloom.holybean.printer

import eloom.holybean.printer.network.PrinterAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrinterAddressTest {

    @Test
    fun `parse host with explicit port`() {
        val addr = PrinterAddress.parse("192.168.0.27:9100")
        assertEquals(PrinterAddress("192.168.0.27", 9100), addr)
    }

    @Test
    fun `parse host without port defaults to 9100`() {
        val addr = PrinterAddress.parse("holybean.local")
        assertEquals(PrinterAddress("holybean.local", 9100), addr)
    }

    @Test
    fun `parse trims whitespace`() {
        assertEquals(PrinterAddress("10.0.0.5", 9100), PrinterAddress.parse("  10.0.0.5  "))
    }

    @Test
    fun `parse blank returns null`() {
        assertNull(PrinterAddress.parse("   "))
        assertNull(PrinterAddress.parse(null))
    }

    @Test
    fun `parse invalid port returns null`() {
        assertNull(PrinterAddress.parse("10.0.0.5:notaport"))
    }

    @Test
    fun `toAuthority round trips`() {
        assertEquals("192.168.0.27:9100", PrinterAddress("192.168.0.27", 9100).toAuthority())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.printer.PrinterAddressTest"` (from `android/`)
Expected: FAIL — `PrinterAddress` 미존재(compile error).

- [ ] **Step 3: Write minimal implementation**

```kotlin
// PrinterAddress.kt
package eloom.holybean.printer.network

/** Pi 인쇄서버 주소. 포트 미지정 시 기본 9100. */
data class PrinterAddress(val host: String, val port: Int) {

    fun toAuthority(): String = "$host:$port"

    companion object {
        const val DEFAULT_PORT = 9100

        /** "host" 또는 "host:port" 파싱. 공백/형식 오류면 null. */
        fun parse(raw: String?): PrinterAddress? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val colon = text.lastIndexOf(':')
            if (colon < 0) return PrinterAddress(text, DEFAULT_PORT)
            val host = text.substring(0, colon).trim()
            if (host.isEmpty()) return null
            val port = text.substring(colon + 1).trim().toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return PrinterAddress(host, port)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.printer.PrinterAddressTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/printer/network/PrinterAddress.kt android/app/src/test/kotlin/eloom/holybean/printer/PrinterAddressTest.kt
git commit -m "feat(print): PrinterAddress 값 객체 + 파싱"
```

---

## Task 2: `PrinterStatus` + `PrinterAddressStore` (인터페이스 + 영속)

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/printer/network/PrinterStatus.kt`
- Create: `android/app/src/main/java/eloom/holybean/printer/network/PrinterAddressStore.kt`
- Create: `android/app/src/test/kotlin/eloom/holybean/printer/network/FakePrinterAddressStore.kt`

SharedPreferences 구현은 Android 프레임워크 의존이라 단위테스트하지 않는다(`NetworkStatusProvider`와 동일한 정책). 대신 리졸버 테스트(Task 4)가 fake store로 동작을 검증한다. 이 Task는 fake가 컴파일되는지까지만 확인한다.

- [ ] **Step 1: Create `PrinterStatus.kt`**

```kotlin
// PrinterStatus.kt
package eloom.holybean.printer.network

/** 프린터 연결 상태(설정 화면 표시용). */
sealed class PrinterStatus {
    data object Unknown : PrinterStatus()       // 아직 해석된 주소 없음
    data object Resolving : PrinterStatus()      // mDNS 탐색 중
    data class Connected(val address: PrinterAddress) : PrinterStatus()
    data object Unreachable : PrinterStatus()    // 탐색 실패 + 캐시 없음
}
```

- [ ] **Step 2: Create `PrinterAddressStore.kt`**

```kotlin
// PrinterAddressStore.kt
package eloom.holybean.printer.network

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Pi 주소 영속 저장소.
 * - override: 관리자가 수동 입력한 주소(있으면 mDNS보다 우선).
 * - lastGood: 마지막으로 도달에 성공한 주소(빠른 경로 시드).
 */
interface PrinterAddressStore {
    var override: String?
    var lastGood: String?
}

class SharedPrefsPrinterAddressStore @Inject constructor(
    @ApplicationContext context: Context,
) : PrinterAddressStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("printer_address", Context.MODE_PRIVATE)

    override var override: String?
        get() = prefs.getString(KEY_OVERRIDE, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_OVERRIDE) else putString(KEY_OVERRIDE, value)
        }.apply()

    override var lastGood: String?
        get() = prefs.getString(KEY_LAST_GOOD, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_LAST_GOOD) else putString(KEY_LAST_GOOD, value)
        }.apply()

    private companion object {
        const val KEY_OVERRIDE = "override"
        const val KEY_LAST_GOOD = "last_good"
    }
}
```

- [ ] **Step 3: Create `FakePrinterAddressStore.kt` (test)**

```kotlin
// src/test/.../printer/network/FakePrinterAddressStore.kt
package eloom.holybean.printer.network

class FakePrinterAddressStore(
    override var override: String? = null,
    override var lastGood: String? = null,
) : PrinterAddressStore
```

- [ ] **Step 4: Verify compilation**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:compileDebugUnitTestKotlin` (from `android/`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/printer/network/PrinterStatus.kt android/app/src/main/java/eloom/holybean/printer/network/PrinterAddressStore.kt android/app/src/test/kotlin/eloom/holybean/printer/network/FakePrinterAddressStore.kt
git commit -m "feat(print): PrinterStatus + PrinterAddressStore(영속) + 테스트 fake"
```

---

## Task 3: `MdnsDiscovery` 인터페이스 + `NsdManager` 구현

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/printer/network/MdnsDiscovery.kt`

`NsdManager`/`WifiManager`는 Android 프레임워크라 JVM 단위테스트 불가(`NetworkStatusProvider`와 동일 정책). 리졸버 테스트는 fake `MdnsDiscovery`로 검증하고, 실제 동작은 수동 검증(Task 14)으로 확인한다. 따라서 이 Task는 실패 테스트 없이 구현 후 컴파일만 확인한다.

- [ ] **Step 1: Write implementation**

```kotlin
// MdnsDiscovery.kt
package eloom.holybean.printer.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

/** mDNS(Bonjour/avahi)로 Pi 인쇄서버를 탐색한다. */
interface MdnsDiscovery {
    /** 성공 시 PrinterAddress, 타임아웃/미발견 시 null. */
    suspend fun discover(timeoutMs: Long): PrinterAddress?
}

class NsdMdnsDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) : MdnsDiscovery {

    override suspend fun discover(timeoutMs: Long): PrinterAddress? {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("holybean-mdns")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        return try {
            withTimeoutOrNull(timeoutMs) { runDiscovery(nsd) }
        } catch (e: TimeoutCancellationException) {
            null
        } finally {
            lock?.takeIf { it.isHeld }?.release()
        }
    }

    private suspend fun runDiscovery(nsd: NsdManager): PrinterAddress? =
        suspendCancellableCoroutine { cont ->
            var resumed = false
            fun finish(result: PrinterAddress?) {
                if (!resumed) { resumed = true; if (cont.isActive) cont.resume(result) }
            }

            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "resolve failed: $errorCode")
                    finish(null)
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val host = serviceInfo.host?.hostAddress
                    val port = serviceInfo.port
                    finish(host?.let { PrinterAddress(it, port) })
                }
            }

            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = finish(null)
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(serviceInfo, resolveListener)
                }
            }

            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            cont.invokeOnCancellation {
                runCatching { nsd.stopServiceDiscovery(discoveryListener) }
            }
        }

    private companion object {
        const val TAG = "NsdMdnsDiscovery"
        const val SERVICE_TYPE = "_holybean-print._tcp."
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:compileDebugKotlin` (from `android/`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/printer/network/MdnsDiscovery.kt
git commit -m "feat(print): NsdManager 기반 mDNS 탐색(_holybean-print._tcp)"
```

---

## Task 4: `PrinterAddressResolver` (우선순위/캐시/재탐색)

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/printer/network/PrinterAddressResolver.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/printer/PrinterAddressResolverTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// PrinterAddressResolverTest.kt
package eloom.holybean.printer

import eloom.holybean.printer.network.FakePrinterAddressStore
import eloom.holybean.printer.network.MdnsDiscovery
import eloom.holybean.printer.network.PrinterAddress
import eloom.holybean.printer.network.PrinterAddressResolver
import eloom.holybean.printer.network.PrinterStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@ExperimentalCoroutinesApi
class PrinterAddressResolverTest {

    /** 미리 정해진 결과를 반환하는 fake 탐색기. 호출 횟수도 센다. */
    private class FakeDiscovery(var result: PrinterAddress?) : MdnsDiscovery {
        var calls = 0
        override suspend fun discover(timeoutMs: Long): PrinterAddress? { calls++; return result }
    }

    @Test
    fun `current seeds from lastGood at construction`() {
        val store = FakePrinterAddressStore(lastGood = "10.0.0.5:9100")
        val resolver = PrinterAddressResolver(store, FakeDiscovery(null))
        assertEquals(PrinterAddress("10.0.0.5", 9100), resolver.current())
    }

    @Test
    fun `current prefers override over lastGood`() {
        val store = FakePrinterAddressStore(override = "1.1.1.1", lastGood = "10.0.0.5:9100")
        val resolver = PrinterAddressResolver(store, FakeDiscovery(null))
        assertEquals(PrinterAddress("1.1.1.1", 9100), resolver.current())
    }

    @Test
    fun `current is null when nothing stored`() {
        val resolver = PrinterAddressResolver(FakePrinterAddressStore(), FakeDiscovery(null))
        assertNull(resolver.current())
    }

    @Test
    fun `rediscover with override short-circuits and does not call mdns`() = runTest {
        val store = FakePrinterAddressStore(override = "2.2.2.2:9999")
        val discovery = FakeDiscovery(PrinterAddress("9.9.9.9", 9100))
        val resolver = PrinterAddressResolver(store, discovery)
        val result = resolver.rediscover()
        assertEquals(PrinterAddress("2.2.2.2", 9999), result)
        assertEquals(0, discovery.calls)
    }

    @Test
    fun `rediscover persists found address to lastGood and caches it`() = runTest {
        val store = FakePrinterAddressStore()
        val discovery = FakeDiscovery(PrinterAddress("192.168.0.27", 9100))
        val resolver = PrinterAddressResolver(store, discovery)
        val result = resolver.rediscover()
        assertEquals(PrinterAddress("192.168.0.27", 9100), result)
        assertEquals("192.168.0.27:9100", store.lastGood)
        assertEquals(PrinterAddress("192.168.0.27", 9100), resolver.current())
        assertEquals(PrinterStatus.Connected(PrinterAddress("192.168.0.27", 9100)), resolver.status.value)
    }

    @Test
    fun `rediscover failure keeps stale cache`() = runTest {
        val store = FakePrinterAddressStore(lastGood = "10.0.0.5:9100")
        val resolver = PrinterAddressResolver(store, FakeDiscovery(null))
        val result = resolver.rediscover()
        assertEquals(PrinterAddress("10.0.0.5", 9100), result)
    }

    @Test
    fun `rediscover failure with no cache sets Unreachable`() = runTest {
        val resolver = PrinterAddressResolver(FakePrinterAddressStore(), FakeDiscovery(null))
        assertNull(resolver.rediscover())
        assertEquals(PrinterStatus.Unreachable, resolver.status.value)
    }

    @Test
    fun `setManualOverride updates cache and store`() = runTest {
        val store = FakePrinterAddressStore()
        val resolver = PrinterAddressResolver(store, FakeDiscovery(null))
        resolver.setManualOverride("3.3.3.3:8080")
        assertEquals("3.3.3.3:8080", store.override)
        assertEquals(PrinterAddress("3.3.3.3", 8080), resolver.current())
    }

    @Test
    fun `clearing override falls back to lastGood`() = runTest {
        val store = FakePrinterAddressStore(override = "3.3.3.3", lastGood = "10.0.0.5:9100")
        val resolver = PrinterAddressResolver(store, FakeDiscovery(null))
        resolver.setManualOverride(null)
        assertNull(store.override)
        assertEquals(PrinterAddress("10.0.0.5", 9100), resolver.current())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.printer.PrinterAddressResolverTest"`
Expected: FAIL — `PrinterAddressResolver` 미존재.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// PrinterAddressResolver.kt
package eloom.holybean.printer.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pi 주소 단일 해석기. 우선순위: 수동 override > 영속 lastGood > mDNS 재탐색.
 * current()는 인메모리 캐시를 동기 반환(인터셉터가 매 요청에서 블로킹 없이 읽음).
 */
@Singleton
class PrinterAddressResolver @Inject constructor(
    private val store: PrinterAddressStore,
    private val discovery: MdnsDiscovery,
) {
    @Volatile
    private var cached: PrinterAddress? = null

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Unknown)
    val status: StateFlow<PrinterStatus> = _status.asStateFlow()

    init {
        cached = PrinterAddress.parse(store.override ?: store.lastGood)
        cached?.let { _status.value = PrinterStatus.Connected(it) }
    }

    /** 인메모리 캐시. 인터셉터 동기 호출용. */
    fun current(): PrinterAddress? = cached

    /** override > lastGood 시드 후, override 없으면 mDNS 탐색. 성공 시 lastGood 영속화. */
    suspend fun rediscover(): PrinterAddress? {
        PrinterAddress.parse(store.override)?.let { setCache(it); return it }

        _status.value = PrinterStatus.Resolving
        val found = discovery.discover(DISCOVERY_TIMEOUT_MS)
        if (found != null) {
            store.lastGood = found.toAuthority()
            setCache(found)
            return found
        }
        // 탐색 실패: 기존 캐시 유지(stale), 없으면 Unreachable.
        if (cached == null) _status.value = PrinterStatus.Unreachable
        else _status.value = PrinterStatus.Connected(cached!!)
        return cached
    }

    /** 수동 주소 설정/해제. null이면 lastGood로 폴백. */
    fun setManualOverride(value: String?) {
        store.override = value
        val parsed = PrinterAddress.parse(value) ?: PrinterAddress.parse(store.lastGood)
        if (parsed != null) setCache(parsed)
        else { cached = null; _status.value = PrinterStatus.Unknown }
    }

    private fun setCache(addr: PrinterAddress) {
        cached = addr
        _status.value = PrinterStatus.Connected(addr)
    }

    companion object {
        const val DISCOVERY_TIMEOUT_MS = 4_000L
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.printer.PrinterAddressResolverTest"`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/printer/network/PrinterAddressResolver.kt android/app/src/test/kotlin/eloom/holybean/printer/PrinterAddressResolverTest.kt
git commit -m "feat(print): PrinterAddressResolver(우선순위/캐시/재탐색)"
```

---

## Task 5: `PrinterUrlRewriter` (순수 URL 치환)

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/printer/network/PrinterUrlRewriter.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/printer/PrinterUrlRewriterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// PrinterUrlRewriterTest.kt
package eloom.holybean.printer

import eloom.holybean.printer.network.PrinterAddress
import eloom.holybean.printer.network.rewritePrinterUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterUrlRewriterTest {

    @Test
    fun `rewrites host and port keeping path`() {
        val original = "http://holybean.invalid/print".toHttpUrl()
        val out = rewritePrinterUrl(original, PrinterAddress("192.168.0.27", 9100))
        assertEquals("192.168.0.27", out.host)
        assertEquals(9100, out.port)
        assertEquals("/print", out.encodedPath)
    }

    @Test
    fun `rewrites to custom port`() {
        val original = "http://holybean.invalid/health".toHttpUrl()
        val out = rewritePrinterUrl(original, PrinterAddress("10.0.0.5", 8080))
        assertEquals("10.0.0.5", out.host)
        assertEquals(8080, out.port)
        assertEquals("/health", out.encodedPath)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.printer.PrinterUrlRewriterTest"`
Expected: FAIL — `rewritePrinterUrl` 미존재.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// PrinterUrlRewriter.kt
package eloom.holybean.printer.network

import okhttp3.HttpUrl

/** 원본 요청 URL의 scheme/host/port를 Pi 주소로 치환. 경로/쿼리는 유지. */
fun rewritePrinterUrl(original: HttpUrl, address: PrinterAddress): HttpUrl =
    original.newBuilder()
        .scheme("http")
        .host(address.host)
        .port(address.port)
        .build()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.printer.PrinterUrlRewriterTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/printer/network/PrinterUrlRewriter.kt android/app/src/test/kotlin/eloom/holybean/printer/PrinterUrlRewriterTest.kt
git commit -m "feat(print): PrinterUrlRewriter(순수 URL 치환)"
```

---

## Task 6: `PrinterHostInterceptor` (OkHttp 인터셉터)

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/printer/network/PrinterHostInterceptor.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/printer/PrinterHostInterceptorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// PrinterHostInterceptorTest.kt
package eloom.holybean.printer

import eloom.holybean.printer.network.MdnsDiscovery
import eloom.holybean.printer.network.PrinterAddress
import eloom.holybean.printer.network.PrinterAddressResolver
import eloom.holybean.printer.network.FakePrinterAddressStore
import eloom.holybean.printer.network.PrinterHostInterceptor
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class PrinterHostInterceptorTest {

    private class FixedDiscovery(val addr: PrinterAddress?) : MdnsDiscovery {
        override suspend fun discover(timeoutMs: Long): PrinterAddress? = addr
    }

    private fun resolverWith(lastGood: String?): PrinterAddressResolver =
        PrinterAddressResolver(FakePrinterAddressStore(lastGood = lastGood), FixedDiscovery(null))

    private fun chainFor(request: Request, captured: (Request) -> Unit): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        val slot = slot<Request>()
        every { chain.request() } returns request
        every { chain.proceed(capture(slot)) } answers {
            captured(slot.captured)
            Response.Builder()
                .request(slot.captured).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").build()
        }
        return chain
    }

    @Test
    fun `rewrites request host to resolved address`() {
        val interceptor = PrinterHostInterceptor(resolverWith("192.168.0.27:9100"))
        val request = Request.Builder().url("http://holybean.invalid/print").build()
        var seen: Request? = null
        interceptor.intercept(chainFor(request) { seen = it })
        assertEquals("192.168.0.27", seen!!.url.host)
        assertEquals(9100, seen!!.url.port)
        assertEquals("/print", seen!!.url.encodedPath)
    }

    @Test
    fun `throws IOException when no address resolved`() {
        val interceptor = PrinterHostInterceptor(resolverWith(null))
        val request = Request.Builder().url("http://holybean.invalid/print").build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        assertThrows(IOException::class.java) { interceptor.intercept(chain) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.printer.PrinterHostInterceptorTest"`
Expected: FAIL — `PrinterHostInterceptor` 미존재.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// PrinterHostInterceptor.kt
package eloom.holybean.printer.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/**
 * 매 요청에서 리졸버 캐시의 host:port로 요청 URL을 치환한다.
 * 해석된 주소가 없으면 IOException → PiPrintClient가 ServerUnreachable로 매핑.
 */
class PrinterHostInterceptor @Inject constructor(
    private val resolver: PrinterAddressResolver,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val address = resolver.current()
            ?: throw IOException("printer address unresolved")
        val request = chain.request()
        val rewritten = request.newBuilder()
            .url(rewritePrinterUrl(request.url, address))
            .build()
        return chain.proceed(rewritten)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.printer.PrinterHostInterceptorTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/printer/network/PrinterHostInterceptor.kt android/app/src/test/kotlin/eloom/holybean/printer/PrinterHostInterceptorTest.kt
git commit -m "feat(print): PrinterHostInterceptor(요청 URL 동적 치환)"
```

---

## Task 7: DI 배선 (`PrintNetworkModule` + 인터페이스 바인딩)

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/di/PrintNetworkModule.kt`

이 Task는 프레임워크 배선이라 단위테스트 없이 컴파일/조립으로 검증한다.

- [ ] **Step 1: Replace `PrintNetworkModule.kt` 전체**

```kotlin
package eloom.holybean.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eloom.holybean.BuildConfig
import eloom.holybean.printer.network.FakePrintServerApi
import eloom.holybean.printer.network.MdnsDiscovery
import eloom.holybean.printer.network.NsdMdnsDiscovery
import eloom.holybean.printer.network.PrinterAddressStore
import eloom.holybean.printer.network.PrinterHostInterceptor
import eloom.holybean.printer.network.PrintServerApi
import eloom.holybean.printer.network.SharedPrefsPrinterAddressStore
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Pi 프린트 서버 전용 Retrofit. baseUrl은 더미이며 PrinterHostInterceptor가
 * 매 요청에서 런타임 해석된 host:port로 치환한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object PrintNetworkModule {

    /** 인터셉터가 host를 갈아끼우므로 baseUrl 자체는 더미 placeholder. */
    private const val PLACEHOLDER_BASE_URL = "http://holybean.invalid/"

    @Provides
    @Singleton
    @Named("PrintServer")
    fun providePrintServerOkHttp(
        interceptor: PrinterHostInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(interceptor)
            .build()

    @Provides
    @Singleton
    @Named("PrintServer")
    fun providePrintServerRetrofit(
        @Named("PrintServer") client: OkHttpClient,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun providePrintServerApi(
        @Named("PrintServer") retrofit: Retrofit,
    ): PrintServerApi =
        if (BuildConfig.DEBUG) {
            // debug 빌드는 실제 프린터를 호출하지 않는다(인터셉터/네트워크 미경유).
            FakePrintServerApi()
        } else {
            retrofit.create(PrintServerApi::class.java)
        }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class Bindings {
        @Binds
        @Singleton
        abstract fun bindPrinterAddressStore(impl: SharedPrefsPrinterAddressStore): PrinterAddressStore

        @Binds
        @Singleton
        abstract fun bindMdnsDiscovery(impl: NsdMdnsDiscovery): MdnsDiscovery
    }
}
```

- [ ] **Step 2: Verify assembleDebug + Hilt graph**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug` (from `android/`)
Expected: BUILD SUCCESSFUL (Hilt 컴파일 통과 = 그래프 충족).

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/di/PrintNetworkModule.kt
git commit -m "feat(print): 동적 host 인터셉터 배선 + Store/Discovery 바인딩"
```

---

## Task 8: `PiPrintClient` — IOException 시 1회 재탐색

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/printer/PiPrintClient.kt`
- Modify: `android/app/src/test/kotlin/eloom/holybean/printer/PiPrintClientTest.kt`

- [ ] **Step 1: Add the failing test + fix existing constructors**

`PiPrintClientTest.kt` 상단의 import에 추가:
```kotlin
import eloom.holybean.printer.network.PrinterAddressResolver
import io.mockk.coVerify
```
(이미 `coVerify` import가 있으면 중복 추가하지 말 것.)

모든 기존 `PiPrintClient(api, StandardTestDispatcher(testScheduler))` 호출을 다음으로 교체(생성자에 resolver 추가됨):
```kotlin
val resolver = mockk<PrinterAddressResolver>(relaxed = true)
val client = PiPrintClient(api, resolver, StandardTestDispatcher(testScheduler))
```
각 테스트 함수 시작부에 `resolver`를 만들고 client 생성에 넘긴다. (relaxed mock이라 `rediscover()`는 no-op으로 동작.)

새 테스트 추가:
```kotlin
@Test
fun `rediscovers once on unreachable then retries`() = runTest {
    val resolver = mockk<PrinterAddressResolver>(relaxed = true)
    val client = PiPrintClient(api, resolver, StandardTestDispatcher(testScheduler))
    coEvery { api.print(any()) } throws IOException("refused") andThen Response.success(Unit)
    client.print(emptyList())
    advanceUntilIdle()
    coVerify(exactly = 1) { resolver.rediscover() }
    coVerify(exactly = 2) { api.print(any<PrintRequestDto>()) }
}

@Test
fun `does not rediscover on http error (only on IOException)`() = runTest {
    val resolver = mockk<PrinterAddressResolver>(relaxed = true)
    val client = PiPrintClient(api, resolver, StandardTestDispatcher(testScheduler))
    coEvery { api.print(any()) } returns Response.error(503, ResponseBody.create(null, ""))
    runCatching { client.print(emptyList()) }
    advanceUntilIdle()
    coVerify(exactly = 0) { resolver.rediscover() }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.printer.PiPrintClientTest"`
Expected: FAIL — 생성자 인자 불일치(compile error) 또는 `rediscover` 미호출.

- [ ] **Step 3: Modify `PiPrintClient.kt`**

생성자에 resolver 주입(추가):
```kotlin
import eloom.holybean.printer.network.PrinterAddressResolver
```
```kotlin
@Singleton
class PiPrintClient @Inject constructor(
    private val api: PrintServerApi,
    private val resolver: PrinterAddressResolver,
    @PrinterDispatcher private val printerDispatcher: CoroutineDispatcher,
) {
```

`print()`의 `mutex.withLock { withRetry { ... } }` 블록을 다음으로 교체(IOException catch에서 1회 재탐색):
```kotlin
    suspend fun print(commands: List<PrintCommandDto>) = withContext(printerDispatcher) {
        mutex.withLock {
            var rediscovered = false
            withRetry {
                val response = try {
                    api.print(PrintRequestDto(commands))
                } catch (e: java.io.IOException) {
                    if (!rediscovered) {
                        rediscovered = true
                        resolver.rediscover()   // 다음 시도에서 인터셉터가 새 주소 사용
                    }
                    throw PrintServerException(
                        PrintFailureReason.ServerUnreachable,
                        "print server unreachable",
                        e,
                    )
                }
                if (!response.isSuccessful) {
                    val reason = when (response.code()) {
                        503 -> PrintFailureReason.PrinterOffline
                        500 -> PrintFailureReason.PrinterError
                        else -> PrintFailureReason.Unknown
                    }
                    throw PrintServerException(reason, "print server returned HTTP ${response.code()}")
                }
            }
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.printer.PiPrintClientTest"`
Expected: PASS (기존 8 + 신규 2 = 10 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/printer/PiPrintClient.kt android/app/src/test/kotlin/eloom/holybean/printer/PiPrintClientTest.kt
git commit -m "feat(print): 연결 실패 시 1회 mDNS 재탐색 후 재시도"
```

---

## Task 9: `PRINT_SERVER_URL` 빌드 고정값 제거 + DevTools 기본값 정리

**Files:**
- Modify: `android/app/build.gradle.kts:30`
- Modify: `android/app/src/main/java/eloom/holybean/ui/settings/DevToolsViewModel.kt`

DevToolsViewModel은 Task 10에서 본격 수정하지만, 여기서는 컴파일을 깨는 `BuildConfig.PRINT_SERVER_URL` 참조만 먼저 제거한다.

- [ ] **Step 1: build.gradle.kts에서 라인 삭제**

`android/app/build.gradle.kts`의 다음 라인을 삭제:
```kotlin
        buildConfigField("String", "PRINT_SERVER_URL", "\"http://192.168.4.1:9100/\"")
```

- [ ] **Step 2: DevToolsViewModel의 State에서 printerUrl 기본값 변경**

`DevToolsViewModel.kt`에서:
```kotlin
        val printerUrl: String = BuildConfig.PRINT_SERVER_URL,
```
를 다음으로 교체:
```kotlin
        val printerStatusText: String = "확인 전",
```
그리고 파일 상단의 사용되지 않게 된 import 제거:
```kotlin
import eloom.holybean.BuildConfig
```
(BuildConfig가 이 파일에서 더 이상 쓰이지 않으면 제거. 다른 곳에서 쓰이면 유지.)

- [ ] **Step 3: Verify compilation**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:compileDebugKotlin` (from `android/`)
Expected: BUILD SUCCESSFUL. (만약 `printerUrl`을 참조하는 Compose 화면이 있으면 다음 Task 10/11에서 함께 갱신되므로, 이 단계에서 화면 참조 컴파일 에러가 나면 해당 참조를 `printerStatusText`로 임시 치환.)

- [ ] **Step 4: Commit**

```bash
git add android/app/build.gradle.kts android/app/src/main/java/eloom/holybean/ui/settings/DevToolsViewModel.kt
git commit -m "refactor(print): PRINT_SERVER_URL 빌드 고정값 제거"
```

---

## Task 10: `DevToolsViewModel` — 프린터 연결 상태/재탐색/수동입력

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/settings/DevToolsViewModel.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/ui/settings/DevToolsViewModelTest.kt` (없으면 생성)

- [ ] **Step 1: Write the failing test**

```kotlin
// DevToolsViewModelTest.kt (해당 동작만 신규 검증)
package eloom.holybean.ui.settings

import eloom.holybean.printer.network.PrinterAddressResolver
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class DevToolsViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `rescanPrinter delegates to resolver rediscover`() = runTest {
        val resolver = mockk<PrinterAddressResolver>(relaxed = true)
        val vm = DevToolsViewModel(
            piPrintClient = mockk(relaxed = true),
            firestoreRepository = mockk(relaxed = true),
            networkStatusProvider = mockk(relaxed = true),
            printerAddressResolver = resolver,
        )
        vm.rescanPrinter()
        advanceUntilIdle()
        coVerify(exactly = 1) { resolver.rediscover() }
    }

    @Test
    fun `setPrinterOverride delegates to resolver`() = runTest {
        val resolver = mockk<PrinterAddressResolver>(relaxed = true)
        val vm = DevToolsViewModel(
            piPrintClient = mockk(relaxed = true),
            firestoreRepository = mockk(relaxed = true),
            networkStatusProvider = mockk(relaxed = true),
            printerAddressResolver = resolver,
        )
        vm.setPrinterOverride("10.0.0.9:9100")
        advanceUntilIdle()
        coVerify(exactly = 1) { resolver.setManualOverride("10.0.0.9:9100") }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.settings.DevToolsViewModelTest"`
Expected: FAIL — 생성자 인자/메서드 미존재.

- [ ] **Step 3: Modify `DevToolsViewModel.kt`**

import 추가:
```kotlin
import eloom.holybean.printer.network.PrinterAddressResolver
import eloom.holybean.printer.network.PrinterStatus
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
```
생성자에 resolver 추가:
```kotlin
@HiltViewModel
class DevToolsViewModel @Inject constructor(
    private val piPrintClient: PiPrintClient,
    private val firestoreRepository: FirestoreRepository,
    private val networkStatusProvider: NetworkStatusProvider,
    private val printerAddressResolver: PrinterAddressResolver,
) : ViewModel() {
```
State에 표시 텍스트 유지(Task 9에서 `printerStatusText` 추가됨). init에서 status 구독:
```kotlin
    init {
        printerAddressResolver.status
            .onEach { status -> _uiState.update { it.copy(printerStatusText = status.toDisplay()) } }
            .launchIn(viewModelScope)
    }

    private fun PrinterStatus.toDisplay(): String = when (this) {
        is PrinterStatus.Connected -> "연결됨 ${address.toAuthority()}"
        PrinterStatus.Resolving -> "탐색 중…"
        PrinterStatus.Unreachable -> "연결 안 됨"
        PrinterStatus.Unknown -> "확인 전"
    }

    fun rescanPrinter() {
        viewModelScope.launchSafely(onError = {
            _uiEvent.tryEmit(DevToolsUiEvent.ShowToast("프린터 탐색 실패"))
        }) {
            printerAddressResolver.rediscover()
        }
    }

    fun setPrinterOverride(value: String?) {
        viewModelScope.launchSafely(onError = {
            _uiEvent.tryEmit(DevToolsUiEvent.ShowToast("주소 저장 실패"))
        }) {
            printerAddressResolver.setManualOverride(value?.takeIf { it.isNotBlank() })
            _uiEvent.tryEmit(DevToolsUiEvent.ShowToast("프린터 주소를 저장했습니다"))
        }
    }
```
(`setManualOverride`는 suspend 아님 — `launchSafely` 안에서 호출해도 무방하나, 일관성과 향후 IO 대비 코루틴 컨텍스트 유지.)

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.settings.DevToolsViewModelTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/settings/DevToolsViewModel.kt android/app/src/test/kotlin/eloom/holybean/ui/settings/DevToolsViewModelTest.kt
git commit -m "feat(print): DevTools에 프린터 상태/재탐색/수동주소 노출"
```

---

## Task 11: DevTools 화면에 "프린터 연결" UI

**Files:**
- Modify: DevTools Compose 화면 (구현 시점에 확인: `find android/app/src/main -iname "*DevTools*Screen*" -o -iname "*DevTools*.kt"` 로 경로 확정)

Compose UI는 단위테스트 대신 수동/조립 검증(Task 14). 기존 화면의 진단 섹션 패턴(`uiState`, `viewModel.refresh()`, `viewModel.testPrint()`)을 그대로 따른다.

- [ ] **Step 1: 화면 파일 경로 확정**

Run: `grep -rln "DevToolsViewModel\|fun testPrint\|viewModel.refresh" android/app/src/main` (from repo root)
DevTools 화면 Composable 파일을 연다.

- [ ] **Step 2: "프린터 연결" 섹션 추가**

진단 섹션 인근에 다음 Composable 블록 추가(상태 텍스트 + 재탐색 + 수동 입력). 기존 화면의 Text/Button/OutlinedTextField 스타일을 그대로 차용:
```kotlin
// 프린터 연결
Text(text = "프린터 연결: ${uiState.printerStatusText}")

Button(onClick = { viewModel.rescanPrinter() }) {
    Text("다시 탐색")
}

var manual by remember { mutableStateOf("") }
OutlinedTextField(
    value = manual,
    onValueChange = { manual = it },
    label = { Text("수동 주소 (예: 192.168.0.27 또는 192.168.0.27:9100)") },
    singleLine = true,
)
Row {
    Button(onClick = { viewModel.setPrinterOverride(manual) }) { Text("저장") }
    Spacer(Modifier.width(8.dp))
    OutlinedButton(onClick = { manual = ""; viewModel.setPrinterOverride(null) }) { Text("해제") }
}
```
(import: `androidx.compose.runtime.*`, `androidx.compose.foundation.layout.*`, `androidx.compose.material3.*` — 화면에 이미 대부분 존재. 누락분만 추가.)

만약 Task 9에서 기존 화면이 `uiState.printerUrl`을 참조해 임시 치환했다면, 여기서 `uiState.printerStatusText` 기반 UI로 정리.

- [ ] **Step 3: Verify assembleDebug**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug` (from `android/`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main
git commit -m "feat(print): DevTools 화면에 프린터 연결 상태/재탐색/수동입력 UI"
```

---

## Task 12: `StartupViewModel` — 시작 시 비차단 워밍업

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/startup/StartupViewModel.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/ui/startup/StartupViewModelTest.kt` (없으면 생성)

시작 시 프린터 health 체크 전에 `resolver.rediscover()`를 1회 호출해 캐시를 데운다(첫 인쇄 지연 최소화). 데이터 단계는 막지 않는다.

- [ ] **Step 1: Write the failing test**

```kotlin
// StartupViewModelTest.kt (워밍업 동작만 검증)
package eloom.holybean.ui.startup

import eloom.holybean.printer.network.PrinterAddressResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class StartupViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `warms up printer address on init before health check`() = runTest {
        val resolver = mockk<PrinterAddressResolver>(relaxed = true)
        val client = mockk<eloom.holybean.printer.PiPrintClient>(relaxed = true)
        coEvery { client.checkHealth() } returns true
        StartupViewModel(
            menuRepository = mockk(relaxed = true),
            firestoreRepository = mockk(relaxed = true),
            piPrintClient = client,
            printerAddressResolver = resolver,
        )
        advanceUntilIdle()
        coVerify(atLeast = 1) { resolver.rediscover() }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.startup.StartupViewModelTest"`
Expected: FAIL — 생성자 인자 미존재.

- [ ] **Step 3: Modify `StartupViewModel.kt`**

import 추가:
```kotlin
import eloom.holybean.printer.network.PrinterAddressResolver
```
생성자에 resolver 추가:
```kotlin
@HiltViewModel
class StartupViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
    private val firestoreRepository: FirestoreRepository,
    private val piPrintClient: PiPrintClient,
    private val printerAddressResolver: PrinterAddressResolver,
) : ViewModel() {
```
`check()`의 프린터 단계 코루틴 시작부에서 health 체크 전에 워밍업:
```kotlin
        viewModelScope.launchSafely(onError = {
            _uiState.update { it.copy(printer = StepStatus.Failed) }
        }) {
            printerAddressResolver.rediscover()              // 캐시 워밍업(실패해도 health가 판정)
            val ok = piPrintClient.checkHealth()
            _uiState.update { it.copy(printer = if (ok) StepStatus.Success else StepStatus.Failed) }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.startup.StartupViewModelTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/startup/StartupViewModel.kt android/app/src/test/kotlin/eloom/holybean/ui/startup/StartupViewModelTest.kt
git commit -m "feat(print): 시작 시 프린터 주소 비차단 워밍업"
```

---

## Task 13: Pi 측 — avahi mDNS 광고 + 셋업 문서

**Files:**
- Create: `pi/deploy/holybean-print.service`
- Create: `pi/deploy/README.md`

Pi 측은 호스트 OS 설정이라 자동 테스트 대상이 아니다. 파일 작성 + 문서화로 완료하고 실제 검증은 Task 14 수동 항목.

- [ ] **Step 1: Create avahi 서비스 정의**

```xml
<!-- pi/deploy/holybean-print.service
     설치 위치: /etc/avahi/services/holybean-print.service
     avahi-daemon이 _holybean-print._tcp 서비스를 9100 포트로 광고한다. -->
<?xml version="1.0" standalone='no'?>
<!DOCTYPE service-group SYSTEM "avahi-service.dtd">
<service-group>
  <name replace-wildcards="yes">HolyBean Printer on %h</name>
  <service>
    <type>_holybean-print._tcp</type>
    <port>9100</port>
  </service>
</service-group>
```

- [ ] **Step 2: Create 셋업 문서**

```markdown
<!-- pi/deploy/README.md -->
# Pi 인쇄서버 배포 (공유기 이더넷 + mDNS)

Pi는 핫스팟/NAT 없이 **공유기에 이더넷으로 연결된 순수 인쇄서버**로 동작한다.
앱은 mDNS(`_holybean-print._tcp`)로 Pi를 자동 발견하므로 Pi IP가 바뀌어도 무방하다.

## 1. 네트워크
- Pi를 공유기에 **이더넷으로 연결**한다. (Wi-Fi 핫스팟/AP 구성 불필요)
- 태블릿은 같은 공유기 Wi-Fi에 접속한다(Pi와 동일 서브넷).
- **공유기에서 Client/AP Isolation(단말 격리)을 반드시 OFF** — 켜져 있으면 태블릿이 Pi에 도달하지 못한다.
- 권장(선택): 공유기 DHCP에서 Pi의 MAC에 **고정 IP 예약**. mDNS가 막힌 환경에서도 안정적이며, 앱의 lastGood 캐시가 영구 유효해진다.

## 2. 핫스팟/NAT 은퇴
기존 구성에서 다음을 비활성화/제거한다:
```bash
sudo systemctl disable --now hostapd dnsmasq
sudo iptables -t nat -F        # 기존 NAT 규칙 제거 (영속 규칙 파일도 정리)
```

## 3. mDNS 광고(avahi)
```bash
sudo apt-get install -y avahi-daemon
sudo cp holybean-print.service /etc/avahi/services/holybean-print.service
sudo systemctl enable --now avahi-daemon
sudo systemctl restart avahi-daemon
```

## 4. 인쇄서버 실행
서버는 기본 `0.0.0.0:9100`에 바인딩한다(`HOLYBEAN_PRINT_BIND`로 변경 가능, `pi/src/config.rs`).
포트를 바꾸면 `holybean-print.service`의 `<port>`도 같은 값으로 맞춘다.

## 5. 검증
```bash
# 다른 mac/리눅스에서 광고 확인
avahi-browse -rt _holybean-print._tcp
# health 체크
curl http://<pi-ip>:9100/health
```
앱: 설정 → 개발자 도구 → "프린터 연결"에서 "다시 탐색" → `연결됨 <ip>:9100` 확인.
mDNS가 안 잡히면 같은 화면의 수동 주소 입력으로 `<pi-ip>` 저장.
```

- [ ] **Step 3: Commit**

```bash
git add pi/deploy/holybean-print.service pi/deploy/README.md
git commit -m "feat(pi): avahi mDNS 광고 + 공유기 이더넷 셋업 문서(핫스팟/NAT 은퇴)"
```

---

## Task 14: 전체 빌드 + 테스트 그린 + 수동 검증 항목

**Files:** 없음(검증 전용)

- [ ] **Step 1: 전체 단위 테스트**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest` (from `android/`)
Expected: PASS — 기존 테스트 + 신규 테스트 전부 그린(기존 109 유지 + 신규 약 22).

- [ ] **Step 2: Debug 조립**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Pi Rust 회귀(변경 없음 확인)**

Run: `cd pi && cargo test && cargo clippy --all-targets -- -D warnings`
Expected: 기존 32 tests PASS, clippy clean (Pi 코드 변경 없음 — 문서/서비스 파일만 추가).

- [ ] **Step 4: 수동 검증 (실기기 + 공유기 + Pi, release 빌드)**

다음을 사람이 확인하고 체크:
- [ ] Pi를 공유기 이더넷에 연결, avahi 광고 동작(`avahi-browse -rt _holybean-print._tcp`).
- [ ] 태블릿(같은 공유기 Wi-Fi)에서 앱 시작 → 시작 화면 프린터 단계 Success.
- [ ] 주문 영수증 정상 출력.
- [ ] Pi 재부팅으로 IP 변경 후 인쇄 시도 → 자동 재탐색·복구 후 출력 성공.
- [ ] 설정에서 수동 주소 입력 → 해당 주소로 출력(override 우선) 확인.
- [ ] avahi 중지 상태에서 lastGood 캐시로 평상시 인쇄 지속 확인.

- [ ] **Step 5: Final commit (필요 시)**

수동 검증 중 수정이 없었다면 추가 커밋 불필요.

---

## Self-Review 결과

- **Spec 커버리지:** §2 결정(하이브리드/순수인쇄서버/머지드캐시/방식A) → Task 1~8. §3 Pi측 → Task 13. §4 컴포넌트 6종 → Task 1~7. §5 UX(설정 UI/워밍업) → Task 10~12. §6 DEBUG 무영향 → Task 7(FakePrintServerApi 유지). §7 테스트 → 각 Task TDD + Task 14. 누락 없음.
- **타입 일관성:** `PrinterAddress(host,port)`, `parse/toAuthority`, `PrinterAddressResolver.{current,rediscover,setManualOverride,status}`, `PrinterStatus.{Unknown,Resolving,Connected,Unreachable}`, `PrinterAddressStore.{override,lastGood}`, `rewritePrinterUrl`, `PrinterHostInterceptor`가 모든 Task에서 동일 시그니처로 사용됨.
- **플레이스홀더:** 없음. Compose 화면 경로만 Task 11에서 grep으로 확정(기존 파일 위치가 환경에 따라 다를 수 있어 명령으로 특정).
