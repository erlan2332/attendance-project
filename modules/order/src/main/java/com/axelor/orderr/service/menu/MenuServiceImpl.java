package com.axelor.orderr.service.menu;

import com.axelor.order.db.Dish;
import com.axelor.order.db.Menu;
import com.axelor.order.db.repo.DishRepository;
import com.axelor.order.db.repo.MenuRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.hibernate.Hibernate;

import java.util.List;

public class MenuServiceImpl implements MenuService{

    private final MenuRepository menuRepository;
    private final DishRepository dishRepository;

    @Inject
    public MenuServiceImpl(MenuRepository menuRepository, DishRepository dishRepository) {
        this.menuRepository = menuRepository;
        this.dishRepository = dishRepository;
    }

    @Override
    @Transactional(rollbackOn = {Exception.class})
    public Menu getTomorrowMenu() {
        Menu menu = menuRepository.all().fetchOne();
        if (menu == null) {
            menu = new Menu();
            menuRepository.save(menu);
        }
        Hibernate.initialize(menu.getDishes());
        return menu;
    }

    @Override
    @Transactional(rollbackOn = {Exception.class})
    public Menu addDishToTomorrowMenu(Long dishId) {
        Menu menu = getTomorrowMenu();
        Dish dish = dishRepository.find(dishId);
        if (dish != null) {
            System.out.println("Dish before: " + dish + ", current menu: " + dish.getMenu());
            dish.setMenu(menu);
            dishRepository.save(dish);
            System.out.println("MenuServiceImpl. Добавлено блюдо: " + dish.getMenu());
        } else {
            System.out.println("Dish with id " + dishId + " not found");
        }

        return menu;
    }

    @Override
    @Transactional(rollbackOn = {Exception.class})
    public Menu removeDishFromTomorrowMenu(Long dishId) {
        Dish dish = dishRepository.find(dishId);
        if (dish != null) {
            dish.setMenu(null);
            dishRepository.save(dish);
        }
        return getTomorrowMenu();
    }

    @Override
    @Transactional(rollbackOn = {Exception.class})
    public void clearTomorrowMenu() {
        Menu menu = getTomorrowMenu();
        List<Dish> dishes = dishRepository.all().filter("self.menu = ?", menu).fetch();
        for (Dish dish : dishes) {
            dish.setMenu(null);
            dishRepository.save(dish);
        }
    }
}
