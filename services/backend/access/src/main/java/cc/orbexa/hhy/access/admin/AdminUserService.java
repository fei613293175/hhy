package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.AdminUserContracts.PageMetaResource;
import cc.orbexa.hhy.access.admin.AdminUserContracts.UserPageResource;
import cc.orbexa.hhy.access.admin.AdminUserContracts.UserResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private static final Map<String, String> SORTS = Map.of(
            "createdAt:desc", "u.created_at DESC,u.id DESC",
            "createdAt:asc", "u.created_at ASC,u.id ASC",
            "status:asc", "u.status ASC,u.id ASC",
            "status:desc", "u.status DESC,u.id DESC",
            "id:asc", "u.id ASC",
            "id:desc", "u.id DESC");

    private final AdminUserStore store;

    public AdminUserService(AdminUserStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public UserPageResource listUsers(
            int page, int pageSize, String status, String keyword, String sort) {
        String orderBy = SORTS.get(sort);
        if (orderBy == null) {
            throw validation("排序字段不在允许范围内");
        }
        AdminUserStore.UserPage result = store.listUsers(
                page, pageSize, status, keyword, orderBy);
        boolean hasMore = (long) page * pageSize < result.total();
        return new UserPageResource(
                result.items().stream().map(AdminUserService::resource).toList(),
                new PageMetaResource(
                        page,
                        pageSize,
                        Long.toString(result.total()),
                        null,
                        Boolean.toString(hasMore)));
    }

    @Transactional(readOnly = true)
    public UserResource getUser(String userId) {
        long id = parseUserId(userId);
        return store.findUser(id)
                .map(AdminUserService::resource)
                .orElseThrow(AdminUserService::notFound);
    }

    private static UserResource resource(AdminUserStore.UserRow row) {
        return new UserResource(
                Long.toString(row.id()),
                maskPhone(row.phone()),
                row.nickname(),
                row.avatarUrl(),
                row.bio(),
                row.status(),
                row.identityStatus(),
                row.membershipStatus(),
                row.createdAt(),
                row.version());
    }

    private static long parseUserId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException("non-positive");
            return id;
        } catch (NumberFormatException exception) {
            throw validation("用户标识无效");
        }
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return null;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private static BusinessException notFound() {
        return new BusinessException("COMMON-404-NOT_FOUND", "用户不存在或不可见", 404, false);
    }
}
