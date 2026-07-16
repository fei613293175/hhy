import {describe,it,expect} from 'vitest';import {h5Pages} from './catalog';
describe('H5 route catalog',()=>{it('contains 13 unique frozen routes',()=>{expect(h5Pages).toHaveLength(13);expect(new Set(h5Pages.map(x=>x.路由)).size).toBe(13);expect(h5Pages.every(x=>!x.vueRoute.includes('{'))).toBe(true);});});
