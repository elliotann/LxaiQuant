import { get, post, put, del } from "./base";

export interface MonitorTask {
  id: string;
  symbols: string[];
  intervalMin: number;
  notifyChannels: string[];
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AnalysisReport {
  id: string;
  taskId: string;
  symbol: string;
  decision: string;
  confidence: number;
  summary: string;
  analysis: string;
  risks: string;
  triggerType: string;
  createdAt: string;
}

interface ApiPage<T> {
  records: T[];
  total: number;
  pages: number;
  current: number;
  size: number;
}

export const createTask = async (data: {
  symbols: string[];
  intervalMin: number;
  notifyChannels: string[];
}) => {
  const res = await post("/ai/tasks", data);
  return res.data as MonitorTask;
};

export const listTasks = async (params?: { page?: number; size?: number }) => {
  const res = await get("/ai/tasks", { params });
  return res.data as ApiPage<MonitorTask>;
};

export const getTask = async (id: string) => {
  const res = await get(`/ai/tasks/${id}`);
  return res.data as MonitorTask;
};

export const updateTask = async (
  id: string,
  data: { intervalMin?: number; notifyChannels?: string[]; enabled?: boolean }
) => {
  const res = await put(`/ai/tasks/${id}`, data);
  return res.data as MonitorTask;
};

export const deleteTask = async (id: string) => {
  await del(`/ai/tasks/${id}`);
};

export const executeTask = async (id: string) => {
  await post(`/ai/tasks/${id}/execute`);
};

export const listReports = async (params: {
  taskId: string;
  page?: number;
  size?: number;
}) => {
  const res = await get("/ai/tasks/reports", { params });
  return res.data as ApiPage<AnalysisReport>;
};
