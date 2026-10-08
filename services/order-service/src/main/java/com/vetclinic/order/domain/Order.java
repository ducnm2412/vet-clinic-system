package com.vetclinic.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** "orders" là từ khoá SQL nên phải đặt tên bảng tường minh. */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_code", nullable = false, unique = true, length = 20)
    private String orderCode;

    // NULL với hoá đơn tại quầy của khách lẻ không có tài khoản.
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderChannel channel = OrderChannel.ONLINE;

    // Đơn online có ngay lúc đặt (COD); hoá đơn tại quầy được lập cùng lúc thu tiền.
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "recipient_name", nullable = false, length = 200)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "shipping_address", nullable = false, length = 500)
    private String shippingAddress;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal shippingFee;

    // Khoản khám/thuốc ở payment-service được gộp vào hoá đơn này. exam_amount nằm trong total
    // nhưng không tính vào doanh thu đơn hàng (payment-service đã tính khoản khám).
    @Column(name = "exam_payment_id")
    private UUID examPaymentId;

    @Column(name = "exam_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal examAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "transfer_reference", length = 100)
    private String transferReference;

    // Chỉ có với đơn ONLINE chưa trả: quá hạn này thì đơn bị tự huỷ.
    @Column(name = "payment_expires_at")
    private Instant paymentExpiresAt;

    // Mã giao dịch phía cổng thanh toán, ghi khi cổng báo kết quả.
    @Column(name = "gateway_txn_ref", length = 64)
    private String gatewayTxnRef;

    // Lúc cấp link thanh toán hiện tại; cổng thật cần đúng mốc này khi hỏi lại trạng thái giao dịch.
    @Column(name = "gateway_txn_created_at")
    private Instant gatewayTxnCreatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;
}
