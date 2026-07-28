from __future__ import annotations

from pathlib import Path
from collections import OrderedDict
import argparse, csv, re, hashlib, json, datetime
import yaml

ROOT = Path(__file__).resolve().parents[1]
TODAY = '2026-07-16'
WEBSOCKET_FREEZE_DATE = '2026-07-28'
LEGACY_WEBSOCKET_EVENT_CODES = (
    'chat.message.send',
    'chat.message.ack',
    'chat.message.new',
    'chat.message.read',
    'chat.read.updated',
    'chat.typing',
    'notification.new',
    'system.kickout',
    'system.ping',
    'system.pong',
)
NEW_WEBSOCKET_EVENT_CODES = ('system.delivery.ack', 'system.resume')
R16_FREEZE_DATE = '2026-07-28'


def read_csv(rel: str) -> list[dict[str,str]]:
    with (ROOT/rel).open('r', encoding='utf-8-sig', newline='') as f:
        return list(csv.DictReader(f))

def write_csv(rel: str, rows: list[dict[str,str]], fields: list[str] | None = None) -> None:
    p=ROOT/rel; p.parent.mkdir(parents=True,exist_ok=True)
    if fields is None: fields=list(rows[0]) if rows else []
    with p.open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.DictWriter(f,fieldnames=fields); w.writeheader(); w.writerows(rows)

def safe_name(value: str) -> str:
    value = re.sub(r'\{([^}]+)\}', lambda m: 'By' + ''.join(x.title() for x in re.split(r'[^A-Za-z0-9]+', m.group(1)) if x), value)
    tokens = [x for x in re.split(r'[^A-Za-z0-9]+', value) if x]
    return ''.join(t[:1].upper() + t[1:] for t in tokens)

def operation_id(row: dict[str,str]) -> str:
    """Return a globally unique, stable OpenAPI operationId.

    Client and administration catalogs intentionally contain several identical
    domain/path shapes. Administration operations therefore receive an
    explicit ``admin`` namespace so generated clients can safely merge both
    contracts without overwriting entries.
    """
    prefix_removed = re.sub(r'^/(?:admin-api|public-api|api)/v1', '', row['路径'])
    module = re.sub(r'[^A-Za-z0-9]', '', row['模块'])
    base = module[:1].lower() + module[1:] + row['方法'].title() + safe_name(prefix_removed)
    if row['路径'].startswith('/admin-api/'):
        return 'admin' + base[:1].upper() + base[1:]
    return base

def schema_name(opid: str, suffix: str) -> str:
    return opid[:1].upper()+opid[1:]+suffix

def scalar(name: str, *, required=False, desc='', example=None, enum=None, fmt=None, minv=None, maxv=None, nullable=False):
    lower=name.lower()
    if lower.endswith('ids') or name in {'mediaIds','evidenceMediaIds','messageIds','permissionCodes','agreementVersions','attachments','parts','items','values','secretRefs','targeting','attributes','payload','device','clientContext','quoteRules','benefits','config','metadata','criteria','rule','schedule','artifact','releasePolicy'}:
        if name.endswith('Ids') or name in {'messageIds','permissionCodes','agreementVersions','attachments'}:
            s={'type':'array','items':{'type':'string'},'maxItems':100}
        elif name=='parts':
            s={'type':'array','items':{'type':'object','additionalProperties':True},'maxItems':10000}
        else:
            s={'type':'object','additionalProperties':True}
    elif lower.endswith('at') or lower.endswith('time') or name in {'expiresAt','scheduledAt','effectiveAt','startAt','endAt','clientTime'}:
        s={'type':'string','format':'date-time'}
    elif lower.endswith('date') or name in {'businessDate'}:
        s={'type':'string','format':'date'}
    elif lower.endswith('cent') or lower.endswith('count') or lower.endswith('seconds') or lower.endswith('bytes') or name in {'quantity','versionCode','clientSequence','serverSequence','finalHeartbeatSequence','pageSize','page','weight','ttlSeconds','expectedVersion','version','durationDays'}:
        s={'type':'integer','format':'int64'}
        if minv is not None: s['minimum']=minv
        elif lower.endswith('cent') or lower.endswith('count') or name in {'quantity','pageSize','page','ttlSeconds','durationDays'}: s['minimum']=0
        if maxv is not None: s['maximum']=maxv
    elif name in {'enabled','dryRun','force','typing','pageVisible','consent','active'}:
        s={'type':'boolean'}
    else:
        s={'type':'string','maxLength':2000}
        if name in {'phone'}: s.update({'pattern':'^1[3-9]\\d{9}$','maxLength':11,'example':'13800000000'})
        elif name in {'password','currentPassword','newPassword'}: s.update({'minLength':8,'maxLength':72,'format':'password','writeOnly':True})
        elif name in {'smsCode','code'}: s.update({'minLength':4,'maxLength':10})
        elif lower.endswith('id') or name in {'id','userId','contentId','quoteId','skuId','profileId','versionId','approvalId','targetVersionId','payoutAccountId','peerUserId','sourceContentId','lastReadMessageId'}: s.update({'maxLength':64})
        elif name == 'sha256': s.update({'pattern':'^[A-Fa-f0-9]{64}$','minLength':64,'maxLength':64})
        elif name == 'commitSha': s.update({'pattern':'^[A-Fa-f0-9]{40,64}$','maxLength':64})
        elif name in {'returnUrl'}: s.update({'format':'uri','maxLength':2048})
        elif name in {'idNumber','alipayAccount','accountName'}: s['x-sensitive']=True
    if desc: s['description']=desc
    if example is not None: s['example']=example
    if enum: s['enum']=enum
    if fmt: s['format']=fmt
    if nullable: s['type']=[s.get('type','string'),'null']
    return s

def object_schema(fields: list[str], required: list[str] | None=None, descriptions: dict[str,str] | None=None, enums: dict[str,list[str]] | None=None, extra: dict | None=None):
    descriptions=descriptions or {}; enums=enums or {}; required=required or []
    props=OrderedDict()
    for f in fields:
        props[f]=scalar(f, desc=descriptions.get(f,''), enum=enums.get(f))
    result=OrderedDict(type='object', additionalProperties=False, properties=props)
    if required: result['required']=required
    if extra: result.update(extra)
    return result

NO_BODY_PATH_SUFFIXES = {
    '/logout','/read-all','/online','/favorite','/copy','/submit','/submit-review','/close','/cancel','/unfreeze','/force-logout','/query','/publish-staging','/retry'
}

