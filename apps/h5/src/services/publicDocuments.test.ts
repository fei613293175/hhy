import { describe, expect, it, vi } from 'vitest';
import { PublicDocumentApi } from './publicDocuments';

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function documentResponse(code: string) {
  return {
    success: true,
    requestId: 'req-r15-document',
    timestamp: '2026-08-03T08:30:00Z',
    data: { code, title: '用户服务协议', version: 6, content: [] },
  };
}

describe('PublicDocumentApi', () => {
  it('calls the frozen public agreement operation and returns envelope metadata', async () => {
    const fetcher = vi.fn().mockResolvedValue(response(documentResponse('USER_SERVICE')));

    const result = await new PublicDocumentApi('https://api.orbexa.cc', fetcher).getAgreement('USER_SERVICE');

    expect(fetcher).toHaveBeenCalledWith(
      new URL('https://api.orbexa.cc/public-api/v1/agreements/USER_SERVICE'),
      expect.objectContaining({ headers: { Accept: 'application/json' } }),
    );
    expect(result).toMatchObject({ requestId: 'req-r15-document', page: { code: 'USER_SERVICE', version: 6 } });
  });

  it('calls the frozen public help article operation', async () => {
    const fetcher = vi.fn().mockResolvedValue(response(documentResponse('HELP01')));

    await new PublicDocumentApi('https://api.orbexa.cc', fetcher).getHelpArticle('article_15');

    expect(fetcher).toHaveBeenCalledWith(
      new URL('https://api.orbexa.cc/public-api/v1/help/articles/article_15'),
      expect.any(Object),
    );
  });

  it('rejects malformed help ids before network access', async () => {
    const fetcher = vi.fn();
    const api = new PublicDocumentApi('https://api.orbexa.cc', fetcher);

    await expect(api.getHelpArticle('../private')).rejects.toMatchObject({ status: 400, code: 'COMMON-400-VALIDATION' });
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('preserves the stable server error and request id', async () => {
    const fetcher = vi.fn().mockResolvedValue(response({
      requestId: 'req-document-404',
      error: { code: 'COMMON-404-NOT_FOUND', message: '不存在' },
    }, 404));

    await expect(new PublicDocumentApi('https://api.orbexa.cc', fetcher).getAgreement('PRIVACY'))
      .rejects.toMatchObject({ status: 404, code: 'COMMON-404-NOT_FOUND', requestId: 'req-document-404' });
  });
});
