package com.example.contor.mailprovider;

import com.example.contor.service.ImageData;

/** A contact's photo for one of its (lower-cased) addresses. */
public record ContactPhotoData(String emailAddress, ImageData photo) {
}
