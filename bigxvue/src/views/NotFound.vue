<template>
  <div class="not-found">
    <h1>404</h1>
    <p>抱歉，您访问的页面不存在</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>

<script setup></script>

<style scoped>
.not-found {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.not-found h1 {
  font-size: 120px;
  color: #409EFF;
  margin: 0;
}
.not-found p {
  font-size: 18px;
  color: #909399;
  margin: 16px 0 24px;
}
</style>
