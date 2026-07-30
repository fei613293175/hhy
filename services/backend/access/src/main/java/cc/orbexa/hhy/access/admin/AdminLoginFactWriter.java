package cc.orbexa.hhy.access.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminLoginFactWriter {
    private final AdminSecurityStore store;

    public AdminLoginFactWriter(AdminSecurityStore store) {
        this.store = store;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void failure(
            Long adminId, String usernameMasked, String eventType, String failureCode,
            String ip, String device, String requestId) {
        store.loginLog(adminId, usernameMasked, eventType, "FAILED", failureCode, ip, device, requestId);
    }
}
