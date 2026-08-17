<template>
  <div class="users">
    <div class="users-header">
      <div class="header-left">
        <div class="header-title">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </div>
        <div class="header-subtitle">管理系统用户、角色与权限</div>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="handleAdd">
          <el-icon><plus /></el-icon>
          添加用户
        </el-button>
      </div>
    </div>

    <el-card class="dark-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">
            <el-icon><Search /></el-icon>
            搜索条件
          </span>
        </div>
      </template>
      <el-form :model="searchForm" inline>
        <el-form-item label="用户名">
          <el-input
            v-model="searchForm.username"
            placeholder="请输入用户名"
            clearable
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input
            v-model="searchForm.email"
            placeholder="请输入邮箱"
            clearable
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="searchForm.role"
            placeholder="请选择角色"
            clearable
          >
            <el-option label="管理员" value="admin" />
            <el-option label="用户" value="user" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="dark-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">
            <el-icon><List /></el-icon>
            用户列表
          </span>
        </div>
      </template>
      <el-table
        v-loading="loading"
        :data="tableData"
        style="width: 100%"
        border
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="role" label="角色">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : 'success'" effect="dark">
              {{ row.role === "admin" ? "管理员" : "用户" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'" effect="dark">
              {{ row.status === "active" ? "启用" : "禁用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="350">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              size="small"
              :type="row.status === 'active' ? 'danger' : 'success'"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === "active" ? "禁用" : "启用" }}
            </el-button>
            <el-button
              size="small"
              type="danger"
              @click="handleDelete(row)"
            >删除</el-button>
            <el-button
              size="small"
              type="warning"
              @click="handleResetPassword(row)"
            >重置密码</el-button>
            <el-button
              size="small"
              type="primary"
              @click="handleAssignRole(row)"
            >分配角色</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '添加用户' : '编辑用户'"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="userForm"
        :rules="userRules"
        label-width="80px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="userForm.phone" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="userForm.role" placeholder="请选择角色">
            <el-option label="管理员" value="admin" />
            <el-option label="用户" value="user" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="dialogType === 'add'" label="密码" prop="password">
          <el-input
            v-model="userForm.password"
            type="password"
            placeholder="请输入密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignRoleDialogVisible" title="分配角色" width="500px" :close-on-click-modal="false">
      <div class="assign-role-user">用户：<strong>{{ assignRoleUser?.username }}</strong></div>
      <el-checkbox-group v-model="assignRoleSelectedIds" class="role-checkbox-group">
        <el-checkbox v-for="r in allRoles" :key="r.id" :label="r.id" :value="r.id" border>
          {{ r.roleName }}
          <span class="role-code-tag">({{ r.roleCode }})</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="assignRoleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAssignRoleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from "element-plus";
import { get, post, put, del } from "@/api/base";
import { getRoles, getUserRoles, assignUserRoles } from "@/api/admin";

const loading = ref(false);
const dialogVisible = ref(false);
const dialogType = ref<"add" | "edit">("add");
const formRef = ref<FormInstance>();

const searchForm = reactive({
  username: "",
  email: "",
  role: "",
});

const userForm = reactive({
  id: "",
  username: "",
  email: "",
  phone: "",
  role: "user",
  password: "",
});

const userRules: FormRules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    {
      min: 3,
      max: 20,
      message: "用户名长度在 3 到 20 个字符",
      trigger: "blur",
    },
  ],
  email: [
    { required: true, message: "请输入邮箱", trigger: "blur" },
    { type: "email", message: "请输入正确的邮箱格式", trigger: "blur" },
  ],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$/,
      message: "请输入正确的手机号格式",
      trigger: "blur",
    },
  ],
  role: [{ required: true, message: "请选择角色", trigger: "change" }],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, message: "密码长度不能少于6位", trigger: "blur" },
  ],
};

const tableData = ref([]);
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
});

