<template>
  <div class="slide-captcha">
    <div class="captcha-tip" :class="statusClass">{{ tipText }}</div>
    <div class="captcha-canvas" :class="{ 'is-fail': status === 'fail', 'is-success': status === 'success' }">
      <img class="bg-img" :src="bgSrc" alt="滑块验证码" draggable="false" />
      <img
        class="block-img"
        :src="blockSrc"
        :style="{ transform: `translateX(${moveLeft}px)` }"
        draggable="false"
      />
    </div>
    <div class="captcha-slider">
      <div class="slider-track" :style="{ width: moveLeft + BLOCK_W + 'px' }"></div>
      <div
        class="slider-btn"
        :style="{ left: moveLeft + 'px' }"
        @pointerdown="onDown"
      >
        <el-icon v-if="status !== 'success'"><Right /></el-icon>
        <el-icon v-else color="#67C23A"><Check /></el-icon>
      </div>
      <span class="slider-word" v-show="status === 'idle'">向右拖动滑块完成验证</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { Right, Check } from '@element-plus/icons-vue'
import { getSlideCaptcha, checkSlideCaptcha } from '@/api/auth'
import { buildPointPlain, buildPointJson, buildCaptchaVerification } from '@/utils/ajCaptcha'

/**
 * AJ-Captcha 滑动拼图组件（登录二次行为验证）
 * <p>
 * 契约与官方 vue 组件一致（服务端 1.4.0 字节码确认）：
 * 1. get 拿底图（original 310px 原始尺寸渲染，不做缩放，避免坐标换算）；
 * 2. 拖动结束后提交 pointJson = AES-ECB(坐标明文, secretKey)；
 * 3. check 成功后前端计算凭证 captchaVerification = AES-ECB(token + "---" + 坐标明文, secretKey)
 *    （repData 里该字段恒为 null），通过 verified 事件交给父组件随登录请求提交。
 */
const emit = defineEmits(['verified', 'failed'])

/** 底图原始宽度（AJ 默认底图 310×155） */
const IMG_W = 310
/** 滑块拼图宽度（默认模板 52px，加载后按图片实际宽度更新） */
const BLOCK_W = ref(52)

const bgSrc = ref('')
const blockSrc = ref('')
const token = ref('')
const secretKey = ref('')
const moveLeft = ref(0)
const status = ref('idle') // idle | dragging | verifying | success | fail
const tipText = ref('请完成滑块验证后登录')

const statusClass = computed(() => ({
  success: status.value === 'success',
  fail: status.value === 'fail'
}))

let startX = 0
let startLeft = 0

async function refresh() {
  status.value = 'idle'
  tipText.value = '请完成滑块验证后登录'
  moveLeft.value = 0
  bgSrc.value = ''
  blockSrc.value = ''
  try {
    const res = await getSlideCaptcha()
    const model = res.data || {}
    if (model.repCode !== '0000' || !model.repData) {
      tipText.value = model.repMsg || '验证码获取失败'
      return
    }
    const repData = model.repData
    bgSrc.value = 'data:image/png;base64,' + repData.originalImageBase64
    blockSrc.value = 'data:image/png;base64,' + repData.jigsawImageBase64
    token.value = repData.token
    secretKey.value = repData.secretKey
    // 拼图模板宽度按图片实际尺寸校准（官方默认 52）
    const probe = new Image()
    probe.onload = () => {
      if (probe.naturalWidth > 0) BLOCK_W.value = probe.naturalWidth
    }
    probe.src = blockSrc.value
  } catch (e) {
    tipText.value = '验证码获取失败，请重试'
  }
}

function onDown(e) {
  if (status.value === 'success' || status.value === 'verifying') return
  if (!token.value) {
    refresh()
    return
  }
  status.value = 'dragging'
  startX = e.clientX
  startLeft = moveLeft.value
  document.addEventListener('pointermove', onMove)
  document.addEventListener('pointerup', onUp)
}

function onMove(e) {
  const delta = e.clientX - startX
  const max = IMG_W - BLOCK_W.value
  moveLeft.value = Math.min(max, Math.max(0, startLeft + delta))
}

async function onUp() {
  document.removeEventListener('pointermove', onMove)
  document.removeEventListener('pointerup', onUp)
  if (status.value !== 'dragging') return
  // 拖动距离太小视为误触，直接回弹不消耗校验次数
  if (moveLeft.value < 5) {
    reset()
    return
  }
  status.value = 'verifying'
  tipText.value = '校验中...'
  // 坐标明文只构造一次：pointJson 与二次校验凭证必须用完全相同的明文（服务端以密文作缓存键）
  const plainPoint = buildPointPlain(Math.round(moveLeft.value))
  try {
    const res = await checkSlideCaptcha({
      captchaType: 'blockPuzzle',
      token: token.value,
      pointJson: buildPointJson(plainPoint, secretKey.value)
    })
    const model = res.data || {}
    if (model.repCode === '0000') {
      status.value = 'success'
      tipText.value = '验证成功'
      // 凭证由前端计算：AES(token + "---" + 坐标明文, secretKey)，与服务端写入的
      // 二次校验缓存键逐字节一致（ECB 确定性加密）；repData.captchaVerification 恒为 null
      emit('verified', buildCaptchaVerification(token.value, plainPoint, secretKey.value))
    } else {
      fail(model.repMsg || '验证失败，请重试')
    }
  } catch (e) {
    fail('验证失败，请重试')
  }
}

function fail(msg) {
  status.value = 'fail'
  tipText.value = msg
  emit('failed')
  setTimeout(() => {
    reset()
    refresh()
  }, 600)
}

function reset() {
  moveLeft.value = 0
  if (status.value !== 'success') status.value = 'idle'
}

defineExpose({ refresh })

onBeforeUnmount(() => {
  document.removeEventListener('pointermove', onMove)
  document.removeEventListener('pointerup', onUp)
})

refresh()
</script>

<style scoped>
.slide-captcha {
  width: 310px;
  user-select: none;
}
.captcha-tip {
  height: 24px;
  line-height: 24px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.captcha-tip.success { color: #67c23a; }
.captcha-tip.fail { color: #f56c6c; }
.captcha-canvas {
  position: relative;
  width: 310px;
  height: 155px;
  border-radius: 4px;
  overflow: hidden;
  background: #f5f7fa;
}
.captcha-canvas.is-fail { outline: 1px solid #f56c6c; }
.captcha-canvas.is-success { outline: 1px solid #67c23a; }
.bg-img {
  width: 310px;
  height: 155px;
  display: block;
}
.block-img {
  position: absolute;
  top: 0;
  left: 0;
  height: 155px;
  pointer-events: none;
}
.captcha-slider {
  position: relative;
  width: 310px;
  height: 40px;
  margin-top: 8px;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
.slider-track {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: #d1e9fe;
}
.slider-btn {
  position: absolute;
  top: 0;
  left: 0;
  width: 40px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  touch-action: none;
  z-index: 2;
}
.slider-word {
  position: absolute;
  width: 100%;
  text-align: center;
  line-height: 38px;
  font-size: 13px;
  color: #a8abb2;
  z-index: 1;
}
</style>
