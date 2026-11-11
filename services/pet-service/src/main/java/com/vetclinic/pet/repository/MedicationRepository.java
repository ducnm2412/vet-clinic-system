package com.vetclinic.pet.repository;

import com.vetclinic.pet.domain.Medication;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {

    boolean existsBySearchName(String searchName);

    boolean existsBySearchNameAndIdNot(String searchName, UUID id);

    /**
     * Tìm theo tên đã bỏ dấu. Tên bắt đầu bằng từ khoá xếp trước tên chỉ chứa từ khoá, để gõ
     * "sm" thì "Smecta" lên trước "Nasmil". Từ khoá rỗng thì trả tất cả (theo tên).
     * Từ khoá phải được escape {@code \}, {@code %}, {@code _} trước (xem MedicationService).
     */
    @Query("""
            select m from Medication m
            where (:includeInactive = true or m.active = true)
              and m.searchName like concat('%', :q, '%') escape '\\'
            order by case when m.searchName like concat(:q, '%') escape '\\' then 0 else 1 end, m.searchName
            """)
    List<Medication> search(@Param("q") String normalizedQuery,
                            @Param("includeInactive") boolean includeInactive,
                            Pageable pageable);
}
