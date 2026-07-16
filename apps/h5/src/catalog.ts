import pages from './generated/h5-pages.json';
export interface H5Page { ID:string; 页面:string; 路由:string; vueRoute:string; 访问:string; 功能摘要:string; 计划版本:string; }
export const h5Pages=pages as H5Page[];
