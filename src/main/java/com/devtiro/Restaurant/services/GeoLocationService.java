package com.devtiro.Restaurant.services;

import com.devtiro.Restaurant.domain.GeoLocation;
import com.devtiro.Restaurant.domain.entity.Address;

public interface GeoLocationService {

    GeoLocation getlocate(Address address);
}