def request_fields(row: dict[str,str]) -> tuple[list[str],list[str],dict[str,list[str]],dict[str,str]]:
    path=row['路径']; method=row['方法']; purpose=row['用途']; p=path
    fields: list[str]=[]; req: list[str]=[]; enums={}; desc={}
    # GET and DELETE inputs are represented by path/query/header parameters,
    # never by a JSON request body. This early return prevents path-only exact
    # mappings (for example /contents) from leaking a POST body into GET.
    if method not in {'POST','PUT','PATCH'}:
        return [],[],{},desc
    # Exact client contracts.
    exact: dict[str, tuple[list[str],list[str],dict[str,list[str]]]] = {
      '/api/v1/auth/security-challenges': (['scene','clientNonce','deviceFingerprint'],['scene','clientNonce'],{'scene':['LOGIN','REGISTER','RESET_PASSWORD','SENSITIVE_OPERATION']}),
      '/api/v1/auth/password/login': (['phone','password','challengeId','challengeProof','device'],['phone','password','challengeId','challengeProof'],{}),
      '/api/v1/auth/sms/send': (['phone','scene','challengeId','challengeProof'],['phone','scene','challengeId','challengeProof'],{'scene':['LOGIN','REGISTER','RESET_PASSWORD']}),
      '/api/v1/auth/sms/login': (['phone','smsCode','device'],['phone','smsCode'],{}),
      '/api/v1/auth/invite-codes/validate': (['inviteCode'],['inviteCode'],{}),
      '/api/v1/auth/register': (['phone','smsCode','password','inviteCode','agreementVersions','device'],['phone','smsCode','password','inviteCode','agreementVersions'],{}),
      '/api/v1/auth/password/reset': (['phone','smsCode','newPassword'],['phone','smsCode','newPassword'],{}),
      '/api/v1/me/security/password/change': (['currentPassword','newPassword','smsCode'],['currentPassword','newPassword'],{}),
      '/api/v1/auth/refresh': (['refreshToken','deviceId'],['refreshToken','deviceId'],{}),
      '/api/v1/me/profile': (['nickname','avatarMediaId','bio','expectedVersion'],['expectedVersion'],{}),
      '/api/v1/me/cancellation': (['reason','smsCode','expectedVersion'],['reason','smsCode','expectedVersion'],{}),
      '/api/v1/identity/sessions': (['realName','idNumber','consentVersion'],['realName','idNumber','consentVersion'],{}),
      '/api/v1/media/upload-sessions': (['purpose','fileName','contentType','sizeBytes','sha256'],['purpose','fileName','contentType','sizeBytes','sha256'],{}),
      '/api/v1/contents': (['contentType','title','summary','description','categoryCode','regionCode','mediaIds','contacts','attributes'],['contentType','title','description','categoryCode'],{'contentType':['PROJECT','APP','GROUP_CHAT','TEAM_LEADER']}),
      '/api/v1/conversations/direct': (['peerUserId','sourceContentId'],['peerUserId'],{}),
      '/api/v1/membership/orders': (['skuId','paymentChannel'],['skuId','paymentChannel'],{}),
      '/api/v1/membership/upgrade-quotes': (['targetSkuId'],['targetSkuId'],{}),
      '/api/v1/membership/upgrade-orders': (['quoteId','paymentChannel'],['quoteId','paymentChannel'],{}),
      '/api/v1/props/orders': (['skuId','quantity','paymentChannel'],['skuId','quantity','paymentChannel'],{}),
      '/api/v1/red-packet-campaigns': (['contentId','totalCount','amountPerClaimCent','startAt','endAt','targeting'],['contentId','totalCount','amountPerClaimCent','startAt','endAt'],{}),
      '/api/v1/withdrawals/quote': (['amountCent','payoutAccountId'],['amountCent','payoutAccountId'],{}),
      '/api/v1/withdrawals': (['amountCent','quoteId','payoutAccountId'],['amountCent','quoteId','payoutAccountId'],{}),
      '/api/v1/support/tickets': (['category','subject','content','attachments'],['category','subject','content'],{}),
      '/public-api/v1/app/version-check': (['platform','versionCode','versionName','channel','environment'],['platform','versionCode','channel','environment'],{'platform':['ANDROID'],'environment':['DEV','TEST','STAGING','PROD']}),
    }
    if p in exact:
        fields,req,enums=exact[p]; return fields,req,enums,desc
    # Route patterns and specific semantics.
    if p.endswith('/liveness-token'):
        return ['returnUrl'],['returnUrl'],{},desc
    if p.endswith('/complete') and 'upload-sessions' in p:
        return ['etag','parts'],['etag'],{},desc
    if method=='PATCH' and '/contents/{id}' in p:
        return ['title','summary','description','categoryCode','regionCode','mediaIds','contacts','attributes','expectedVersion'],['expectedVersion'],{},desc
    if any(p.endswith(x) for x in ['/submit','/online','/offline','/copy']) and '/contents/' in p:
        return ['expectedVersion','reason'],['expectedVersion'],{},desc
    if p.endswith('/contacts/{channel}/access'):
        return ['clientContext'],[],{},desc
    if p.endswith('/report'):
        return ['reasonCode','description','evidenceMediaIds','messageIds','expectedVersion'],['reasonCode','description'],{},desc
    if p.endswith('/invalid-feedback'):
        return ['reasonCode','description'],['reasonCode'],{},desc
    if p.endswith('/share'):
        return ['channel'],['channel'],{'channel':['WECHAT','WECHAT_MOMENTS','COPY_LINK','OTHER']},desc
    if p.endswith('/messages') and '/conversations/' in p:
        return ['clientMessageId','messageType','payload'],['clientMessageId','messageType','payload'],{'messageType':['TEXT','IMAGE','CONTENT_CARD','CONTACT_CARD']},desc
    if p.endswith('/read'):
        return ['lastReadMessageId'],['lastReadMessageId'],{},desc
    if p.endswith('/block'):
        return ['reason'],[],{},desc
    if p.endswith('/use') and '/props/' in p:
        return ['targetContentId','scheduledAt','expectedVersion'],['targetContentId','expectedVersion'],{},desc
    if method=='PATCH' and '/red-packet-campaigns/{id}' in p:
        return ['totalCount','amountPerClaimCent','startAt','endAt','targeting','expectedVersion'],['expectedVersion'],{},desc
    if p.endswith('/submit-review'):
        return ['expectedVersion','remark'],['expectedVersion'],{},desc
    if p.endswith('/quote') and 'red-packet-campaigns' in p:
        return ['totalCount','amountPerClaimCent','expectedVersion'],['totalCount','amountPerClaimCent','expectedVersion'],{},desc
    if p.endswith('/orders') and 'red-packet-campaigns' in p:
        return ['quoteId','paymentChannel','expectedVersion'],['quoteId','paymentChannel','expectedVersion'],{},desc
    if any(p.endswith(x) for x in ['/pause','/resume','/close']) and 'red-packet' in p:
        return ['reason','expectedVersion'],['expectedVersion'],{},desc
    if p.endswith('/increase-quotes'):
        return ['newAmountPerClaimCent','expectedVersion'],['newAmountPerClaimCent','expectedVersion'],{},desc
    if p.endswith('/increase-orders'):
        return ['quoteId','paymentChannel','expectedVersion'],['quoteId','paymentChannel','expectedVersion'],{},desc
    if p.endswith('/view-sessions'):
        return ['clientNonce','deviceContext'],['clientNonce','deviceContext'],{},desc
    if p.endswith('/heartbeat'):
        return ['clientSequence','elapsedSeconds','pageVisible'],['clientSequence','elapsedSeconds','pageVisible'],{},desc
    if p.endswith('/claim') and 'red-packet-view' in p:
        return ['clientNonce','finalHeartbeatSequence'],['clientNonce','finalHeartbeatSequence'],{},desc
    if p.endswith('/cancel') and 'red-packet-view' in p:
        return ['reason'],[],{},desc
    if p.endswith('/payments') and '/orders/' in p:
        return ['gateway','returnUrl'],['gateway','returnUrl'],{'gateway':['ALIPAY','WECHAT_PAY']},desc
    if '/payment/{gateway}/notify' in p:
        return ['notificationId','eventType','orderNo','amountCent','currency','status','rawPayload'],['notificationId','eventType','orderNo','status','rawPayload'],{},desc
    if p.endswith('/payout-account') and method=='PUT':
        return ['accountName','alipayAccount','smsCode','expectedVersion'],['accountName','alipayAccount','smsCode'],{},desc
    if p.endswith('/claim') and '/tasks/' in p:
        return ['expectedVersion'],['expectedVersion'],{},desc
    if p.endswith('/messages') and '/support/tickets/' in p:
        return ['content','attachments'],['content'],{},desc
    # Admin/write inference.
    if p.endswith('/auth/login'):
        return ['username','password','captchaToken'],['username','password'],{},desc
    if p.endswith('/mfa/verify'):
        return ['mfaTicket','code'],['mfaTicket','code'],{},desc
    if '/restrictions' in p and method=='POST':
        return ['restrictionType','reason','expiresAt','expectedVersion'],['restrictionType','reason','expectedVersion'],{},desc
    if any(p.endswith(x) for x in ['/freeze','/unfreeze','/force-logout']):
        return ['reason','expectedVersion'],['reason','expectedVersion'],{},desc
    if p.endswith('/media-access'):
        return ['purpose','ttlSeconds'],['purpose'],{},desc
    if p.endswith('/review') or p.endswith('/decide') or p.endswith('/risk-review') or p.endswith('/finance-review'):
        return ['decision','reason','expectedVersion','evidenceIds'],['decision','reason','expectedVersion'],{'decision':['APPROVE','REJECT','ESCALATE']},desc
    if p.endswith('/assign'):
        return ['assigneeId','reason','expectedVersion'],['assigneeId','expectedVersion'],{},desc
    if any(p.endswith(x) for x in ['/online','/offline','/ban','/terminate']):
        return ['reason','expectedVersion'],['reason','expectedVersion'],{},desc
    if p.endswith('/recommend'):
        return ['enabled','weight','startAt','endAt','expectedVersion'],['enabled','expectedVersion'],{},desc
    if p.endswith('/official-mark'):
        return ['enabled','label','expectedVersion'],['enabled','expectedVersion'],{},desc
    if 'content-dictionaries' in p and method=='PUT':
        return ['items','expectedVersion'],['items','expectedVersion'],{},desc
    if p.endswith('/products') and method=='POST':
        return ['productCode','name','productType','description','status'],['productCode','name','productType'],{},desc
    if '/products/{id}' in p and method=='PATCH':
        return ['name','description','status','expectedVersion'],['expectedVersion'],{},desc
    if p.endswith('/skus') and method=='POST':
        return ['productId','skuCode','name','priceCent','durationDays','benefits','status'],['productId','skuCode','name','priceCent'],{},desc
    if '/skus/{id}' in p and method in {'PATCH','PUT'}:
        return ['name','priceCent','durationDays','benefits','status','expectedVersion'],['expectedVersion'],{},desc
    if p.endswith('/resolve'):
        return ['resolution','reason','expectedVersion'],['resolution','reason','expectedVersion'],{},desc
    if p.endswith('/reconciliation/run'):
        return ['businessDate','gateway','dryRun'],['businessDate'],{},desc
    if p.endswith('/reward-adjustments'):
        return ['userId','direction','amountCent','reason','businessRef'],['userId','direction','amountCent','reason'],{'direction':['CREDIT','DEBIT']},desc
    if p.endswith('/payout'):
        return ['payoutChannel','expectedVersion'],['payoutChannel','expectedVersion'],{},desc
    if p.endswith('/grants'):
        return ['userId','skuId','durationDays','reason'],['userId','reason'],{},desc
    if p.endswith('/adjust'):
        return ['newInviterId','reason','expectedVersion'],['newInviterId','reason','expectedVersion'],{},desc
    if p.endswith('/commission-policies') and method=='POST':
        return ['name','effectiveAt','criteria','rule','remark'],['name','effectiveAt','rule'],{},desc
    if p.endswith('/rule-versions') and method=='POST':
        return ['name','effectiveAt','rule','remark'],['name','effectiveAt','rule'],{},desc
    if p.endswith('/milestones') and method=='PUT':
        return ['items','effectiveAt','expectedVersion'],['items','effectiveAt','expectedVersion'],{},desc
    if p.endswith('/tasks') and method=='POST':
        return ['taskCode','name','taskType','criteria','reward','schedule','budgetId','status'],['taskCode','name','taskType','criteria','reward'],{},desc
    if '/tasks/{id}' in p and method=='PATCH':
        return ['name','criteria','reward','schedule','budgetId','status','expectedVersion'],['expectedVersion'],{},desc
    if '/growth-budgets/{id}' in p:
        return ['dailyLimitCent','totalLimitCent','effectiveAt','expectedVersion'],['dailyLimitCent','totalLimitCent','expectedVersion'],{},desc
    if p.endswith('/cms/home-modules') and method=='PUT':
        return ['items','expectedVersion'],['items','expectedVersion'],{},desc
    if p.endswith('/cms/banners') and method=='POST':
        return ['title','imageMediaId','targetUrl','startAt','endAt','weight','enabled'],['title','imageMediaId'],{},desc
    if p.endswith('/cms/articles') and method=='POST':
        return ['articleType','title','summary','content','status','publishAt'],['articleType','title','content'],{},desc
    if '/cms/h5-pages/{code}' in p:
        return ['title','content','seoMetadata','expectedVersion'],['content','expectedVersion'],{},desc
    if p.endswith('/versions') and '/agreements/' in p:
        return ['version','title','content','effectiveAt'],['version','title','content','effectiveAt'],{},desc
    if p.endswith('/app-versions') and method=='POST':
        return ['platform','versionCode','versionName','channel','artifactId','releaseNotes','forcePolicy'],['platform','versionCode','versionName','artifactId'],{},desc
    if p.endswith('/publish'):
        return ['rolloutPercent','targetChannels','forcePolicy','approvalId','expectedVersion'],['rolloutPercent','approvalId','expectedVersion'],{},desc
    if p.endswith('/blacklists') and method=='POST':
        return ['subjectType','subjectValue','reason','expiresAt'],['subjectType','subjectValue','reason'],{},desc
    if '/configs/{key}' in p or '/feature-flags/{key}' in p:
        return ['value','effectiveAt','expectedVersion','approvalId'],['value','expectedVersion'],{},desc
    if p.endswith('/admin-users') and method=='POST':
        return ['username','displayName','phone','roleIds','mfaRequired'],['username','displayName','roleIds'],{},desc
    if p.endswith('/permissions'):
        return ['permissionCodes','expectedVersion'],['permissionCodes','expectedVersion'],{},desc
    if '/provider-configs/{provider}/versions' in p:
        return ['environment','values','secretRefs','remark'],['environment','values'],{},desc
    if '/provider-configs/{provider}/test' in p:
        return ['versionId','testRecipient'],['versionId'],{},desc
    if '/provider-configs/{provider}/activate' in p:
        return ['versionId','approvalId','expectedVersion'],['versionId','approvalId','expectedVersion'],{},desc
    if '/provider-configs/{provider}/rollback' in p:
        return ['targetVersionId','approvalId','reason','expectedVersion'],['targetVersionId','approvalId','reason','expectedVersion'],{},desc
    if p.endswith('/provider-certificates') and method=='POST':
        return ['provider','certificateType','alias','encryptedContentBase64','passwordSecretRef','expiresAt'],['provider','certificateType','alias','encryptedContentBase64'],{},desc
    if p.endswith('/rotate'):
        return ['newCertificateId','approvalId','reason','expectedVersion'],['newCertificateId','approvalId','reason','expectedVersion'],{},desc
    if '/domains/{code}' in p and method=='PUT':
        return ['hostname','certificateMode','expectedVersion'],['hostname','expectedVersion'],{},desc
    if p.endswith('/verify') and '/domains/' in p:
        return ['force'],[],{},desc
    if p.endswith('/app-build/profiles') and method=='POST':
        return ['name','environment','applicationIdSuffix','apiBaseUrl','webSocketUrl','featureFlags','signingProfileId'],['name','environment','apiBaseUrl','webSocketUrl'],{},desc
    if '/app-build/profiles/{id}' in p and method=='PUT':
        return ['name','apiBaseUrl','webSocketUrl','featureFlags','signingProfileId','expectedVersion'],['expectedVersion'],{},desc
    if p.endswith('/app-build/jobs') and method=='POST':
        return ['profileId','gitRef','commitSha','reason'],['profileId','gitRef','commitSha'],{},desc
    if p.endswith('/cancel') and '/app-build/jobs/' in p:
        return ['reason','expectedVersion'],['reason','expectedVersion'],{},desc
    if p.endswith('/promote'):
        return ['approvalId','releasePolicy','expectedVersion'],['approvalId','releasePolicy','expectedVersion'],{},desc
    if p.endswith('/signing-profiles') and method=='POST':
        return ['name','keystoreEncryptedBase64','storePasswordSecretRef','keyAlias','keyPasswordSecretRef'],['name','keystoreEncryptedBase64','storePasswordSecretRef','keyAlias','keyPasswordSecretRef'],{},desc
    if p.endswith('/test'):
        return ['expectedVersion','testPayload'],[],{},desc
    if p.endswith('/run'):
        return ['parameters','dryRun'],[],{},desc
    # Conservative endpoint-specific fallback for remaining command operations.
    if method in {'POST','PUT','PATCH'}:
        fields=['reason','expectedVersion','payload']
        req=['expectedVersion'] if method in {'PUT','PATCH'} else []
        return fields,req,{},desc
    return [],[],{},desc

def body_required(row: dict[str,str], fields: list[str]) -> bool:
    if not fields: return False
    # Commands with only optional reason/version are allowed empty only for explicitly no-payload actions.
    p=row['路径']
    if p.endswith('/logout') or p.endswith('/read-all') or (row['方法']=='POST' and p.endswith('/query')):
        return False
    return True

def resource_ref(module: str) -> str:
    mapping={
      'Auth':'AuthSessionResource','User':'UserResource','Identity':'IdentitySessionResource','Media':'MediaResource','Home':'HomeResource','Search':'SearchResultResource',
      'Content':'ContentResource','Chat':'ConversationResource','Notification':'NotificationResource','Membership':'MembershipResource','Prop':'PropResource',
      'RedPacket':'RedPacketCampaignResource','Order':'OrderResource','Payment':'PaymentResource','Reward':'RewardAccountResource','Withdrawal':'WithdrawalResource',
      'Referral':'ReferralResource','Task':'TaskResource','Support':'SupportTicketResource','AppRelease':'AppVersionPolicyResource','Public':'PublicPageResource',
      'AdminAuth':'AdminSessionResource','Dashboard':'DashboardResource','Users':'UserResource','Review':'ReviewResource','Reports':'ReportResource','Appeals':'AppealResource',
      'Products':'ProductResource','Payments':'PaymentResource','Rewards':'RewardAccountResource','Withdrawals':'WithdrawalResource','Props':'PropResource',
      'ReferralActivity':'ReferralActivityResource','Tasks':'TaskResource','CMS':'CmsResource','Agreements':'AgreementResource','Risk':'RiskEventResource','Config':'ConfigResource',
      'RBAC':'RoleResource','Audit':'AuditLogResource','Approvals':'ApprovalResource','Jobs':'JobResource','ProviderConfig':'ProviderConfigResource',
      'ProviderCertificate':'CertificateResource','DomainConfig':'DomainResource','AppBuildProfile':'BuildProfileResource','AppBuildJob':'BuildJobResource',
      'AppBuildArtifact':'BuildArtifactResource','AppBuildRelease':'AppVersionPolicyResource','AppSigning':'SigningProfileResource'
    }
    return mapping.get(module,'CommandResultResource')

