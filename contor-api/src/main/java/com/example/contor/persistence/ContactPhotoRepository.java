package com.example.contor.persistence;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.ContactPhoto;
import com.example.contor.domain.MailAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactPhotoRepository extends JpaRepository<ContactPhoto, ContactPhoto.ContactPhotoId> {

    List<ContactPhoto> findByAccount_Owner_Id(AppUser.AppUserId ownerId);

    void deleteByAccount_Id(MailAccount.MailAccountId accountId);
}
