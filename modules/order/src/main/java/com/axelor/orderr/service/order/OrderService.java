package com.axelor.orderr.service.order;

import com.axelor.auth.db.User;
import com.axelor.order.db.Dish;
import com.axelor.order.db.Orderr;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface OrderService {
    List<Orderr> getOrderList();
    List<Orderr> getActiveOrders();
    List<Orderr> getOrderById(User user);
    Map<User, BigDecimal> getLastMonthReport();
    Orderr makeOrder(User user, Dish dish, String portionSize);
    void deactivateAllOrders();
}