import pages from './generated/admin-pages.json';
export interface AdminPage { ID:string; 模块:string; 页面:string; 路由:string; 菜单分组:string; 读取权限:string; 写入权限:string; 敏感级别:string; 功能摘要:string; 计划版本:string; 成熟度:string; }
export const adminPages = pages as AdminPage[];
export const routeToVue = (path:string) => path.replaceAll(/\{([^}]+)\}/g, ':$1');
