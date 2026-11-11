<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>发票管理</span>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增发票</el-button>
      </div>
    </template>
    <el-form :inline="true" :model="queryForm" class="query-form">
      <el-form-item label="关键字">
        <el-input v-model="queryForm.name" placeholder="发票号 / 订单号 / 客户名称" clearable />
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
      <el-table-column prop="invoiceNo" label="发票号" width="160" />
      <el-table-column prop="orderNo" label="订单号" width="140" />
      <el-table-column prop="customerName" label="客户名称" min-width="160" />
      <el-table-column label="不含税金额" width="120">
        <template #default="{ row }">¥ {{ Number(row.invoiceAmount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="taxRate" label="税率" width="80">
        <template #default="{ row }">{{ Number(row.taxRate || 0).toFixed(2) }}%</template>
      </el-table-column>
      <el-table-column label="税额" width="110">
        <template #default="{ row }">¥ {{ Number(row.taxAmount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="价税合计" width="120">
        <template #default="{ row }">¥ {{ Number(row.totalAmount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="invoiceType" label="类型" width="90" />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑发票' : '新增发票'" width="560px" destroy-on-close>
      <el-form :model="formData" label-width="100px">
        <el-form-item label="发票号">
          <el-input v-model="formData.invoiceNo" placeholder="请输入发票号" />
        </el-form-item>
        <el-form-item label="关联订单号">
          <el-input v-model="formData.orderNo" placeholder="可选" />
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="formData.customerName" placeholder="请输入客户名称 / 发票抬头" />
        </el-form-item>
        <el-form-item label="不含税金额">
          <el-input-number v-model="formData.invoiceAmount" :min="0" :precision="2" style="width: 100%" @change="autoCalcTotal" />
        </el-form-item>
        <el-form-item label="税率(%)">
          <el-input-number v-model="formData.taxRate" :min="0" :precision="2" style="width: 100%" @change="autoCalcTotal" />
        </el-form-item>
        <el-form-item label="税额">
          <el-input-number v-model="formData.taxAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="价税合计">
          <el-input-number v-model="formData.totalAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="发票类型">
          <el-input v-model="formData.invoiceType" placeholder="如：增值税专票 / 普票 / 电子专票" />
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
import { getInvoicePage, createInvoice, updateInvoice, deleteInvoice, confirmInvoice, cancelInvoice } from '@/api/invoice'

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
  invoiceNo: '',
  orderNo: '',
  customerName: '',
  invoiceAmount: 0,
  taxRate: 0,
  taxAmount: 0,
  totalAmount: 0,
  invoiceType: '',
  remark: ''
})

function autoCalcTotal() {
  const amount = Number(formData.value.invoiceAmount || 0)
  const rate = Number(formData.value.taxRate || 0)
  const tax = Number((amount * rate / 100).toFixed(2))
  formData.value.taxAmount = tax
  formData.value.totalAmount = Number((amount + tax).toFixed(2))
}

async function loadData() {
  loading.value = true
  try {
    const mapped = {
      current: queryForm.value.pageNum,
      size: queryForm.value.pageSize,
      keyword: queryForm.value.name || undefined,
      status: queryForm.value.status
    }
    const res = await getInvoicePage(mapped)
    const data = res.data || {}
    tableData.value = data.records || data.list || []
    total.value = data.total || 0
  } catch (e) {
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
      invoiceNo: row.invoiceNo || '',
      orderNo: row.orderNo || '',
      customerName: row.customerName || '',
      invoiceAmount: row.invoiceAmount ?? 0,
      taxRate: row.taxRate ?? 0,
      taxAmount: row.taxAmount ?? 0,
      totalAmount: row.totalAmount ?? 0,
      invoiceType: row.invoiceType || '',
      remark: row.remark || ''
    }
  } else {
    formData.value = {
      id: null, invoiceNo: '', orderNo: '', customerName: '',
      invoiceAmount: 0, taxRate: 0, taxAmount: 0, totalAmount: 0,
      invoiceType: '', remark: ''
    }
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formData.value.invoiceNo || !formData.value.customerName) {
    ElMessage.warning('发票号和客户名称必填')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateInvoice(formData.value)
      ElMessage.success('修改已提交，待确认后生效')
    } else {
      await createInvoice(formData.value)
      ElMessage.success('新增草稿已保存，确认后生效')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      '删除后发票将标记为"删除待确认"状态，需二次确认才会真正删除。\n确定要标记删除吗？',
      '删除待确认',
      { confirmButtonText: '标记删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteInvoice(row.id)
    ElMessage.success('已标记为删除待确认')
    loadData()
  } catch (e) {
  }
}

async function handleConfirmDelete(row) {
  try {
    await ElMessageBox.confirm(
      '确定要物理删除该发票吗？此操作不可恢复！',
      '最终确认删除',
      { confirmButtonText: '物理删除', cancelButtonText: '取消', type: 'error' }
    )
    await confirmInvoice(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
  }
}

async function handleConfirm(row) {
  try {
    const action = row.status === 0 ? '确认新增' : '应用修改'
    await ElMessageBox.confirm(`确定要${action}吗？`, '确认', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'success'
    })
    await confirmInvoice(row.id)
    ElMessage.success('操作成功')
    loadData()
  } catch (e) {
  }
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm('确定要取消吗？', '取消待确认', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    })
    await cancelInvoice(row.id)
    ElMessage.success('已取消')
    loadData()
  } catch (e) {
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