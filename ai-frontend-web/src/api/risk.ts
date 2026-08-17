import api from "./base";

export const getEquityCurve = (params: {
  taskId?: string;
  scope?: "account" | "member";
  accountId?: string;
  memberId?: number;
  from?: number;
  to?: number;
}) => api.get("/risk/equity-curve", { params });

export const getEquityBaseline = (params: {
  taskId?: string;
  scope?: "account" | "member";
  accountId?: string;
  memberId?: number;
}) => api.get("/risk/equity-baseline", { params });

export default {
  getEquityCurve,
  getEquityBaseline,
};

