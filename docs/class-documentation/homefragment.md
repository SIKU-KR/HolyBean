# HomeFragment()

**Class Design Document**

**Class Name:** HomeFragment

**Purpose:** This class is responsible for constructing the home screen, displaying menus, and processing orders.

**Properties:**

1. `mainListener: MainActivityListener?`: Listener object to communicate with MainActivity class.
2. `menuBoard: RecyclerView`: RecyclerView to display menus.
3. `menuTab: TabLayout`: Tab layout to select menu categories.
4. `basket: RecyclerView`: RecyclerView to display the basket.
5. `itemList: ArrayList<MenuItem>`: List of all menu items.
6. `basketList: ArrayList<BasketItem>`: List of items in the basket.
7. `orderId: Int`: Current order number.
8. `totalPrice: Int`: Total price of current order.

**Methods:**

1. `onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?`: Initialize the view and properties.
2. `initMenuBoard()`: Method to initialize the menuBoard.
3. `initTabs()`: Method to initialize menuTab.
4. `initBasket()`: Method to initialize the basket.
5. `updateRecyclerViewForCategory(category: Int)`: Method to update the menu RecyclerView based on the selected category(menuTab).
6. `addToBasket(id: Int)`: Method to add a menu item to the basket.
7. `deleteFromBasket(id: Int)`: Method to delete a menu item from the basket.
8. `getTotal(): Int`: Method to calculate and return the total order price.
9. `updateTotal()`: Method to update the total order price.
10. `searchMenuItem(menuItems: ArrayList<MenuItem>, itemId: Int): MenuItem?`: Method to perform binary search for a menu item.
11. `addCouponListener()`: OnClickListener method to display a dialog for adding a coupon.
12. `printReceipt(takeOption: String, orderMethod: String, ordererName: String)`: Method to print the receipt.
13. `receiptTextForCustomer(): String`: Method to generate text for the customer's receipt.
14. `receiptTextForPOS(takeOption: String, orderMethod: String, ordererName: String, date: String): String`: Method to generate text for the POS receipt.
15. `onAttach(context: Context)`: Method to initialize mainListener when fragment attaches to Activity.
16. `onDetach()`: Method to detach the main listener.
17. `onOrderConfirmed(takeOption: String, ordererName: String, orderMethod: String)`: Method to process order with a single payment method (Overloaded).
18. `onOrderConfirmed(takeOption: String, ordererName: String, firstMethod: String, secondMethod: String, firstAmount: Int, secondAmount: Int)`: Method to process order with a dual payment method (Overloaded).