def is_list(row: dict[str,str]) -> bool:
    path=row['路径']; purpose=row['用途']; method=row['方法']
    if method!='GET': return False
    # These P00 bootstrap reads return one typed snapshot/policy.  Treating a
    # path ending in ``s`` or a purpose containing ``版本`` as a collection
    # produced the invalid frozen contracts corrected by CR-0003.
    if path in {'/public-api/v1/platform/status','/api/v1/app/version-check'}:
        return False
    singular_detail = bool(re.search(r'\{[^}]+\}$',path)) and not any(path.endswith(s) for s in ['/messages','/analytics','/ledger','/download','/logs'])
    if singular_detail: return False
    list_tokens=['列表','队列','记录','历史','流水','SKU','商城','热搜','进度','关系','规则','档位','模块','轮播','协议','版本','配置','日志','产物','Profile']
    return any(t in purpose for t in list_tokens) or path.endswith(('s','/queue','/history','/hot','/store','/todos'))

def make_common_schemas() -> OrderedDict:
    S=OrderedDict()
    S['ErrorDetail']=object_schema(['field','code','message'],['code','message'])
    S['ErrorResponse']=OrderedDict(type='object',additionalProperties=False,required=['success','requestId','error'],properties=OrderedDict([
      ('success',{'type':'boolean','const':False}),('requestId',{'type':'string','maxLength':64}),('timestamp',{'type':'string','format':'date-time'}),
      ('error',OrderedDict(type='object',additionalProperties=False,required=['code','message'],properties=OrderedDict([
          ('code',{'type':'string','pattern':'^[A-Z][A-Z0-9_-]+$','example':'COMMON-400-VALIDATION'}),('message',{'type':'string','maxLength':500}),
          ('details',{'type':'array','items':{'$ref':'#/components/schemas/ErrorDetail'}}),('retryable',{'type':'boolean'}),('traceId',{'type':'string','maxLength':64})])))
    ]))
    S['PageMeta']=object_schema(['page','pageSize','total','nextCursor','hasMore'],['pageSize','hasMore'])
    S['CommandResultResource']=object_schema(['resourceId','businessNo','status','version','acceptedAt'],['status','acceptedAt'])
    S['DeviceResource']=object_schema(['deviceId','platform','model','osVersion','appVersion','channel'],['deviceId','platform'])
    # Concise but typed domain resources. All are stable API views, never direct DB entities.
    resources={
      'ChallengeResource':(['challengeId','challengeType','expiresAt','imageBase64','token'],['challengeId','challengeType','expiresAt']),
      'AuthSessionResource':(['accessToken','refreshToken','expiresAt','userId','sessionId','device','capabilities'],['accessToken','refreshToken','expiresAt','userId','sessionId']),
      'AdminSessionResource':(['accessToken','expiresAt','adminUserId','displayName','permissionCodes','mfaRequired','mfaTicket'],['adminUserId','mfaRequired']),
      'UserResource':(['id','phoneMasked','nickname','avatarUrl','bio','status','identityStatus','membershipStatus','createdAt','version'],['id','status','version']),
      'IdentitySessionResource':(['id','userId','status','provider','livenessUrl','failureCode','expiresAt','version'],['id','status','version']),
      'MediaResource':(['id','purpose','contentType','sizeBytes','sha256','uploadUrl','readUrl','status','expiresAt'],['id','status']),
      'HomeResource':(['modules','serverTime','featureFlags','trackingContext'],['modules','serverTime']),
      'SearchResultResource':(['id','contentType','title','summary','coverUrl','publisher','score','badges'],['id','contentType','title']),
      'ContentResource':(['id','contentType','title','summary','description','categoryCode','regionCode','media','publisher','contactsMasked','status','reviewStatus','statistics','createdAt','updatedAt','version'],['id','contentType','title','status','version']),
      'ConversationResource':(['id','peer','lastMessage','unreadCount','lastReadMessageId','updatedAt','version'],['id','unreadCount','version']),
      'NotificationResource':(['id','type','title','body','target','readAt','createdAt'],['id','type','title','createdAt']),
      'MembershipResource':(['id','skuId','name','status','startsAt','expiresAt','benefits','paidValueCent','remainingValueCent','version'],['status','version']),
      'PropResource':(['id','propType','name','quantity','status','expiresAt','configuration','version'],['id','propType','status','version']),
      'RedPacketCampaignResource':(['id','contentId','ownerUserId','status','totalCount','remainingCount','amountPerClaimCent','principalCent','serviceFeeCent','startAt','endAt','version'],['id','status','totalCount','remainingCount','amountPerClaimCent','version']),
      'OrderResource':(['orderNo','userId','orderType','status','currency','originalAmountCent','discountAmountCent','payableAmountCent','paidAmountCent','createdAt','paidAt','version'],['orderNo','orderType','status','currency','payableAmountCent','version']),
      'PaymentResource':(['id','orderNo','gateway','gatewayTradeNo','status','amountCent','currency','paidAt','reconciliationStatus','version'],['id','orderNo','gateway','status','amountCent','currency','version']),
      'RewardAccountResource':(['userId','pendingCent','availableCent','frozenCent','withdrawnCent','version','updatedAt'],['userId','pendingCent','availableCent','frozenCent','version']),
      'WithdrawalResource':(['id','withdrawalNo','userId','status','amountCent','feeCent','netAmountCent','payoutAccountMasked','failureCode','createdAt','completedAt','version'],['id','withdrawalNo','status','amountCent','feeCent','netAmountCent','version']),
      'ReferralResource':(['userId','inviteCode','directCount','level2Count','qualifiedDirectCount','pendingCommissionCent','settledCommissionCent','milestones','version'],['userId','inviteCode','version']),
      'ReferralActivityResource':(['userId','ruleVersion','score','qualified','milestoneCode','rewardStatus','calculatedAt','version'],['userId','ruleVersion','score','qualified','version']),
      'TaskResource':(['id','taskCode','name','taskType','status','progress','target','reward','claimable','expiresAt','version'],['id','taskCode','status','claimable','version']),
      'SupportTicketResource':(['id','ticketNo','category','subject','status','assignee','lastMessageAt','createdAt','version'],['id','ticketNo','status','version']),
      'AppVersionPolicyResource':(['platform','latestVersionCode','latestVersionName','updateType','downloadUrl','sha256','releaseNotes','minSupportedVersionCode','serverTime'],['platform','latestVersionCode','latestVersionName','updateType','serverTime']),
      'PublicPageResource':(['code','title','description','content','seoMetadata','download','trackingContext','version'],['code','version']),
      'DashboardResource':(['metrics','todos','trends','generatedAt'],['metrics','generatedAt']),
      'ReviewResource':(['id','subjectType','subjectId','status','riskLevel','assigneeId','decision','reason','createdAt','version'],['id','subjectType','subjectId','status','version']),
      'ReportResource':(['id','reporterId','subjectType','subjectId','reasonCode','status','decision','createdAt','version'],['id','status','version']),
      'AppealResource':(['id','appellantId','subjectType','subjectId','reason','status','decision','createdAt','version'],['id','status','version']),
      'ProductResource':(['id','productCode','name','productType','status','skus','version'],['id','productCode','name','status','version']),
      'CmsResource':(['id','code','contentType','title','status','content','version','effectiveAt'],['code','status','version']),
      'AgreementResource':(['code','version','title','content','effectiveAt','status'],['code','version','title','effectiveAt']),
      'RiskEventResource':(['id','eventType','subjectType','subjectId','riskLevel','score','status','ruleHits','createdAt','version'],['id','eventType','riskLevel','status','version']),
      'ConfigResource':(['key','environment','valueMasked','version','status','effectiveAt','requiresRestart','updatedBy'],['key','environment','version','status']),
      'RoleResource':(['id','code','name','permissionCodes','status','version'],['id','code','name','version']),
      'AuditLogResource':(['id','actorType','actorId','action','resourceType','resourceId','requestId','ipMasked','result','createdAt'],['id','action','result','createdAt']),
      'ApprovalResource':(['id','approvalType','resourceType','resourceId','requesterId','reviewerId','status','reason','createdAt','decidedAt','version'],['id','approvalType','status','version']),
      'JobResource':(['code','name','schedule','status','lastRunAt','nextRunAt','lastResult','version'],['code','status','version']),
      'ProviderConfigResource':(['provider','environment','activeVersion','draftVersion','configuredSecrets','connectionStatus','lastTestAt','version'],['provider','environment','version']),
      'CertificateResource':(['id','provider','certificateType','alias','fingerprint','notBefore','expiresAt','status','version'],['id','provider','certificateType','fingerprint','status','version']),
      'DomainResource':(['code','environment','hostname','dnsStatus','httpsStatus','certificateStatus','lastVerifiedAt','version'],['code','hostname','dnsStatus','httpsStatus','version']),
      'BuildProfileResource':(['id','name','environment','applicationId','apiBaseUrl','webSocketUrl','signingProfileId','status','version'],['id','name','environment','status','version']),
      'BuildJobResource':(['id','jobNo','profileId','gitRef','commitSha','status','steps','artifactId','createdAt','finishedAt','version'],['id','jobNo','profileId','commitSha','status','version']),
      'BuildArtifactResource':(['id','jobId','artifactType','fileName','sizeBytes','sha256','signatureFingerprint','downloadUrl','expiresAt'],['id','jobId','artifactType','fileName','sha256']),
      'SigningProfileResource':(['id','name','keyAlias','certificateFingerprint','expiresAt','status','lastTestAt','version'],['id','name','keyAlias','certificateFingerprint','status','version']),
    }
    for name,(fields,req) in resources.items(): S[name]=object_schema(fields,req)
    S['AppVersionPolicyResource']=object_schema(
      ['platform','latestVersionCode','latestVersionName','updateType','downloadUrl','sha256','releaseNotes','minSupportedVersionCode','serverTime'],
      ['platform','latestVersionCode','latestVersionName','updateType','downloadUrl','sha256','releaseNotes','minSupportedVersionCode','serverTime'],
      enums={'platform':['ANDROID'],'updateType':['NONE','OPTIONAL','FORCED']})
    app_policy=S['AppVersionPolicyResource']['properties']
    app_policy['latestVersionCode'].update({'minimum':1})
    app_policy['latestVersionName'].update({'maxLength':32})
    app_policy['downloadUrl'].update({'format':'uri','maxLength':2048})
    app_policy['minSupportedVersionCode'].update({'minimum':1})
    S['PlatformCapabilitiesResource']=object_schema(
      ['registration','publishing','redPacket','withdrawal'],
      ['registration','publishing','redPacket','withdrawal'])
    for field in S['PlatformCapabilitiesResource']['properties'].values():
        field.clear(); field['type']='boolean'
    S['PlatformStatusResource']=object_schema(
      ['maintenance','maintenanceMessage','capabilities','serverTime'],
      ['maintenance','maintenanceMessage','capabilities','serverTime'])
    S['PlatformStatusResource']['properties']['maintenance']={'type':'boolean'}
    S['PlatformStatusResource']['properties']['maintenanceMessage']={'type':'string','maxLength':500}
    S['PlatformStatusResource']['properties']['capabilities']={'$ref':'#/components/schemas/PlatformCapabilitiesResource'}
    S['PlatformStatusResource']['properties']['serverTime']={'type':'string','format':'date-time'}
    # Override broad object fields to keep JSON Schema valid and practical.
    for n, sch in S.items():
        if not isinstance(sch,dict) or 'properties' not in sch: continue
        for key in list(sch['properties']):
            if key in {'modules','featureFlags','trackingContext','publisher','contactsMasked','statistics','media','peer','lastMessage','benefits','configuration','milestones','progress','target','reward','metrics','todos','trends','skus','content','seoMetadata','download','ruleHits','valueMasked','steps','configuredSecrets'}:
                sch['properties'][key]={'type':'object','additionalProperties':True}
            if key in {'capabilities','permissionCodes','badges'}:
                sch['properties'][key]={'type':'array','items':{'type':'string'}}
    return S

