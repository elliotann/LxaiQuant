<template>
  <div class="menu-management">
    <div class="page-header animate-fade-in">
      <h2 class="text-gradient-primary">菜单维护</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd" class="premium-button primary">
          <el-icon><plus /></el-icon>
          添加菜单
        </el-button>
      </div>
    </div>

    <el-card class="premium-card fade-in">
      <el-table v-loading="loading" :data="menus" style="width: 100%" border class="premium-table" row-key="id" default-expand-all :tree-props="{ children: 'children' }">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="menuCode" label="编码" width="140" />
        <el-table-column prop="menuName" label="名称" width="140" />
        <el-table-column prop="icon" label="图标" width="80">
          <template #default="{ row }">
            <el-icon v-if="row.icon"><component :is="row.icon" /></el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="routePath" label="路由路径" width="180" />
        <el-table-column prop="parentId" label="父ID" width="60" />
        <el-table-column prop="sortOrder" label="排序" width="60" />
        <el-table-column prop="permCode" label="权限编码" width="140" />
        <el-table-column prop="enabled" label="启用" width="60">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :disabled="true" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)" class="premium-button">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)" class="premium-button danger">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogType === 'add' ? '添加菜单' : '编辑菜单'" width="600px" class="premium-modal">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="编码" prop="menuCode">
          <el-input v-model="form.menuCode" placeholder="如: system:menus" />
        </el-form-item>
        <el-form-item label="名称" prop="menuName">
          <el-input v-model="form.menuName" placeholder="如: 菜单维护" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="如: Menu, Setting" />
        </el-form-item>
        <el-form-item label="路由路径">
          <el-input v-model="form.routePath" placeholder="如: /system/menus" />
        </el-form-item>
        <el-form-item label="父级菜单">
          <el-select v-model="form.parentId" placeholder="顶级菜单" clearable style="width: 100%">
            <el-option v-for="m in parentOptions" :key="m.id" :label="m.menuName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="1" :max="99" />
        </el-form-item>
        <el-form-item label="权限编码">
          <el-input v-model="form.permCode" placeholder="如: system:menu" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false" class="premium-button">取消</el-button>
        <el-button type="primary" @click="handleSave" class="premium-button primary">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { getAllMenus, createMenu, updateMenu, deleteMenu } from "@/api/admin";

const loading = ref(false);
const menus = ref<any[]>([]);

const dialogVisible = ref(false);
const dialogType = ref<"add" | "edit">("add");
const formRef = ref<FormInstance>();
const editingId = ref<number | null>(null);

const form = reactive({
  menuCode: "",
  menuName: "",
  icon: "",
  routePath: "",
  parentId: null as number | null,
  sortOrder: 1,
  permCode: "",
  enabled: true,
});

const rules: FormRules = {
  menuCode: [{ required: true, message: "请输入菜单编码", trigger: "blur" }],
  menuName: [{ required: true, message: "请输入菜单名称", trigger: "blur" }],
};

const parentOptions = computed(() => {
  return menus.value.filter(m => !m.parentId);
});

const fetchMenus = async () => {
  loading.value = true;
  try {
    const res = await getAllMenus();
    const flat = Array.isArray(res) ? res : res.data || [];
    const roots = flat.filter((m: any) => !m.parentId);
    for (const root of roots) {
      root.children = flat.filter((m: any) => m.parentId === root.id);
    }
    menus.value = roots;
  } catch (error) {
    console.error("获取菜单列表失败:", error);
    ElMessage.error("获取菜单列表失败");
  } finally {
    loading.value = false;
  }
};

const handleAdd = () => {
  dialogType.value = "add";
  editingId.value = null;
  Object.assign(form, {
    menuCode: "",
    menuName: "",
    icon: "",
    routePath: "",
    parentId: null,
    sortOrder: 1,
    permCode: "",
    enabled: true,
  });
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogType.value = "edit";
  editingId.value = row.id;
  Object.assign(form, {
    menuCode: row.menuCode,
    menuName: row.menuName,
    icon: row.icon ?? "",
    routePath: row.routePath ?? "",
    parentId: row.parentId,
    sortOrder: row.sortOrder,
    permCode: row.permCode ?? "",
    enabled: row.enabled,
  });
  dialogVisible.value = true;
};

const handleSave = async () => {
  if (!formRef.value) return;
  try {
    await formRef.value.validate();
    const payload = { ...form };
    if (dialogType.value === "add") {
      await createMenu(payload);
      ElMessage.success("菜单创建成功");
    } else {
      payload.id = editingId.value;
      await updateMenu(payload);
      ElMessage.success("菜单更新成功");
    }
    dialogVisible.value = false;
    fetchMenus();
  } catch (error) {
    console.error("保存菜单失败:", error);
  }
};

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除菜单「${row.menuName}」吗？`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
    await deleteMenu(row.id);
    ElMessage.success("菜单已删除");
    fetchMenus();
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除菜单失败:", error);
    }
  }
};

onMounted(fetchMenus);
</script>
