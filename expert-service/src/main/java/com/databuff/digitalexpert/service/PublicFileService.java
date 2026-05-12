package com.databuff.digitalexpert.service;

import java.nio.file.Path;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

public interface PublicFileService {

    String uploadImage(MultipartFile file);

    PublicImageResource loadImage(String date, String fileName);

    record PublicImageResource(
            Path path,
            MediaType mediaType
    ) {
    }
}
