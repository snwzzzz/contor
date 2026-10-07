package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.RecoveryCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
public class RecoveryCodeRepositoryTest {

    @Autowired
    private RecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void finds_only_unused_codes_of_a_user() {
        var user = appUserRepository.save(Fixtures.user("owner"));
        recoveryCodeRepository.save(RecoveryCode.builder().user(user).codeHash("hash-1").build());
        recoveryCodeRepository.save(RecoveryCode.builder().user(user).codeHash("hash-2").usedAt(Instant.now()).build());

        var unused = recoveryCodeRepository.findByUser_IdAndUsedAtIsNull(user.getId());

        assertThat(unused).extracting(RecoveryCode::getCodeHash).containsExactly("hash-1");
        assertThat(unused.get(0).getId()).isNotNull();
    }
}
