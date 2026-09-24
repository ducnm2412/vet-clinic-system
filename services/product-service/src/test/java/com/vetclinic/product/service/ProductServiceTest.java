package com.vetclinic.product.service;

import com.vetclinic.product.domain.Category;
import com.vetclinic.product.domain.Product;
import com.vetclinic.product.domain.StockMovement;
import com.vetclinic.product.domain.StockMovementType;
import com.vetclinic.product.dto.ProductResponse;
import com.vetclinic.product.dto.StockAdjustmentRequest;
import com.vetclinic.product.exception.InsufficientStockException;
import com.vetclinic.product.exception.ResourceNotFoundException;
import com.vetclinic.product.repository.CategoryRepository;
import com.vetclinic.product.repository.ProductRepository;
import com.vetclinic.product.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private ProductService productService;

    private UUID productId;
    private Product product;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();

        Category category = Category.builder()
                .id(UUID.randomUUID())
                .name("Thức ăn")
                .slug("thuc-an")
                .build();

        product = Product.builder()
                .id(productId)
                .category(category)
                .sku("SKU-001")
                .name("Hạt cho chó")
                .price(new BigDecimal("150000"))
                .unit("gói")
                .stockQuantity(10)
                .lowStockThreshold(3)
                .active(true)
                .build();
    }

    @Test
    void adjustStock_import_increasesQuantityAndRecordsMovement() {
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        ProductResponse response = productService.adjustStock(productId,
                new StockAdjustmentRequest(StockMovementType.IMPORT, 5, "Nhập thêm"), UUID.randomUUID());

        assertThat(response.stockQuantity()).isEqualTo(15);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantityChange()).isEqualTo(5);
        assertThat(captor.getValue().getQuantityAfter()).isEqualTo(15);
    }

    @Test
    void adjustStock_negativeBeyondStock_throwsAndDoesNotRecord() {
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.adjustStock(productId,
                new StockAdjustmentRequest(StockMovementType.ADJUSTMENT, -20, "Kiểm kê"), UUID.randomUUID()))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(product.getStockQuantity()).isEqualTo(10);
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void applySale_deductsStock() {
        UUID orderId = UUID.randomUUID();
        when(stockMovementRepository.existsByProductIdAndReferenceIdAndType(
                productId, orderId, StockMovementType.SALE)).thenReturn(false);
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        boolean applied = productService.applySale(productId, 4, orderId);

        assertThat(applied).isTrue();
        assertThat(product.getStockQuantity()).isEqualTo(6);
    }

    @Test
    void applySale_duplicateEvent_isIgnored() {
        UUID orderId = UUID.randomUUID();
        when(stockMovementRepository.existsByProductIdAndReferenceIdAndType(
                productId, orderId, StockMovementType.SALE)).thenReturn(true);

        boolean applied = productService.applySale(productId, 4, orderId);

        // Message tới lần hai không được trừ kho thêm lần nữa.
        assertThat(applied).isFalse();
        assertThat(product.getStockQuantity()).isEqualTo(10);
        verify(productRepository, never()).findByIdForUpdate(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void applySale_insufficientStock_throws() {
        UUID orderId = UUID.randomUUID();
        when(stockMovementRepository.existsByProductIdAndReferenceIdAndType(
                productId, orderId, StockMovementType.SALE)).thenReturn(false);
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.applySale(productId, 999, orderId))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(product.getStockQuantity()).isEqualTo(10);
    }

    // ---------- VD-14: trừ kho cả đơn ----------

    @Test
    void applyOrderSale_missingOneItem_throwsSoNothingIsDeducted() {
        // Id cố định để biết chắc thứ tự xử lý: món đủ hàng trừ trước, món thiếu ném lỗi sau —
        // đúng tình huống nguy hiểm nhất, nửa đơn đã trừ rồi mới phát hiện thiếu.
        UUID plentyId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID scarceId = UUID.fromString("ffffffff-0000-4000-8000-000000000002");
        UUID orderId = UUID.randomUUID();
        Product plenty = Product.builder().id(plentyId).sku("SKU-001").name("Hạt cho chó")
                .price(new BigDecimal("150000")).unit("gói").stockQuantity(10).active(true).build();
        Product scarce = Product.builder().id(scarceId).sku("SKU-002").name("Sữa cho mèo")
                .price(new BigDecimal("90000")).unit("hộp").stockQuantity(1).active(true).build();
        when(stockMovementRepository.existsByProductIdAndReferenceIdAndType(any(), any(), any())).thenReturn(false);
        when(productRepository.findByIdForUpdate(plentyId)).thenReturn(Optional.of(plenty));
        when(productRepository.findByIdForUpdate(scarceId)).thenReturn(Optional.of(scarce));

        assertThatThrownBy(() -> productService.applyOrderSale(orderId, List.of(
                new ProductService.OrderLine(plentyId, 2), new ProductService.OrderLine(scarceId, 5))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Sữa cho mèo")
                .hasMessageContaining("chỉ còn 1 hộp");

        // Ném lỗi giữa chừng là cả transaction cuốn lại, nên dòng đã trừ trong bộ nhớ cũng không
        // bao giờ xuống database. Điều test kiểm được ở đây: món thiếu không bị đụng tới.
        assertThat(scarce.getStockQuantity()).isEqualTo(1);
    }

    @Test
    void applyOrderSale_sameOrderTwice_deductsOnlyOnce() {
        UUID orderId = UUID.randomUUID();
        when(stockMovementRepository.existsByProductIdAndReferenceIdAndType(
                productId, orderId, StockMovementType.SALE)).thenReturn(true);

        int applied = productService.applyOrderSale(orderId, List.of(new ProductService.OrderLine(productId, 2)));

        assertThat(applied).isZero();
        assertThat(product.getStockQuantity()).isEqualTo(10);
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void getById_unknownProduct_throwsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(productRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void toResponse_marksLowStockWhenAtThreshold() {
        product.setStockQuantity(3);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        // Ngưỡng là 3, tồn bằng đúng 3 -> đã phải cảnh báo, không đợi xuống dưới ngưỡng.
        assertThat(productService.getById(productId).lowStock()).isTrue();
    }

    @Test
    void toResponse_notLowStockAboveThreshold() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThat(productService.getById(productId).lowStock()).isFalse();
    }
}
