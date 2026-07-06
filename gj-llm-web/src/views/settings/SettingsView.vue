<script setup lang="ts">
import { useAppStore } from '@/stores/modules/app'

const appStore = useAppStore()
</script>

<template>
  <div class="settings-view">
    <h2>系统设置</h2>

    <div class="glass-card">
      <div class="glass-card__header">
        <span>外观</span>
      </div>
      <div class="glass-card__body">
        <el-form label-width="120px">
          <el-form-item label="侧边栏">
            <el-switch
              :model-value="appStore.sidebarCollapsed"
              active-text="收起"
              inactive-text="展开"
              @change="appStore.toggleSidebar()"
            />
          </el-form-item>
        </el-form>
      </div>
    </div>

    <div class="glass-card">
      <div class="glass-card__header">
        <span>关于</span>
      </div>
      <div class="glass-card__body">
        <div class="about-info">
          <div class="about-info__item">
            <span class="about-info__label">应用名称</span>
            <span class="about-info__value">GJ-LLM</span>
          </div>
          <div class="about-info__item">
            <span class="about-info__label">版本</span>
            <span class="about-info__value">1.0.0</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.settings-view {
  height: 100%;
  padding: 24px;
  overflow: auto;

  h2 {
    margin-bottom: 24px;
    font-size: 22px;
    font-weight: 700;
    color: #1d1d1f;
    letter-spacing: -0.01em;
  }
}

// ---- 玻璃卡片 ----
.glass-card {
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.05), 0 2px 8px rgba(0, 0, 0, 0.03);
  margin-bottom: 16px;
  max-width: 640px;
  transition: all 0.25s cubic-bezier(0.25, 0.1, 0.25, 1);

  &:hover {
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.07), 0 4px 12px rgba(0, 0, 0, 0.04);
  }

  &__header {
    padding: 18px 24px 0;
    font-size: 15px;
    font-weight: 600;
    color: #1d1d1f;
  }

  &__body {
    padding: 16px 24px;
  }
}

.about-info {
  &__item {
    display: flex;
    align-items: center;
    padding: 8px 0;
  }

  &__label {
    width: 100px;
    color: #86868b;
    font-size: 13px;
  }

  &__value {
    color: #1d1d1f;
    font-weight: 500;
  }
}
</style>
