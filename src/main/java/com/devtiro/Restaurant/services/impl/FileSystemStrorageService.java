package com.devtiro.Restaurant.services.impl;

import com.devtiro.Restaurant.services.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@Service
public class FileSystemStorageService implements StorageService {
    @Override
    public String store(MultipartFile file, String filename) {
        return "";
    }

    @Override
    public Optional<Resource> loadAsResource(String id) {
        return Optional.empty();
    }
}
