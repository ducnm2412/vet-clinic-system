package com.vetclinic.order.dto;

import com.vetclinic.order.domain.PaymentMethod;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private CheckoutRequest validCheckout() {
        return new CheckoutRequest("Nguyen Van A", "0901234567", "12 Le Loi, Q1, TP.HCM",
                "Giao giờ hành chính", PaymentMethod.COD);
    }

    @Test
    void validCheckout_hasNoViolation() {
        assertThat(validator.validate(validCheckout())).isEmpty();
    }

    @Test
    void phoneMustBeTenDigitsStartingWithZero() {
        assertThat(paths(new CheckoutRequest("A", "0123", "dc", null, PaymentMethod.COD)))
                .contains("recipientPhone");
        assertThat(paths(new CheckoutRequest("A", "1901234567", "dc", null, PaymentMethod.COD)))
                .contains("recipientPhone");
        assertThat(paths(new CheckoutRequest("A", "09012345678", "dc", null, PaymentMethod.COD)))
                .contains("recipientPhone");
    }

    @Test
    void blankAddress_isRejected() {
        assertThat(paths(new CheckoutRequest("A", "0901234567", "   ", null, PaymentMethod.COD)))
                .contains("shippingAddress");
    }

    @Test
    void missingPaymentMethod_isRejected() {
        assertThat(paths(new CheckoutRequest("A", "0901234567", "dc", null, null)))
                .contains("paymentMethod");
    }

    @Test
    void counterPaymentMethods_areRejectedForCustomerCheckout() {
        // CASH/BANK_TRANSFER do nhân viên chọn khi thu tại quầy; khách chọn được thì đơn tự thành "đã trả".
        assertThat(paths(new CheckoutRequest("A", "0901234567", "dc", null, PaymentMethod.CASH)))
                .contains("paymentMethodAllowedForCustomers");
        assertThat(paths(new CheckoutRequest("A", "0901234567", "dc", null, PaymentMethod.BANK_TRANSFER)))
                .contains("paymentMethodAllowedForCustomers");
        assertThat(paths(new CheckoutRequest("A", "0901234567", "dc", null, PaymentMethod.COD))).isEmpty();
    }

    @Test
    void onlinePaymentMethod_isAllowedForCustomerCheckout() {
        assertThat(paths(new CheckoutRequest("A", "0901234567", "dc", null, PaymentMethod.ONLINE))).isEmpty();
    }

    @Test
    void onlinePaymentMethod_isRejectedForCounterInvoice() {
        // Tại quầy nhân viên thu trực tiếp; ONLINE phải đi qua cổng nên không hợp lệ ở đây.
        UUID exam = UUID.randomUUID();
        assertThat(paths(new CounterInvoiceRequest(exam, null, null, null, PaymentMethod.ONLINE, null, null)))
                .contains("counterPaymentMethod");
    }

    @Test
    void counterInvoice_validRequests() {
        UUID exam = UUID.randomUUID();
        var line = new CounterInvoiceRequest.Line(UUID.randomUUID(), 2);

        assertThat(validator.validate(new CounterInvoiceRequest(
                exam, null, null, null, PaymentMethod.CASH, null, null))).isEmpty();
        assertThat(validator.validate(new CounterInvoiceRequest(
                null, "Chị Lan", "0901234567", List.of(line), PaymentMethod.BANK_TRANSFER, "FT1", "ghi chú"))).isEmpty();
        // Khách lẻ không để lại số điện thoại thì gửi chuỗi rỗng cũng được.
        assertThat(validator.validate(new CounterInvoiceRequest(
                null, null, "", List.of(line), PaymentMethod.CASH, null, null))).isEmpty();
    }

    @Test
    void counterInvoice_needsExamOrAtLeastOneProduct() {
        assertThat(paths(new CounterInvoiceRequest(null, null, null, null, PaymentMethod.CASH, null, null)))
                .contains("contentPresent");
        assertThat(paths(new CounterInvoiceRequest(null, null, null, List.of(), PaymentMethod.CASH, null, null)))
                .contains("contentPresent");
    }

    @Test
    void counterInvoice_onlyCashOrBankTransfer() {
        UUID exam = UUID.randomUUID();
        assertThat(paths(new CounterInvoiceRequest(exam, null, null, null, PaymentMethod.COD, null, null)))
                .contains("counterPaymentMethod");
        assertThat(paths(new CounterInvoiceRequest(exam, null, null, null, null, null, null)))
                .contains("paymentMethod");
    }

    @Test
    void counterInvoice_rejectsBadLinesAndPhone() {
        UUID exam = UUID.randomUUID();
        assertThat(paths(new CounterInvoiceRequest(exam, null, null,
                List.of(new CounterInvoiceRequest.Line(UUID.randomUUID(), 0)), PaymentMethod.CASH, null, null)))
                .contains("items[0].quantity");
        assertThat(paths(new CounterInvoiceRequest(exam, null, null,
                List.of(new CounterInvoiceRequest.Line(null, 1)), PaymentMethod.CASH, null, null)))
                .contains("items[0].productId");
        assertThat(paths(new CounterInvoiceRequest(exam, null, "12345", null, PaymentMethod.CASH, null, null)))
                .contains("customerPhone");
    }

    @Test
    void addToCart_quantityMustBeAtLeastOne() {
        assertThat(paths(new AddToCartRequest(UUID.randomUUID(), 0))).contains("quantity");
        assertThat(paths(new AddToCartRequest(UUID.randomUUID(), -3))).contains("quantity");
        assertThat(validator.validate(new AddToCartRequest(UUID.randomUUID(), 1))).isEmpty();
    }

    @Test
    void addToCart_productIdRequired() {
        assertThat(paths(new AddToCartRequest(null, 2))).contains("productId");
    }

    @Test
    void updateCartItem_zeroIsRejected() {
        // Bỏ hẳn sản phẩm thì dùng DELETE, không truyền 0.
        assertThat(paths(new UpdateCartItemRequest(0))).contains("quantity");
    }

    @Test
    void cancelReason_isRequired() {
        assertThat(paths(new CancelOrderRequest("  "))).contains("reason");
        assertThat(validator.validate(new CancelOrderRequest("Đổi ý"))).isEmpty();
    }

    private <T> Set<String> paths(T target) {
        return validator.validate(target).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
