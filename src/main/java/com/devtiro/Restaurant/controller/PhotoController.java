package com.devtiro.Restaurant.controller;

import com.devtiro.Restaurant.domain.dtos.PhotoDto;
import com.devtiro.Restaurant.domain.entity.Photo;
import com.devtiro.Restaurant.mappers.PhotoMapper;
import com.devtiro.Restaurant.services.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/photos")
public class PhotoController {

    private final PhotoService photoService;
    private final PhotoMapper photoMapper;

    @PostMapping
    public PhotoDto uploadPhoto(@RequestParam("file")MultipartFile file){

        Photo savedPhoto = photoService.uploadPhoto(file);
        return photoMapper.toDto(savedPhoto);
    }

    @GetMapping(path = "/{id:.*}") //why are we using regex here?
    public ResponseEntity<Resource> getPhoto(@PathVariable String id){
        return photoService.getPhotoAsResource(id).map(photo -> //revisit streams
                ResponseEntity.ok()
                        .contentType(
                                MediaTypeFactory.getMediaType(photo)
                                        .orElse(MediaType.APPLICATION_OCTET_STREAM)
                        )
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .body(photo)
                ).orElse(ResponseEntity.notFound().build());
    }
}