const fetchUsers = async () => {
  loading.value = true;
  try {
    const params = {
      username: searchForm.username || undefined,
      email: searchForm.email || undefined,
      role: searchForm.role || undefined,
      page: pagination.page,
      limit: pagination.size,
    };

    const response = await get("/users", { params });

    if (response.success) {
      tableData.value = response.users.map((user) => ({
        id: user.userId,
        username: user.username,
        email: user.email,
        phone: user.phone,
        role: user.role,
        status: String(user.status || "").toLowerCase(),
        createdAt: user.createTime,
        updatedAt: user.updateTime,
        lastLoginAt: user.lastLoginTime,
      }));
      pagination.total = response.total;
    } else {
      ElMessage.error(response.message || "获取用户列表失败");
    }
  } catch (error) {
    console.error("获取用户列表失败:", error);
    ElMessage.error("获取用户列表失败");
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  pagination.page = 1;
  fetchUsers();
};

const handleReset = () => {
  Object.assign(searchForm, {
    username: "",
    email: "",
    role: "",
  });
  handleSearch();
};

const handleSizeChange = (size: number) => {
  pagination.size = size;
  fetchUsers();
};

const handleCurrentChange = (page: number) => {
  pagination.page = page;
  fetchUsers();
};

const handleAdd = () => {
  dialogType.value = "add";
  Object.assign(userForm, {
    id: "",
    username: "",
    email: "",
    phone: "",
    role: "user",
    password: "",
  });
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogType.value = "edit";
  Object.assign(userForm, {
    ...row,
    password: "",
  });
  dialogVisible.value = true;
};

const handleSave = async () => {
  if (!formRef.value) return;

  try {
    await formRef.value.validate();

    if (dialogType.value === "edit") {
      const updateData = {
        username: userForm.username,
        email: userForm.email,
        phone: userForm.phone,
        role: userForm.role,
      };
      const response = await put(`/users/${userForm.id}`, updateData);

      if (response.success) {
        ElMessage.success("编辑成功");
        dialogVisible.value = false;
        fetchUsers();
      } else {
        ElMessage.error(response.message || "编辑失败");
      }
    } else {
      const createData = {
        username: userForm.username,
        email: userForm.email,
        phone: userForm.phone,
        role: userForm.role,
        password: userForm.password,
      };
      const response = await post("/users", createData);
      if (response.success) {
        ElMessage.success("添加成功");
        dialogVisible.value = false;
        fetchUsers();
      } else {
        ElMessage.error(response.message || "添加失败");
      }
    }
  } catch (error) {
    console.error("保存用户失败:", error);
    ElMessage.error("保存用户失败");
  }
};

const handleToggleStatus = async (row: any) => {
  try {
    const isActive = row.status !== "active";
    await ElMessageBox.confirm(
      `确定要${isActive ? "启用" : "禁用"}该用户吗？`,
      "确认",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      },
    );

    const response = await put(`/users/${row.id}/status`, { isActive });

    if (response.success) {
      ElMessage.success("操作成功");
      fetchUsers();
    } else {
      ElMessage.error(response.message || "操作失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("切换用户状态失败:", error);
      ElMessage.error("操作失败");
    }
  }
};

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm("确定要删除该用户吗？删除后无法恢复！", "确认", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    const response = await del(`/users/${row.id}`);

    if (response.success) {
      ElMessage.success("删除成功");
      fetchUsers();
    } else {
      ElMessage.error(response.message || "删除失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除用户失败:", error);
      ElMessage.error("删除失败");
    }
  }
};

const handleResetPassword = async (row: any) => {
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入用户 ${row.username} 的新密码`,
      "重置密码",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        inputType: "password",
        inputPlaceholder: "请输入至少6位新密码",
        inputValidator: (val: string) => {
          if (!val || val.trim().length < 6) return "密码长度不能少于6位";
          return true;
        },
      },
    );

    const response = await put(`/users/${row.id}/password`, {
      newPassword: value,
    });

    if (response.success) {
      ElMessage.success("密码重置成功");
    } else {
      ElMessage.error(response.message || "密码重置失败");
    }
  } catch (error: any) {
    if (error !== "cancel") {
      console.error("重置密码失败:", error);
      ElMessage.error("重置密码失败");
    }
  }
};

const assignRoleDialogVisible = ref(false);
const assignRoleUser = ref<any>(null);
const assignRoleSelectedIds = ref<number[]>([]);
const allRoles = ref<any[]>([]);

const handleAssignRole = async (row: any) => {
  assignRoleUser.value = row;
  try {
    const roleRes = await getRoles();
    allRoles.value = roleRes.data ?? [];
    const userRoleRes = await getUserRoles(row.id);
    const userRoles = userRoleRes.data ?? [];
    assignRoleSelectedIds.value = userRoles.map((r: any) => r.id);
    assignRoleDialogVisible.value = true;
  } catch (error) {
    console.error("获取角色数据失败:", error);
    ElMessage.error("获取角色数据失败");
  }
};

const handleAssignRoleSave = async () => {
  if (!assignRoleUser.value) return;
  try {
    await assignUserRoles(assignRoleUser.value.id, assignRoleSelectedIds.value);
    ElMessage.success("角色分配成功");
    assignRoleDialogVisible.value = false;
    fetchUsers();
  } catch (error) {
    console.error("分配角色失败:", error);
  }
};

onMounted(() => {
  fetchUsers();
});
</script>

<style scoped>
.users {
  padding: 15px;
  height: 100%;
  overflow-y: auto;
  background: var(--primary-bg);
  color: var(--text-primary);
}

.users-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: var(--secondary-bg);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  margin-bottom: 15px;
  box-shadow: var(--card-shadow);
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.header-subtitle {
  font-size: 12px;
  color: var(--text-muted);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dark-card {
  background: var(--secondary-bg) !important;
  border: 1px solid var(--border-color) !important;
  margin-bottom: 15px;
  box-shadow: var(--card-shadow);
}

.dark-card :deep(.el-card__header) {
  background: var(--tertiary-bg);
  border-bottom: 1px solid var(--border-color);
  padding: 10px 15px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: bold;
  color: var(--accent-blue);
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
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
</style>
