package com.example.contor.mapper;

import com.example.contor.api.model.CurrentUserDto;
import com.example.contor.api.model.Role;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Email;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class AppUserMapperTest {

    @Test
    void maps_user_to_current_user_dto() {
        var user = AppUser.builder().id(new AppUser.AppUserId(1L)).username(new Username("owner"))
                .email(new Email("owner@example.com")).passwordHash("hash").role(UserRole.OWNER).totpEnabled(true).build();

        CurrentUserDto dto = AppUserMapper.toDto(user);

        assertEquals(1L, dto.getId());
        assertEquals("owner", dto.getUsername());
        assertEquals("owner@example.com", dto.getEmail());
        assertEquals(Role.OWNER, dto.getRole());
        assertTrue(dto.getTotpEnabled());
    }

    @Test
    void handles_null_input() {
        assertNull(AppUserMapper.toDto(null));
    }

    @Test
    void handles_null_id_and_value_objects() {
        CurrentUserDto dto = AppUserMapper.toDto(AppUser.builder().build());

        assertNull(dto.getId());
        assertNull(dto.getUsername());
        assertNull(dto.getEmail());
        assertNull(dto.getRole());
    }
}
