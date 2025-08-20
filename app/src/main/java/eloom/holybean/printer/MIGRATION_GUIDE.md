# Printer System Migration Guide

## Overview

The HolyBean printer system has been completely refactored to improve efficiency, stability, and maintainability. This guide explains the final architecture after completing the refactoring.

## Key Changes

### 1. Eliminated Utility Classes
- **Before**: `HomePrinter`, `OrdersPrinter`, `ReportPrinter` utility objects handled text formatting
- **After**: Formatting logic moved directly into respective ViewModels for better cohesion

### 2. Centralized Connection Management
- **Before**: Each printer instance created its own `EscPosPrinter` connection
- **After**: Single `PrinterManager` singleton manages one shared connection

### 3. Enhanced Error Handling
- **Before**: Basic print/disconnect with limited error handling
- **After**: Automatic retry logic, reconnection, and detailed error reporting with `PrintResult` sealed class

## Migration Steps

### Step 1: Update Imports
```kotlin
// Remove these imports (no longer needed)
import eloom.holybean.printer.HomePrinter
import eloom.holybean.printer.OrdersPrinter
import eloom.holybean.printer.ReportPrinter
import eloom.holybean.printer.PrinterHelper

// Add these imports
import eloom.holybean.printer.PrinterManager
import eloom.holybean.printer.PrintResult
import eloom.holybean.printer.PrinterState
```

### Step 2: Inject Dependencies
```kotlin
// In your ViewModel - inject PrinterManager directly
@Inject
lateinit var printerManager: PrinterManager
```

### Step 3: Update Printing Code

#### Before (Old System):
```kotlin
// Old way - creating new instance each time
val homePrinter = HomePrinter()
val receiptText = homePrinter.receiptTextForCustomer(order)
homePrinter.print(receiptText)
homePrinter.disconnect()
```

#### After (New System):
```kotlin
// Formatting and printing are now done in the ViewModel
private fun formatReceiptTextForCustomer(data: Order): String {
    var result = "[C]=====================================\n"
    result += "[L]\n"
    result += "[C]<u><font size='big'>주문번호 : ${data.orderNum}</font></u>\n"
    // ... rest of formatting logic
    return result
}

private suspend fun printReceipt(data: Order, takeOption: String) {
    val customerReceiptText = formatReceiptTextForCustomer(data)
    when (val result = printerManager.print(customerReceiptText)) {
        is PrintResult.Success -> {
            // Print successful
            _uiEvent.emit(UiEvent.ShowToast("인쇄 완료"))
        }
        is PrintResult.Failure -> {
            // Handle failure with error message
            _uiEvent.emit(UiEvent.ShowToast("프린터 연결을 확인해주세요"))
        }
    }
}
```

### Step 4: Monitor Printer Status (Optional)
```kotlin
// Observe printer connection state in real-time
printerManager.printerState.collect { state ->
    when (state) {
        PrinterState.CONNECTED -> updatePrinterIcon(R.drawable.printer_connected)
        PrinterState.DISCONNECTED -> updatePrinterIcon(R.drawable.printer_disconnected)
        PrinterState.CONNECTING -> updatePrinterIcon(R.drawable.printer_connecting)
        PrinterState.ERROR -> updatePrinterIcon(R.drawable.printer_error)
    }
}
```

## Best Practices

### 1. Keep Formatting Logic in ViewModels
Each ViewModel now contains its own formatting methods for better cohesion:
- `HomeViewModel`: `formatReceiptTextForCustomer()`, `formatReceiptTextForPOS()`
- `OrdersViewModel`: `formatReprintText()`
- `ReportViewModel`: `formatReportText()`

### 2. Handle Print Results Properly
Always check the `PrintResult` to handle failures gracefully:
```kotlin
when (val result = printerManager.print(formattedText)) {
    is PrintResult.Success -> {
        _uiEvent.emit(UiEvent.ShowToast("인쇄 완료"))
    }
    is PrintResult.Failure -> {
        _uiEvent.emit(UiEvent.ShowToast("프린터 연결을 확인해주세요: ${result.errorMessage}"))
    }
}
```

### 3. Monitor Connection State in UI
Update your UI to reflect the current printer connection status:
```kotlin
// In your Fragment/Activity
lifecycleScope.launch {
    printerManager.printerState.collect { state ->
        updatePrinterStatusUI(state)
    }
}
```

### 4. No Manual Disconnection Required
The new system manages connections automatically. You don't need to call `disconnect()` after each print operation.

## Benefits of New System

1. **Performance**: Single connection eliminates repeated bluetooth pairing overhead
2. **Reliability**: Automatic retry and reconnection logic handles connection issues
3. **Maintainability**: Clear separation between formatting and printing logic
4. **Monitoring**: Real-time connection status updates
5. **Error Handling**: Detailed error reporting and recovery mechanisms

## Troubleshooting

### Connection Issues
```kotlin
// Force reconnection if having connection problems
printerHelper.forceReconnect()

// Check current state
val currentState = printerHelper.getPrinterState()
Log.d("Printer", "Current printer state: $currentState")
```

### Testing Connection
The system automatically tests connections before printing, but you can monitor the status:
```kotlin
printerManager.printerState.collect { state ->
    Log.d("Printer", "Printer state changed to: $state")
}
```
