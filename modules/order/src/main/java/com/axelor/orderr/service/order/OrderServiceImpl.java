package com.axelor.orderr.service.order;

import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.order.db.Dish;
import com.axelor.order.db.Orderr;
import com.axelor.order.db.repo.OrderrRepository;
import com.axelor.orderr.service.dish.DishService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import java.util.List;

public class OrderServiceImpl implements OrderService{

    private final OrderrRepository orderRepository;
    private final UserRepository userRepository;
    private final DishService dishService;

    @Inject
    public OrderServiceImpl(OrderrRepository orderRepository, UserRepository userRepository, DishService dishService) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.dishService = dishService;
    }

    @Override
    public List<Orderr> getOrderList() {
        return orderRepository.all().fetch();
    }

    @Override
    public List<Orderr> getOrderById(User user) {
        return orderRepository.all().filter("self.user.tg_id = ?", user.getTg_id()).fetch();
    }

    @Override
    @Transactional(rollbackOn = {Exception.class})
    public Orderr makeOrder(User user, Dish dish, String portionSize) {
        User managedUser = userRepository.merge(user);
        Dish managedDish = dishService.getDishById(dish.getId());
        Orderr order = new Orderr();
        order.setUser(managedUser);
        order.setDish(managedDish);
        order.setPortion_size(portionSize);

        orderRepository.save(order);
        return order;
    }

    @Override
    @Transactional(rollbackOn = {Exception.class})
    public void clearAllOrders() {
        orderRepository.all().remove();
    }
}
