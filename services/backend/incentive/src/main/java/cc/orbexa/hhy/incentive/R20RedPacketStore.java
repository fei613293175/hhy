package cc.orbexa.hhy.incentive;

import cc.orbexa.hhy.incentive.R20RedPacketContracts.AdminCommand;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.AdminReviewRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CampaignPage;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CampaignResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.ClaimResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.LedgerEntryResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.ViewSessionResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CommandResultResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CreateRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.OrderRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.PatchRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.QuoteRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.IncreaseOrderRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.IncreaseQuoteRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.LifecycleRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.SubmitReviewRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.UserCommand;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.ViewSessionRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.HeartbeatRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.ClaimRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CancelRequest;
import java.time.Instant;
import java.util.List;

public interface R20RedPacketStore {
    PageSlice<CampaignResource> publicCampaigns(PageQuery query);

    CampaignResource publicCampaign(long campaignId);

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

    CampaignResource pause(UserCommand command, long campaignId, LifecycleRequest request, String requestHash);

    CampaignResource resume(UserCommand command, long campaignId, LifecycleRequest request, String requestHash);

    CampaignResource close(UserCommand command, long campaignId, LifecycleRequest request, String requestHash);

    CommandResultResource increaseQuote(
            UserCommand command, long campaignId, IncreaseQuoteRequest request, String requestHash);

    CommandResultResource increaseOrder(
            UserCommand command, long campaignId, IncreaseOrderRequest request, String requestHash);

    CommandResultResource startViewSession(
            UserCommand command, long campaignId, ViewSessionRequest request, String requestHash);

    CommandResultResource heartbeat(
            UserCommand command, long sessionId, HeartbeatRequest request, String requestHash);

    CommandResultResource claim(
            UserCommand command, long sessionId, ClaimRequest request, String requestHash);

    CommandResultResource cancel(
            UserCommand command, long sessionId, CancelRequest request, String requestHash);

    PageSlice<CampaignResource> claims(long userId, PageQuery query);

    PageSlice<CampaignResource> analytics(long userId, long campaignId, PageQuery query);

    PageSlice<CampaignResource> adminCampaigns(PageQuery query);

    CampaignResource adminCampaign(long campaignId);

    PageSlice<ViewSessionResource> adminSessions(long campaignId, PageQuery query);

    PageSlice<ClaimResource> adminClaims(long campaignId, PageQuery query);

    List<LedgerEntryResource> adminLedger(long campaignId);

    CampaignResource review(
            AdminCommand command, long campaignId, AdminReviewRequest request, String requestHash);

    CampaignResource adminPause(
            AdminCommand command, long campaignId, LifecycleRequest request, String requestHash);

    CampaignResource adminResume(
            AdminCommand command, long campaignId, LifecycleRequest request, String requestHash);

    CampaignResource adminTerminate(
            AdminCommand command, long campaignId, LifecycleRequest request, String requestHash);

    record PageQuery(
            int page, int pageSize, long offset, String status, String keyword, String sort) { }

    record PageSlice<T>(List<T> items, long total) {
        public PageSlice { items = List.copyOf(items); }
    }

    enum Kind {
        NOT_FOUND, CONFLICT, BUSINESS_RULE, INVALID_DATA, INTERNAL,
        IDENTITY_NOT_VERIFIED, STOCK_EXHAUSTED, ALREADY_CLAIMED, VIEW_INVALID
    }

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