def path_parameters(path: str) -> list[dict]:
    out=[]
    for name in re.findall(r'\{([^}]+)\}',path):
        schema={'type':'string','minLength':1,'maxLength':128}
        if name in {'id','userId','inviteeId'}: schema={'type':'string','pattern':'^[A-Za-z0-9_-]{1,64}$'}
        out.append({'name':name,'in':'path','required':True,'description':f'路径资源标识：{name}','schema':schema})
    return out

def query_parameters(row: dict[str,str]) -> list[dict]:
    path=row['路径']
    if row['方法']=='DELETE':
        if path == '/api/v1/contents/{id}':
            return [{
              'name':'expectedVersion','in':'query','required':True,
              'description':'当前内容乐观锁版本；版本不一致返回 COMMON-409-VERSION_CONFLICT',
              'schema':{'type':'integer','format':'int64','minimum':0},
            }]
        return []
    if row['方法']!='GET': return []
    purpose=row['用途']; params=[]
    if is_list(row):
        params += [
          {'name':'page','in':'query','required':False,'schema':{'type':'integer','minimum':1,'default':1},'description':'页码；游标接口可忽略'},
          {'name':'pageSize','in':'query','required':False,'schema':{'type':'integer','minimum':1,'maximum':100,'default':20},'description':'每页数量'},
          {'name':'cursor','in':'query','required':False,'schema':{'type':'string','maxLength':256},'description':'游标；与page二选一'},
          {'name':'status','in':'query','required':False,'schema':{'type':'string','maxLength':64},'description':'状态筛选'},
          {'name':'keyword','in':'query','required':False,'schema':{'type':'string','maxLength':100},'description':'关键词筛选'},
          {'name':'sort','in':'query','required':False,'schema':{'type':'string','maxLength':64},'description':'白名单排序字段，例如 createdAt:desc'},
        ]
    if path.endswith('/search'):
        params += [
          {'name':'q','in':'query','required':True,'schema':{'type':'string','minLength':1,'maxLength':100}},
          {'name':'contentType','in':'query','required':False,'schema':{'type':'string','enum':['PROJECT','APP','GROUP_CHAT','TEAM_LEADER']}},
          {'name':'categoryCode','in':'query','required':False,'schema':{'type':'string','maxLength':64}},
          {'name':'regionCode','in':'query','required':False,'schema':{'type':'string','maxLength':32}},
        ]
    if '/contents' in path and is_list(row):
        params += [
          {'name':'contentType','in':'query','required':False,'schema':{'type':'string','enum':['PROJECT','APP','GROUP_CHAT','TEAM_LEADER']}},
          {'name':'categoryCode','in':'query','required':False,'schema':{'type':'string','maxLength':64}},
          {'name':'regionCode','in':'query','required':False,'schema':{'type':'string','maxLength':32}},
        ]
    if 'version-check' in path:
        params += [
          {'name':'platform','in':'query','required':True,'schema':{'type':'string','enum':['ANDROID']}},
          {'name':'versionCode','in':'query','required':True,'schema':{'type':'integer','format':'int64','minimum':1}},
          {'name':'channel','in':'query','required':True,'schema':{'type':'string','maxLength':64}},
          {'name':'environment','in':'query','required':True,'schema':{'type':'string','enum':['DEV','TEST','STAGING','PROD']}},
        ]
    if path.endswith('/payment-status'):
        params.append({'name':'waitSeconds','in':'query','required':False,'schema':{'type':'integer','minimum':0,'maximum':10,'default':0},'description':'可选短轮询等待秒数'})
    if path.endswith('/download'):
        params.append({'name':'disposition','in':'query','required':False,'schema':{'type':'string','enum':['INLINE','ATTACHMENT'],'default':'ATTACHMENT'}})
    # de-duplicate by name/in
    seen=set(); result=[]
    for x in params:
        k=(x['name'],x['in'])
        if k not in seen: seen.add(k); result.append(x)
    return result


def parameter_contract_schema(parameters: list[dict]) -> OrderedDict:
    """Build the named request contract for path/query/header parameters.

    The named schema is used by contract registries and generated clients. The
    OpenAPI operation continues to expose parameters in their native locations;
    this schema is an auditable aggregate view, not a replacement for them.
    """
    properties: OrderedDict[str, dict] = OrderedDict()
    required: list[str] = []
    referenced_headers = {
        '#/components/parameters/XIdempotencyKey': (
            'xIdempotencyKey',
            {'type':'string','minLength':16,'maxLength':128},
            True,
        ),
        '#/components/parameters/XProviderTimestamp': (
            'xProviderTimestamp',
            {'type':'string','pattern':'^\\d{10,13}$'},
            True,
        ),
        '#/components/parameters/XProviderNonce': (
            'xProviderNonce',
            {'type':'string','minLength':8,'maxLength':128},
            True,
        ),
        '#/components/parameters/XProviderSignature': (
            'xProviderSignature',
            {'type':'string','minLength':16,'maxLength':1024},
            True,
        ),
    }
    for parameter in parameters:
        if '$ref' in parameter:
            resolved = referenced_headers.get(parameter['$ref'])
            if not resolved:
                continue
            name, schema, is_required = resolved
            properties[name] = dict(schema)
            if is_required:
                required.append(name)
            continue
        if parameter.get('in') not in {'path','query','header'}:
            continue
        name = parameter['name']
        schema = dict(parameter.get('schema') or {'type':'string'})
        if parameter.get('description'):
            schema['description'] = parameter['description']
        properties[name] = schema
        if parameter.get('required'):
            required.append(name)
    result = OrderedDict(type='object', additionalProperties=False, properties=properties)
    if required:
        result['required'] = list(dict.fromkeys(required))
    return result

def security_for(permission: str):
    if '供应商签名' in permission: return [{'ProviderSignature':[]}]
    if 'RefreshToken' in permission: return [{'RefreshTokenAuth':[]}]
    if '已登录待二次验证' in permission: return [{'MfaTicketAuth':[]}]
    if '公开' in permission: return []
    if '登录可选' in permission: return [{}, {'BearerAuth':[]}]
    return [{'BearerAuth':[]}]

def error_codes_for(row: dict[str,str]) -> list[str]:
    codes=['COMMON-400-VALIDATION','COMMON-401-UNAUTHENTICATED','COMMON-403-FORBIDDEN','COMMON-404-NOT_FOUND','COMMON-409-VERSION_CONFLICT','COMMON-422-BUSINESS_RULE','COMMON-429-RATE_LIMITED','COMMON-500-INTERNAL']
    m=row['模块']; p=row['路径']
    if m=='Auth': codes += ['AUTH-401-SESSION_REVOKED','AUTH-423-ACCOUNT_RESTRICTED']
    if m=='Identity' or '实名' in row['权限']: codes += ['IDENTITY-422-NOT_VERIFIED']
    if m in {'Content','Review'}: codes += ['CONTENT-409-STATUS_TRANSITION']
    if m in {'Payment','Payments','Order'}: codes += ['PAYMENT-409-AMOUNT_MISMATCH','PAYMENT-422-GATEWAY_REJECTED']
    if m=='RedPacket': codes += ['REDPACKET-409-STOCK_EXHAUSTED','REDPACKET-409-ALREADY_CLAIMED','REDPACKET-422-VIEW_INVALID']
    if m in {'Reward','Rewards'}: codes += ['REWARD-423-RISK_FROZEN']
    if m=='Withdrawal' or m=='Withdrawals': codes += ['WITHDRAWAL-422-BALANCE_INSUFFICIENT','WITHDRAWAL-409-DUPLICATE']
    if 'Config' in m or 'Certificate' in m or 'Signing' in m: codes += ['CONFIG-409-ACTIVATION_FAILED']
    return list(dict.fromkeys(codes))

def response_statuses(row: dict[str,str]) -> list[str]:
    sts=['400','401','403','404','409','422','429','500']
    if '公开' in row['权限']:
        sts=[x for x in sts if x not in {'401','403'}]
    return sts

def generate_spec(rows: list[dict[str,str]], admin=False) -> OrderedDict:
    title='合伙云 Pro 管理后台 API' if admin else '合伙云 Pro 客户端与公开 API'
    spec=OrderedDict()
    spec['openapi']='3.1.0'
    spec['info']=OrderedDict(title=title,version='1.2.2',description='V1.2.2 冻结开发契约。Schema 为 API View/Command，不得直接暴露数据库实体；breaking change 必须通过 ADR、兼容窗口和契约测试。')
    spec['servers']=[{'url':'https://api.orbexa.cc','description':'生产环境'},{'url':'https://api-staging.orbexa.cc','description':'预发布环境'}]
    spec['tags']=[{'name':m,'description':f'{m} 领域接口'} for m in list(dict.fromkeys(r['模块'] for r in rows))]
    paths=OrderedDict(); schemas=make_common_schemas()
    body_methods={'POST','PUT','PATCH'}
    for row in rows:
        path=row['路径']; http_method=row['方法']; method=http_method.lower(); opid=operation_id(row)
        req_name=schema_name(opid,'Request'); params_name=schema_name(opid,'Parameters'); resp_name=schema_name(opid,'Response')
        fields, required, enums, desc=request_fields(row)
        if http_method in body_methods:
            # Every command has a named request contract, including explicit
            # no-payload commands, so registry and code generation never point
            # to a missing component.
            schemas[req_name]=object_schema(fields,required,desc,enums)
            if path == '/public-api/v1/app/version-check':
                request=schemas[req_name]['properties']
                request['versionCode'].update({'minimum':1})
                request['versionName'].update({'maxLength':32})
                request['channel'].update({'minLength':1,'maxLength':64})
                request['environment'].pop('maxLength',None)
            # Provider callbacks preserve the signed original payload.
            if 'notify' in path:
                schemas[req_name]['properties']['rawPayload']={'type':'object','additionalProperties':True,'description':'供应商原始字段；验签前不得修改'}
        resource=resource_ref(row['模块'])
        if path == '/public-api/v1/platform/status':
            data_schema={'$ref':'#/components/schemas/PlatformStatusResource'}
        elif path in {'/api/v1/app/version-check','/public-api/v1/app/version-check'}:
            data_schema={'$ref':'#/components/schemas/AppVersionPolicyResource'}
        elif http_method=='GET' and is_list(row):
            data_schema=OrderedDict(type='object',additionalProperties=False,required=['items','page'],properties=OrderedDict([
              ('items',{'type':'array','items':{'$ref':f'#/components/schemas/{resource}'}}),('page',{'$ref':'#/components/schemas/PageMeta'})]))
        elif http_method=='DELETE':
            data_schema={'$ref':'#/components/schemas/CommandResultResource'}
        elif http_method in body_methods and not any(x in path for x in ['/login','/refresh','security-challenges','version-check','upload-sessions']):
            data_schema={'oneOf':[{'$ref':f'#/components/schemas/{resource}'},{'$ref':'#/components/schemas/CommandResultResource'}]}
        elif 'security-challenges' in path:
            data_schema={'$ref':'#/components/schemas/ChallengeResource'}
        else:
            data_schema={'$ref':f'#/components/schemas/{resource}'}
        schemas[resp_name]=OrderedDict(type='object',additionalProperties=False,required=['success','requestId','data'],properties=OrderedDict([
          ('success',{'type':'boolean','const':True}),('requestId',{'type':'string','maxLength':64}),('timestamp',{'type':'string','format':'date-time'}),('data',data_schema)
        ]))
        op=OrderedDict()
        op['operationId']=opid; op['summary']=row['用途']; op['tags']=[row['模块']]
        op['security']=security_for(row['权限'])
        op['x-permission']=row['权限']; op['x-idempotent']=row['幂等']=='是'; op['x-release']=row['计划版本']; op['x-contract-maturity']='FROZEN'; op['x-error-codes']=error_codes_for(row)
        params=path_parameters(path)+query_parameters(row)
        if row['幂等']=='是' and http_method in {'POST','PUT','PATCH','DELETE'}:
            params.append({'$ref':'#/components/parameters/XIdempotencyKey'})
        if '供应商签名' in row['权限']:
            params += [{'$ref':'#/components/parameters/XProviderTimestamp'},{'$ref':'#/components/parameters/XProviderNonce'},{'$ref':'#/components/parameters/XProviderSignature'}]
        if params:
            op['parameters']=params
        if http_method in body_methods:
            op['x-request-schema']=f'#/components/schemas/{req_name}'
            if fields:
                op['requestBody']=OrderedDict(required=body_required(row,fields),content={'application/json':{'schema':{'$ref':f'#/components/schemas/{req_name}'}}})
        else:
            schemas[params_name]=parameter_contract_schema(params)
            op['x-request-schema']=f'#/components/schemas/{params_name}'
        responses=OrderedDict()
        responses['200']={'description':'成功','content':{'application/json':{'schema':{'$ref':f'#/components/schemas/{resp_name}'}}}}
        for status in response_statuses(row):
            descriptions={'400':'请求参数错误','401':'未认证或会话失效','403':'权限或能力不足','404':'资源不存在','409':'版本、幂等或状态冲突','422':'业务规则不满足','429':'触发限流','500':'内部错误'}
            responses[status]={'description':descriptions[status],'content':{'application/json':{'schema':{'$ref':'#/components/schemas/ErrorResponse'}}}}
        op['responses']=responses
        paths.setdefault(path,OrderedDict())[method]=op
    spec['paths']=paths
    spec['components']=OrderedDict()
    spec['components']['securitySchemes']=OrderedDict([
      ('BearerAuth',{'type':'http','scheme':'bearer','bearerFormat':'JWT'}),
      ('RefreshTokenAuth',{'type':'apiKey','in':'header','name':'X-Refresh-Token'}),
      ('MfaTicketAuth',{'type':'apiKey','in':'header','name':'X-MFA-Ticket'}),
      ('ProviderSignature',{'type':'apiKey','in':'header','name':'X-Provider-Signature'})
    ])
    spec['components']['parameters']=OrderedDict([
      ('XIdempotencyKey',{'name':'X-Idempotency-Key','in':'header','required':True,'description':'UUID/ULID。相同主体、操作和键必须返回首次结果；请求摘要不一致返回 COMMON-409-IDEMPOTENCY_CONFLICT。','schema':{'type':'string','minLength':16,'maxLength':128}}),
      ('XProviderTimestamp',{'name':'X-Provider-Timestamp','in':'header','required':True,'schema':{'type':'string','pattern':'^\\d{10,13}$'}}),
      ('XProviderNonce',{'name':'X-Provider-Nonce','in':'header','required':True,'schema':{'type':'string','minLength':8,'maxLength':128}}),
      ('XProviderSignature',{'name':'X-Provider-Signature','in':'header','required':True,'schema':{'type':'string','minLength':16,'maxLength':1024}}),
    ])
    spec['components']['schemas']=schemas
    return spec

