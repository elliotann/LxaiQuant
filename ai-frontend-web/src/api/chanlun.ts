import api from "./base";

export interface ChanLunRequest {
  symbol: string;
  interval: string;
  limit?: number;
}

export const getChanLunData = (params: ChanLunRequest) =>
  api.get("/member/chanlun/data", { params });

export const getChanLunConfig = () =>
  api.get("/member/chanlun/config");

export default { getChanLunData, getChanLunConfig };
