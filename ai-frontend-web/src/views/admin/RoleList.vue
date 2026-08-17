<template>
  <div class="role-list">
    <div class="page-header animate-fade-in">
      <h2 class="text-gradient-primary">权限管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleUserRoleAssign" class="premium-button primary">
          <el-icon><user /></el-icon>
          用户授权
        </el-button>
        <el-button type="primary" @click="handleAddRole" class="premium-button primary">
          <el-icon><plus /></el-icon>
          添加角色
        </el-button>
      </div>
    </div>

    <el-card class="premium-card fade-in">
      <el-table v-loading="loading" :data="roles" style="width: 100%" border class="premium-table">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="roleCode" label="编码" width="120" />
        <el-table-column prop="roleName" label="名称" width="160" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEditRole(row)" class="premium-button">编辑</el-button>
            <el-button size="small" @click="handleAssignPerm(row)" class="premium-button">
              <el-icon><setting /></el-icon>
              权限
            </el-button>
            <el-button size="small" type="danger" @click="handleDeleteRole(row)" class="premium-button danger">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="roleDialogVisible" :title="roleDialogType === 'add' ? '添加角色' : '编辑角色'" width="500px" class="premium-modal">
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-width="80px">
        <el-form-item label="编码" prop="roleCode">
          <el-input v-model="roleForm.roleCode" placeholder="如: ADMIN, VIP" :disabled="roleDialogType === 'edit'" />
        </el-form-item>
        <el-form-item label="名称" prop="roleName">
          <el-input v-model="roleForm.roleName" placeholder="如: 管理员" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="roleForm.description" type="textarea" :rows="3" placeholder="角色描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false" class="premium-button">取消</el-button>
        <el-button type="primary" @click="handleRoleSave" class="premium-button primary">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permDialogVisible" title="分配权限" width="600px" class="premium-modal">
      <div v-if="currentRole" class="perm-dialog-header">
        为角色 <strong>{{ currentRole.roleName }}</strong> 分配权限
      </div>
      <el-tree
        ref="permTreeRef"
        :data="permTreeData"
        show-checkbox
        node-key="id"
        default-expand-all
        :check-strictly="false"
        :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
        class="perm-tree"
      />
      <template #footer>
        <el-button @click="permDialogVisible = false" class="premium-button">取消</el-button>
        <el-button @click="handlePermSelectAll" class="premium-button">全选/取消</el-button>
        <el-button type="primary" @click="handlePermSave" class="premium-button primary">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userRoleDialogVisible" title="用户授权" width="700px" class="premium-modal">
      <el-form :model="userRoleSearchForm" inline>
        <el-form-item label="用户">
          <el-input v-model="userRoleSearchForm.keyword" placeholder="用户名/邮箱" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleUserSearch" class="premium-button">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-table
        v-loading="userLoading"
        :data="users"
        style="width: 100%"
        border
        class="premium-table"
        @row-click="handleUserSelect"
      >
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="160" />
        <el-table-column label="当前角色" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="r in row.roles" :key="r.id" size="small" style="margin-right: 4px">{{ r.roleName }}</el-tag>
            <span v-if="!row.roles || row.roles.length === 0" class="no-role">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" type="warning" @click.stop="handleUserSelect(row)" class="premium-button">授权</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="assignRoleDialogVisible" title="分配角色" width="500px" class="premium-modal">
      <div class="assign-role-user">用户：<strong>{{ selectedUser?.username }}</strong></div>
      <el-checkbox-group v-model="selectedRoleIds" class="role-checkbox-group">
        <el-checkbox v-for="r in roles" :key="r.id" :label="r.id" :value="r.id" border>
          {{ r.roleName }}
          <span class="role-code-tag">({{ r.roleCode }})</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="assignRoleDialogVisible = false" class="premium-button">取消</el-button>
        <el-button type="primary" @click="handleAssignRoleSave" class="premium-button primary">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import { Plus, Setting, User } from "@element-plus/icons-vue";
