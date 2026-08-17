// 认证相关类型
export interface LoginData {
  username?: string;
  email?: string;
  password: string;
  rememberMe?: boolean;
}

export interface RegisterData {
  username: string;
  email: string;
  password: string;
  confirmPassword: string;
  emailCode?: string;
  inviteCode?: string;
}

export interface User {
  id: string;
  userId?: string;
  username: string;
  email: string;
  role: "user" | "admin";
  permissions?: string[];
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  lastLoginAt?: string;
  preferences?: UserPreferences;
}

export interface UserPreferences {
  theme: "light" | "dark";
  language: string;
  timezone: string;
  notifications: NotificationSettings;
}

export interface NotificationSettings {
  email: boolean;
  push: boolean;
  strategy: boolean;
  trading: boolean;
  system: boolean;
}

export interface AuthState {
  user: User | null;
  token: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}
