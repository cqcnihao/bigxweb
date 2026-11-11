<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>产品管理</span>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增产品</el-button>
      </div>
    </template>
    <el-form :inline="true" :model="queryForm" class="query-form">
      <el-form-item label="名称">
        <el-input v-model="queryForm.name" placeholder="请输入产品名称" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryForm.status" placeholder="全部" clearable style="width: 160px">
          <el-option label="新增待确认" :value="0" />
          <el-option label="已生效" :value="1" />
          <el-option label="修改待确认" :value="2" />
          <el-option label="删除待确认" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="productName" label="产品名称" min-width="160" />
      <el-table-column prop="productCode" label="编码" width="160" />
      <el-table-column prop="price" label="价格" width="100">
        <template #default="{ row }">¥ {{ Number(row.price || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="80" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="warning" effect="plain">新增待确认</el-tag>
          <el-tag v-else-if="row.status === 1" type="success">已生效</el-tag>
          <el-tag v-else-if="row.status === 2" type="warning" effect="dark">修改待确认</el-tag>
          <el-tag v-else-if="row.status === 3" type="danger">删除待确认</el-tag>
          <el-tag v-else type="info">未设置</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button size="small" type="primary" link :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="success" link :icon="Check" @click="handleConfirm(row)">确认</el-button>
            <el-button size="small" type="info" link @click="handleCancel(row)">取消</el-button>
          </template>
          <template v-else-if="row.status === 1">
            <el-button size="small" type="primary" link :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
          <template v-else-if="row.status === 2">
            <el-button size="small" type="success" link :icon="Check" @click="handleConfirm(row)">应用修改</el-button>
            <el-button size="small" type="info" link @click="handleCancel(row)">丢弃修改</el-button>
          </template>
          <template v-else-if="row.status === 3">
            <el-button size="small" type="danger" link :icon="Delete" @click="handleConfirmDelete(row)">确认删除</el-button>
            <el-button size="small" type="info" link @click="handleCancel(row)">恢复</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      class="pagination"
      v-model:current-page="queryForm.pageNum"
      v-model:page-size="queryForm.pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="loadData"
      @current-change="loadData"
    />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑产品' : '新增产品'" width="500px" destroy-on-close>
      <el-form :model="formData" label-width="90px">
        <el-form-item label="产品名称">
          <el-input v-model="formData.productName" placeholder="请输入产品名称" />
        </el-form-item>
        <el-form-item label="产品编码">
          <el-input v-model="formData.productCode" placeholder="请输入产品编码" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="formData.category" placeholder="如：电子产品" />
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="formData.price" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="formData.stock" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, Edit, Delete, Check } from '@element-plus/icons-vue'
import { getProductPage, createProduct, updateProduct, deleteProduct, confirmProduct, cancelProduct } from '@/api/product'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const queryForm = ref({
  pageNum: 1,
  pageSize: 10,
  name: '',
  status: null
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formData = ref({
  id: null,
  productName: '',
  productCode: '',
  category: '',
  price: 0,
  stock: 0,
  description: ''
})

async function loadData() {
  loading.value = true
  try {
    const mapped = {
      current: queryForm.value.pageNum,
      size: queryForm.value.pageSize,
      keyword: queryForm.value.name || undefined,
      status: queryForm.value.status
    }
    const res = await getProductPage(mapped)
    const data = res.data || {}
    tableData.value = data.records || data.list || []
    total.value = data.total || 0
  } catch (e) {
    // handled
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  queryForm.value = { pageNum: 1, pageSize: 10, name: '', status: null }
  loadData()
}

function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    formData.value = {
      id: row.id,
      productName: row.productName || '',
      productCode: row.productCode || '',
      category: row.category || '',
      price: row.price ?? 0,
      stock: row.stock ?? 0,
      description: row.description || ''
    }
  } else {
    formData.value = { id: null, productName: '', productCode: '', category: '', price: 0, stock: 0, description: '' }
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formData.value.productName || !formData.value.productCode) {
    ElMessage.warning('名称和编码必填')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateProduct(formData.value)
      ElMessage.success('修改已提交，待确认后生效')
    } else {
      await createProduct(formData.value)
      ElMessage.success('新增草稿已保存，确认后生效')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    // handled
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      '删除后商品将标记为"删除待确认"状态，需二次确认才会真正删除。\n确定要标记删除吗？',
      '删除待确认',
      { confirmButtonText: '标记删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteProduct(row.id)
    ElMessage.success('已标记为删除待确认')
    loadData()
  } catch (e) {
    // handled
  }
}

async function handleConfirmDelete(row) {
  try {
    await ElMessageBox.confirm(
      '确定要物理删除该商品吗？此操作不可恢复！',
      '最终确认删除',
      { confirmButtonText: '物理删除', cancelButtonText: '取消', type: 'error' }
    )
    await confirmProduct(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    // handled
  }
}

async function handleConfirm(row) {
  try {
    const action = row.status === 0 ? '确认新增' : '应用修改'
    await ElMessageBox.confirm(`确定要${action}吗？`, '确认', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'success'
    })
    await confirmProduct(row.id)
    ElMessage.success('操作成功')
    loadData()
  } catch (e) {
    // handled
  }
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm('确定要取消吗？', '取消待确认', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    })
    await cancelProduct(row.id)
    ElMessage.success('已取消')
    loadData()
  } catch (e) {
    // handled
  }
}

onMounted(loadData)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
}
.query-form {
  margin-bottom: 16px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
  display: flex;
}
</style>