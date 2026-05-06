package com.devtiro.Restaurant.mappers;

import com.devtiro.Restaurant.domain.RestaurantCreateUpdateRequest;
import com.devtiro.Restaurant.domain.dtos.GeoPointDto;
import com.devtiro.Restaurant.domain.dtos.RestaurantCreateUpdateRequestDto;
import com.devtiro.Restaurant.domain.dtos.RestaurantDto;
import com.devtiro.Restaurant.domain.entity.Restaurant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RestaurantMapper {

    RestaurantCreateUpdateRequest toRestaurantCreateUpdateRequest(RestaurantCreateUpdateRequestDto dto);

    RestaurantDto toRestaurantDto(Restaurant restaurant);

    @Mapping(target = "latitude", expression = "java(geoPoint.getLat()")
    @Mapping(target = "longitude", expression = "java(geoPoint.getLon()")
    GeoPointDto toGeoPointDto(GeoPoint geoPoint);
}
