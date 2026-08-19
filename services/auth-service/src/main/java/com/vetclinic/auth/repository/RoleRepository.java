package com.vetclinic.auth.repository;

import com.vetclinic.auth.domain.Role;
import com.vetclinic.auth.domain.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByName(RoleName name);
}
