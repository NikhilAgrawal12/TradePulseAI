package com.pm.userservice.repository;

import com.pm.userservice.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByEmail(String email);

    default boolean existsByEmailAndIdNot(String email, UUID id) {
        return false;
    }

}
