package com.vetclinic.product.repository;

import com.vetclinic.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySku(String sku);

    Optional<Product> findBySku(String sku);

    boolean existsByCategoryId(UUID categoryId);

    /**
     * CN-29: tìm kiếm có lọc. Mọi tham số đều tuỳ chọn — null nghĩa là bỏ qua tiêu chí đó,
     * nhờ vậy chỉ cần một query thay vì dựng Specification động.
     * Khách chưa đăng nhập chỉ thấy hàng đang bán (activeOnly=true).
     *
     * Riêng keyword nhận chuỗi rỗng thay vì null khi không lọc: bind null vào lower() làm
     * PostgreSQL không suy được kiểu tham số và báo "function lower(bytea) does not exist".
     * Chuỗi rỗng cho ra like '%%' nên vẫn khớp mọi bản ghi.
     */
    @Query("""
            select p from Product p
            where (:categoryId is null or p.category.id = :categoryId)
              and lower(p.name) like lower(concat('%', :keyword, '%'))
              and (:minPrice is null or p.price >= :minPrice)
              and (:maxPrice is null or p.price <= :maxPrice)
              and (:activeOnly = false or p.active = true)
            """)
    Page<Product> search(@Param("categoryId") UUID categoryId,
                         @Param("keyword") String keyword,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("activeOnly") boolean activeOnly,
                         Pageable pageable);

    /** CN-30: cảnh báo sắp hết hàng. */
    @Query("select p from Product p where p.stockQuantity <= p.lowStockThreshold order by p.stockQuantity asc")
    List<Product> findLowStock();
}
