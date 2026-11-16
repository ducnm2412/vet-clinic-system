package com.vetclinic.product.repository;

import com.vetclinic.product.domain.Medication;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {

    boolean existsBySearchName(String searchName);

    Optional<Medication> findBySearchName(String searchName);

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

    /** Khoá dòng khi đổi tồn kho: hai phiếu thu cùng lúc không được đọc cùng một số tồn rồi cùng trừ. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Medication m where m.id = :id")
    Optional<Medication> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Danh sách cho màn kho: giống {@link #search} nhưng có thể chỉ lấy thuốc sắp hết (tồn <= ngưỡng,
     * kể cả hết hẳn). Thuốc đã ẩn chỉ hiện khi xin; thuốc ẩn không bị tính là "sắp hết".
     */
    @Query("""
            select m from Medication m
            where (:includeInactive = true or m.active = true)
              and m.searchName like concat('%', :q, '%') escape '\\'
              and (:lowOnly = false or (m.active = true and m.stockQuantity <= m.lowStockThreshold))
            order by case when m.searchName like concat(:q, '%') escape '\\' then 0 else 1 end, m.searchName
            """)
    List<Medication> inventory(@Param("q") String normalizedQuery,
                               @Param("includeInactive") boolean includeInactive,
                               @Param("lowOnly") boolean lowOnly,
                               Pageable pageable);
}
