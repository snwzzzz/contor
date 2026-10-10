package com.example.contor.persistence;

/** One sender of an owner's inbox and how many emails came from it (search suggestions). */
public record SenderCount(String name, String address, Long emails) {
}
