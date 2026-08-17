import { post, get, put } from "./base";
import { LoginData, RegisterData, User } from "@/types/auth";

// 登录
export const login = async (data: LoginData) => {
  const response = await post<{
    user: User;
    token: string;
    refreshToken: string;
  }>("/auth/login", data);
  return response;
};

// 邮箱验证码登录
export const loginByEmail = async (data: { email: string; emailCode: string }) => {
  const response = await post<{
    user: User;
    token: string;
    refreshToken: string;
  }>("/auth/login-by-email", data);
  return response;
};

// 注册
export const register = async (data: RegisterData) => {
  const response = await post<{
    user: User;
    token: string;
    refreshToken: string;
  }>("/auth/register", data);
  return response;
};

// 发送邮箱验证码
export const sendEmailCode = async (email: string) => {
  const response = await post<{ success: boolean }>("/auth/send-email-code", { email });
  return response;
};

// 获取当前用户信息
export const getCurrentUser = async () => {
  const response = await get<User>("/auth/me");
  return response;
};

// 修改密码
export const changePassword = async (data: {
  currentPassword: string;
  newPassword: string;
}) => {
  const response = await post("/auth/password", data);
  return response;
};

// 刷新token
export const refreshToken = async (refreshToken: string, accessToken?: string) => {
  const response = await post<{
    token: string;
    refreshToken: string;
    rememberMe: boolean;
  }>("/auth/refresh", { refreshToken, accessToken });
  return response;
};

// 登出
export const logout = async () => {
  const response = await post("/auth/logout");
  return response;
};

export default {
  login,
  register,
  getCurrentUser,
  changePassword,
  refreshToken,
  logout,
};
