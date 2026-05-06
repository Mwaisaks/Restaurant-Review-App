package com.devtiro.Restaurant.services;

import com.devtiro.Restaurant.domain.RestaurantCreateUpdateRequest;
import com.devtiro.Restaurant.domain.entity.Restaurant;

public interface RestaurantService {

    Restaurant createRestaurant(RestaurantCreateUpdateRequest request);
}
