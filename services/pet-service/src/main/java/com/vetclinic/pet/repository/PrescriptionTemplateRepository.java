package com.vetclinic.pet.repository;

import com.vetclinic.pet.domain.PrescriptionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PrescriptionTemplateRepository extends JpaRepository<PrescriptionTemplate, UUID> {

    boolean existsBySearchName(String searchName);

    boolean existsBySearchNameAndIdNot(String searchName, UUID id);

    /** Tìm theo tên đã bỏ dấu, xếp theo tên. Từ khoá rỗng thì trả tất cả; phải escape trước (xem TextNormalizer). */
    @Query("""
            select t from PrescriptionTemplate t
            where t.searchName like concat('%', :q, '%') escape '\\'
            order by t.searchName
            """)
    List<PrescriptionTemplate> search(@Param("q") String normalizedQuery);
}
