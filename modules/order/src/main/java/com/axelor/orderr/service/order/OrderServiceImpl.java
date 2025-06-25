package com.axelor.orderr.service.order;

import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.order.db.Dish;
import com.axelor.order.db.Orderr;
import com.axelor.order.db.repo.OrderrRepository;
import com.axelor.orderr.service.dish.DishService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public List<Orderr> getActiveOrders() {
        return orderRepository.all()
                .filter("self.isActive = true")
                .fetch();
    }

    @Override
    public List<Orderr> getOrderById(User user) {
        return orderRepository.all().filter("self.user.tg_id = ?", user.getTg_id()).fetch();
    }

    @Override
    public Map<User, BigDecimal> getLastMonthReport() {
        LocalDate now = LocalDate.now();

        LocalDate reportTo = now.withDayOfMonth(1);
        LocalDate reportFrom = reportTo.minusMonths(1);

        LocalDateTime fromDateTime = reportFrom.atStartOfDay();
        LocalDateTime toDateTime = reportTo.atStartOfDay();

        System.out.println("Отчет за месяц: от " + fromDateTime + " по " + toDateTime);

        List<Orderr> orders = orderRepository.all()
                .filter("self.createdOn >= ?1 AND self.createdOn < ?2", fromDateTime, toDateTime)
                .fetch();

        Map<User, BigDecimal> report = new LinkedHashMap<>();
        for (Orderr order : orders) {
            BigDecimal price = null;

            User user = order.getUser();
            if (order.getPortion_size().equals("большая")) {
                price = BigDecimal.valueOf(180);
            } else if (order.getPortion_size().equals("маленькая")) {
                price = BigDecimal.valueOf(140);
            }
            if (price != null) {
                report.merge(user, price, BigDecimal::add);
            }
        }
        return report;
    }


    @Override
    @Transactional(rollbackOn = {Exception.class})
    public Orderr makeOrder(User user, Dish dish, String portionSize) {
        User managedUser = userRepository.merge(user);
        Dish managedDish = dishService.getDishById(dish.getId());
        Orderr order = new Orderr();
        order.setIsActive(true);
        order.setUser(managedUser);
        order.setDish(managedDish);
        order.setPortion_size(portionSize);

        orderRepository.save(order);
        return order;
    }

    @Override
    @Transactional(rollbackOn = {Exception.class})
    public void deactivateAllOrders() {
        orderRepository.all()
                .filter("self.isActive = true")
                .fetch()
                .forEach(order -> {
                    order.setIsActive(false);
                    orderRepository.save(order);
                });
    }
}
