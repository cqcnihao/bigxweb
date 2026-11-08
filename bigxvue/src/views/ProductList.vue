<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>产品管理</span>
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增产品</el-button>
      </div>
    </template>
    <el-form :inline="true" :model="queryForm" class="query-form">
      <el-form-item label="名称">
        <el-input v-model="queryForm.name" placeholder="请输入产品名称" clearable />
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="queryForm.category" placeholder="全部" clearable style="width: 160px">
          <el-option label="电子产品" value="电子产品" />
          <el-option label="电脑配件" value="电脑配件" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="productName" label="产品名称" />
      <el-table-column prop="productCode" label="编码" width="180" />
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column prop="price" label="价格" width="120">
        <template #default="{ row }">¥ {{ Number(row.price || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="100" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
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
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, Edit, Delete } from '@element-plus/icons-vue'
import { getProductPage, deleteProduct } from '@/api/product'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const queryForm = ref({
  pageNum: 1,
  pageSize: 10,
  name: '',
  status: null
})

async function loadData() {
  loading.value = true
  try {
    const res = await getProductPage(queryForm.value)
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

function handleAdd() {
  ElMessage.info('新增功能开发中')
}

function handleEdit(row) {
  ElMessage.info('编辑功能开发中: ' + row.id)
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定要删除产品 ' + row.name + ' 吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteProduct(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      // handled
    }
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