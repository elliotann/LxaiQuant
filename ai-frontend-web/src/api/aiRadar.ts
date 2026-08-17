import { get } from "./base";

export interface AiRadarOpportunity {
  symbol: string;
  name: string;
  price: number;
  change_24h: number;
  change_7d?: number;
  signal: string;
  strength: string;
  reason: string;
  impact: string;
  market: string;
  timestamp: number;
}

export interface AiRadarResponse {
  code: number;
  msg: string;
  data: AiRadarOpportunity[];
}

export const getAiRadarOpportunities = async (params?: {
  force?: boolean;
  market?: string;
}) => {
  return await get("/ai-radar/opportunities", { params });
};
