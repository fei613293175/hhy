package cc.orbexa.hhy.commerce;

import java.time.Instant;
import java.util.List;

/** Java shapes that mirror the frozen R16 product, SKU, and order schemas. */
public final class R16CommerceContracts {
    private R16CommerceContracts() { }

    public record PageMeta(
            long page, long pageSize, String total, String nextCursor, String hasMore) { }

    public record BenefitResource(
            String benefitCode, String name, Object value, String unit) { }

    public record ProductSkuResource(
            String id,
            String productId,
            String skuCode,
            String name,
            long priceCent,
            Long memberPriceCent,
            Long durationDays,
            List<BenefitResource> benefits,
            boolean commissionEnabled,
            Integer level1Bps,
            Integer level2Bps,
            Instant saleStartsAt,
            Instant saleEndsAt,
            String status,
            long version) {
        public ProductSkuResource {
            benefits = List.copyOf(benefits);
        }
    }

    public record ProductResource(
            String id,
            String productCode,
            String name,
            String productType,
            String description,
            Integer displayOrder,
            String status,
            List<ProductSkuResource> skus,
            long version) {
        public ProductResource {
            skus = List.copyOf(skus);
        }
    }

    public record OrderItemResource(
            String skuId,
            String itemName,
            int quantity,
            long unitPriceCent,
            long subtotalAmountCent) { }

    public record OrderPriceSnapshotResource(
            long originalAmountCent,
            long discountAmountCent,
            long serviceFeeCent,
            long payableAmountCent,
            List<String> ruleVersions) {
        public OrderPriceSnapshotResource {
            ruleVersions = List.copyOf(ruleVersions);
        }
    }

    public record NoRefundEvidenceResource(
            boolean confirmed, String agreementVersion, Instant confirmedAt) { }

    public record OrderResource(
            String orderNo,
            String userId,
            String orderType,
            String status,
            String currency,
            List<OrderItemResource> items,
            OrderPriceSnapshotResource priceSnapshot,
            NoRefundEvidenceResource noRefundEvidence,
            Long paidAmountCent,
            Instant createdAt,
            Instant paidAt,
            long version) {
        public OrderResource {
            items = List.copyOf(items);
        }
    }

    public record ProductPage(List<ProductResource> items, PageMeta page) {
        public ProductPage {
            items = List.copyOf(items);
        }
    }

    public record ProductSkuPage(List<ProductSkuResource> items, PageMeta page) {
        public ProductSkuPage {
            items = List.copyOf(items);
        }
    }

    public record OrderPage(List<OrderResource> items, PageMeta page) {
        public OrderPage {
            items = List.copyOf(items);
        }
    }

    public record ProductCreateRequest(
            String productCode,
            String name,
            String productType,
            String description,
            Integer displayOrder,
            String status) { }

    public record ProductPatchRequest(
            String name,
            String productType,
            String description,
            Integer displayOrder,
            String status,
            Long expectedVersion) { }

    public record ProductSkuCreateRequest(
            String productId,
            String skuCode,
            String name,
            Long priceCent,
            Long memberPriceCent,
            Long durationDays,
            List<BenefitResource> benefits,
            Boolean commissionEnabled,
            Integer level1Bps,
            Integer level2Bps,
            Instant saleStartsAt,
            Instant saleEndsAt,
            String status) {
        public ProductSkuCreateRequest {
            benefits = benefits == null ? null : List.copyOf(benefits);
        }
    }

    public record ProductSkuPatchRequest(
            String name,
            Long priceCent,
            Long memberPriceCent,
            Long durationDays,
            List<BenefitResource> benefits,
            Boolean commissionEnabled,
            Integer level1Bps,
            Integer level2Bps,
            Instant saleStartsAt,
            Instant saleEndsAt,
            String status,
            Long expectedVersion) {
        public ProductSkuPatchRequest {
            benefits = benefits == null ? null : List.copyOf(benefits);
        }
    }

    public record AdminActorContext(
            long adminId,
            long sessionId,
            String username,
            String operationId,
            String requestId,
            String ip) { }
}

