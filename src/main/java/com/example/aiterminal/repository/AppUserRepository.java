package com.example.aiterminal.repository;

import com.example.aiterminal.entity.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 账号存取接口。 */
public interface AppUserRepository extends JpaRepository<AppUser, String> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