def dump_yaml(path: Path, data):
    class Dumper(yaml.SafeDumper):
        pass
    def represent_ordered(dumper, data):
        return dumper.represent_dict(data.items())
    Dumper.add_representer(OrderedDict, represent_ordered)
    path.write_text(yaml.dump(data,Dumper=Dumper,allow_unicode=True,sort_keys=False,width=140),encoding='utf-8')


def _closed_object(properties: OrderedDict, required: list[str]) -> OrderedDict:
    return OrderedDict(
        type='object',
        additionalProperties=False,
        required=required,
        properties=properties,
    )


def _r16_schemas() -> OrderedDict[str, OrderedDict]:
    string_id = lambda: OrderedDict(type='string', maxLength=64)
    text = lambda length=2000: OrderedDict(type='string', maxLength=length)
    money = lambda: OrderedDict(type='integer', format='int64', minimum=0)
    version = lambda: OrderedDict(type='integer', format='int64', minimum=0)
    date_time = lambda: OrderedDict(type='string', format='date-time')
    schemas: OrderedDict[str, OrderedDict] = OrderedDict()
    schemas['BenefitResource'] = _closed_object(OrderedDict([
        ('benefitCode', text(64)),
        ('name', text(255)),
        ('value', OrderedDict([('$ref', '#/components/schemas/JsonValue')])),
        ('unit', text(64)),
    ]), ['benefitCode', 'name', 'value'])
    schemas['ProductSkuResource'] = _closed_object(OrderedDict([
        ('id', string_id()),
        ('productId', string_id()),
        ('skuCode', text(64)),
        ('name', text(255)),
        ('priceCent', money()),
        ('memberPriceCent', money()),
        ('durationDays', OrderedDict(type='integer', format='int64', minimum=0)),
        ('benefits', OrderedDict(type='array', maxItems=100, items=OrderedDict([('$ref', '#/components/schemas/BenefitResource')]))),
        ('commissionEnabled', OrderedDict(type='boolean')),
        ('level1Bps', OrderedDict(type='integer', minimum=0, maximum=10000)),
        ('level2Bps', OrderedDict(type='integer', minimum=0, maximum=10000)),
        ('saleStartsAt', date_time()),
        ('saleEndsAt', date_time()),
        ('status', text(64)),
        ('version', version()),
    ]), ['id', 'productId', 'skuCode', 'name', 'priceCent', 'benefits', 'commissionEnabled', 'status', 'version'])
    schemas['ProductResource'] = _closed_object(OrderedDict([
        ('id', string_id()),
        ('productCode', text(64)),
        ('name', text(255)),
        ('productType', text(64)),
        ('description', text(2000)),
        ('displayOrder', OrderedDict(type='integer', format='int32')),
        ('status', text(64)),
        ('skus', OrderedDict(type='array', maxItems=100, items=OrderedDict([('$ref', '#/components/schemas/ProductSkuResource')]))),
        ('version', version()),
    ]), ['id', 'productCode', 'name', 'productType', 'status', 'skus', 'version'])
    schemas['OrderItemResource'] = _closed_object(OrderedDict([
        ('skuId', string_id()),
        ('itemName', text(255)),
        ('quantity', OrderedDict(type='integer', minimum=1)),
        ('unitPriceCent', money()),
        ('subtotalAmountCent', money()),
    ]), ['itemName', 'quantity', 'unitPriceCent', 'subtotalAmountCent'])
    schemas['OrderPriceSnapshotResource'] = _closed_object(OrderedDict([
        ('originalAmountCent', money()),
        ('discountAmountCent', money()),
        ('serviceFeeCent', money()),
        ('payableAmountCent', money()),
        ('ruleVersions', OrderedDict(type='array', maxItems=100, items=text(128))),
    ]), ['originalAmountCent', 'discountAmountCent', 'serviceFeeCent', 'payableAmountCent', 'ruleVersions'])
    schemas['NoRefundEvidenceResource'] = _closed_object(OrderedDict([
        ('confirmed', OrderedDict(type='boolean')),
        ('agreementVersion', text(64)),
        ('confirmedAt', date_time()),
    ]), ['confirmed', 'agreementVersion'])
    schemas['OrderResource'] = _closed_object(OrderedDict([
        ('orderNo', text(128)),
        ('userId', string_id()),
        ('orderType', text(64)),
        ('status', OrderedDict(type='string', enum=[
            'PENDING_PAYMENT', 'PAYMENT_PROCESSING', 'PAID', 'FULFILLING',
            'COMPLETED', 'PAYMENT_FAILED', 'CLOSED', 'CHANNEL_REVERSAL',
        ])),
        ('currency', OrderedDict(type='string', minLength=3, maxLength=3)),
        ('items', OrderedDict(type='array', maxItems=100, items=OrderedDict([('$ref', '#/components/schemas/OrderItemResource')]))),
        ('priceSnapshot', OrderedDict([('$ref', '#/components/schemas/OrderPriceSnapshotResource')])),
        ('noRefundEvidence', OrderedDict([('$ref', '#/components/schemas/NoRefundEvidenceResource')])),
        ('paidAmountCent', money()),
        ('createdAt', date_time()),
        ('paidAt', date_time()),
        ('version', version()),
    ]), ['orderNo', 'userId', 'orderType', 'status', 'currency', 'items', 'priceSnapshot', 'noRefundEvidence', 'createdAt', 'version'])
    return schemas


def _r16_request_schemas() -> OrderedDict[str, OrderedDict]:
    common_product = OrderedDict([
        ('productCode', OrderedDict(type='string', maxLength=64)),
        ('name', OrderedDict(type='string', maxLength=255)),
        ('productType', OrderedDict(type='string', maxLength=64)),
        ('description', OrderedDict(type='string', maxLength=2000)),
        ('displayOrder', OrderedDict(type='integer', format='int32')),
        ('status', OrderedDict(type='string', maxLength=64)),
    ])
    benefit_array = OrderedDict(type='array', maxItems=100, items=OrderedDict([('$ref', '#/components/schemas/BenefitResource')]))
    common_sku = OrderedDict([
        ('productId', OrderedDict(type='string', maxLength=64)),
        ('skuCode', OrderedDict(type='string', maxLength=64)),
        ('name', OrderedDict(type='string', maxLength=255)),
        ('priceCent', OrderedDict(type='integer', format='int64', minimum=0)),
        ('memberPriceCent', OrderedDict(type='integer', format='int64', minimum=0)),
        ('durationDays', OrderedDict(type='integer', format='int64', minimum=0)),
        ('benefits', benefit_array),
        ('commissionEnabled', OrderedDict(type='boolean')),
        ('level1Bps', OrderedDict(type='integer', minimum=0, maximum=10000)),
        ('level2Bps', OrderedDict(type='integer', minimum=0, maximum=10000)),
        ('saleStartsAt', OrderedDict(type='string', format='date-time')),
        ('saleEndsAt', OrderedDict(type='string', format='date-time')),
        ('status', OrderedDict(type='string', maxLength=64)),
    ])
    return OrderedDict([
        ('AdminProductsPostProductsRequest', _closed_object(common_product.copy(), ['productCode', 'name', 'productType', 'status'])),
        ('AdminProductsPatchProductsByIdRequest', _closed_object(OrderedDict([
            ('name', common_product['name']),
            ('productType', common_product['productType']),
            ('description', common_product['description']),
            ('displayOrder', common_product['displayOrder']),
            ('status', common_product['status']),
            ('expectedVersion', OrderedDict(type='integer', format='int64', minimum=0)),
        ]), ['expectedVersion'])),
        ('AdminProductsPostSkusRequest', _closed_object(common_sku.copy(), [
            'productId', 'skuCode', 'name', 'priceCent', 'benefits',
            'commissionEnabled', 'status',
        ])),
        ('AdminProductsPatchSkusByIdRequest', _closed_object(OrderedDict([
            (key, value) for key, value in common_sku.items()
            if key not in {'productId', 'skuCode'}
        ] + [('expectedVersion', OrderedDict(type='integer', format='int64', minimum=0))]), ['expectedVersion'])),
    ])


def _success_response(data_schema: OrderedDict) -> OrderedDict:
    return _closed_object(OrderedDict([
        ('success', OrderedDict(type='boolean', const=True)),
        ('requestId', OrderedDict(type='string', maxLength=64)),
        ('timestamp', OrderedDict(type='string', format='date-time')),
        ('data', data_schema),
    ]), ['success', 'requestId', 'data'])


def _schema_block(name: str, schema: OrderedDict) -> str:
    class Dumper(yaml.SafeDumper):
        pass
    Dumper.add_representer(
        OrderedDict,
        lambda dumper, value: dumper.represent_dict(value.items()),
    )
    body = yaml.dump(
        {name: schema},
        Dumper=Dumper,
        allow_unicode=True,
        sort_keys=False,
        width=140,
    ).rstrip()
    return '\n'.join('    ' + line for line in body.splitlines()) + '\n'


def _replace_schema(text: str, name: str, schema: OrderedDict) -> str:
    block = _schema_block(name, schema)
    pattern = re.compile(
        rf'(?ms)^    {re.escape(name)}:\n.*?(?=^    [A-Za-z][A-Za-z0-9]*:\n|\Z)'
    )
    if pattern.search(text):
        return pattern.sub(block, text, count=1)
    marker = '    JsonScalar:\n'
    if marker not in text:
        raise SystemExit(f'cannot insert R16 schema {name}: JsonScalar marker missing')
    return text.replace(marker, block + marker, 1)


