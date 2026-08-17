import { get, post, put, del } from "./base";

export const getRoles = async () => {
  return await get("/admin/roles");
};

export const createRole = async (data: any) => {
  return await post("/admin/roles", data);
};

export const updateRole = async (id: number, data: any) => {
  return await put(`/admin/roles/${id}`, data);
};

export const deleteRole = async (id: number) => {
  return await del(`/admin/roles/${id}`);
};

export const getPermissions = async () => {
  return await get("/admin/permissions");
};

export const getRolePermissions = async (roleId: number) => {
  return await get(`/admin/roles/${roleId}/permissions`);
};

export const assignPermissionsToRole = async (roleId: number, permissionIds: number[]) => {
  return await put(`/admin/roles/${roleId}/permissions`, permissionIds);
};

export const getUserRoles = async (userId: string) => {
  return await get(`/admin/users/${userId}/roles`);
};

export const assignUserRoles = async (userId: string, roleIds: number[]) => {
  return await put(`/admin/users/${userId}/roles`, roleIds);
};

export const getMenus = async () => {
  return await get("/admin/menus");
};

export const getAllMenus = async () => {
  return await get("/admin/menus/all");
};

export const createMenu = async (data: any) => {
  return await post("/admin/menus", data);
};

export const updateMenu = async (data: any) => {
  return await put("/admin/menus", data);
};

export const deleteMenu = async (id: number) => {
  return await del(`/admin/menus/${id}`);
};
