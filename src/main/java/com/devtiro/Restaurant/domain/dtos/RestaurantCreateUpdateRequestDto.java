package com.devtiro.Restaurant.domain.dtos;

import com.devtiro.Restaurant.domain.entity.Address;
import com.devtiro.Restaurant.domain.entity.OperatingHours;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RestaurantCreateUpdateRequestDto {

    @NotBlank(message = "Restaurant's name is required.")
    private String name;

    @NotBlank(message = "Cuisine type is required.")
    private String cuisineType;

    @NotBlank(message = "Contact information is required.")
    private String contactInformation;

    @Valid
    private AddressDto address;

    @Valid
    private OperatingHoursDto operatingHours;

    @Size(min = 1, message = "At least one photo ID is required.")
    private List<String> photoIds;

}