import { getRoles, createRole, updateRole, deleteRole, getPermissions, getRolePermissions, assignPermissionsToRole, getUserRoles, assignUserRoles } from "@/api/admin";
import { get } from "@/api/base";

const loading = ref(false);
const roles = ref<any[]>([]);

const roleDialogVisible = ref(false);
const roleDialogType = ref<"add" | "edit">("add");
const roleFormRef = ref<FormInstance>();
const roleForm = reactive({
  roleCode: "",
  roleName: "",
  description: "",
});
const editingRoleId = ref<number | null>(null);

const roleRules: FormRules = {
  roleCode: [
    { required: true, message: "请输入角色编码", trigger: "blur" },
    { pattern: /^[A-Z_]+$/, message: "编码只能包含大写字母和下划线", trigger: "blur" },
  ],
  roleName: [{ required: true, message: "请输入角色名称", trigger: "blur" }],
};

const fetchRoles = async () => {
  loading.value = true;
  try {
    const res = await getRoles();
    roles.value = res.data ?? res;
  } catch (error) {
    console.error("获取角色列表失败:", error);
    ElMessage.error("获取角色列表失败");
  } finally {
    loading.value = false;
  }
};

const handleAddRole = () => {
  roleDialogType.value = "add";
  editingRoleId.value = null;
  Object.assign(roleForm, { roleCode: "", roleName: "", description: "" });
  roleDialogVisible.value = true;
};

const handleEditRole = (row: any) => {
  roleDialogType.value = "edit";
  editingRoleId.value = row.id;
  Object.assign(roleForm, {
    roleCode: row.roleCode,
    roleName: row.roleName,
    description: row.description ?? "",
  });
  roleDialogVisible.value = true;
};

const handleRoleSave = async () => {
  if (!roleFormRef.value) return;
  try {
    await roleFormRef.value.validate();
    if (roleDialogType.value === "add") {
      await createRole({ roleCode: roleForm.roleCode, roleName: roleForm.roleName, description: roleForm.description });
      ElMessage.success("角色创建成功");
    } else {
      await updateRole(editingRoleId.value!, { roleCode: roleForm.roleCode, roleName: roleForm.roleName, description: roleForm.description });
      ElMessage.success("角色更新成功");
    }
    roleDialogVisible.value = false;
    fetchRoles();
  } catch (error) {
    console.error("保存角色失败:", error);
  }
};

