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
        <el-table-column label="回合 / 最好成绩" min-width="150">
          <template #default="{ row }">
            {{ row.roundCount }} 个回合
            <span v-if="row.bestScore" class="best">最高 {{ row.bestScore }} 环</span>
            <span v-else class="muted">暂无成绩</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRecharge(row)">充值</el-button>
            <el-button link type="primary" @click="openCerts(row)">认证</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 会员认证抽屉：当前有效认证 + 历史记录 -->
    <el-drawer v-model="certVisible" :title="`弓种能力认证 · ${certMember ? certMember.name : ''}`" size="62%">
      <template v-if="certMember">
        <div class="rule-tip">
          仅「已通过且在有效期内」的认证可用；适用范围按高射距覆盖低射距。过期、撤回、驳回或重新评定被取代的认证都不再有效。
        </div>

        <div class="panel-title">当前有效认证（{{ effectiveCerts.length }}）</div>
        <div v-if="effectiveCerts.length" class="cert-cards">
          <div v-for="c in effectiveCerts" :key="c.id" class="cert-card is-valid">
            <div class="cert-head">
              <b>{{ c.bowTypeName }}</b>
              <el-tag size="small" type="success" effect="dark">有效</el-tag>
            </div>
            <div class="cert-scope">适用 {{ c.distance }} 米及以内射距</div>
            <div class="muted mono">{{ c.certNo }}</div>
            <div class="muted">有效期 {{ c.validFrom }} ~ {{ c.validUntil }}</div>
            <div class="muted">复核：{{ c.reviewedBy }}</div>
          </div>
        </div>
        <el-empty v-else description="暂无有效认证" :image-size="70" />

        <div class="panel-title" style="margin-top: 16px">认证历史（{{ certHistory.length }}）</div>
        <el-table :data="certHistory" size="small" border>
          <el-table-column prop="certNo" label="编号" width="130" />
          <el-table-column label="弓种射距" width="130">
            <template #default="{ row }">{{ row.bowTypeName }} · {{ row.distance }}m</template>
          </el-table-column>
          <el-table-column label="规则" width="100">
            <template #default="{ row }">{{ row.ruleCode }} v{{ row.versionNo }}</template>
          </el-table-column>
          <el-table-column label="证据" width="140">
            <template #default="{ row }">{{ row.evidenceRounds }}回合 / {{ row.evidenceArrows }}箭 / {{ row.evidenceAverage }}环</template>
          </el-table-column>
          <el-table-column label="状态" width="92">
            <template #default="{ row }">
              <el-tag size="small" :type="certStatusType(row.status)" effect="dark">{{ row.statusName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="有效期" min-width="180">
            <template #default="{ row }">
              <span v-if="row.validFrom">{{ row.validFrom }} ~ {{ row.validUntil }}</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="发起 / 复核" min-width="150">
            <template #default="{ row }">
              <div class="muted">{{ row.createdBy }} 发起</div>
              <div v-if="row.reviewedBy" class="muted">{{ row.reviewedBy }} 复核</div>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { memberApi, certApi, money } from '../api'

const members = ref([])
const createVisible = ref(false)
const rechargeVisible = ref(false)
const pick = ref(null)
const rechargeAmount = ref(200)
const createForm = reactive({ cardNo: '', name: '', phone: '', level: 'NORMAL', balance: 200 })

// 会员认证抽屉
const certVisible = ref(false)
const certMember = ref(null)
const effectiveCerts = ref([])
const certHistory = ref([])

function certStatusType(status) {
  return {
    PENDING_REVIEW: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    NEED_MORE: 'warning',
    EXPIRED: 'info',
    REVOKED: 'danger',
    SUPERSEDED: 'info'
  }[status] || 'info'
}

async function openCerts(row) {
  certMember.value = row
  certVisible.value = true
  const [effective, history] = await Promise.all([
    certApi.effective(row.id),
    certApi.list({ memberId: row.id })
  ])
  effectiveCerts.value = effective
  certHistory.value = history
}

function levelType(level) {
  if (level === 'GOLD') return 'warning'
  if (level === 'SILVER') return 'info'
  return 'success'
}

async function loadMembers() {
  members.value = await memberApi.list()
}

function openRecharge(row) {
  pick.value = row
  rechargeAmount.value = 200
  rechargeVisible.value = true
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

.cert-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.cert-card {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cert-card.is-valid {
  border-top: 3px solid #2eb872;
  background: #f6fdf9;
}

.cert-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cert-head b {
  font-size: 15px;
}

.cert-scope {
  font-size: 13px;
  font-weight: 600;
  color: #04463c;
}
</style>
