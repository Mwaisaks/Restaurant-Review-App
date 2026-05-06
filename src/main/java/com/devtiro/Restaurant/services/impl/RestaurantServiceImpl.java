package com.devtiro.Restaurant.services.impl;

import com.devtiro.Restaurant.domain.GeoLocation;
import com.devtiro.Restaurant.domain.RestaurantCreateUpdateRequest;
import com.devtiro.Restaurant.domain.entity.Address;
import com.devtiro.Restaurant.domain.entity.Photo;
import com.devtiro.Restaurant.domain.entity.Restaurant;
import com.devtiro.Restaurant.repository.RestaurantRepository;
import com.devtiro.Restaurant.services.GeoLocationService;
import com.devtiro.Restaurant.services.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RestaurantServiceImpl implements RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final GeoLocationService geoLocationService;

    @Override
    public Restaurant createRestaurant(RestaurantCreateUpdateRequest request) {

        Address address = request.getAddress();
        GeoLocation geoLocation = geoLocationService.getlocate(address);
        GeoPoint geoPoint = new GeoPoint(geoLocation.getLatitude(), geoLocation.getLongitude());

        List<String> photoIds = request.getPhotoIds();
        List<Photo> photos = photoIds.stream()
                .map(photourl -> Photo.builder()
                        .url(photourl)
                        .uploadedDate(LocalDateTime.now())
                        .build()).toList();

        Restaurant restaurant = Restaurant.builder()
                .name(request.getName())
                .cuisineType(request.getCuisineType())
                .contactInformation(request.getContactInformation())
                .address(address)
                .geoLocation(geoPoint)
                .operatingHours(request.getOperatingHours())
                .averageRating(0f)
                .photos(photos)
                .build();

        return restaurantRepository.save(restaurant);

    }
}
