package com.vetclinic.product.service;

import com.vetclinic.product.domain.Category;
import com.vetclinic.product.domain.Product;
import com.vetclinic.product.domain.StockMovement;
import com.vetclinic.product.domain.StockMovementType;
import com.vetclinic.product.dto.PageResponse;
import com.vetclinic.product.dto.ProductRequest;
import com.vetclinic.product.dto.ProductResponse;
import com.vetclinic.product.dto.StockAdjustmentRequest;
import com.vetclinic.product.dto.StockMovementResponse;
import com.vetclinic.product.exception.DuplicateResourceException;
import com.vetclinic.product.exception.InsufficientStockException;
import com.vetclinic.product.exception.ResourceNotFoundException;
import com.vetclinic.product.repository.CategoryRepository;
import com.vetclinic.product.repository.ProductRepository;
import com.vetclinic.product.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** CN-28, CN-29, CN-30, CN-31: sản phẩm và tồn kho. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StockMovementRepository stockMovementRepository;

    // ---------- CN-29: tra cứu ----------

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(UUID categoryId, String keyword, BigDecimal minPrice,
                                                BigDecimal maxPrice, boolean activeOnly, Pageable pageable) {
        // Không lọc theo tên thì truyền chuỗi rỗng (khớp mọi bản ghi), không truyền null —
        // xem ghi chú kiểu tham số ở ProductRepository.search.
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? "" : keyword.trim();

        Page<ProductResponse> page = productRepository
                .search(categoryId, normalizedKeyword, minPrice, maxPrice, activeOnly, pageable)
                .map(this::toResponse);

        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    // ---------- CN-28: quản lý sản phẩm ----------

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateResourceException("Product SKU already exists: " + request.sku());
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.categoryId()));

        int initialStock = request.initialStock() == null ? 0 : request.initialStock();

        Product product = Product.builder()
                .category(category)
                .sku(request.sku())
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .unit(request.unit())
                .imageUrl(request.imageUrl())
                .stockQuantity(initialStock)
                .lowStockThreshold(request.lowStockThreshold() == null ? 0 : request.lowStockThreshold())
                .active(request.active() == null || request.active())
                .build();

        // saveAndFlush chứ không phải save: @CreationTimestamp chỉ được điền lúc flush,
        // nếu không response của POST sẽ trả createdAt/updatedAt là null.
        productRepository.saveAndFlush(product);

        // Tồn ban đầu cũng phải có vết trong lịch sử, nếu không số liệu kho sẽ không đối soát được.
        if (initialStock > 0) {
            recordMovement(product, StockMovementType.IMPORT, initialStock, "Tồn kho khởi tạo", null, null);
        }

        return toResponse(product);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = findOrThrow(id);

        if (!product.getSku().equals(request.sku()) && productRepository.existsBySku(request.sku())) {
            throw new DuplicateResourceException("Product SKU already exists: " + request.sku());
        }

        if (!product.getCategory().getId().equals(request.categoryId())) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.categoryId()));
            product.setCategory(category);
        }

        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setUnit(request.unit());
        product.setImageUrl(request.imageUrl());
        if (request.lowStockThreshold() != null) {
            product.setLowStockThreshold(request.lowStockThreshold());
        }
        if (request.active() != null) {
            product.setActive(request.active());
        }
        // initialStock cố tình bị bỏ qua ở đây — đổi tồn kho phải qua adjustStock() để có vết.

        return toResponse(product);
    }

    @Transactional
    public void delete(UUID id) {
        Product product = findOrThrow(id);
        productRepository.delete(product);
    }

    // ---------- CN-30: tồn kho ----------

    @Transactional
    public ProductResponse adjustStock(UUID productId, StockAdjustmentRequest request, UUID actorUserId) {
        Product product = findOrThrow(productId);

        int after = product.getStockQuantity() + request.quantityChange();
        if (after < 0) {
            throw new InsufficientStockException(productId, product.getStockQuantity(), -request.quantityChange());
        }

        product.setStockQuantity(after);
        recordMovement(product, request.type(), request.quantityChange(), request.note(), null, actorUserId);

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listLowStock() {
        return productRepository.findLowStock().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> listMovements(UUID productId) {
        findOrThrow(productId);

        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(m -> new StockMovementResponse(m.getId(), productId, m.getType(), m.getQuantityChange(),
                        m.getQuantityAfter(), m.getNote(), m.getReferenceId(), m.getCreatedBy(), m.getCreatedAt()))
                .toList();
    }

    // ---------- CN-31: trừ kho tự động theo sự kiện đơn hàng ----------

    /**
     * Trừ kho cho một dòng hàng của đơn. Trả về false nếu sự kiện này đã xử lý rồi
     * (RabbitMQ giao at-least-once nên message có thể tới hai lần).
     */
    @Transactional
    public boolean applySale(UUID productId, int quantity, UUID orderId) {
        if (stockMovementRepository.existsByProductIdAndReferenceIdAndType(
                productId, orderId, StockMovementType.SALE)) {
            log.info("Bỏ qua sự kiện trùng: order={} product={} đã trừ kho trước đó", orderId, productId);
            return false;
        }

        Product product = findOrThrow(productId);

        int after = product.getStockQuantity() - quantity;
        if (after < 0) {
            throw new InsufficientStockException(productId, product.getStockQuantity(), quantity);
        }

        product.setStockQuantity(after);
        recordMovement(product, StockMovementType.SALE, -quantity, "Trừ kho theo đơn hàng", orderId, null);

        return true;
    }

    /**
     * Hoàn hàng về kho khi đơn đã xác nhận bị huỷ. Cũng chống trùng như applySale, nhưng
     * theo type RETURN nên một đơn có thể vừa có dòng SALE vừa có dòng RETURN.
     */
    @Transactional
    public boolean applyReturn(UUID productId, int quantity, UUID orderId) {
        if (stockMovementRepository.existsByProductIdAndReferenceIdAndType(
                productId, orderId, StockMovementType.RETURN)) {
            log.info("Bỏ qua sự kiện trùng: order={} product={} đã hoàn kho trước đó", orderId, productId);
            return false;
        }

        Product product = findOrThrow(productId);
        product.setStockQuantity(product.getStockQuantity() + quantity);
        recordMovement(product, StockMovementType.RETURN, quantity, "Hoàn kho do huỷ đơn", orderId, null);

        return true;
    }

    // ---------- nội bộ ----------

    private void recordMovement(Product product, StockMovementType type, int change,
                                String note, UUID referenceId, UUID actorUserId) {
        stockMovementRepository.save(StockMovement.builder()
                .product(product)
                .type(type)
                .quantityChange(change)
                .quantityAfter(product.getStockQuantity())
                .note(note)
                .referenceId(referenceId)
                .createdBy(actorUserId)
                .build());
    }

    private Product findOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getCategory().getId(),
                p.getCategory().getName(),
                p.getSku(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getUnit(),
                p.getImageUrl(),
                p.getStockQuantity(),
                p.getLowStockThreshold(),
                p.getStockQuantity() <= p.getLowStockThreshold(),
                p.getActive(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
