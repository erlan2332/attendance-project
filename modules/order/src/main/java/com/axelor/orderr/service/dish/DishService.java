package com.axelor.orderr.service.dish;

import com.axelor.order.db.Dish;

import java.util.List;

public interface DishService {
    List<Dish> getAllDishes();
    Dish getDishById(long id);
    Dish createDish(Dish dish);
    void deleteDish(long id);
//    void setDishRating(long dishId, Integer rating);
}
