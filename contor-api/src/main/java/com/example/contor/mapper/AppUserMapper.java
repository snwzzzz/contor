package com.example.contor.mapper;

import com.example.contor.api.model.CurrentUserDto;
import com.example.contor.api.model.Role;
import com.example.contor.domain.AppUser;

public class AppUserMapper {

    public static CurrentUserDto toDto(AppUser user) {
        if (user == null) return null;

        return new CurrentUserDto(
                user.getId() != null ? user.getId().id() : null,
                user.getUsername() != null ? user.getUsername().value() : null,
                user.getEmail() != null ? user.getEmail().value() : null,
                user.getRole() != null ? Role.valueOf(user.getRole().name()) : null,
                user.isTotpEnabled()
        );
    }
}
