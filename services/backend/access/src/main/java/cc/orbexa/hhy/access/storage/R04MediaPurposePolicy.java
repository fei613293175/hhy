package cc.orbexa.hhy.access.storage;

import cc.orbexa.hhy.access.storage.MediaUploadService.StorageSelection;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Scope;
import java.util.Locale;

/** Resolves only the six frozen scope identifiers; there is no implicit public default. */
public final class R04MediaPurposePolicy implements MediaUploadService.PurposePolicy {
    private final R04StoragePostgresStore bindings;

    public R04MediaPurposePolicy(R04StoragePostgresStore bindings) {
        this.bindings = bindings;
    }

    @Override
    public StorageSelection resolve(long userId, String purpose, String contentType, long sizeBytes) {
        final Scope scope;
        try {
            scope = Scope.valueOf(purpose.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalid) {
            return null;
        }
        try {
            return bindings.findActiveMediaBinding(scope)
                    .map(binding -> new StorageSelection(scope, binding.id()))
                    .orElse(null);
        } catch (IllegalStateException invalidActiveConfiguration) {
            return null;
        }
    }
}
