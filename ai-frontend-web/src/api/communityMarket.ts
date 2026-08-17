import { get, post, put, del } from "./base";

export interface CommunityPublishRequest {
  productType: string;
  sourceId: string;
  name: string;
  description?: string;
  previewImage?: string;
  pricingType: string;
  price: number;
  vipFree: boolean;
  tags?: string[];
}

export interface CommunityReviewRequest {
  id: number;
  action: string;
  note?: string;
}

export interface CommunityQuery {
  page?: number;
  pageSize?: number;
  productType?: string;
  keyword?: string;
  pricingType?: string;
  sortBy?: string;
  tag?: string;
}

/** 获取市场列表 */
export function getListings(query: CommunityQuery) {
  return get("/community-market/listings", { params: query });
}

/** 获取商品详情 */
export function getListingDetail(id: number) {
  return get(`/community-market/listings/${id}`);
}

/** 获取表现数据 */
export function getPerformance(id: number) {
  return get(`/community-market/listings/${id}/performance`);
}

/** 获取评论列表 */
export function getComments(id: number, page = 1, pageSize = 20) {
  return get(`/community-market/listings/${id}/comments`, { params: { page, pageSize } });
}

/** 获取我的评论 */
export function getMyComment(id: number) {
  return get(`/community-market/listings/${id}/my-comment`);
}

/** 购买商品 */
export function purchaseListing(id: number) {
  return post(`/community-market/listings/${id}/purchase`);
}

/** 同步商品更新 */
export function syncListingUpdate(id: number) {
  return post(`/community-market/listings/${id}/sync`);
}

/** 获取我的已购列表 */
export function getMyPurchases() {
  return get("/community-market/my-purchases");
}

/** 发布商品 */
export function publishListing(data: CommunityPublishRequest) {
  return post("/community-market/listings", data);
}

/** 获取我发布的商品 */
export function getMyListings(page = 1, pageSize = 20) {
  return get("/community-market/my-listings", { params: { page, pageSize } });
}

/** 更新发布信息 */
export function updatePublishListing(id: number, data: CommunityPublishRequest) {
  return put(`/community-market/listings/${id}`, data);
}

/** 下架商品 */
export function unpublishListing(id: number) {
  return del(`/community-market/listings/${id}`);
}

/** 发表评论 */
export function addComment(id: number, data: { rating?: number; content: string }) {
  return post(`/community-market/listings/${id}/comments`, data);
}

/** 修改评论 */
export function updateComment(listingId: number, commentId: number, data: { rating?: number; content: string }) {
  return put(`/community-market/listings/${listingId}/comments/${commentId}`, data);
}

/** 获取待审核列表 */
export function getPendingList(page = 1, pageSize = 20, productType?: string) {
  return get("/community-market/admin/pending", { params: { page, pageSize, productType } });
}

/** 审核统计 */
export function getReviewStats() {
  return get("/community-market/admin/stats");
}

/** 审核操作 */
export function reviewListing(data: CommunityReviewRequest) {
  return post("/community-market/admin/review", data);
}
