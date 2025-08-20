# Printer System Migration Guide

## Overview

The HolyBean printer system has been completely refactored to improve efficiency, stability, and maintainability. This guide explains how to migrate existing code to use the new printer system.

## Key Changes

### 1. Eliminated Inheritance-Based Structure
- **Before**: `HomePrinter`, `OrdersPrinter`, `ReportPrinter` extended `Printer` class
- **After**: Utility objects that only handle text formatting

### 2. Centralized Connection Management
- **Before**: Each printer instance created its own `EscPosPrinter` connection
- **After**: Single `PrinterManager` singleton manages one shared connection

### 3. Enhanced Error Handling
- **Before**: Basic print/disconnect with limited error handling
- **After**: Automatic retry logic, reconnection, and detailed error reporting

## Migration Steps

### Step 1: Update Imports
```kotlin
// Remove these imports
import eloom.holybean.printer.HomePrinter
import eloom.holybean.printer.OrdersPrinter
import eloom.holybean.printer.ReportPrinter

// Add these imports
import eloom.holybean.printer.PrinterManager
import eloom.holybean.printer.PrinterHelper
import eloom.holybean.printer.PrintResult
import eloom.holybean.printer.PrinterState
```

### Step 2: Inject Dependencies
```kotlin
// In your ViewModel or Fragment
@Inject
lateinit var printerHelper: PrinterHelper

// Or inject PrinterManager directly if you need more control
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
// Method 1: Using PrinterHelper (Recommended)
val success = printerHelper.printCustomerReceipt(order)
if (!success) {
    // Handle print failure
    showPrintErrorDialog()
}

// Method 2: Using PrinterManager directly
val receiptText = HomePrinter.receiptTextForCustomer(order)
when (val result = printerManager.print(receiptText)) {
    is PrintResult.Success -> {
        // Print successful
        showPrintSuccessMessage()
    }
    is PrintResult.Failure -> {
        // Handle failure with error message
        showPrintErrorDialog(result.errorMessage)
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

### 1. Use PrinterHelper for Common Operations
The `PrinterHelper` class provides convenient methods for all common printing operations:
- `printCustomerReceipt(order)`
- `printPOSReceipt(order, option)`
- `printOrderReprint(orderNum, basketList)`
- `printSalesReport(reportData)`

### 2. Handle Print Results Properly
Always check the return value or `PrintResult` to handle failures gracefully:
```kotlin
val success = printerHelper.printCustomerReceipt(order)
if (!success) {
    // Show user-friendly error message
    Toast.makeText(context, "프린터 연결을 확인해주세요", Toast.LENGTH_SHORT).show()
    
    // Optionally try to reconnect
    printerHelper.forceReconnect()
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
