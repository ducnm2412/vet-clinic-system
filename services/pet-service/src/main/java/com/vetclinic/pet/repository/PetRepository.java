package com.vetclinic.pet.repository;

import com.vetclinic.pet.domain.Pet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PetRepository extends JpaRepository<Pet, UUID> {

    List<Pet> findByOwnerUserIdOrderByCreatedAtAsc(UUID ownerUserId);

    Optional<Pet> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    List<Pet> findByIdIn(Collection<UUID> ids);

    List<Pet> findByOwnerUserIdInOrderByCreatedAtAsc(Collection<UUID> ownerUserIds);


    /** Con có ảnh trên đĩa mà chưa có URL Cloudinary — đối tượng của LegacyPhotoMigration. */
    List<Pet> findByPhotoVersionGreaterThanAndPhotoUrlIsNull(int photoVersion);
}
