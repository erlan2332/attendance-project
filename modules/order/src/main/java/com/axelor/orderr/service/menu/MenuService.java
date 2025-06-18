package com.axelor.orderr.service.menu;

import com.axelor.order.db.Menu;

public interface MenuService {
    Menu getTomorrowMenu();
    Menu addDishToTomorrowMenu(Long dishId);
    Menu removeDishFromTomorrowMenu(Long dishId);
    void clearTomorrowMenu();
}