const handleDeleteRole = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要删除角色「${row.roleName}」吗？如有关联用户则无法删除。`, "确认", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
    await deleteRole(row.id);
    ElMessage.success("删除成功");
    fetchRoles();
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除角色失败:", error);
    }
  }
};

const permDialogVisible = ref(false);
const permTreeRef = ref<any>(null);
const currentRole = ref<any>(null);
const allPermissions = ref<any[]>([]);

const permTreeData = computed(() => {
  const grouped = new Map<string, any[]>();
  for (const p of allPermissions.value) {
    const module = p.module ?? "其他";
    if (!grouped.has(module)) {
      grouped.set(module, []);
    }
    grouped.get(module)!.push({ id: p.id, label: `${p.permName} (${p.permCode})` });
  }
  return Array.from(grouped.entries()).map(([module, children]) => ({
    id: `module-${module}`,
    label: module,
    disabled: true,
    children,
  }));
});

const handleAssignPerm = async (row: any) => {
  currentRole.value = row;
  permDialogVisible.value = true;
  try {
    const permRes = await getPermissions();
    allPermissions.value = permRes.data ?? permRes;
    const rolePermRes = await getRolePermissions(row.id);
    const rolePerms = rolePermRes.data ?? rolePermRes;
    await nextTick();
    if (permTreeRef.value) {
      permTreeRef.value.setCheckedKeys(rolePerms.map((p: any) => p.id));
    }
  } catch (error) {
    console.error("获取权限数据失败:", error);
    ElMessage.error("获取权限数据失败");
  }
};

let allChecked = false;
const handlePermSelectAll = () => {
  if (!permTreeRef.value) return;
  allChecked = !allChecked;
  if (allChecked) {
    const allIds = allPermissions.value.map((p: any) => p.id);
    permTreeRef.value.setCheckedKeys(allIds);
  } else {
    permTreeRef.value.setCheckedKeys([]);
  }
};

const handlePermSave = async () => {
  if (!currentRole.value || !permTreeRef.value) return;
  try {
    const checkedKeys = permTreeRef.value.getCheckedKeys();
    const halfCheckedKeys = permTreeRef.value.getHalfCheckedKeys();
    const permIds = [...checkedKeys, ...halfCheckedKeys].filter((k: any) => typeof k === "number");
    await assignPermissionsToRole(currentRole.value.id, permIds);
    ElMessage.success("权限分配成功");
    permDialogVisible.value = false;
  } catch (error) {
    console.error("分配权限失败:", error);
  }
};

const userRoleDialogVisible = ref(false);
const userRoleSearchForm = reactive({ keyword: "" });
const userLoading = ref(false);
const users = ref<any[]>([]);

const handleUserRoleAssign = () => {
  userRoleSearchForm.keyword = "";
  users.value = [];
  userRoleDialogVisible.value = true;
  fetchUsers();
};

const fetchUsers = async () => {
  userLoading.value = true;
  try {
    const params: any = { page: 1, limit: 100 };
    if (userRoleSearchForm.keyword.trim()) {
      params.username = userRoleSearchForm.keyword.trim();
    }
    const res = await get("/users", { params });
    if (res.success) {
      const list = res.users ?? res.data ?? [];
      const userList = await Promise.all(
        list.map(async (u: any) => {
          try {
            const roleRes = await getUserRoles(u.userId ?? u.id);
            return { ...u, id: u.userId ?? u.id, roles: roleRes.data ?? roleRes };
          } catch {
            return { ...u, id: u.userId ?? u.id, roles: [] };
          }
        })
      );
      users.value = userList;
    } else {
      users.value = [];
    }
  } catch (error) {
    console.error("搜索用户失败:", error);
    ElMessage.error("搜索用户失败");
  } finally {
    userLoading.value = false;
  }
};

const handleUserSearch = () => {
  fetchUsers();
};

const assignRoleDialogVisible = ref(false);
const selectedUser = ref<any>(null);
const selectedRoleIds = ref<number[]>([]);

const handleUserSelect = async (row: any) => {
  selectedUser.value = row;
  selectedRoleIds.value = row.roles.map((r: any) => r.id);
  assignRoleDialogVisible.value = true;
};

const handleAssignRoleSave = async () => {
  if (!selectedUser.value) return;
  try {
    await assignUserRoles(selectedUser.value.id, selectedRoleIds.value);
    ElMessage.success("角色分配成功");
    assignRoleDialogVisible.value = false;
    fetchUsers();
  } catch (error) {
    console.error("分配角色失败:", error);
  }
};

import { nextTick } from "vue";

onMounted(() => {
  fetchRoles();
});
</script>

<style scoped>
.role-list {
  padding: 20px;
  background: var(--gradient-primary);
  min-height: 100vh;
  position: relative;
}

.role-list::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background:
    radial-gradient(circle at 10% 20%, rgba(0, 122, 255, 0.05) 0%, transparent 50%),
    radial-gradient(circle at 90% 80%, rgba(0, 212, 170, 0.05) 0%, transparent 50%);
  pointer-events: none;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  position: relative;
  z-index: 1;
  background: var(--glass-bg);
  backdrop-filter: blur(20px);
  border: 1px solid var(--glass-border);
  border-radius: 16px;
  padding: 20px 24px;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.perm-dialog-header {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  font-size: 14px;
}

.perm-tree {
  max-height: 400px;
  overflow-y: auto;
}

.assign-role-user {
  margin-bottom: 20px;
  font-size: 14px;
}

.role-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.role-checkbox-group .el-checkbox {
  margin-right: 0;
  margin-left: 0;
  width: 100%;
  height: auto;
  padding: 10px 16px;
}

.role-code-tag {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-left: 4px;
}

.no-role {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
