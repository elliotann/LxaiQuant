/**
 * 统一标的字典 & 自选股 API
 */
import { get, post, del } from "./base";

/** 标的查询参数 */
export interface SymbolQueryParams {
  market?: string;
  keyword?: string;
  isHot?: boolean;
}

/** 标的 */
export interface SymbolItem {
  id: number;
  market: string;
  symbol: string;
  name?: string;
  exchange?: string;
  isHot?: boolean;
}

/** 自选股 */
export interface UserFavoriteItem {
  id: number;
  userId: string;
  symbolId: number;
  symbol?: SymbolItem;
}

/**
 * 查询标的列表
 * isHot=true 查热门标的，否则 keyword 模糊搜索
 */
export const getSymbols = async (params?: SymbolQueryParams) => {
  return await get("/symbols", { params });
};

/**
 * 获取用户自选股列表
 */
export const getFavorites = async () => {
  return await get("/user/favorites");
};

/**
 * 添加自选股
 */
export const addFavorite = async (symbolId: number) => {
  return await post(`/user/favorites/${symbolId}`);
};

/**
 * 删除自选股
 */
export const removeFavorite = async (symbolId: number) => {
  return await del(`/user/favorites/${symbolId}`);
};

/** 检查标的是否在自选列表中 */
export const isFavorite = (favorites: UserFavoriteItem[], symbolId: number): boolean => {
  return favorites.some(f => f.symbolId === symbolId);
};

/** 根据 symbol 代码查找自选股中的 symbolId */
export const getFavoriteSymbolId = (favorites: UserFavoriteItem[], symbolCode: string): number | undefined => {
  const f = favorites.find(f => f.symbol?.symbol === symbolCode);
  return f?.symbolId;
};
