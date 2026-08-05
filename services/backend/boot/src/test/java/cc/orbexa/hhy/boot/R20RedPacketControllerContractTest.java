package cc.orbexa.hhy.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cc.orbexa.hhy.boot.admin.R20RedPacketAdminController;
import cc.orbexa.hhy.boot.user.R20RedPacketController;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class R20RedPacketControllerContractTest {
    @Test
    void springManagedControllersRemainProxyable() {
        assertFalse(Modifier.isFinal(R20RedPacketController.class.getModifiers()));
        assertFalse(Modifier.isFinal(R20RedPacketAdminController.class.getModifiers()));
    }

    @Test
    void frozenOperationIdsAndAdminPermissionsExist() {
        Map<Class<?>, Map<String, String>> expected = new LinkedHashMap<>();
        expected.put(R20RedPacketController.class, Map.of(
                "redPacketGetRedPacketCampaigns", "",
                "redPacketGetMeRedPacketCampaigns", "",
                "redPacketPostRedPacketCampaigns", "",
                "redPacketGetRedPacketCampaignsById", "",
                "redPacketPatchRedPacketCampaignsById", "",
                "redPacketPostRedPacketCampaignsByIdSubmitReview", "",
                "redPacketPostRedPacketCampaignsByIdQuote", "",
                "redPacketPostRedPacketCampaignsByIdOrders", "",
                "redPacketPostRedPacketCampaignsByIdPause", "",
                "redPacketPostRedPacketCampaignsByIdResume", "",
                "redPacketPostRedPacketCampaignsByIdClose", "",
                "redPacketPostRedPacketCampaignsByIdIncreaseQuotes", "",
                "redPacketPostRedPacketCampaignsByIdIncreaseOrders", "",
                "redPacketGetMeRedPacketCampaignsByIdAnalytics", ""));
        expected.put(R20RedPacketAdminController.class, Map.of(
                "adminRedPacketGetRedPacketCampaigns", "hasAuthority('redpacket.read')",
                "adminRedPacketGetRedPacketCampaignsById", "hasAuthority('redpacket.read')",
                "adminRedPacketPostRedPacketCampaignsByIdReview", "hasAuthority('redpacket.review')"));

        for (Map.Entry<Class<?>, Map<String, String>> controller : expected.entrySet()) {
            for (Map.Entry<String, String> operation : controller.getValue().entrySet()) {
                Method method = java.util.Arrays.stream(controller.getKey().getDeclaredMethods())
                        .filter(candidate -> candidate.getName().equals(operation.getKey()))
                        .reduce((left, right) -> {
                            throw new AssertionError("duplicate operationId " + operation.getKey());
                        })
                        .orElseThrow(() -> new AssertionError("missing operationId " + operation.getKey()));
                if (!operation.getValue().isEmpty()) {
                    PreAuthorize authority = method.getAnnotation(PreAuthorize.class);
                    assertNotNull(authority, operation.getKey());
                    assertEquals(operation.getValue(), authority.value(), operation.getKey());
                }
            }
        }
    }
}
