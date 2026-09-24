package com.vetclinic.product.service;

import com.vetclinic.product.domain.Category;
import com.vetclinic.product.domain.Product;
import com.vetclinic.product.domain.StockMovementType;
import com.vetclinic.product.dto.StockAdjustmentRequest;
import com.vetclinic.product.exception.InsufficientStockException;
import com.vetclinic.product.repository.CategoryRepository;
import com.vetclinic.product.repository.ProductRepository;
import com.vetclinic.product.repository.StockMovementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VD-02 và VD-14: hai thao tác sửa kho chạy cùng lúc.
 *
 * Phải chạy trên database thật (`product_db_test`) và KHÔNG bọc `@Transactional`: hai luồng cần
 * nhìn thấy dữ liệu đã commit của nhau, đúng như hai request thật. TestDatabaseGuard chặn nếu ai
 * đó trỏ vào database đang dùng.
 *
 * Bỏ khoá dòng trong ProductRepository là hai test này hỏng ngay: đọc cùng số tồn cũ rồi ghi đè.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
class ProductStockConcurrencyTest {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private StockMovementRepository stockMovementRepository;

    private Category category;
    private UUID productId;

    @BeforeEach
    void createProduct() {
        category = categoryRepository.save(Category.builder()
                .name("Kiểm thử " + UUID.randomUUID()).slug("kiem-thu-" + UUID.randomUUID()).build());
    }

    @AfterEach
    void cleanUp() {
        if (productId != null) {
            stockMovementRepository.deleteAll(stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId));
            productRepository.deleteById(productId);
        }
        categoryRepository.deleteById(category.getId());
    }

    private void givenStock(int quantity) {
        productId = productRepository.saveAndFlush(Product.builder()
                .category(category).sku("SKU-" + UUID.randomUUID()).name("Hạt cho chó")
                .price(new BigDecimal("150000")).unit("gói").stockQuantity(quantity)
                .lowStockThreshold(1).active(true).build()).getId();
    }

    private int stockNow() {
        return productRepository.findById(productId).orElseThrow().getStockQuantity();
    }

    /** Chạy hai việc thật sự cùng lúc, trả về số việc thành công. */
    private int runTogether(Runnable first, Runnable second) throws InterruptedException {
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger succeeded = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        for (Runnable task : List.of(first, second)) {
            pool.submit(() -> {
                try {
                    startLine.await();
                    task.run();
                    succeeded.incrementAndGet();
                } catch (InsufficientStockException expected) {
                    // Đúng như mong đợi: người thứ hai không còn hàng để trừ.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        startLine.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).as("hai luồng phải xong trong 20 giây").isTrue();
        pool.shutdownNow();
        return succeeded.get();
    }

    @Test
    void twoOrdersForTheLastItem_onlyOneGoesThrough() throws InterruptedException {
        givenStock(1);
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();

        int succeeded = runTogether(
                () -> productService.applyOrderSale(orderA, List.of(new ProductService.OrderLine(productId, 1))),
                () -> productService.applyOrderSale(orderB, List.of(new ProductService.OrderLine(productId, 1))));

        assertThat(succeeded).as("chỉ một đơn được trừ kho").isEqualTo(1);
        assertThat(stockNow()).as("không bao giờ âm kho").isZero();
        assertThat(stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId))
                .filteredOn(m -> m.getType() == StockMovementType.SALE).hasSize(1);
    }

    @Test
    void twoStaffImportingAtOnce_bothCountsAreKept() throws InterruptedException {
        givenStock(10);
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();

        int succeeded = runTogether(
                () -> productService.adjustStock(productId,
                        new StockAdjustmentRequest(StockMovementType.IMPORT, 5, "Nhập ca sáng"), staffA),
                () -> productService.adjustStock(productId,
                        new StockAdjustmentRequest(StockMovementType.IMPORT, 7, "Nhập ca chiều"), staffB));

        assertThat(succeeded).isEqualTo(2);
        assertThat(stockNow()).as("không lần nhập nào bị ghi đè mất").isEqualTo(22);
    }
}
