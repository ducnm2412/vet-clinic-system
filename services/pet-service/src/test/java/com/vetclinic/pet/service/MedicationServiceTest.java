package com.vetclinic.pet.service;

import com.vetclinic.pet.dto.MedicationRequest;
import com.vetclinic.pet.dto.MedicationResponse;
import com.vetclinic.pet.exception.DuplicateMedicationException;
import com.vetclinic.pet.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Danh mục thuốc. Transaction rollback sau mỗi test, chạy trên pet_db_test (VD-12). */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class MedicationServiceTest {

    @Autowired private MedicationService medicationService;

    private MedicationResponse add(String name) {
        return medicationService.create(new MedicationRequest(name, null, null, null));
    }

    private List<String> names(String q) {
        return medicationService.search(q, false, null).stream().map(MedicationResponse::name).toList();
    }

    @Test
    void normalizeStripsDiacriticsAndCase() {
        assertThat(TextNormalizer.normalize("  Đau  Bụng ")).isEqualTo("dau bung");
        assertThat(TextNormalizer.normalize("Tiêu Độc")).isEqualTo("tieu doc");
        assertThat(TextNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void createTrimsAndStoresOptionalFieldsAsNullWhenBlank() {
        MedicationResponse created = medicationService.create(
                new MedicationRequest("  Smecta Test  ", " gói ", "   ", null));

        assertThat(created.name()).isEqualTo("Smecta Test");
        assertThat(created.unit()).isEqualTo("gói");
        // Ô trống thành null chứ không phải chuỗi rỗng — "chưa có lưu ý" khác "lưu ý rỗng".
        assertThat(created.caution()).isNull();
        assertThat(created.active()).isTrue();
    }

    @Test
    void duplicateNameIsRejectedIgnoringCaseAndDiacritics() {
        add("Tiêu Độc Test");

        assertThatThrownBy(() -> add("tieu doc test")).isInstanceOf(DuplicateMedicationException.class);
    }

    @Test
    void searchIsDiacriticInsensitiveAndRanksPrefixFirst() {
        add("Zzsmecta Test");
        add("Smecta Test");
        add("Kháng sinh Test");

        // Khớp đầu tên xếp trước khớp giữa tên.
        assertThat(names("smec")).containsExactly("Smecta Test", "Zzsmecta Test");
        // Gõ không dấu vẫn ra thuốc có dấu.
        assertThat(names("khang sinh")).containsExactly("Kháng sinh Test");
        assertThat(names("khung sinh")).isEmpty();
    }

    @Test
    void searchTreatsPercentAndUnderscoreAsPlainText() {
        add("Thuoc 5% Test");
        add("Thuoc Abc Test");

        assertThat(names("5%")).containsExactly("Thuoc 5% Test");
        // "_" không được thành ký tự đại diện khớp mọi chữ.
        assertThat(names("thuoc_")).isEmpty();
    }

    @Test
    void deactivatedMedicationDisappearsFromSuggestionsButStaysForManagement() {
        MedicationResponse m = add("Thuoc An Test");

        medicationService.deactivate(m.id());
        medicationService.deactivate(m.id()); // gọi lại không lỗi

        assertThat(names("thuoc an test")).isEmpty();
        assertThat(medicationService.search("thuoc an test", true, null))
                .extracting(MedicationResponse::name).containsExactly("Thuoc An Test");
    }

    @Test
    void unitPriceIsStoredAndCanBeChangedOrCleared() {
        MedicationResponse created = medicationService.create(
                new MedicationRequest("Thuoc Gia Test", "viên", null, null, new BigDecimal("12500")));
        assertThat(created.unitPrice()).isEqualByComparingTo("12500");

        MedicationResponse repriced = medicationService.update(created.id(),
                new MedicationRequest("Thuoc Gia Test", "viên", null, null, new BigDecimal("15000")));
        assertThat(repriced.unitPrice()).isEqualByComparingTo("15000");

        // Ô giá để trống = chưa có giá (quầy nhập tay), không phải 0 đồng.
        MedicationResponse cleared = medicationService.update(created.id(),
                new MedicationRequest("Thuoc Gia Test", "viên", null, null, null));
        assertThat(cleared.unitPrice()).isNull();
    }

    @Test
    void updateChangesFieldsAndCanReactivate() {
        MedicationResponse m = add("Thuoc Sua Test");
        medicationService.deactivate(m.id());

        MedicationResponse updated = medicationService.update(m.id(),
                new MedicationRequest("Thuoc Sua Moi Test", "viên", "Không dùng cho mèo", true));

        assertThat(updated.name()).isEqualTo("Thuoc Sua Moi Test");
        assertThat(updated.caution()).isEqualTo("Không dùng cho mèo");
        assertThat(updated.active()).isTrue();
        assertThat(names("sua moi")).containsExactly("Thuoc Sua Moi Test");
    }

    @Test
    void updateKeepsActiveFlagWhenNotSentAndAllowsOwnNameButNotAnothers() {
        MedicationResponse a = add("Thuoc A Test");
        add("Thuoc B Test");
        medicationService.deactivate(a.id());

        // Gửi lại đúng tên của chính nó thì không bị coi là trùng, và cờ active để trống thì giữ nguyên.
        MedicationResponse same = medicationService.update(a.id(),
                new MedicationRequest("Thuoc A Test", "ml", null, null));
        assertThat(same.active()).isFalse();

        assertThatThrownBy(() -> medicationService.update(a.id(),
                new MedicationRequest("thuoc b test", null, null, null)))
                .isInstanceOf(DuplicateMedicationException.class);
    }

    @Test
    void updateAndDeactivateUnknownIdThrowNotFound() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> medicationService.update(missing,
                new MedicationRequest("X", null, null, null))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> medicationService.deactivate(missing))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void limitIsClamped() {
        for (int i = 0; i < 3; i++) {
            add("Thuoc Gioi Han " + i + " Test");
        }

        assertThat(medicationService.search("gioi han", false, 2)).hasSize(2);
        // Giới hạn không hợp lệ không được làm vỡ truy vấn.
        assertThat(medicationService.search("gioi han", false, 0)).hasSize(1);
        assertThat(medicationService.search("gioi han", false, 9999)).hasSize(3);
    }
}
