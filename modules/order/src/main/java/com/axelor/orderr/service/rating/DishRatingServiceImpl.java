package com.axelor.orderr.service.rating;

import com.axelor.order.db.Dish;
import com.axelor.order.db.DishRating;
import com.axelor.order.db.repo.DishRatingRepository;
import com.axelor.order.db.repo.DishRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class DishRatingServiceImpl implements DishRatingService{

    private final DishRatingRepository repository;
    private final DishRepository dishRepository;

    @Inject
    public DishRatingServiceImpl(DishRatingRepository repository, DishRepository dishRepository) {
        this.repository = repository;
        this.dishRepository = dishRepository;
    }

    @Override
    @Transactional(rollbackOn = {Exception.class})
    public void setDishRating(long dishId, Integer rating) {
        if (rating > 5 || rating < 1) {
            throw new IllegalArgumentException("Оценка должна быть от 1 до 5");
        }
        Dish dish = dishRepository.find(dishId);
        if (dish == null) {
            throw new IllegalArgumentException("Блюдо с id " + dishId + " не найдено");
        }

        DishRating newRating = new DishRating();
        newRating.setDish(dish);
        newRating.setDish_rating(Long.valueOf(rating));
        repository.save(newRating);
    }
}
