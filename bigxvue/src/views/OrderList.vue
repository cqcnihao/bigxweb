<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>订单管理</span>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增订单</el-button>
      </div>
    </template>
    <el-form :inline="true" :model="queryForm" class="query-form">
      <el-form-item label="关键字">
        <el-input v-model="queryForm.name" placeholder="订单号 / 客户名称" clearable />
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
      <el-table-column prop="orderNo" label="订单号" width="160" />
      <el-table-column prop="customerName" label="客户名称" min-width="160" />
      <el-table-column prop="totalAmount" label="金额" width="120">
        <template #default="{ row }">¥ {{ Number(row.totalAmount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="itemCount" label="数量" width="80" />
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑订单' : '新增订单'" width="500px" destroy-on-close>
      <el-form :model="formData" label-width="90px">
        <el-form-item label="订单号">
          <el-input v-model="formData.orderNo" placeholder="请输入订单号" />
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="formData.customerName" placeholder="请输入客户名称" />
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="formData.totalAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="formData.itemCount" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="可选" />
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
import { getOrderPage, createOrder, updateOrder, deleteOrder, confirmOrder, cancelOrder } from '@/api/order'

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
  orderNo: '',
  customerName: '',
  totalAmount: 0,
  itemCount: 0,
  remark: ''
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
    const res = await getOrderPage(mapped)
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
      orderNo: row.orderNo || '',
      customerName: row.customerName || '',
      totalAmount: row.totalAmount ?? 0,
      itemCount: row.itemCount ?? 0,
      remark: row.remark || ''
    }
  } else {
    formData.value = { id: null, orderNo: '', customerName: '', totalAmount: 0, itemCount: 0, remark: '' }
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formData.value.orderNo || !formData.value.customerName) {
    ElMessage.warning('订单号和客户名称必填')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateOrder(formData.value)
      ElMessage.success('修改已提交，待确认后生效')
    } else {
      await createOrder(formData.value)
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
      '删除后订单将标记为"删除待确认"状态，需二次确认才会真正删除。\n确定要标记删除吗？',
      '删除待确认',
      { confirmButtonText: '标记删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteOrder(row.id)
    ElMessage.success('已标记为删除待确认')
    loadData()
  } catch (e) {
    // handled
  }
}

async function handleConfirmDelete(row) {
  try {
    await ElMessageBox.confirm(
      '确定要物理删除该订单吗？此操作不可恢复！',
      '最终确认删除',
      { confirmButtonText: '物理删除', cancelButtonText: '取消', type: 'error' }
    )
    await confirmOrder(row.id)
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
    await confirmOrder(row.id)
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
    await cancelOrder(row.id)
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