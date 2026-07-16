import { describe,it,expect } from 'vitest'; import { adminPages } from './catalog';
describe('admin route catalog',()=>{it('freezes 60 unique routes and permissions',()=>{expect(adminPages).toHaveLength(60);expect(new Set(adminPages.map(x=>x.路由)).size).toBe(60);expect(adminPages.every(x=>x.读取权限&&x.成熟度==='ROUTE_PERMISSION_FROZEN')).toBe(true);});});
