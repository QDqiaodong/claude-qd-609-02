<template>
  <div class="page">
    <div class="page-head">
      <h2 class="page-title">会员管理 <em>开卡 · 充值 · 等级与余额</em></h2>
      <div class="page-tools">
        <el-button type="primary" @click="createVisible = true">开卡</el-button>
      </div>
    </div>

    <div class="rule-tip">
      单次充值 100 ~ 10000 元；累计消费满 3000 元升银卡、满 10000 元升金卡，等级只升不降，折扣分别为 9.5 折 / 9 折。
    </div>

    <div class="panel">
      <el-table :data="members">
        <el-table-column prop="cardNo" label="会员卡号" width="150" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="等级" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="levelType(row.level)" effect="dark">{{ row.levelName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="折扣" width="90">
          <template #default="{ row }">{{ (Number(row.discount) * 10).toFixed(1) }} 折</template>
        </el-table-column>
        <el-table-column label="余额" width="120">
          <template #default="{ row }">¥ {{ money(row.balance) }}</template>
        </el-table-column>
        <el-table-column label="累计消费" width="120">
          <template #default="{ row }">¥ {{ money(row.totalSpend) }}</template>
        </el-table-column>
        <el-table-column prop="registerDate" label="注册日期" width="120" />
        <el-table-column label="有效认证" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.validCertCount > 0" size="small" type="success" effect="dark">
              {{ row.validCertCount }} 项有效
            </el-tag>
            <span v-else class="muted">无</span>
          </template>
        </el-table-column>
        <el-table-column label="回合 / 最好成绩" min-width="150">
          <template #default="{ row }">
            {{ row.roundCount }} 个回合
            <span v-if="row.bestScore" class="best">最高 {{ row.bestScore }} 环</span>
            <span v-else class="muted">暂无成绩</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRecharge(row)">充值</el-button>
            <el-button link type="success" @click="openCerts(row)">认证</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createVisible" title="会员开卡" width="420px">
      <el-form label-width="80px">
        <el-form-item label="会员卡号"><el-input v-model="createForm.cardNo" placeholder="如 AR-2026-0013" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="createForm.name" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="createForm.phone" placeholder="11 位手机号" /></el-form-item>
        <el-form-item label="等级">
          <el-select v-model="createForm.level" style="width: 100%">
            <el-option label="普通会员" value="NORMAL" />
            <el-option label="银卡会员" value="SILVER" />
            <el-option label="金卡会员" value="GOLD" />
          </el-select>
        </el-form-item>
        <el-form-item label="初始余额"><el-input-number v-model="createForm.balance" :min="0" :step="100" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">开卡</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rechargeVisible" :title="`充值 · ${pick ? pick.name : ''}`" width="380px">
      <el-form label-width="80px">
        <el-form-item label="当前余额">
          <span v-if="pick">¥ {{ money(pick.balance) }}</span>
        </el-form-item>
        <el-form-item label="充值金额"><el-input-number v-model="rechargeAmount" :min="100" :max="10000" :step="100" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRecharge">确认充值</el-button>
      </template>
    </el-dialog>

    <!-- 弓种能力认证：当前有效认证 + 历史记录 -->
    <el-dialog v-model="certVisible" :title="`弓种能力认证 · ${certPick ? certPick.name : ''}`" width="720px">
      <div v-loading="certLoading">
        <div class="cert-section-title">当前有效认证（{{ certData.current.length }}）</div>
        <div v-if="certData.current.length" class="cert-current">
          <div v-for="c in certData.current" :key="c.applicationId" class="cert-card">
            <div class="cert-card-head">
              <b>{{ c.bowTypeName }} · {{ c.distance }} 米</b>
              <el-tag size="small" type="success" effect="dark">有效</el-tag>
            </div>
            <div class="muted">适用范围：{{ c.distance }} 米及以内射距</div>
            <div class="muted">规则快照：{{ c.ruleCode }} v{{ c.ruleVersion }}</div>
            <div class="muted">有效期：{{ fmtTime(c.validFrom) }} ~ {{ fmtTime(c.validUntil) }}</div>
          </div>
        </div>
        <el-empty v-else description="暂无有效认证（过期 / 撤回 / 被取代的认证不能用于箭道与弓具入口）" :image-size="60" />

        <div class="cert-section-title">认证历史记录（{{ certData.history.length }}）</div>
        <el-table :data="certData.history" size="small" border max-height="260">
          <el-table-column prop="appNo" label="申请号" width="130" />
          <el-table-column label="弓种 / 射距" width="120">
            <template #default="{ row }">{{ row.bowTypeName }} {{ row.distance }}m</template>
          </el-table-column>
          <el-table-column label="预评" width="70">
            <template #default="{ row }">
              <el-tag v-if="row.passFlag !== null" size="small" :type="row.passFlag ? 'success' : 'danger'" effect="plain">
                {{ row.passFlag ? '达标' : '未达标' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="92">
            <template #default="{ row }">
              <el-tag size="small" :type="certStatusType(row.displayStatus)" effect="dark">{{ row.displayStatusName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="复核" width="120">
            <template #default="{ row }">
              <span v-if="row.reviewedBy" class="muted">{{ row.reviewedBy }} · {{ fmtTime(row.reviewedAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="有效期至" width="105">
            <template #default="{ row }">{{ row.validUntil ? shortDate(row.validUntil) : '—' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { certApi, memberApi, money, shortDate } from '../api'

const members = ref([])
const createVisible = ref(false)
const rechargeVisible = ref(false)
const certVisible = ref(false)
const certLoading = ref(false)
const pick = ref(null)
const certPick = ref(null)
const rechargeAmount = ref(200)
const certData = ref({ current: [], history: [] })
const createForm = reactive({ cardNo: '', name: '', phone: '', level: 'NORMAL', balance: 200 })

function levelType(level) {
  if (level === 'GOLD') return 'warning'
  if (level === 'SILVER') return 'info'
  return 'success'
}

function certStatusType(status) {
  return {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    NEED_MORE: 'warning',
    WITHDRAWN: 'info',
    SUPERSEDED: 'info',
    EXPIRED: 'info'
  }[status] || 'info'
}

const fmtTime = (value) => (value ? String(value).slice(0, 16) : '')

async function loadMembers() {
  members.value = await memberApi.list()
}

function openRecharge(row) {
  pick.value = row
  rechargeAmount.value = 200
  rechargeVisible.value = true
}

async function openCerts(row) {
  certPick.value = row
  certData.value = { current: [], history: [] }
  certVisible.value = true
  certLoading.value = true
  try {
    certData.value = await certApi.memberCerts(row.id)
  } finally {
    certLoading.value = false
  }
}

async function submitRecharge() {
  await memberApi.recharge(pick.value.id, rechargeAmount.value)
  ElMessage.success('充值成功')
  rechargeVisible.value = false
  await loadMembers()
}

async function submitCreate() {
  if (!createForm.cardNo.trim() || !createForm.name.trim()) {
    ElMessage.warning('请填写卡号与姓名')
    return
  }
  await memberApi.create({ ...createForm, cardNo: createForm.cardNo.trim(), name: createForm.name.trim() })
  ElMessage.success('开卡成功')
  createVisible.value = false
  createForm.cardNo = ''
  createForm.name = ''
  createForm.phone = ''
  await loadMembers()
}

onMounted(loadMembers)
</script>

<style scoped>
.best {
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-color-primary);
}

.cert-section-title {
  font-size: 13px;
  font-weight: 600;
  margin: 6px 0 8px;
}

.cert-current {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.cert-card {
  border: 1px solid #a8ddc0;
  background: #f2fbf5;
  border-radius: 10px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}

.cert-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 2px;
}
</style>
