package cc.orbexa.hhy.incentive;

import cc.orbexa.hhy.incentive.R20RedPacketContracts.AdminCommand;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.AdminReviewRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CampaignPage;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CampaignResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CommandResultResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CreateRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.OrderRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.PatchRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.QuoteRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.SubmitReviewRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.UserCommand;
import java.time.Instant;
import java.util.List;

public interface R20RedPacketStore {
    PageSlice<CampaignResource> userCampaigns(long userId, PageQuery query);

    CampaignResource userCampaign(long userId, long campaignId);

    CampaignResource create(UserCommand command, CreateRequest request, String requestHash);

    CampaignResource patch(
            UserCommand command, long campaignId, PatchRequest request, String requestHash);

    CampaignResource submitReview(
            UserCommand command, long campaignId, SubmitReviewRequest request, String requestHash);

    CommandResultResource quote(
            UserCommand command, long campaignId, QuoteRequest request, String requestHash);

    CommandResultResource order(
            UserCommand command, long campaignId, OrderRequest request, String requestHash);

    PageSlice<CampaignResource> adminCampaigns(PageQuery query);

    CampaignResource adminCampaign(long campaignId);

    CampaignResource review(
            AdminCommand command, long campaignId, AdminReviewRequest request, String requestHash);

    record PageQuery(
            int page, int pageSize, long offset, String status, String keyword, String sort) { }

    record PageSlice<T>(List<T> items, long total) {
        public PageSlice { items = List.copyOf(items); }
    }

    enum Kind { NOT_FOUND, CONFLICT, BUSINESS_RULE, INVALID_DATA, INTERNAL }

    final class StoreException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Kind kind;

        public StoreException(Kind kind, String message) {
            super(message);
            this.kind = kind;
        }

        public StoreException(Kind kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }

        public Kind kind() { return kind; }
    }
}
