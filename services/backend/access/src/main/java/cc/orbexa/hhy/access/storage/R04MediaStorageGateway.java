package cc.orbexa.hhy.access.storage;

import cc.orbexa.hhy.access.storage.MediaUploadService.MediaObject;
import cc.orbexa.hhy.access.storage.MediaUploadService.ReadTicket;
import cc.orbexa.hhy.access.storage.MediaUploadService.StorageSelection;
import cc.orbexa.hhy.access.storage.MediaUploadService.StoredUpload;
import cc.orbexa.hhy.access.storage.MediaUploadService.UploadCommand;
import cc.orbexa.hhy.access.storage.MediaUploadService.UploadSession;
import cc.orbexa.hhy.access.storage.MediaUploadService.UploadTicket;
import cc.orbexa.hhy.access.storage.R04StoragePostgresStore.MediaBindingRecord;
import cc.orbexa.hhy.access.storage.StorageMigrationService.PortResolver;
import cc.orbexa.hhy.access.storage.StorageObjectPort.CompleteUpload;
import cc.orbexa.hhy.access.storage.StorageObjectPort.CompletedPart;
import cc.orbexa.hhy.access.storage.StorageObjectPort.ObjectRef;
import cc.orbexa.hhy.access.storage.StorageObjectPort.UploadIntent;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Adapts the media aggregate to the provider-neutral R2/OSS port. */
public class R04MediaStorageGateway implements MediaUploadService.MediaStorageGateway {
    private final R04StoragePostgresStore bindings;
    private final PortResolver ports;

    public R04MediaStorageGateway(R04StoragePostgresStore bindings, PortResolver ports) {
        this.bindings = bindings;
        this.ports = ports;
    }

    @Override
    public UploadTicket createUpload(StorageSelection selection, UploadCommand command) {
        MediaBindingRecord binding = active(selection.bindingId());
        if (binding.binding().scope() != selection.scope()) throw unavailable();
        StorageObjectPort.UploadTicket ticket = ports.resolve(binding.binding().provider()).createUpload(
                binding.binding(), new UploadIntent(command.ownerId(), command.purpose(), command.fileName(),
                        command.contentType(), command.sizeBytes(), command.sha256(), command.idempotencyKey()));
        return new UploadTicket(ticket.objectKey(), null, ticket.uploadUrl(), ticket.expiresAt());
    }

    @Override
    public StoredUpload completeUpload(
            UploadSession session, String etag, List<Map<String, Object>> parts, String idempotencyKey) {
        MediaBindingRecord binding = active(session.bindingId());
        if (binding.binding().scope() != session.scope()) throw unavailable();
        StorageObjectPort.StoredObject stored = ports.resolve(binding.binding().provider()).completeUpload(
                binding.binding(), new CompleteUpload(session.ownerId(), session.objectKey(), etag,
                        completedParts(parts), session.sha256(), session.sizeBytes(), idempotencyKey));
        return new StoredUpload(stored.objectKey(), stored.etag(), stored.sizeBytes(),
                stored.sha256(), stored.storedAt());
    }

    @Override
    public ReadTicket createReadUrl(MediaObject media) {
        MediaBindingRecord binding = active(media.bindingId());
        return createReadUrl(media, binding, binding.privatePreviewTtl());
    }

    /** Creates a shorter administrator preview without exceeding the activated storage ceiling. */
    public ReadTicket createReadUrl(MediaObject media, Duration requestedTtl) {
        MediaBindingRecord binding = active(media.bindingId());
        if (requestedTtl == null || requestedTtl.isZero() || requestedTtl.isNegative()
                || requestedTtl.compareTo(binding.privatePreviewTtl()) > 0) {
            throw new BusinessException(
                    "COMMON-400-VALIDATION", "预览有效期不符合要求", 400, false);
        }
        return createReadUrl(media, binding, requestedTtl);
    }

    private ReadTicket createReadUrl(
            MediaObject media, MediaBindingRecord binding, Duration requestedTtl) {
        if (binding.binding().scope() != media.scope()) throw unavailable();
        StorageObjectPort.ReadTicket ticket = ports.resolve(binding.binding().provider()).createReadUrl(
                binding.binding(), new ObjectRef(media.ownerId(), media.objectKey(), media.sha256()),
                requestedTtl);
        return new ReadTicket(ticket.readUrl(), ticket.expiresAt());
    }

    private MediaBindingRecord active(long id) {
        try {
            return bindings.findMediaBinding(id).orElseThrow(R04MediaStorageGateway::unavailable);
        } catch (BusinessException failure) {
            throw failure;
        } catch (RuntimeException invalidActiveConfiguration) {
            throw unavailable();
        }
    }

    private static List<CompletedPart> completedParts(List<Map<String, Object>> rawParts) {
        if (rawParts == null || rawParts.isEmpty()) return List.of();
        List<CompletedPart> parts = new ArrayList<>(rawParts.size());
        for (Map<String, Object> raw : rawParts) {
            Object number = raw.get("number");
            Object etag = raw.get("etag");
            if (!(number instanceof Number value) || value.intValue() < 1
                    || !(etag instanceof String text) || text.isBlank()) {
                throw new BusinessException(
                        "COMMON-400-VALIDATION", "分片完成信息不符合要求", 400, false);
            }
            parts.add(new CompletedPart(value.intValue(), text));
        }
        return List.copyOf(parts);
    }

    private static BusinessException unavailable() {
        return new BusinessException(
                "COMMON-422-BUSINESS_RULE", "对象存储尚未激活，请稍后重试", 422, true);
    }
}
