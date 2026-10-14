package com.example.contor.persistence;

import com.example.contor.domain.SavedQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedQueryRepository extends JpaRepository<SavedQuery, SavedQuery.SavedQueryId> {

    List<SavedQuery> findByPinnedTrueOrderByNameAsc();

    List<SavedQuery> findAllByOrderByPinnedDescNameAsc();
}
