package com.devtiro.Restaurant.repository;

import com.devtiro.Restaurant.domain.entity.Restaurant;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RestaurantRepository extends ElasticsearchRepository<Restaurant, String> {

    // TODO: Custom queries
}
