package xenosoft.imldintelligence.module.notify.internal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import xenosoft.imldintelligence.common.dto.PageQueryRequest;
import xenosoft.imldintelligence.common.dto.PagedResultResponse;
import xenosoft.imldintelligence.module.identity.internal.model.Patient;
import xenosoft.imldintelligence.module.identity.internal.repository.PatientRepository;
import xenosoft.imldintelligence.module.identity.internal.service.SensitiveDataEncryptor;
import xenosoft.imldintelligence.module.notify.api.dto.NotifyApiDtos;
import xenosoft.imldintelligence.module.notify.internal.model.NotificationDelivery;
import xenosoft.imldintelligence.module.notify.internal.model.NotificationMessage;
import xenosoft.imldintelligence.module.notify.internal.repository.NotificationDeliveryRepository;
import xenosoft.imldintelligence.module.notify.internal.repository.NotificationMessageRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 通知消息编排、短信发送和送达记录服务。
 */
@Service
@RequiredArgsConstructor
public class NotificationApplicationService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final NotificationMessageRepository messageRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final PatientRepository patientRepository;
    private final SensitiveDataEncryptor sensitiveDataEncryptor;
    private final PatientSmsSender patientSmsSender;

    @Transactional(readOnly = true)
    public PagedResultResponse<NotifyApiDtos.Response.NotificationMessageResponse> listMessages(
            Long tenantId,
            NotifyApiDtos.Query.NotificationMessagePageQuery query,
            PageQueryRequest pageQuery) {
        NotifyApiDtos.Query.NotificationMessagePageQuery q = query == null
                ? new NotifyApiDtos.Query.NotificationMessagePageQuery(null, null, null, null, null, null, null)
                : query;
        List<NotificationMessage> matching = messageRepository.listByTenantId(tenantId).stream()
                .filter(item -> matches(item.getBizType(), q.bizType()))
                .filter(item -> matches(item.getBizId(), q.bizId()))
                .filter(item -> matches(item.getReceiverType(), q.receiverType()))
                .filter(item -> q.receiverRefId() == null || Objects.equals(q.receiverRefId(), item.getReceiverRefId()))
                .filter(item -> matches(item.getStatus(), q.status()))
                .filter(item -> q.scheduledFrom() == null
                        || item.getScheduledAt() != null && !item.getScheduledAt().isBefore(q.scheduledFrom()))
                .filter(item -> q.scheduledTo() == null
                        || item.getScheduledAt() != null && !item.getScheduledAt().isAfter(q.scheduledTo()))
                .sorted(Comparator
                        .comparing(NotificationMessage::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(NotificationMessage::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
        Page page = page(pageQuery);
        return new PagedResultResponse<>(
                page.index(),
                page.size(),
                matching.size(),
                slice(matching, page).stream().map(this::toMessageResponse).toList()
        );
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public NotifyApiDtos.Response.NotificationMessageResponse createMessage(
            Long tenantId,
            NotifyApiDtos.Request.CreateNotificationMessageRequest request) {
        String channel = normalize(request.channel(), "APP");
        NotificationMessage message = new NotificationMessage();
        message.setTenantId(tenantId);
        message.setBizType(request.bizType().trim());
        message.setBizId(request.bizId().trim());
        message.setReceiverType(request.receiverType().trim().toUpperCase(Locale.ROOT));
        message.setReceiverRefId(request.receiverRefId());
        message.setTitle(request.title().trim());
        message.setContent(request.content().trim());
        message.setChannel(channel);
        message.setStatus("PENDING");
        message.setScheduledAt(request.scheduledAt());
        message.setCreatedAt(now());
        message = messageRepository.save(message);

        try {
            if ("SMS".equals(channel)) {
                String mobile = resolvePatientMobile(tenantId, message);
                PatientSmsSender.SmsSendResult result = patientSmsSender.send(
                        mobile, message.getTitle(), message.getContent(), message.getBizType(), message.getBizId());
                markSent(message);
                saveDelivery(tenantId, message.getId(), result.providerName(), result.providerMessageId(),
                        "ACCEPTED", null, now());
            } else {
                markSent(message);
                saveDelivery(tenantId, message.getId(), "INTERNAL", null,
                        "DELIVERED", null, now());
            }
        } catch (RuntimeException ex) {
            message.setStatus("FAILED");
            messageRepository.update(message);
            saveDelivery(tenantId, message.getId(), "LOCAL_OR_EXTERNAL", null,
                    "FAILED", ex.getMessage(), null);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Notification delivery failed", ex);
        }
        return toMessageResponse(message);
    }

    @Transactional(readOnly = true)
    public PagedResultResponse<NotifyApiDtos.Response.NotificationDeliveryResponse> listDeliveries(
            Long tenantId,
            NotifyApiDtos.Query.NotificationDeliveryPageQuery query,
            PageQueryRequest pageQuery) {
        NotifyApiDtos.Query.NotificationDeliveryPageQuery q = query == null
                ? new NotifyApiDtos.Query.NotificationDeliveryPageQuery(null, null, null, null, null)
                : query;
        List<NotificationDelivery> matching = deliveryRepository.listByTenantId(tenantId).stream()
                .filter(item -> q.messageId() == null || Objects.equals(q.messageId(), item.getMessageId()))
                .filter(item -> matches(item.getProviderName(), q.providerName()))
                .filter(item -> matches(item.getDeliveryStatus(), q.deliveryStatus()))
                .filter(item -> q.deliveredFrom() == null
                        || item.getDeliveredAt() != null && !item.getDeliveredAt().isBefore(q.deliveredFrom()))
                .filter(item -> q.deliveredTo() == null
                        || item.getDeliveredAt() != null && !item.getDeliveredAt().isAfter(q.deliveredTo()))
                .sorted(Comparator
                        .comparing(NotificationDelivery::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(NotificationDelivery::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
        Page page = page(pageQuery);
        return new PagedResultResponse<>(
                page.index(),
                page.size(),
                matching.size(),
                slice(matching, page).stream().map(this::toDeliveryResponse).toList()
        );
    }

    @Transactional
    public NotifyApiDtos.Response.NotificationDeliveryResponse recordDeliveryCallback(
            Long tenantId,
            NotifyApiDtos.Request.RecordDeliveryCallbackRequest request) {
        NotificationMessage message = messageRepository.findById(tenantId, request.messageId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "messageId does not exist"));
        NotificationDelivery delivery = saveDelivery(
                tenantId,
                message.getId(),
                request.providerName(),
                request.providerMessageId(),
                request.deliveryStatus(),
                request.failReason(),
                request.deliveredAt()
        );
        delivery.setCallbackPayload(request.callbackPayload());
        deliveryRepository.update(delivery);
        if ("DELIVERED".equalsIgnoreCase(request.deliveryStatus())) {
            message.setStatus("SENT");
            message.setSentAt(request.deliveredAt() == null ? now() : request.deliveredAt());
            messageRepository.update(message);
        } else if ("FAILED".equalsIgnoreCase(request.deliveryStatus())) {
            message.setStatus("FAILED");
            messageRepository.update(message);
        }
        return toDeliveryResponse(delivery);
    }

    private String resolvePatientMobile(Long tenantId, NotificationMessage message) {
        if (!"PATIENT".equalsIgnoreCase(message.getReceiverType()) || message.getReceiverRefId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "SMS requires receiverType=PATIENT and receiverRefId");
        }
        Patient patient = patientRepository.findById(tenantId, message.getReceiverRefId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "patient receiver does not exist"));
        if (patient.getMobileEncrypted() == null || patient.getMobileEncrypted().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patient mobile number is unavailable");
        }
        return sensitiveDataEncryptor.decrypt(patient.getMobileEncrypted());
    }

    private void markSent(NotificationMessage message) {
        message.setStatus("SENT");
        message.setSentAt(now());
        messageRepository.update(message);
    }

    private NotificationDelivery saveDelivery(Long tenantId,
                                              Long messageId,
                                              String providerName,
                                              String providerMessageId,
                                              String deliveryStatus,
                                              String failReason,
                                              OffsetDateTime deliveredAt) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setTenantId(tenantId);
        delivery.setMessageId(messageId);
        delivery.setProviderName(providerName);
        delivery.setProviderMessageId(providerMessageId);
        delivery.setDeliveryStatus(deliveryStatus);
        delivery.setFailReason(failReason);
        delivery.setDeliveredAt(deliveredAt);
        delivery.setCreatedAt(now());
        return deliveryRepository.save(delivery);
    }

    private NotifyApiDtos.Response.NotificationMessageResponse toMessageResponse(NotificationMessage message) {
        return new NotifyApiDtos.Response.NotificationMessageResponse(
                message.getId(),
                message.getBizType(),
                message.getBizId(),
                new NotifyApiDtos.Shared.MessageRoutingItem(
                        message.getReceiverType(), message.getReceiverRefId(), message.getChannel()),
                message.getTitle(),
                message.getContent(),
                message.getStatus(),
                message.getScheduledAt(),
                message.getSentAt(),
                message.getCreatedAt()
        );
    }

    private NotifyApiDtos.Response.NotificationDeliveryResponse toDeliveryResponse(NotificationDelivery delivery) {
        return new NotifyApiDtos.Response.NotificationDeliveryResponse(
                delivery.getId(),
                delivery.getMessageId(),
                delivery.getProviderName(),
                delivery.getProviderMessageId(),
                delivery.getDeliveryStatus(),
                delivery.getFailReason(),
                delivery.getCallbackPayload(),
                delivery.getDeliveredAt(),
                delivery.getCreatedAt()
        );
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank() || actual != null && actual.equalsIgnoreCase(expected.trim());
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private <T> List<T> slice(List<T> items, Page page) {
        long offset = (long) page.index() * page.size();
        if (offset >= items.size()) {
            return List.of();
        }
        int from = (int) offset;
        return items.subList(from, Math.min(items.size(), from + page.size()));
    }

    private Page page(PageQueryRequest pageQuery) {
        int index = pageQuery == null || pageQuery.page() == null
                ? DEFAULT_PAGE : Math.max(0, pageQuery.page());
        int size = pageQuery == null || pageQuery.size() == null
                ? DEFAULT_SIZE : Math.min(200, Math.max(1, pageQuery.size()));
        return new Page(index, size);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    private record Page(int index, int size) {
    }
}
