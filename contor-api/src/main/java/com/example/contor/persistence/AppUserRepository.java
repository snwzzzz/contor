package com.example.contor.persistence;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.Username;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, AppUser.AppUserId> {

    Optional<AppUser> findByUsername(Username username);

    boolean existsByUsername(Username username);
}
