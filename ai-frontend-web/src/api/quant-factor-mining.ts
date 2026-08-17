import { get, post, del } from "./base";

export interface TerminalPoolVariant {
  name: string;
}

export interface TerminalPoolResponse {
  variants: TerminalPoolVariant[];
  total: number;
}

export interface FactorMiningCreateRequest {
  taskName: string;
  symbol: string;
  interval?: string;
  operatorSet: string[];
  terminalSet: string[];
  populationSize?: number;
  generations?: number;
  tournamentSize?: number;
  crossoverProb?: number;
  mutationProb?: number;
  parsimonyCoefficient?: number;
  fitnessMetric?: string;
  lookbackBars?: number;
}

export interface FactorMiningTaskVO {
  id: string;
  taskName: string;
  symbol: string;
  interval: string;
  operatorSet: string;
  terminalSet: string;
  populationSize: number;
  generations: number;
  tournamentSize: number;
  crossoverProb: number;
  mutationProb: number;
  parsimonyCoefficient: number;
  fitnessMetric: string;
  lookbackBars: number;
  status: string;
  progress: number;
  bestFitness: number;
  bestExpression: string;
  bestExpressionLatex: string;
  errorMsg: string;
  startTime: string;
  endTime: string;
  createTime: string;
}

export interface FactorCandidateVO {
  id: string;
  taskId: string;
  expression: string;
  expressionLatex: string;
  fitness: number;
  rankIc: number;
  sharpe: number;
  turnover: number;
  treeDepth: number;
  nodeCount: number;
  corrWithLabel: number;
  topRet: number;
  selected: boolean;
  customFeatureName: string;
  createTime: string;
}

export const getTerminalPool = async (symbol: string, interval: string = "1H") => {
  return await get("/ml/factor-mining/terminal-pool", { params: { symbol, interval } });
};

export const createFactorMiningTask = async (req: FactorMiningCreateRequest) => {
  return await post("/ml/factor-mining/tasks", req);
};

export const startFactorMiningTask = async (taskId: string) => {
  return await post(`/ml/factor-mining/tasks/${taskId}/start`);
};

export const getFactorMiningTask = async (taskId: string) => {
  return await get(`/ml/factor-mining/tasks/${taskId}`);
};

export const listFactorMiningTasks = async (limit: number = 20) => {
  return await get("/ml/factor-mining/tasks", { params: { limit } });
};

export const cancelFactorMiningTask = async (taskId: string) => {
  return await post(`/ml/factor-mining/tasks/${taskId}/cancel`);
};

export const listCandidates = async (taskId: string) => {
  return await get(`/ml/factor-mining/candidates/${taskId}`);
};

export const listSelectedCandidates = async () => {
  return await get("/ml/factor-mining/candidates/selected");
};

export const selectCandidate = async (candidateId: string, customFeatureName: string) => {
  return await post(`/ml/factor-mining/candidates/${candidateId}/select`, null, {
    params: { customFeatureName }
  });
};

export const deselectCandidate = async (candidateId: string) => {
  return await post(`/ml/factor-mining/candidates/${candidateId}/deselect`);
};
