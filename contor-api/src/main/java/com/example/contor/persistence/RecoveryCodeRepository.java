package com.example.contor.persistence;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.RecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    List<RecoveryCode> findByUser_IdAndUsedAtIsNull(AppUser.AppUserId userId);
}
