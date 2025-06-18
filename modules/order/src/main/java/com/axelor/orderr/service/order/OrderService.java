package com.axelor.orderr.service.order;

import com.axelor.auth.db.User;
import com.axelor.order.db.Dish;
import com.axelor.order.db.Orderr;

import java.util.List;

public interface OrderService {
    List<Orderr> getOrderList();
    List<Orderr> getOrderById(User user);
    Orderr makeOrder(User user, Dish dish, String portionSize);
    void clearAllOrders();
}
