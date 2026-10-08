package com.example.contor.service;

/** An image stored or served by contor (account photos, sender logos, contact photos). */
public record ImageData(byte[] bytes, String contentType) {
}