def _sync_r16_catalogs() -> list[str]:
    changed: list[str] = []

    def save_if_changed(relative: str, before: list[dict[str, str]], after: list[dict[str, str]]) -> None:
        if before != after:
            write_csv(relative, after, list(before[0]) if before else None)
            changed.append(relative)

    fields_path = 'catalogs/ui_page_fields.csv'
    before_fields = read_csv(fields_path)
    target_pages = {'SCR-ORDER-001', 'SCR-ORDER-002', 'ADM-ORDER-001', 'ADM-PROD-001'}
    removed = [
        row for row in before_fields
        if row['页面ID'] in target_pages and row['字段角色'] == 'DISPLAY'
    ]
    retained = [row for row in before_fields if row not in removed]
    reusable_ids = [row['字段ID'] for row in removed]
    max_id = max(int(row['字段ID'].split('-')[1]) for row in before_fields)

    def next_id() -> str:
        nonlocal max_id
        if reusable_ids:
            return reusable_ids.pop(0)
        max_id += 1
        return f'FLD-{max_id:05d}'

    page_meta = {
        'SCR-ORDER-001': ('ANDROID', '订单列表'),
        'SCR-ORDER-002': ('ANDROID', '订单详情'),
        'ADM-ORDER-001': ('ADMIN', '订单列表'),
        'ADM-PROD-001': ('ADMIN', '商品与SKU'),
    }

    def display_row(
        page: str,
        key: str,
        label: str,
        semantic: str,
        control: str,
        source: str,
        order: int,
        operation: str,
        required: str = '服务端返回时展示',
        condition: str = '有值且当前角色拥有字段访问权限',
        validation: str = '按显式OpenAPI Schema校验',
        help_text: str = '',
    ) -> dict[str, str]:
        platform, page_name = page_meta[page]
        row = {column: '' for column in before_fields[0]}
        row.update({
            '字段ID': next_id(),
            '页面ID': page,
            '平台': platform,
            '页面': page_name,
            '区域': '订单卡片' if page == 'SCR-ORDER-001' else ('主要内容' if page != 'ADM-PROD-001' else '商品与SKU表格'),
            '字段键': key,
            '显示名称': label,
            '字段角色': 'DISPLAY',
            '语义类型': semantic,
            '控件类型': control,
            '数据来源': source,
            '必填条件': required,
            '显示条件': condition,
            '可编辑条件': '只读；通过明确写操作更新',
            '校验规则': validation,
            '默认值': '禁止客户端暗设业务默认',
            '选项来源': 'OpenAPI枚举或配置注册表；不得在页面散落硬编码',
            '敏感级别': 'NORMAL',
            '脱敏规则': '无需特殊掩码；仍遵守最小展示',
            '帮助说明': help_text,
            '错误提示': f'{label}暂时无法显示',
            '排序': str(order),
            '关联operationId': operation,
            '成熟度': 'FROZEN_R16_CR0440',
        })
        return row

    order_fields = {
        'orderNo': ('订单号', 'string', 'TEXT'),
        'userId': ('用户ID', 'string', 'TEXT'),
        'orderType': ('订单类型', 'string', 'STATUS_TAG'),
        'status': ('订单状态', 'ORDER_STATUS', 'STATUS_TAG'),
        'currency': ('币种', 'string', 'TEXT'),
        'items': ('订单项', 'OrderItemResource[]', 'STRUCTURED_LIST'),
        'priceSnapshot.originalAmountCent': ('原价', 'integer', 'MONEY_CENT'),
        'priceSnapshot.discountAmountCent': ('优惠金额', 'integer', 'MONEY_CENT'),
        'priceSnapshot.serviceFeeCent': ('服务费', 'integer', 'MONEY_CENT'),
        'priceSnapshot.payableAmountCent': ('应付金额', 'integer', 'MONEY_CENT'),
        'paidAmountCent': ('实付金额', 'integer', 'MONEY_CENT'),
        'noRefundEvidence.confirmed': ('不退款确认', 'boolean', 'STATUS_TAG'),
        'noRefundEvidence.agreementVersion': ('不退款协议版本', 'string', 'TEXT'),
        'noRefundEvidence.confirmedAt': ('确认时间', 'string', 'DATETIME_TEXT'),
        'createdAt': ('创建时间', 'string', 'DATETIME_TEXT'),
        'paidAt': ('支付时间', 'string', 'DATETIME_TEXT'),
        'version': ('版本', 'integer', 'NUMBER_TEXT'),
    }
    order_page_specs = {
        'SCR-ORDER-001': [
            'orderNo', 'orderType', 'status', 'items',
            'priceSnapshot.payableAmountCent', 'paidAmountCent',
            'noRefundEvidence.confirmed', 'createdAt',
        ],
        'SCR-ORDER-002': [
            'orderNo', 'orderType', 'status', 'currency', 'items',
            'priceSnapshot.originalAmountCent', 'priceSnapshot.discountAmountCent',
            'priceSnapshot.serviceFeeCent', 'priceSnapshot.payableAmountCent',
            'paidAmountCent', 'noRefundEvidence.confirmed',
            'noRefundEvidence.agreementVersion', 'noRefundEvidence.confirmedAt',
            'createdAt', 'paidAt', 'version',
        ],
        'ADM-ORDER-001': [
            'orderNo', 'userId', 'orderType', 'status', 'currency', 'items',
            'priceSnapshot.originalAmountCent', 'priceSnapshot.discountAmountCent',
            'priceSnapshot.serviceFeeCent', 'priceSnapshot.payableAmountCent',
            'paidAmountCent', 'noRefundEvidence.confirmed',
            'noRefundEvidence.agreementVersion', 'noRefundEvidence.confirmedAt',
            'createdAt', 'paidAt', 'version',
        ],
    }
    source_ops = {
        'SCR-ORDER-001': ('orderGetMeOrders', 'orderGetMeOrders.response.data.OrderResource'),
        'SCR-ORDER-002': ('orderGetMeOrdersByOrderno', 'orderGetMeOrdersByOrderno.response.data.OrderResource'),
        'ADM-ORDER-001': ('adminOrdersGetOrders', 'adminOrdersGetOrders.response.data.OrderResource'),
    }
    appended: list[dict[str, str]] = []
    for page, keys in order_page_specs.items():
        operation, source = source_ops[page]
        base_order = 17 if page == 'SCR-ORDER-001' else (6 if page == 'SCR-ORDER-002' else 19)
        for offset, key in enumerate(keys):
            label, semantic, control = order_fields[key]
            appended.append(display_row(
                page, key, label, semantic, control, source,
                base_order + offset, operation,
                help_text='金额统一为分；ORDER_STATUS必须使用中文映射，不得直出技术枚举' if 'AmountCent' in key or key == 'status' else '',
            ))

    product_fields = [
        ('id', '商品ID', 'string', 'TEXT'),
        ('productCode', '商品代码', 'string', 'TEXT'),
        ('name', '商品名称', 'string', 'TEXT'),
        ('productType', '商品类型', 'string', 'STATUS_TAG'),
        ('description', '商品说明', 'string', 'TEXT'),
        ('displayOrder', '展示顺序', 'integer', 'NUMBER_TEXT'),
        ('status', '商品状态', 'string', 'STATUS_TAG'),
        ('skus', 'SKU', 'ProductSkuResource[]', 'STRUCTURED_LIST'),
        ('version', '版本', 'integer', 'NUMBER_TEXT'),
    ]
    sku_fields = [
        ('id', 'SKU ID', 'string', 'TEXT'),
        ('productId', '商品ID', 'string', 'TEXT'),
        ('skuCode', 'SKU代码', 'string', 'TEXT'),
        ('name', 'SKU名称', 'string', 'TEXT'),
        ('priceCent', '售价', 'integer', 'MONEY_CENT'),
        ('memberPriceCent', '会员价', 'integer', 'MONEY_CENT'),
        ('durationDays', '有效期', 'integer', 'NUMBER_TEXT'),
        ('benefits', '权益快照', 'BenefitResource[]', 'STRUCTURED_LIST'),
        ('commissionEnabled', '参与分佣', 'boolean', 'STATUS_TAG'),
        ('level1Bps', '一级比例', 'integer', 'BPS_TEXT'),
        ('level2Bps', '二级比例', 'integer', 'BPS_TEXT'),
        ('saleStartsAt', '销售开始时间', 'string', 'DATETIME_TEXT'),
        ('saleEndsAt', '销售结束时间', 'string', 'DATETIME_TEXT'),
        ('status', 'SKU状态', 'string', 'STATUS_TAG'),
        ('version', '版本', 'integer', 'NUMBER_TEXT'),
    ]
    for index, (key, label, semantic, control) in enumerate(product_fields, start=19):
        appended.append(display_row(
            'ADM-PROD-001', key, label, semantic, control,
            'adminProductsGetProducts.response.data.ProductResource',
            index, 'adminProductsGetProducts',
        ))
    for index, (key, label, semantic, control) in enumerate(sku_fields, start=40):
        appended.append(display_row(
            'ADM-PROD-001', f'sku.{key}', label, semantic, control,
            'adminProductsGetSkus.response.data.ProductSkuResource',
            index, 'adminProductsGetSkus',
        ))
    after_fields = retained + appended
    save_if_changed(fields_path, before_fields, after_fields)

    page_documents = {
        'SCR-ORDER-001': 'docs/02-ui/page-specs/android/SCR-ORDER-001_订单列表.md',
        'SCR-ORDER-002': 'docs/02-ui/page-specs/android/SCR-ORDER-002_订单详情.md',
        'ADM-ORDER-001': 'docs/02-ui/page-specs/admin/ADM-ORDER-001_订单列表.md',
        'ADM-PROD-001': 'docs/02-ui/page-specs/admin/ADM-PROD-001_商品与SKU.md',
    }
    document_columns = [
        '字段ID', '区域', '字段键', '显示名称', '字段角色', '语义类型',
        '控件类型', '数据来源', '必填条件', '显示条件', '可编辑条件',
        '校验规则', '敏感级别', '脱敏规则', '错误提示',
    ]
    header = '| ' + ' | '.join(document_columns) + ' |'
    separator = '| ' + ' | '.join('---' for _ in document_columns) + ' |'
    for page, relative in page_documents.items():
        path = ROOT / relative
        text = path.read_text(encoding='utf-8')
        page_rows = sorted(
            (row for row in after_fields if row['页面ID'] == page),
            key=lambda row: int(row['排序'] or 0),
        )
        body = [header, separator]
        for row in page_rows:
            values = [
                str(row[column]).replace('|', r'\|').replace('\r', ' ').replace('\n', ' ')
                for column in document_columns
            ]
            body.append('| ' + ' | '.join(values) + ' |')
        section = '## 3. 字段施工表\n\n' + '\n'.join(body) + '\n\n'
        updated, count = re.subn(
            r'(?ms)^## 3\. 字段施工表\n.*?(?=^## 4\.)',
            section,
            text,
            count=1,
        )
        if count != 1:
            raise SystemExit(f'cannot synchronize R16 page field table: {relative}')
        if updated != text:
            path.write_text(updated, encoding='utf-8', newline='\n')
            changed.append(relative)

    states_path = 'catalogs/ui_page_states.csv'
    before_states = read_csv(states_path)
    after_states = [row.copy() for row in before_states]
    for row in after_states:
        if row['页面ID'] in {'SCR-ORDER-001', 'ADM-ORDER-001'} and row['状态码'] == 'EMPTY':
            row['允许操作'] = '清除筛选/返回'
            row['展示行为'] = '展示当前筛选、暂无订单说明和返回路径；不得出现创建订单或联系客服'
            row['成熟度'] = 'FROZEN_R16_CR0440'
    save_if_changed(states_path, before_states, after_states)

    actions_path = 'catalogs/ui_action_matrix.csv'
    before_actions = read_csv(actions_path)
    after_actions = [row.copy() for row in before_actions]
    for row in after_actions:
        if row['UI_ID'] == 'ADM-ORDER-001':
            for key, value in list(row.items()):
                row[key] = value.replace('CommandResultResource', 'OrderResource')
            row['成熟度'] = 'FROZEN_R16_CR0440'
        if row['UI_ID'] == 'ADM-PROD-001' and 'Skus' in row.get('operationId', ''):
            for key, value in list(row.items()):
                row[key] = value.replace('ProductResource', 'ProductSkuResource')
            row['成熟度'] = 'FROZEN_R16_CR0440'
    save_if_changed(actions_path, before_actions, after_actions)

    visual_refs = {
        'SCR-ORDER-001': 'B08/P04',
        'SCR-ORDER-002': 'SPEC:design/R16-UI-FROZEN/specs/SCR-ORDER-002.md',
        'ADM-ORDER-001': 'SPEC:design/R16-UI-FROZEN/specs/ADM-ORDER-001.md',
    }
    for relative, id_column, reference_column in (
        ('catalogs/ui_page_specifications.csv', '页面ID', 'UI参考'),
        ('catalogs/frontend_backend_matrix.csv', 'UI_ID', 'UI参考'),
    ):
        before = read_csv(relative)
        after = [row.copy() for row in before]
        for row in after:
            if row[id_column] in visual_refs:
                row[reference_column] = visual_refs[row[id_column]]
                if '成熟度' in row:
                    row['成熟度'] = 'FROZEN_R16_CR0440'
                if '绑定成熟度' in row:
                    row['绑定成熟度'] = 'EXACT_CONSTRUCTION_BOUND_R16_CR0440'
        save_if_changed(relative, before, after)

    binding_path = 'catalogs/screen_visual_binding.csv'
    before_binding = read_csv(binding_path)
    after_binding = [
        row for row in before_binding
        if row['Screen_ID'] not in visual_refs
    ]
    after_binding.extend([
        {
            'Screen_ID': 'SCR-ORDER-001', '页面': '订单列表', '路由': '/me/orders',
            '主参考批次': 'B08', '参考面板': 'P04', '绑定方式': 'EXACT',
            '开发说明': '仅复用订单中心结构；过滤示例订单号金额商品内容和联系客服',
        },
        {
            'Screen_ID': 'SCR-ORDER-002', '页面': '订单详情', '路由': '/me/orders/{id}',
            '主参考批次': 'R16-SPEC', '参考面板': 'SCR-ORDER-002', '绑定方式': 'APPROVED_SUPPLEMENT',
            '开发说明': '复用B08/P04视觉语言并严格显示显式订单项报价和不退款证据',
        },
        {
            'Screen_ID': 'ADM-ORDER-001', '页面': '订单列表', '路由': '/commerce/orders',
            '主参考批次': 'R16-SPEC', '参考面板': 'ADM-ORDER-001', '绑定方式': 'APPROVED_SUPPLEMENT',
            '开发说明': '复用商业后台ADM-LIST设计系统；无批量写、退款或联系客服',
        },
    ])
    save_if_changed(binding_path, before_binding, after_binding)

    acceptance_path = 'catalogs/ui_visual_acceptance.csv'
    before_acceptance = read_csv(acceptance_path)
    after_acceptance = [
        row for row in before_acceptance
        if row['页面ID'] not in visual_refs
    ]
    token = 'design/tokens/hhy_design_tokens_v1.2.2.json'
    after_acceptance.extend([
        {
            '页面ID': 'SCR-ORDER-001', '计划版本': 'R16', '平台': 'ANDROID', '页面名称': '订单列表',
            '视觉来源': 'B08/P04', '覆盖状态': 'EXACT',
            '布局建模约束': '保持标题搜索或筛选状态标签订单卡片订单项应付实付时间与详情入口的多层信息密度',
            '业务过滤说明': '只展示OrderResource真实字段；过滤示例订单号金额商品内容联系客服支付退款及任何未登记动作',
            'Token源': token, '实现路径': 'apps/android/feature/order/src/main/java/cc/orbexa/hhy/order/R16OrderScreens.kt',
            '参考证据': 'design/effect-previews/B08/HHY_B08_8PAGE_UI_REFERENCE.png;design/R16-UI-FROZEN/specs/SCR-ORDER-001.md',
            '实现截图证据': '', '验收状态': 'IN_REVIEW',
            '说明': 'TASK-R16-001冻结视觉合同；实现与候选截图完成前不得标PASS',
        },
        {
            '页面ID': 'SCR-ORDER-002', '计划版本': 'R16', '平台': 'ANDROID', '页面名称': '订单详情',
            '视觉来源': 'SPEC:design/R16-UI-FROZEN/specs/SCR-ORDER-002.md', '覆盖状态': 'APPROVED_SUPPLEMENT',
            '布局建模约束': '保持顶栏状态摘要订单项报价拆分不退款证据和时间信息的卡片分区与长页节奏',
            '业务过滤说明': '只展示显式OrderResource；过滤支付、退款、客服、物流和示例业务数据',
            'Token源': token, '实现路径': 'apps/android/feature/order/src/main/java/cc/orbexa/hhy/order/R16OrderScreens.kt',
            '参考证据': 'design/R16-UI-FROZEN/specs/SCR-ORDER-002.md;design/effect-previews/B08/HHY_B08_8PAGE_UI_REFERENCE.png',
            '实现截图证据': '', '验收状态': 'IN_REVIEW',
            '说明': 'TASK-R16-001批准补充规格；实现与候选截图完成前不得标PASS',
        },
        {
            '页面ID': 'ADM-ORDER-001', '计划版本': 'R16', '平台': 'ADMIN', '页面名称': '订单列表',
            '视觉来源': 'SPEC:design/R16-UI-FROZEN/specs/ADM-ORDER-001.md', '覆盖状态': 'APPROVED_SUPPLEMENT',
            '布局建模约束': '使用固定侧栏与独立滚动主区，标题统计筛选专业表格分页详情抽屉和状态反馈层级完整',
            '业务过滤说明': '只读上帝视角来自OrderResource；无批量写、退款、余额修改、客服或虚构经营指标',
            'Token源': token, '实现路径': 'apps/admin-web/src/pages/CommerceOrdersPage.tsx',
            '参考证据': 'design/R16-UI-FROZEN/specs/ADM-ORDER-001.md;docs/02-ui/后台视觉与交互设计系统_V1.2.2.md',
            '实现截图证据': '', '验收状态': 'IN_REVIEW',
            '说明': 'TASK-R16-001批准商业后台补充规格；实现与候选截图完成前不得标PASS',
        },
    ])
    save_if_changed(acceptance_path, before_acceptance, after_acceptance)

    admin_specs_path = 'catalogs/admin_page_operation_specs.csv'
    before_admin_specs = read_csv(admin_specs_path)
    after_admin_specs = [row.copy() for row in before_admin_specs]
    for row in after_admin_specs:
        if row['页面ID'] == 'ADM-ORDER-001':
            frozen = '订单号;用户ID;订单类型;状态;订单项;原价;优惠金额;服务费;应付金额;实付金额;不退款确认;创建时间;支付时间;版本'
            row['表格列'] = frozen
            row['冻结列'] = '订单号;用户ID;订单类型;状态;应付金额;实付金额;创建时间;操作'
            row['批量操作'] = '无批量写操作'
            row['行操作'] = '查看详情'
            row['导出字段'] = frozen
            row['成熟度'] = 'FROZEN_R16_CR0440'
    save_if_changed(admin_specs_path, before_admin_specs, after_admin_specs)
    return changed


