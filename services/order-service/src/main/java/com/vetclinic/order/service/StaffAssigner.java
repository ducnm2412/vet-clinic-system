package com.vetclinic.order.service;

import com.vetclinic.order.client.AuthInternalClient;
import com.vetclinic.order.client.StaffInternalClient;
import com.vetclinic.order.domain.Order;
import com.vetclinic.order.domain.OrderChannel;
import com.vetclinic.order.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Chia đơn hàng mới cho nhân viên theo vòng: chỉ xét nhân viên có ca trong ngày, ai hôm nay được giao ít đơn nhất
 * thì nhận đơn tiếp theo (bằng nhau thì người được giao lâu nhất). Nhờ vậy mỗi đơn có một người phụ trách rõ ràng
 * thay vì báo chung cho tất cả rồi làm trùng hoặc bỏ sót.
 *
 * Không chia được (hôm đó không ai có ca, hoặc auth-service/staff-service không trả lời) thì để trống: đơn vẫn được
 * báo cho mọi nhân viên như trước, không bao giờ vì lỗi chia mà kẹt đơn hay làm đơn của khách thất bại.
 */
@Slf4j
@Component
public class StaffAssigner {

    static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String STAFF_ROLE = "STAFF";

    private final AuthInternalClient authClient;
    private final StaffInternalClient staffClient;
    private final OrderRepository orderRepository;
    private final String internalKey;

    public StaffAssigner(AuthInternalClient authClient, StaffInternalClient staffClient,
                         OrderRepository orderRepository, @Value("${app.internal-api-key:}") String internalKey) {
        this.authClient = authClient;
        this.staffClient = staffClient;
        this.orderRepository = orderRepository;
        this.internalKey = internalKey;
    }

    /** Gắn nhân viên phụ trách vào đơn nếu chia được; hoá đơn quầy không chia (nhân viên tự lập). */
    public void assign(Order order) {
        if (order.getChannel() == OrderChannel.COUNTER || order.getAssignedStaffId() != null) {
            return;
        }
        try {
            LocalDate today = LocalDate.now(CLINIC_ZONE);
            pick(today).ifPresent(staffId -> {
                order.setAssignedStaffId(staffId);
                order.setAssignedStaffName(nameOf(staffId));
                order.setAssignedAt(Instant.now());
                log.info("Đơn {} giao cho nhân viên {}", order.getOrderCode(), staffId);
            });
        } catch (RuntimeException e) {
            log.warn("Không chia được đơn {} cho nhân viên, sẽ báo chung: {}", order.getOrderCode(), e.getMessage());
        }
    }

    /** Họ tên để ghi lên đơn; tra không được thì để trống (giao diện hiện "Nhân viên khác"), không làm hỏng việc chính. */
    public String nameOf(UUID staffId) {
        try {
            String name = authClient.userNames(List.of(staffId), internalKey).get(staffId);
            return name == null || name.isBlank() ? null : name;
        } catch (RuntimeException e) {
            log.warn("Không tra được tên nhân viên {}: {}", staffId, e.getMessage());
            return null;
        }
    }

    /** Nhân viên có ca hôm nay và đang nhận ít đơn nhất; rỗng nếu không ai có ca. */
    Optional<UUID> pick(LocalDate day) {
        List<UUID> onShift = staffClient.userIdsOnShift(day.toString(), internalKey);
        List<UUID> candidates = new ArrayList<>(authClient.userIdsByRole(STAFF_ROLE, internalKey));
        candidates.retainAll(onShift);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Instant since = day.atStartOfDay(CLINIC_ZONE).toInstant();
        Map<UUID, Long> count = new HashMap<>();
        Map<UUID, Instant> last = new HashMap<>();
        for (Object[] row : orderRepository.assignmentLoad(candidates, since)) {
            count.put((UUID) row[0], (Long) row[1]);
            last.put((UUID) row[0], (Instant) row[2]);
        }
        return candidates.stream().min(Comparator
                .comparingLong((UUID id) -> count.getOrDefault(id, 0L))
                .thenComparing(id -> last.getOrDefault(id, Instant.MIN))
                .thenComparing(UUID::compareTo));
    }
}
