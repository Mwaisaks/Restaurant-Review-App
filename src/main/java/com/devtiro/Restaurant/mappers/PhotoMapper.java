package com.devtiro.Restaurant.mappers;

import com.devtiro.Restaurant.domain.dtos.PhotoDto;
import com.devtiro.Restaurant.domain.entity.Photo;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PhotoMapper {

    PhotoDto toDto(Photo photo);
}
