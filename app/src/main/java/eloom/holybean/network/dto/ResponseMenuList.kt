package eloom.holybean.network.dto

import eloom.holybean.data.model.MenuItem

data class ResponseMenuList(
    val timestamp: String,
    val menulist: List<MenuItem>
)
