package com.axelor.orderr.service.menu;

import com.axelor.order.db.Menu;

public interface MenuService {
    Menu getTomorrowMenu();
    void addDishToTomorrowMenu(Long dishId);
    void removeDishFromTomorrowMenu(Long dishId);
    void clearTomorrowMenu();
}
