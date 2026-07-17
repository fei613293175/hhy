import { describe, expect, it } from 'vitest'
import { adminPages } from './catalog'

describe('admin route catalog', () => {
  it('freezes 74 unique routes and permissions', () => {
    expect(adminPages).toHaveLength(74)
    expect(new Set(adminPages.map((page) => page.路由)).size).toBe(74)
    const frozenMaturities = new Set(['ROUTE_PERMISSION_FROZEN', 'PAGE_SPEC_FROZEN'])
    expect(adminPages.every((page) => page.读取权限 && frozenMaturities.has(page.成熟度))).toBe(true)
  })
})
