package cc.orbexa.hhy.access.storage;

import cc.orbexa.hhy.access.storage.StorageMigrationService.PortResolver;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Provider;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.util.Objects;

/** Resolves only production-ready storage ports; unsupported providers fail closed. */
public final class R04StoragePortResolver implements PortResolver {
    private final StorageObjectPort r2;
    private final StorageObjectPort oss;

    public R04StoragePortResolver(StorageObjectPort r2, StorageObjectPort oss) {
        this.r2 = Objects.requireNonNull(r2, "r2");
        this.oss = Objects.requireNonNull(oss, "oss");
    }

    @Override
    public StorageObjectPort resolve(Provider provider) {
        if (provider == Provider.CLOUDFLARE_R2) return r2;
        if (provider == Provider.ALIYUN_OSS) return oss;
        throw new BusinessException("COMMON-422-BUSINESS_RULE", "对象存储供应商不可用", 422, true);
    }
}
