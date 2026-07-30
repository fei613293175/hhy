package cc.orbexa.hhy.access.user;

import cc.orbexa.hhy.access.user.UserAuthContracts.AuthScene;

/** Provider boundary. The R03 configuration lifecycle supplies the activated implementation. */
public interface SmsProvider {
    DeliveryReceipt sendVerificationCode(String phone, AuthScene scene, String code);

    record DeliveryReceipt(String providerMessageId, String templateCode) { }
}