def synchronize_r16_contract() -> int:
    shared = _r16_schemas()
    request_schemas = _r16_request_schemas()
    list_of = lambda ref: OrderedDict(
        type='object',
        additionalProperties=False,
        required=['items', 'page'],
        properties=OrderedDict([
            ('items', OrderedDict(type='array', items=OrderedDict([('$ref', ref)]))),
            ('page', OrderedDict([('$ref', '#/components/schemas/PageMeta')])),
        ]),
    )
    response_overrides = {
        'OrderGetMeOrdersResponse': _success_response(list_of('#/components/schemas/OrderResource')),
        'OrderGetMeOrdersByOrdernoResponse': _success_response(OrderedDict([('$ref', '#/components/schemas/OrderResource')])),
        'AdminProductsGetProductsResponse': _success_response(list_of('#/components/schemas/ProductResource')),
        'AdminProductsPostProductsResponse': _success_response(OrderedDict([('$ref', '#/components/schemas/ProductResource')])),
        'AdminProductsPatchProductsByIdResponse': _success_response(OrderedDict([('$ref', '#/components/schemas/ProductResource')])),
        'AdminProductsGetSkusResponse': _success_response(list_of('#/components/schemas/ProductSkuResource')),
        'AdminProductsPostSkusResponse': _success_response(OrderedDict([('$ref', '#/components/schemas/ProductSkuResource')])),
        'AdminProductsPatchSkusByIdResponse': _success_response(OrderedDict([('$ref', '#/components/schemas/ProductSkuResource')])),
        'AdminOrdersGetOrdersResponse': _success_response(list_of('#/components/schemas/OrderResource')),
        'AdminOrdersGetOrdersByOrdernoResponse': _success_response(OrderedDict([('$ref', '#/components/schemas/OrderResource')])),
    }
    changed = []
    for relative, names in (
        ('contracts/openapi.yaml', {
            'OrderGetMeOrdersResponse', 'OrderGetMeOrdersByOrdernoResponse',
        }),
        ('contracts/admin-openapi.yaml', set(response_overrides) - {
            'OrderGetMeOrdersResponse', 'OrderGetMeOrdersByOrdernoResponse',
        }),
    ):
        path = ROOT / relative
        text = path.read_text(encoding='utf-8')
        before = text
        for name, schema in shared.items():
            text = _replace_schema(text, name, schema)
        if relative.endswith('admin-openapi.yaml'):
            for name, schema in request_schemas.items():
                text = _replace_schema(text, name, schema)
        for name in sorted(names):
            text = _replace_schema(text, name, response_overrides[name])
        if text != before:
            path.write_text(text, encoding='utf-8', newline='\n')
            changed.append(relative)
    catalog_changes = _sync_r16_catalogs()
    refresh_contract_status(check=False)
    print(json.dumps({
        'status': 'PASS',
        'mode': 'sync-r16',
        'freeze_date': R16_FREEZE_DATE,
        'changed': changed,
        'catalog_changes': catalog_changes,
        'resources': list(shared),
        'responses': sorted(response_overrides),
    }, ensure_ascii=False, indent=2))
    return 0

def update_ui_schema_refs(all_rows):
    mapping={(r['方法'],r['路径']):operation_id(r) for r in all_rows}
    ui=read_csv('catalogs/ui_action_matrix.csv')
    for row in ui:
        m=re.match(r'^(GET|POST|PUT|PATCH|DELETE)\s+(.+)$',row['API契约'])
        if not m: continue
        op=mapping.get((m.group(1),m.group(2)))
        if not op: continue
        row['operationId']=op
        row['请求Schema']=schema_name(op,'Request') if m.group(1) in {'POST','PUT','PATCH'} else schema_name(op,'Parameters')
        row['成熟度']='FROZEN'
    write_csv('catalogs/ui_action_matrix.csv',ui)

def websocket_authentication_contract() -> dict:
    return {
      'request_subprotocols': {
        'required_exactly_once': ['hhy.v1', 'hhy.access.<compact-JWT>'],
        'compact_jwt_pattern': r'^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$',
        'padding_allowed': False,
        'whitespace_allowed': False,
        'reject_duplicate_or_missing': True,
      },
      'selected_subprotocol': 'hhy.v1',
      'validate_before_upgrade': ['jwt_signature', 'jwt_expiry', 'session_active'],
      'ws_ticket': {
        'status': 'RESERVED_UNAVAILABLE',
        'query_parameter': 'wsTicket',
        'ticket_ttl_seconds': 60,
      },
      'credential_transport': {
        'token_in_url_forbidden': True,
        'token_in_response_forbidden': True,
        'token_in_error_text_forbidden': True,
      },
      'log_redaction': {
        'header': 'Sec-WebSocket-Protocol',
        'replacement': '[REDACTED]',
        'required_scopes': [
          'edge', 'reverse_proxy', 'handshake_error', 'server', 'application',
        ],
      },
    }


def websocket_delivery_contract() -> dict:
    return {
      'client_message_id_unique_scope': 'authenticatedUserId + conversationId + clientMessageId',
      'server_sequence_scope': 'authenticatedUserId',
      'ack_timeout_seconds': 10,
      'max_redeliveries': 5,
      'ack': {
        's2c_confirmation_event': 'system.delivery.ack',
        'match_fields': ['eventId', 'serverSequence'],
        'match_scope': 'same authenticated user and actually delivered event',
        'duplicate_result': 'IDEMPOTENT_SUCCESS',
        'unknown_or_mismatched_result': 'REJECT',
        'stop_redelivery_only_after_valid_ack': True,
        'ack_event_ack_required': False,
        'c2s_confirmations': {
          'chat.message.send': {
            'event': 'chat.message.ack',
            'match': ['conversationId', 'clientMessageId'],
          },
          'chat.message.read': {
            'event': 'chat.read.updated',
            'match': [
              'conversationId',
              'authenticatedUserId=userId',
              'lastReadMessageId',
            ],
          },
        },
      },
      'resume': {
        'query_parameter': {
          'name': 'lastServerSequence',
          'type': 'integer',
          'minimum': 0,
          'zero_means': 'no event has been processed',
          'positive_means': 'highest contiguous processed sequence with every required S2C ACK completed',
          'reject_above_server_high_watermark': True,
        },
        'response_event': 'system.resume',
        'modes': {
          'REPLAY_COMPLETE': {
            'affected_scopes': [],
            'resume_from': 'serverHighWatermark captured when the handshake was accepted',
          },
          'REST_GAP_FILL': {
            'allowed_affected_scopes': ['CHAT', 'NOTIFICATIONS'],
            'resume_from': 'server supplied safe watermark after every affected scope is fully refreshed',
          },
        },
        'gap_fill': {
          'CHAT': {
            'operations': [
              'chatGetConversations', 'chatGetConversationsByIdMessages',
            ],
            'completion': 'exhaust every conversations page and every active conversation messages page',
          },
          'NOTIFICATIONS': {
            'operations': ['notificationGetNotifications'],
            'completion': 'exhaust every notifications page',
          },
          'reconnect_sequence_source': 'system.resume.payload.resumeFromServerSequence',
          'infer_sequence_from_rest_forbidden': True,
        },
      },
      'retention_hours': 72,
      'ordering': 'strict within one conversation; no global ordering guarantee',
      'typing_event_persisted': False,
    }


