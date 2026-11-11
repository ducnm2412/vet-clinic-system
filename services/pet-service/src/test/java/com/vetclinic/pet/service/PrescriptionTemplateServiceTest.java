package com.vetclinic.pet.service;

import com.vetclinic.pet.dto.MedicationRequest;
import com.vetclinic.pet.dto.MedicationResponse;
import com.vetclinic.pet.dto.PrescriptionTemplateItemRequest;
import com.vetclinic.pet.dto.PrescriptionTemplateRequest;
import com.vetclinic.pet.dto.PrescriptionTemplateResponse;
import com.vetclinic.pet.dto.PrescriptionTemplateSummary;
import com.vetclinic.pet.exception.DuplicateTemplateException;
import com.vetclinic.pet.exception.InvalidTemplateException;
import com.vetclinic.pet.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Đơn mẫu theo bệnh. Transaction rollback sau mỗi test, chạy trên pet_db_test (VD-12). */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class PrescriptionTemplateServiceTest {

    @Autowired private PrescriptionTemplateService templateService;
    @Autowired private MedicationService medicationService;

    private final UUID doctor = UUID.randomUUID();

    private MedicationResponse medication(String name) {
        return medicationService.create(new MedicationRequest(name, "gói", "Lưu ý của " + name, null));
    }

    private PrescriptionTemplateItemRequest item(MedicationResponse m, String dosage) {
        return new PrescriptionTemplateItemRequest(m.id(), dosage, "2 lần/ngày", 3, null);
    }

    private PrescriptionTemplateRequest template(String name, PrescriptionTemplateItemRequest... items) {
        return new PrescriptionTemplateRequest(name, "Viêm ruột tiêu chảy cấp", "Bù nước", null, List.of(items));
    }

    @Test
    void createKeepsItemOrderAndExposesMedicationDetails() {
        MedicationResponse a = medication("Tpl Thuoc A");
        MedicationResponse b = medication("Tpl Thuoc B");
        MedicationResponse c = medication("Tpl Thuoc C");

        // Cố tình không theo thứ tự tên để chứng tỏ thứ tự là thứ tự gửi lên, không phải thứ tự DB tự trả.
        PrescriptionTemplateResponse created = templateService.create(doctor,
                new PrescriptionTemplateRequest("  Tpl Tieu chay  ", "  Viêm ruột  ", "  ", null,
                        List.of(item(c, "1 gói"), item(a, "2 viên"), item(b, "3 ml"))));

        assertThat(created.name()).isEqualTo("Tpl Tieu chay");
        assertThat(created.diagnosis()).isEqualTo("Viêm ruột");
        // Ô để trống thành null — "chưa ghi" khác "ghi rỗng".
        assertThat(created.treatment()).isNull();
        assertThat(created.createdBy()).isEqualTo(doctor);

        PrescriptionTemplateResponse loaded = templateService.get(created.id());
        assertThat(loaded.items()).extracting(PrescriptionTemplateResponse.Item::medicationName)
                .containsExactly("Tpl Thuoc C", "Tpl Thuoc A", "Tpl Thuoc B");
        assertThat(loaded.items().get(0).medicationCaution()).isEqualTo("Lưu ý của Tpl Thuoc C");
        assertThat(loaded.items().get(0).medicationUnit()).isEqualTo("gói");
        assertThat(loaded.items().get(0).medicationActive()).isTrue();
        assertThat(loaded.items().get(1).dosage()).isEqualTo("2 viên");
    }

    @Test
    void templateWithoutMedicationsIsAllowed() {
        PrescriptionTemplateResponse created = templateService.create(doctor,
                new PrescriptionTemplateRequest("Tpl Theo doi", "Theo dõi", null, null, null));

        assertThat(created.items()).isEmpty();
        assertThat(templateService.get(created.id()).items()).isEmpty();
    }

    @Test
    void medicationMustExistInCatalogAndBeActive() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> templateService.create(doctor,
                template("Tpl Thieu thuoc", new PrescriptionTemplateItemRequest(missing, "1", "1", 1, null))))
                .isInstanceOf(InvalidTemplateException.class);

        MedicationResponse hidden = medication("Tpl Thuoc An");
        medicationService.deactivate(hidden.id());
        assertThatThrownBy(() -> templateService.create(doctor, template("Tpl Thuoc an", item(hidden, "1"))))
                .isInstanceOf(InvalidTemplateException.class)
                .hasMessageContaining("Tpl Thuoc An");
    }

    @Test
    void duplicateNameIsRejectedIgnoringCaseAndDiacritics() {
        templateService.create(doctor, template("Tpl Tiêu Chảy"));

        assertThatThrownBy(() -> templateService.create(doctor, template("tpl tieu chay")))
                .isInstanceOf(DuplicateTemplateException.class);
    }

    @Test
    void updateReplacesAllItemsAndKeepsCreator() {
        MedicationResponse a = medication("Tpl Sua A");
        MedicationResponse b = medication("Tpl Sua B");
        PrescriptionTemplateResponse created = templateService.create(doctor,
                template("Tpl Sua", item(a, "1 gói")));

        UUID otherDoctor = UUID.randomUUID();
        PrescriptionTemplateResponse updated = templateService.update(created.id(),
                new PrescriptionTemplateRequest("Tpl Sua Moi", "Chẩn đoán mới", "Điều trị mới", "Ghi chú",
                        List.of(item(b, "5 ml"))));

        assertThat(updated.name()).isEqualTo("Tpl Sua Moi");
        assertThat(updated.diagnosis()).isEqualTo("Chẩn đoán mới");
        assertThat(updated.notes()).isEqualTo("Ghi chú");
        assertThat(updated.items()).extracting(PrescriptionTemplateResponse.Item::medicationName)
                .containsExactly("Tpl Sua B");
        // Người sửa không đổi người tạo.
        assertThat(updated.createdBy()).isEqualTo(doctor);
        assertThat(otherDoctor).isNotEqualTo(updated.createdBy());
    }

    @Test
    void updateMayKeepAMedicationHiddenSinceButNotAddAHiddenOne() {
        MedicationResponse a = medication("Tpl Giu A");
        MedicationResponse hiddenLater = medication("Tpl Giu An");
        PrescriptionTemplateResponse created = templateService.create(doctor,
                template("Tpl Giu", item(a, "1"), item(hiddenLater, "2")));

        medicationService.deactivate(hiddenLater.id());

        // Sửa chẩn đoán nhưng giữ nguyên danh sách thuốc: không bị chặn vì thuốc đã ẩn từ trước.
        PrescriptionTemplateResponse kept = templateService.update(created.id(),
                new PrescriptionTemplateRequest("Tpl Giu", "Chẩn đoán khác", null, null,
                        List.of(item(a, "1"), item(hiddenLater, "2"))));
        assertThat(kept.items()).hasSize(2);
        assertThat(kept.items().get(1).medicationActive()).isFalse();

        // Nhưng thuốc ẩn không nằm sẵn trong mẫu thì không thêm được.
        MedicationResponse other = medication("Tpl Giu Khac");
        medicationService.deactivate(other.id());
        assertThatThrownBy(() -> templateService.update(created.id(),
                template("Tpl Giu", item(a, "1"), item(other, "1"))))
                .isInstanceOf(InvalidTemplateException.class);
    }

    @Test
    void updateMayKeepOwnNameButNotAnothersName() {
        PrescriptionTemplateResponse first = templateService.create(doctor, template("Tpl Ten 1"));
        templateService.create(doctor, template("Tpl Ten 2"));

        assertThat(templateService.update(first.id(), template("Tpl Ten 1")).name()).isEqualTo("Tpl Ten 1");
        assertThatThrownBy(() -> templateService.update(first.id(), template("tpl ten 2")))
                .isInstanceOf(DuplicateTemplateException.class);
    }

    @Test
    void deleteRemovesTemplateButKeepsMedications() {
        MedicationResponse a = medication("Tpl Xoa A");
        PrescriptionTemplateResponse created = templateService.create(doctor, template("Tpl Xoa", item(a, "1")));

        templateService.delete(created.id());

        assertThatThrownBy(() -> templateService.get(created.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(medicationService.search("tpl xoa a", true, null)).hasSize(1);
    }

    @Test
    void searchFiltersByNameIgnoringDiacriticsAndReportsItemCount() {
        MedicationResponse a = medication("Tpl Dem A");
        MedicationResponse b = medication("Tpl Dem B");
        templateService.create(doctor, template("Tpl Viêm da", item(a, "1"), item(b, "1")));
        templateService.create(doctor, template("Tpl Tiêu chảy"));

        List<PrescriptionTemplateSummary> found = templateService.search("viem da");

        assertThat(found).extracting(PrescriptionTemplateSummary::name).containsExactly("Tpl Viêm da");
        assertThat(found.get(0).itemCount()).isEqualTo(2);
        assertThat(templateService.search("tpl")).extracting(PrescriptionTemplateSummary::name)
                .containsExactly("Tpl Tiêu chảy", "Tpl Viêm da");
        // "_" không được thành ký tự đại diện.
        assertThat(templateService.search("tpl_")).isEmpty();
    }

    @Test
    void unknownIdThrowsNotFound() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> templateService.get(missing)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> templateService.update(missing, template("Tpl X")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> templateService.delete(missing)).isInstanceOf(ResourceNotFoundException.class);
    }
}
