import { get, put, post, del } from "./base";

export interface NotificationConfig {
  id: string;
  userId: string;
  channel: string;
  enabled: boolean;
  configJson: string;
}

export interface SiteMessage {
  id: string;
  userId: string;
  type: string;
  title: string;
  content: string;
  severity: string;
  isRead: boolean;
  readAt: string;
  createTime: string;
}

interface ApiPage<T> {
  records: T[];
  total: number;
  pages: number;
  current: number;
  size: number;
}

export const getConfigs = async () => {
  const res = await get("/notification/configs");
  return res.data as NotificationConfig[];
};

export const updateConfig = async (channel: string, enabled: boolean, configJson?: string) => {
  const res = await put(`/notification/configs/${channel}`, { enabled, configJson });
  return res.data as NotificationConfig;
};

export const listMessages = async (params?: { page?: number; size?: number; unreadOnly?: boolean }) => {
  const res = await get("/notification/messages", { params });
  return res.data as ApiPage<SiteMessage>;
};

export const getUnreadCount = async () => {
  const res = await get("/notification/messages/unread-count");
  return res.data as number;
};

export const markAsRead = async (id: string) => {
  await post(`/notification/messages/${id}/read`);
};

export const markAllAsRead = async () => {
  await post("/notification/messages/read-all");
};

export const deleteMessage = async (id: string) => {
  await del(`/notification/messages/${id}`);
};

export const testEmail = async (config: {
  to: string;
  smtpHost: string;
  smtpPort: number;
  emailUser: string;
  emailPassword: string;
  proxyEnabled: boolean;
  proxyHost: string;
  proxyPort: number;
}) => {
  const res = await post("/notification/test/email", config);
  return res.data as string;
};