def new_websocket_events() -> list[dict]:
    return [
      {
        'code': 'system.delivery.ack',
        'direction': 'C2S',
        'description': '确认当前认证用户收到的需确认服务端事件',
        'release': 'R14/R15',
        'contract_maturity': 'FROZEN_V1.2.2',
        'ack_required': False,
        'payload': {
          'type': 'object',
          'additionalProperties': False,
          'required': ['eventId', 'serverSequence'],
          'properties': {
            'eventId': {'type': 'string', 'format': 'uuid'},
            'serverSequence': {'type': 'integer', 'format': 'int64', 'minimum': 1},
          },
        },
      },
      {
        'code': 'system.resume',
        'direction': 'S2C',
        'description': '声明重放完成或要求按服务端水位执行REST补洞',
        'release': 'R14/R15',
        'contract_maturity': 'FROZEN_V1.2.2',
        'ack_required': False,
        'payload': {
          'type': 'object',
          'additionalProperties': False,
          'required': [
            'mode', 'requestedLastServerSequence', 'serverHighWatermark',
            'resumeFromServerSequence', 'affectedScopes',
          ],
          'properties': {
            'mode': {'type': 'string', 'enum': ['REPLAY_COMPLETE', 'REST_GAP_FILL']},
            'requestedLastServerSequence': {'type': 'integer', 'format': 'int64', 'minimum': 0},
            'serverHighWatermark': {'type': 'integer', 'format': 'int64', 'minimum': 0},
            'resumeFromServerSequence': {'type': 'integer', 'format': 'int64', 'minimum': 0},
            'affectedScopes': {
              'type': 'array',
              'uniqueItems': True,
              'maxItems': 2,
              'items': {'type': 'string', 'enum': ['CHAT', 'NOTIFICATIONS']},
            },
          },
        },
      },
    ]


def websocket_status_row(event: dict, ws_sha: str) -> dict[str, str]:
    owners = {
      'system.delivery.ack': 'SCR-CHAT-001;SCR-CHAT-002;SYSTEM_RUNTIME',
      'system.resume': 'SCR-CHAT-001;SCR-CHAT-002;SCR-MSG-001;SYSTEM_RUNTIME',
    }
    return {
      '契约类型': 'WEBSOCKET',
      '契约标识': event['code'],
      'operationId': event['code'],
      '计划版本': event['release'],
      '成熟度': event['contract_maturity'],
      '安全模型': 'Sec-WebSocket-Protocol:hhy.v1+hhy.access.<compact-JWT>;serverSequence',
      '请求Schema': f"inline:{event['code']}.payload",
      '响应Schema': f"inline:{event['code']}.payload",
      '幂等': '是' if event['code'] == 'system.delivery.ack' else '否',
      'UI/系统所有者': owners[event['code']],
      '事实源': 'contracts/websocket-events.yaml',
      '事实源SHA256': ws_sha,
      '冻结日期': WEBSOCKET_FREEZE_DATE,
      '变更策略': '只允许向后兼容新增事件/字段；序列语义和ACK协议不得破坏性变更',
    }


def synchronize_websocket_contract() -> int:
    contract_path = ROOT / 'contracts/websocket-events.yaml'
    runtime_path = ROOT / 'services/backend/boot/src/main/resources/contracts/websocket-events.yaml'
    registry_path = 'contracts/contract_status.csv'
    openapi_paths = (ROOT / 'contracts/openapi.yaml', ROOT / 'contracts/admin-openapi.yaml')
    openapi_hashes = {path: hashlib.sha256(path.read_bytes()).hexdigest() for path in openapi_paths}

    ws = yaml.safe_load(contract_path.read_text(encoding='utf-8'))
    events_by_code = {event['code']: event for event in ws.get('events', [])}
    allowed_codes = set(LEGACY_WEBSOCKET_EVENT_CODES + NEW_WEBSOCKET_EVENT_CODES)
    if set(events_by_code) - allowed_codes:
        raise SystemExit(f"unknown websocket events: {sorted(set(events_by_code) - allowed_codes)}")
    missing_legacy = set(LEGACY_WEBSOCKET_EVENT_CODES) - set(events_by_code)
    if missing_legacy:
        raise SystemExit(f"missing legacy websocket events: {sorted(missing_legacy)}")
    legacy_snapshot = {
      code: json.dumps(events_by_code[code], ensure_ascii=False, sort_keys=True)
      for code in LEGACY_WEBSOCKET_EVENT_CODES
    }
    definitions_snapshot = json.dumps(ws.get('definitions', {}), ensure_ascii=False, sort_keys=True)

    ws['authentication'] = websocket_authentication_contract()
    ws['delivery'] = websocket_delivery_contract()
    ws['events'] = [events_by_code[code] for code in LEGACY_WEBSOCKET_EVENT_CODES] + new_websocket_events()
    dump_yaml(contract_path, ws)

    generated = yaml.safe_load(contract_path.read_text(encoding='utf-8'))
    generated_by_code = {event['code']: event for event in generated['events']}
    for code, snapshot in legacy_snapshot.items():
        if json.dumps(generated_by_code[code], ensure_ascii=False, sort_keys=True) != snapshot:
            raise SystemExit(f'legacy websocket event drift: {code}')
    if json.dumps(generated.get('definitions', {}), ensure_ascii=False, sort_keys=True) != definitions_snapshot:
        raise SystemExit('websocket definitions drift')

    runtime_path.parent.mkdir(parents=True, exist_ok=True)
    runtime_path.write_bytes(contract_path.read_bytes())
    ws_sha = hashlib.sha256(contract_path.read_bytes()).hexdigest()
    rows = read_csv(registry_path)
    fields = list(rows[0])
    ws_indexes = [
      index for index, row in enumerate(rows)
      if row.get('事实源') == 'contracts/websocket-events.yaml'
    ]
    if not ws_indexes:
        raise SystemExit('contract registry has no websocket rows')
    insertion_index = sum(
      1 for row in rows[:ws_indexes[0]]
      if row.get('事实源') != 'contracts/websocket-events.yaml'
    )
    existing_ws_rows = {
      row['契约标识']: row for row in rows
      if row.get('事实源') == 'contracts/websocket-events.yaml'
    }
    non_ws_before = [
      row.copy() for row in rows
      if row.get('事实源') != 'contracts/websocket-events.yaml'
    ]
    generated_rows = []
    for event in generated['events']:
        if event['code'] in existing_ws_rows:
            row = existing_ws_rows[event['code']].copy()
            row['事实源SHA256'] = ws_sha
        else:
            row = websocket_status_row(event, ws_sha)
        generated_rows.append(row)
    without_ws = [
      row for row in rows
      if row.get('事实源') != 'contracts/websocket-events.yaml'
    ]
    rebuilt = without_ws[:insertion_index] + generated_rows + without_ws[insertion_index:]
    if [row for row in rebuilt if row.get('事实源') != 'contracts/websocket-events.yaml'] != non_ws_before:
        raise SystemExit('non-websocket contract registry rows changed')
    write_csv(registry_path, rebuilt, fields)

    for path, expected in openapi_hashes.items():
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual != expected:
            raise SystemExit(f'OpenAPI changed during websocket sync: {path.name}')
    print(json.dumps({
      'status': 'PASS',
      'mode': 'sync-websocket',
      'websocket_events': len(generated['events']),
      'runtime_sha256': ws_sha,
      'openapi_unchanged': True,
      'non_websocket_registry_rows_unchanged': True,
      'legacy_events_unchanged': True,
      'definitions_unchanged': True,
    }, ensure_ascii=False, indent=2))
    return 0

def refresh_contract_status(*, check: bool) -> int:
    """Refresh only authoritative-file hashes in the frozen registry.

    V1.2.2 enriched the checked-in OpenAPI files after the original catalog
    generator was written.  A default full rebuild would therefore delete
    valid typed schemas.  Keep the checked-in contracts authoritative and
    make the destructive legacy rebuild an explicit opt-in.
    """
    rows=read_csv('contracts/contract_status.csv')
    expected={
      relative: hashlib.sha256((ROOT/relative).read_bytes()).hexdigest()
      for relative in ('contracts/openapi.yaml','contracts/admin-openapi.yaml','contracts/websocket-events.yaml')
    }
    changed=[]
    for index,row in enumerate(rows,start=2):
        source=row.get('事实源','')
        if source not in expected:
            raise SystemExit(f'contract_status row {index}: unsupported source {source!r}')
        actual=expected[source]
        if row.get('事实源SHA256') != actual:
            changed.append({'row':index,'source':source,'old':row.get('事实源SHA256'),'new':actual})
            row['事实源SHA256']=actual
    if check and changed:
        print(json.dumps({'status':'FAIL','reason':'CONTRACT_REGISTRY_STALE','changes':changed},ensure_ascii=False,indent=2))
        return 1
    if not check and changed:
        write_csv('contracts/contract_status.csv',rows)
    print(json.dumps({'status':'PASS','mode':'check' if check else 'sync','updated_rows':0 if check else len(changed),'stale_rows':len(changed)},ensure_ascii=False,indent=2))
    return 0

def full_rebuild():
    client=read_csv('catalogs/api_endpoints.csv'); admin=read_csv('catalogs/admin_api_endpoints.csv')
    cs=generate_spec(client,False); ads=generate_spec(admin,True)
    dump_yaml(ROOT/'contracts/openapi.yaml',cs); dump_yaml(ROOT/'contracts/admin-openapi.yaml',ads)
    synchronize_websocket_contract(); update_ui_schema_refs(client+admin)
    status=[]
    for kind,rows,rel in [('CLIENT_API',client,'contracts/openapi.yaml'),('ADMIN_API',admin,'contracts/admin-openapi.yaml')]:
        spec_path=ROOT/rel; spec_sha=hashlib.sha256(spec_path.read_bytes()).hexdigest()
        for r in rows:
            opid=operation_id(r)
            status.append({'契约类型':kind,'契约标识':f"{r['方法']} {r['路径']}",'operationId':opid,'计划版本':r['计划版本'],'成熟度':'FROZEN','安全模型':json.dumps(security_for(r['权限']),ensure_ascii=False,separators=(',',':')),'请求Schema':schema_name(opid,'Request') if r['方法'] in {'POST','PUT','PATCH'} else schema_name(opid,'Parameters'),'响应Schema':schema_name(opid,'Response'),'幂等':r['幂等'],'事实源':rel,'事实源SHA256':spec_sha,'冻结日期':TODAY,'变更策略':'非破坏性扩展；破坏性变更必须新增API版本并记录ADR'})
    ws_sha=hashlib.sha256((ROOT/'contracts/websocket-events.yaml').read_bytes()).hexdigest()
    wsdata=yaml.safe_load((ROOT/'contracts/websocket-events.yaml').read_text(encoding='utf-8'))
    for e in wsdata['events']:
        status.append({'契约类型':'WEBSOCKET_EVENT','契约标识':e['code'],'operationId':e['code'],'计划版本':e['release'],'成熟度':'FROZEN','安全模型':'Bearer/wsTicket','请求Schema':e['code']+'.payload','响应Schema':'EventEnvelope','幂等':'是' if e['ack_required'] else '否','事实源':'contracts/websocket-events.yaml','事实源SHA256':ws_sha,'冻结日期':TODAY,'变更策略':'新增字段必须可选；事件语义破坏性变更新事件名'})
    write_csv('contracts/contract_status.csv',status)
    print(json.dumps({'client_operations':len(client),'admin_operations':len(admin),'ws_events':len(wsdata['events']),'schemas_client':len(cs['components']['schemas']),'schemas_admin':len(ads['components']['schemas'])},ensure_ascii=False,indent=2))

def main() -> int:
    parser=argparse.ArgumentParser(description='Safely synchronize frozen contract metadata.')
    parser.add_argument('--check',action='store_true',help='fail when contract registry hashes are stale')
    parser.add_argument('--sync-websocket',action='store_true',help='safely update only WebSocket contract assets and registry rows')
    parser.add_argument('--sync-r16',action='store_true',help='safely update only the frozen R16 product/order schemas and responses')
    parser.add_argument('--allow-legacy-full-rebuild',action='store_true',help='explicitly run the legacy catalog rebuild; may replace enriched schemas')
    args=parser.parse_args()
    if sum(bool(value) for value in (args.check, args.sync_websocket, args.sync_r16, args.allow_legacy_full_rebuild)) > 1:
        parser.error('--check, --sync-websocket, --sync-r16 and --allow-legacy-full-rebuild are mutually exclusive')
    if args.sync_websocket:
        return synchronize_websocket_contract()
    if args.sync_r16:
        return synchronize_r16_contract()
    if args.allow_legacy_full_rebuild:
        full_rebuild()
        return 0
    return refresh_contract_status(check=args.check)

if __name__=='__main__': raise SystemExit(main())
