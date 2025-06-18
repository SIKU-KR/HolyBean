package eloom.holybean.interfaces

import eloom.holybean.data.model.Order

interface OrderDialogListener {
    fun onOrderConfirmed(data: Order, takeOption: String)
}