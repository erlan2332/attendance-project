package com.axelor.orderr.service.dish;

import com.axelor.order.db.Dish;
import com.axelor.order.db.repo.DishRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import java.util.List;

public class DishServiceImpl implements DishService{

    private final DishRepository repo;

    @Inject
    public DishServiceImpl(DishRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(rollbackOn = {Exception.class})
    public List<Dish> getAllDishes() {
        return repo.all().fetch();
    }

    @Override
    public Dish getDishById(long id) {
        return repo.all().filter("self.id = ?", id).fetchOne();
    }

    @Override
    @Transactional(rollbackOn = {Exception.class})
    public Dish createDish(Dish dish) {
        return repo.save(dish);
    }

    @Override
    @Transactional(rollbackOn = {Exception.class})
    public void deleteDish(long id) {
        Dish exsistDish = repo.find(id);
        if (exsistDish == null) {
            throw new IllegalArgumentException("блюдо с id" + id + " не найдено.");
        }
        repo.remove(exsistDish);
    }

//    @Transactional(rollbackOn = {Exception.class})
//    public void setDishRating(long dishId, Integer rating) {
//        if (rating > 5 || rating < 1) {
//            throw new IllegalArgumentException("Оценка должна быть от 1 до 5");
//        }
//        Dish dish = repo.find(dishId);
//        if (dish == null) {
//            throw new IllegalArgumentException("Блюдо с id " + dishId + " не найдено");
//        }
//
//        dish.setRating_count(dish.getRating_count() + 1);
//        dish.setRating_sum(dish.getRating_sum() + rating);
//        double avgRating = (double) dish.getRating_sum() / dish.getRating_count();
//        dish.setDish_rating((long) avgRating);
//        repo.save(dish);
//    }
}
