package com.racehorse.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Case-insensitive email lookup with roles loaded (login, /me). Emails are unique (uq_app_user__email). */
    @Query("select distinct u from AppUser u left join fetch u.userRoles ur left join fetch ur.role "
            + "where lower(u.email) = lower(:email)")
    Optional<AppUser> findWithRolesByEmail(@Param("email") String email);

    @Query("select distinct u from AppUser u left join fetch u.userRoles ur left join fetch ur.role where u.id = :id")
    Optional<AppUser> findWithRolesById(@Param("id") Long id);

    boolean existsByEmailIgnoreCase(String email);
}
