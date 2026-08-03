from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "services/backend/access/src/main/java/cc/orbexa/hhy/access/r15/R15PostgresStore.java"
SERVICE = ROOT / "services/backend/access/src/main/java/cc/orbexa/hhy/access/r15/R15Service.java"
ADMIN = ROOT / "services/backend/boot/src/main/java/cc/orbexa/hhy/boot/admin/R15SupportAdminController.java"
PUBLIC = ROOT / "services/backend/boot/src/main/java/cc/orbexa/hhy/boot/user/R15PublicController.java"
SELF = ROOT / "services/backend/boot/src/main/java/cc/orbexa/hhy/boot/user/UserSelfServiceController.java"
USER_CONTENT = ROOT / "services/backend/boot/src/main/java/cc/orbexa/hhy/boot/user/R13Controller.java"
CONTENT_SERVICE = ROOT / "services/backend/content/src/main/java/cc/orbexa/hhy/content/R13Service.java"
REVIEW_ADMIN = ROOT / "services/backend/boot/src/main/java/cc/orbexa/hhy/boot/admin/R12ReviewController.java"
REVIEW_SERVICE = ROOT / "services/backend/content/src/main/java/cc/orbexa/hhy/content/R12ReviewService.java"
MIGRATION = ROOT / "database/migrations/V049__r15_access_contract_alignment.sql"


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_r15_audit_uses_real_admin_operation_log_columns() -> None:
    store = text(STORE)
    assert "admin_id,action,resource,resource_id,before_json,after_json,ip,created_at" in store
    assert "operation,resource,request_id" not in store


def test_r15_help_articles_query_has_item_alias_for_sorting() -> None:
    store = text(STORE)
    assert "FROM hhy.cms_articles item" in store
    assert 'order("item", query.sort())' in store


def test_r15_notification_read_claims_idempotency_before_version_lock() -> None:
    service = text(SERVICE)
    store = text(STORE)
    controller = text(USER_CONTENT.parent / "R15Controller.java")
    claim_index = service.index("R15Store.IdempotencyClaim claim = claim(scope, key, requestHash);")
    lock_index = service.index("store.lockNotification(userId, id)")
    assert claim_index < lock_index
    assert "store.complete(claim.id(), \"notification:\" + id + \":read\")" in service
    assert 'store.outbox("NOTIFICATION", id, "notification.read.v1", requestId' in service
    assert 'store.outbox("NOTIFICATION", userId, "notification.read-all.v1", requestId' in service
    assert "store.markNotificationRead(userId, id, expectedVersion, now)" in service
    assert "WHERE user_id=? AND id=? AND version=?" in store
    assert "service.readNotification(principal.userId(), id, body, key, requestId(request))" in controller
    assert "service.readAllNotifications(principal.userId(), key, requestId(request))" in controller
    assert "String input = json(value);" in service
    assert "String input = value.toString();" not in service


def test_r15_chat_reports_return_conversation_contract_not_support_ticket() -> None:
    admin = text(ADMIN)
    assert "ApiResponse<ConversationPage> chatReports" in admin
    assert "ApiResponse<ConversationResource> decideChatReport" in admin
    assert "SupportTicketPage> chatReports" not in admin


def test_r15_permission_seed_contains_granular_authorities() -> None:
    migration = text(MIGRATION)
    for permission in (
        "report.decide",
        "appeal.decide",
        "chat.report.read",
        "chat.report.decide",
        "support.read",
        "support.assign",
        "support.reply",
        "support.close",
    ):
        assert permission in migration


def test_r15_public_help_and_support_create_have_single_authoritative_mapping() -> None:
    public = text(PUBLIC)
    r15_user = text(USER_CONTENT.parent / "R15Controller.java")
    self_service = text(SELF)
    assert '@RequestMapping("/public-api/v1")' in public
    assert '@GetMapping("/help/articles/{id}")' in public
    assert "service.helpArticle(id)" in public
    assert '@PostMapping("/support/tickets")' not in r15_user
    assert "service.createSupportTicket(principal, body, key)" in self_service


def test_r15_content_report_is_idempotent_and_emits_outbox() -> None:
    controller = text(USER_CONTENT)
    service = text(CONTENT_SERVICE)
    assert '@PostMapping("/api/v1/contents/{id}/report")' in controller
    assert "ContentReportRequest" in controller
    assert 'String scope = "r15.content-report:"' in service
    assert "shared.outbox(userId, \"CONTENT_REPORT\", \"content.report.created.v1\"" in service


def test_r15_admin_report_and_appeal_decisions_use_granular_permissions() -> None:
    controller = text(REVIEW_ADMIN)
    service = text(REVIEW_SERVICE)
    assert '@PostMapping("/content-reports/{id}/decide")' in controller
    assert '@PostMapping("/appeals/{id}/decide")' in controller
    assert "hasAuthority('report.decide')" in controller
    assert "hasAuthority('appeal.decide')" in controller
    assert "r15.adminReportsPostContentReportsByIdDecide:admin:" in service
    assert "r15.adminAppealsPostAppealsByIdDecide:admin:" in service
