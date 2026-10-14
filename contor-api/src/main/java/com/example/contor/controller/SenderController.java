package com.example.contor.controller;

import com.example.contor.api.SendersApi;
import com.example.contor.api.model.ContactPhotoDto;
import com.example.contor.service.ImageData;
import com.example.contor.service.SenderPhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RequiredArgsConstructor
@RestController
public class SenderController implements SendersApi {

    private final SenderPhotoService senderPhotoService;

    @Override
    public ResponseEntity<List<String>> listSenderLogos(List<String> domain) {
        return ResponseEntity.ok(senderPhotoService.listLogos(domain));
    }

    @Override
    public ResponseEntity<Resource> getSenderLogo(String domain) {
        return image(senderPhotoService.getLogo(domain));
    }

    @Override
    public ResponseEntity<List<ContactPhotoDto>> listContactPhotos() {
        return ResponseEntity.ok(senderPhotoService.listContactPhotos());
    }

    @Override
    public ResponseEntity<Resource> getContactPhoto(Long contactPhotoId) {
        return image(senderPhotoService.getContactPhoto(contactPhotoId));
    }

    private static ResponseEntity<Resource> image(ImageData image) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(new ByteArrayResource(image.bytes()));
    }
}
