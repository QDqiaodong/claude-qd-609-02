<template>
  <div class="page">
    <div class="page-head">
      <h2 class="page-title">弓种能力认证 <em>规则带版本 · 证据窗口 · 教练复核 · 有效期与适用范围</em></h2>
      <div class="page-tools">
        <el-radio-group v-model="scope" size="small">
          <el-radio-button value="workbench">复核台</el-radio-button>
          <el-radio-button value="rules">规则版本</el-radio-button>
          <el-radio-button value="all">全部申请</el-radio-button>
        </el-radio-group>
        <el-button size="small" @click="refreshAll">刷新</el-button>
        <el-input v-model="operator" placeholder="当前教练姓名" style="width: 150px" />
      </div>
    </div>

    <div class="rule-tip">
      教练从会员<b>已完成的计分回合</b>中圈定一段<b>明确时间范围</b>的证据窗口，选择某弓种射距的<b>规则版本</b>发起认证；
      系统按该版本的箭数、射距、通过线给出「达标待复核 / 未达标」初判。复核可<b>通过 / 驳回 / 要求补充证据</b>。
      通过后带有效期与适用范围（高射距覆盖低射距）；过期、撤回或重新评定后，会员可用状态立即变化。
      两位教练同时决定同一申请时只有一个最终结论，后到者会收到冲突提示。
    </div>

    <!-- ============ 复核台：左申请列表，右详情/复核 ============ -->
    <template v-if="scope === 'workbench'">
      <div class="board">
        <div class="panel list-panel">
          <div class="panel-title">
            认证申请
            <small>待复核 <b class="c-warn">{{ pendingCount }}</b> · 待补证据 {{ needMoreCount }}</small>
            <el-checkbox v-model="onlyPending" label="只看待处理" class="only-active" />
          </div>
          <el-table
            :data="shownApplications"
            row-key="id"
            highlight-current-row
            :current-row-key="selectedId"
            height="560"
            @current-change="onPick"
          >
            <el-table-column label="认证编号 / 会员" min-width="150">
              <template #default="{ row }">
                <div class="mono">{{ row.certNo }}</div>
                <div class="muted">{{ row.memberName }} · {{ row.memberCardNo }}</div>
              </template>
            </el-table-column>
            <el-table-column label="弓种 / 射距" width="110">
              <template #default="{ row }">
                {{ row.bowTypeName }}
                <em class="muted">{{ row.distance }}m</em>
              </template>
            </el-table-column>
            <el-table-column label="规则" width="90">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.ruleCode }} v{{ row.versionNo }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="初判" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.systemResult === 'MEETS_STANDARD' ? 'success' : 'danger'" effect="plain">
                  {{ row.systemResultName }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="96">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)" effect="dark">{{ row.statusName }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="panel detail-panel">
          <template v-if="detail">
            <div class="detail-head">
              <div>
                <span class="mono strong">{{ detail.application.certNo }}</span>
                <el-tag size="small" :type="statusType(detail.application.status)" effect="dark">
                  {{ detail.application.statusName }}
                </el-tag>
                <el-tag size="small" type="info" effect="plain">
                  {{ detail.application.ruleCode }} v{{ detail.application.versionNo }}（规则快照）
                </el-tag>
                <el-tag v-if="detail.application.effective" size="small" type="success" effect="dark">当前有效</el-tag>
              </div>
              <div class="muted">{{ detail.application.memberName }} · 第 {{ detail.application.revision }} 轮评定</div>
            </div>

            <!-- 有效期与适用范围 -->
            <div v-if="detail.application.validFrom" class="valid-box">
              <span>有效期：<b>{{ detail.application.validFrom }}</b> 至 <b>{{ detail.application.validUntil }}</b></span>
              <span>适用范围：<b>{{ detail.application.bowTypeName }} · {{ detail.application.distance }} 米及以内射距</b></span>
              <el-tag v-if="detail.application.effective" size="small" type="success" effect="dark">有效</el-tag>
              <el-tag v-else size="small" type="danger" effect="dark">已不可用</el-tag>
            </div>

            <!-- 规则快照 + 证据窗口 -->
            <div class="snapshot-grid">
              <div class="res-box">
                <div class="res-title">评定标准（规则快照，不随后续改版变化）</div>
                <div class="kv-row"><span>弓种 / 射距</span><b>{{ detail.application.bowTypeName }} · {{ detail.application.distance }} 米</b></div>
                <div class="kv-row"><span>最少回合</span><b>{{ detail.application.minRounds }} 个</b></div>
                <div class="kv-row"><span>要求箭数</span><b>{{ detail.application.requiredArrows }} 支</b></div>
                <div class="kv-row"><span>通过线</span><b>平均环 ≥ {{ detail.application.minAverage }}</b></div>
                <div class="kv-row"><span>有效期</span><b>{{ detail.application.validMonths }} 个月</b></div>
              </div>
              <div class="res-box">
                <div class="res-title">证据窗口</div>
                <div class="kv-row"><span>时间范围</span><b>{{ detail.application.evidenceFrom }} ~ {{ detail.application.evidenceTo }}</b></div>
                <div class="kv-row"><span>采纳回合</span><b>{{ detail.application.evidenceRounds }} 个</b></div>
                <div class="kv-row"><span>采纳箭数</span><b>{{ detail.application.evidenceArrows }} 支</b></div>
                <div class="kv-row"><span>加权平均</span><b>{{ detail.application.evidenceAverage }} 环</b></div>
                <div class="kv-row">
                  <span>系统初判</span>
                  <el-tag size="small" :type="detail.application.systemResult === 'MEETS_STANDARD' ? 'success' : 'danger'" effect="dark">
                    {{ detail.application.systemResultName }}
                  </el-tag>
                </div>
              </div>
            </div>

            <!-- 证据回合（计入 + 排除都要看到原因） -->
            <div class="res-title" style="margin-top: 12px">证据回合明细</div>
            <el-table :data="detail.evidences" size="small" border>
              <el-table-column prop="roundNo" label="回合号" width="130" />
              <el-table-column label="时间" width="140">
                <template #default="{ row }">{{ fmtTime(row.roundDate) }}</template>
              </el-table-column>
              <el-table-column label="弓种" width="100">
                <template #default="{ row }">{{ row.bowTypeName }} · {{ row.distance }}m</template>
              </el-table-column>
              <el-table-column prop="arrowCount" label="箭数" width="60" />
              <el-table-column prop="totalScore" label="总分" width="60" />
              <el-table-column label="平均" width="70">
                <template #default="{ row }">{{ Number(row.averageScore).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column label="是否采纳" min-width="180">
                <template #default="{ row }">
                  <el-tag v-if="row.included" size="small" type="success" effect="dark">计入证据</el-tag>
                  <template v-else>
                    <el-tag size="small" type="danger" effect="dark">不予采纳</el-tag>
                    <span class="muted" style="margin-left: 6px">{{ row.excludeReasonName }}</span>
                  </template>
                </template>
              </el-table-column>
            </el-table>

            <!-- 复核操作区 -->
            <div v-if="canDecide" class="decide-box">
              <div class="res-title">复核决定（第 {{ detail.application.revision }} 轮）</div>
              <el-input
                v-model="decideNote"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
                :placeholder="notePlaceholder"
                style="margin: 8px 0"
              />
              <div class="decide-actions">
                <el-button type="success" :loading="acting" @click="submitDecide('APPROVE')">通过</el-button>
                <el-button type="danger" :loading="acting" @click="submitDecide('REJECT')">驳回</el-button>
                <el-button type="warning" :loading="acting" @click="submitDecide('REQUEST_MORE')">要求补充证据</el-button>
              </div>
            </div>

            <!-- 已通过：可撤回 -->
            <div v-if="detail.application.status === 'APPROVED' && !detail.application.effective" class="muted" style="margin-top: 8px">
              该认证已过有效期，不再被视为有效认证。
            </div>
            <div v-if="detail.application.status === 'APPROVED' && detail.application.effective" class="decide-box">
              <el-button type="danger" plain :loading="acting" @click="submitRevoke">撤回该认证</el-button>
              <span class="muted">撤回后会员在课程 / 箭道入口立即失去该适用范围资格</span>
            </div>
            <div v-if="detail.application.status === 'REJECTED'" class="muted reject-note">
              驳回原因：{{ detail.application.reviewNote }}
            </div>
            <div v-if="detail.application.status === 'NEED_MORE'" class="decide-box">
              <div class="res-title">待补充证据 <small class="muted">重新圈定窗口与回合后按原规则快照重新评定</small></div>
              <p class="muted" style="margin: 4px 0">复核意见：{{ detail.application.reviewNote }}</p>
              <el-button type="primary" @click="openResubmit">补充证据并重新评定</el-button>
            </div>

            <!-- 流转轨迹 -->
            <div class="res-title" style="margin-top: 12px">流转轨迹</div>
            <el-timeline class="log-line">
              <el-timeline-item
                v-for="(log, idx) in detail.logs"
                :key="idx"
                :timestamp="`${log.operator} · ${fmtTime(log.createdAt)}`"
                :type="logDot(log.action)"
              >
                <b>{{ log.actionName }}</b>
                <span class="muted" style="margin-left: 6px">{{ log.detail }}</span>
              </el-timeline-item>
            </el-timeline>
          </template>
          <el-empty v-else description="从左侧选择一份认证申请" :image-size="90" />
        </div>
      </div>

      <!-- 发起认证 -->
      <div class="panel" style="margin-top: 14px">
        <div class="panel-title">发起认证申请 <small>选会员 → 选弓种射距规则版本 → 圈定证据窗口与回合 → 预检后提交</small></div>
        <el-form label-width="92px" label-position="left" class="apply-form">
          <el-form-item label="会员">
            <el-select v-model="applyForm.memberId" filterable placeholder="选择会员" style="width: 240px" @change="resetWindow">
              <el-option v-for="m in members" :key="m.id" :label="`${m.name} · ${m.cardNo}`" :value="m.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="认证规则">
            <el-select v-model="applyForm.ruleId" placeholder="弓种 + 射距（自动取当前版本）" style="width: 320px">
              <el-option
                v-for="r in activeRules"
                :key="r.id"
                :label="`${r.bowTypeName} ${r.distance}米 · ${r.ruleCode} v${r.versionNo}（≥${r.requiredArrows}支 均${r.minAverage}环）`"
                :value="r.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="证据窗口">
            <el-date-picker
              v-model="applyForm.range"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 320px"
              @change="loadWindow"
            />
          </el-form-item>
          <el-form-item label="证据回合" v-if="windowRounds.length">
            <el-checkbox-group v-model="applyForm.roundIds" class="round-pick">
              <el-checkbox
                v-for="r in windowRounds"
                :key="r.roundId"
                :value="r.roundId"
                :disabled="!canPickRound(r)"
                class="round-item"
              >
                <span class="mono">{{ r.roundNo }}</span>
                {{ fmtTime(r.roundDate) }} · {{ r.bowTypeName }} {{ r.distance }}m
                · {{ r.arrowCount }}支 {{ r.totalScore }}环
                <el-tag size="small" :type="r.status === 'SUBMITTED' ? 'success' : 'info'" effect="plain">{{ r.statusName }}</el-tag>
                <em v-if="!canPickRound(r)" class="pick-ban">{{ roundBanReason(r) }}</em>
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label=" " v-if="applyForm.memberId && applyForm.range && !windowRounds.length">
            <span class="muted">该窗口内没有查到该会员的回合。</span>
          </el-form-item>

          <el-form-item v-if="preview" label="预检结果">
            <div class="preview-box" :class="preview.allIncluded ? 'is-ok' : 'is-bad'">
              <div>
                采纳 <b>{{ preview.includedRounds }}</b> 个回合 ·
                <b>{{ preview.includedArrows }}</b> 支箭 ·
                加权平均 <b>{{ preview.includedAverage }}</b> 环 ·
                <el-tag size="small" :type="preview.meetsStandard ? 'success' : 'danger'" effect="dark">
                  {{ preview.meetsStandard ? '达到通过线' : '未达通过线' }}
                </el-tag>
              </div>
              <div class="muted" style="margin-top: 4px">{{ preview.reasonSummary }}</div>
            </div>
          </el-form-item>

          <el-form-item label=" ">
            <el-button :disabled="!canPreview" :loading="previewing" @click="runPreview">按所选规则预检</el-button>
            <el-button type="primary" :disabled="!canSubmitApply" :loading="acting" @click="submitApply">发起认证申请</el-button>
          </el-form-item>
        </el-form>
      </div>
    </template>

    <!-- ============ 规则版本管理 ============ -->
    <template v-if="scope === 'rules'">
      <div class="panel">
        <div class="panel-title">
          认证规则版本
          <small>调整规则会生成新版本并作废旧版；已发认证保留各自的规则快照，历史不改写</small>
          <el-button size="small" type="primary" style="margin-left: auto" @click="openRuleDialog">发布新版本</el-button>
        </div>
        <el-table :data="rules" border>
          <el-table-column prop="ruleCode" label="规则" width="100">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.ruleCode }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="版本" width="70">
            <template #default="{ row }">v{{ row.versionNo }}</template>
          </el-table-column>
          <el-table-column label="弓种 / 射距" width="140">
            <template #default="{ row }">{{ row.bowTypeName }} · {{ row.distance }} 米</template>
          </el-table-column>
          <el-table-column prop="minRounds" label="最少回合" width="90" />
          <el-table-column prop="requiredArrows" label="要求箭数" width="90" />
          <el-table-column prop="minAverage" label="通过线(均环)" width="110" />
          <el-table-column prop="validMonths" label="有效期(月)" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="dark">{{ row.statusName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdBy" label="发布人" width="100" />
          <el-table-column label="发布时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" min-width="160" show-overflow-tooltip />
        </el-table>
      </div>

      <el-dialog v-model="ruleVisible" title="发布认证规则新版本" width="460px">
        <el-form label-width="110px">
          <el-form-item label="弓种">
            <el-select v-model="ruleForm.bowType" style="width: 100%">
              <el-option v-for="b in options.bowTypes || []" :key="b.code" :label="b.name" :value="b.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="射距（米）">
            <el-select v-model="ruleForm.distance" style="width: 100%">
              <el-option v-for="d in options.distances || []" :key="d" :label="`${d} 米`" :value="d" />
            </el-select>
          </el-form-item>
          <el-form-item label="最少回合数"><el-input-number v-model="ruleForm.minRounds" :min="1" :max="20" /></el-form-item>
          <el-form-item label="要求总箭数"><el-input-number v-model="ruleForm.requiredArrows" :min="1" :max="240" /></el-form-item>
          <el-form-item label="通过线(均环)"><el-input-number v-model="ruleForm.minAverage" :min="0" :max="10" :step="0.1" :precision="2" /></el-form-item>
          <el-form-item label="有效期(月)"><el-input-number v-model="ruleForm.validMonths" :min="1" :max="60" /></el-form-item>
          <el-form-item label="调整说明">
            <el-input v-model="ruleForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="ruleVisible = false">取消</el-button>
          <el-button type="primary" @click="submitRule">发布</el-button>
        </template>
      </el-dialog>
    </template>

    <!-- ============ 全部申请（含历史） ============ -->
    <template v-if="scope === 'all'">
      <div class="panel">
        <div class="panel-title">
          全部认证申请
          <small>含已驳回 / 已过期 / 已撤回 / 重新评定历史；点开看详情与规则快照</small>
          <el-select v-model="memberFilter" clearable filterable placeholder="按会员" style="width: 200px; margin-left: auto" @change="loadApplications">
            <el-option v-for="m in members" :key="m.id" :label="`${m.name} · ${m.cardNo}`" :value="m.id" />
          </el-select>
        </div>
        <el-table :data="applications" border @row-click="(row) => (selectedId = row.id)">
          <el-table-column prop="certNo" label="认证编号" width="140" />
          <el-table-column label="会员" width="120">
            <template #default="{ row }">{{ row.memberName }}</template>
          </el-table-column>
          <el-table-column label="弓种射距" width="130">
            <template #default="{ row }">{{ row.bowTypeName }} · {{ row.distance }}m</template>
          </el-table-column>
          <el-table-column label="规则" width="110">
            <template #default="{ row }">{{ row.ruleCode }} v{{ row.versionNo }}</template>
          </el-table-column>
          <el-table-column label="证据" width="130">
            <template #default="{ row }">{{ row.evidenceRounds }}回合 / {{ row.evidenceArrows }}箭 / {{ row.evidenceAverage }}环</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)" effect="dark">{{ row.statusName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="有效期" min-width="190">
            <template #default="{ row }">
              <template v-if="row.validFrom">{{ row.validFrom }} ~ {{ row.validUntil }}</template>
              <span v-else class="muted">—</span>
              <el-tag v-if="row.effective" size="small" type="success" effect="dark" style="margin-left: 6px">有效</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openDetail(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- ============ 补充证据重新评定弹窗 ============ -->
    <el-dialog v-model="resubmitVisible" title="补充证据并重新评定" width="720px" append-to-body>
      <div class="rule-tip" v-if="detail">
        仍按原规则快照 <b>{{ detail.application.ruleCode }} v{{ detail.application.versionNo }}</b>
        （{{ detail.application.bowTypeName }} {{ detail.application.distance }}米 ·
        ≥{{ detail.application.requiredArrows }}支 · 均{{ detail.application.minAverage }}环）评定。
      </div>
      <el-form label-width="92px" label-position="left">
        <el-form-item label="证据窗口">
          <el-date-picker
            v-model="resubmitForm.range"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 320px"
            @change="loadResubmitWindow"
          />
        </el-form-item>
        <el-form-item label="证据回合" v-if="resubmitRounds.length">
          <el-checkbox-group v-model="resubmitForm.roundIds" class="round-pick">
            <el-checkbox
              v-for="r in resubmitRounds"
              :key="r.roundId"
              :value="r.roundId"
              :disabled="!canPickResubmit(r)"
              class="round-item"
            >
              <span class="mono">{{ r.roundNo }}</span>
              {{ fmtTime(r.roundDate) }} · {{ r.bowTypeName }} {{ r.distance }}m
              · {{ r.arrowCount }}支 {{ r.totalScore }}环
              <el-tag size="small" :type="r.status === 'SUBMITTED' ? 'success' : 'info'" effect="plain">{{ r.statusName }}</el-tag>
              <em v-if="!canPickResubmit(r)" class="pick-ban">{{ resubmitBanReason(r) }}</em>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="预检" v-if="resubmitPreview">
          <div class="preview-box" :class="resubmitPreview.allIncluded ? 'is-ok' : 'is-bad'">
            采纳 <b>{{ resubmitPreview.includedRounds }}</b> 回合 ·
            <b>{{ resubmitPreview.includedArrows }}</b> 支 ·
            平均 <b>{{ resubmitPreview.includedAverage }}</b> 环 ·
            <el-tag size="small" :type="resubmitPreview.meetsStandard ? 'success' : 'danger'" effect="dark">
              {{ resubmitPreview.meetsStandard ? '达到通过线' : '未达通过线（仍可提交由教练复核）' }}
            </el-tag>
            <div class="muted" style="margin-top: 4px">{{ resubmitPreview.reasonSummary }}</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resubmitVisible = false">取消</el-button>
        <el-button :disabled="!canResubmitPreview" @click="runResubmitPreview">预检</el-button>
        <el-button type="primary" :disabled="!canResubmitSubmit" @click="submitResubmit">重新评定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { certApi, memberApi } from '../api'

const scope = ref('workbench')
const operator = ref('李慕白')
const options = ref({ bowTypes: [], distances: [], decisions: [] })
const members = ref([])
const rules = ref([])
const applications = ref([])
const selectedId = ref(null)
const detail = ref(null)
const onlyPending = ref(true)
const acting = ref(false)
const decideNote = ref('')

const applyForm = reactive({ memberId: null, ruleId: null, range: null, roundIds: [] })
const windowRounds = ref([])
const preview = ref(null)
const previewing = ref(false)

const memberFilter = ref(null)
const ruleVisible = ref(false)
const ruleForm = reactive({
  bowType: 'RECURVE', distance: 18, minRounds: 1, requiredArrows: 6, minAverage: 7, validMonths: 12, remark: ''
})

// 补充证据重新评定
const resubmitVisible = ref(false)
const resubmitForm = reactive({ range: null, roundIds: [] })
const resubmitRounds = ref([])
const resubmitPreview = ref(null)

const activeRules = computed(() => rules.value.filter((r) => r.status === 'ACTIVE'))
const shownApplications = computed(() =>
  onlyPending.value
    ? applications.value.filter((a) => ['PENDING_REVIEW', 'NEED_MORE'].includes(a.status))
    : applications.value
)
const pendingCount = computed(() => applications.value.filter((a) => a.status === 'PENDING_REVIEW').length)
const needMoreCount = computed(() => applications.value.filter((a) => a.status === 'NEED_MORE').length)

const canDecide = computed(() =>
  detail.value && detail.application.status === 'PENDING_REVIEW' && operator.value.trim())

const canPreview = computed(() =>
  applyForm.memberId && applyForm.ruleId && applyForm.range && applyForm.range.length === 2 && applyForm.roundIds.length)
const canSubmitApply = computed(() => preview.value && preview.value.allIncluded && operator.value.trim())

const notePlaceholder = computed(() => {
  if (!detail.value) return ''
  if (detail.value.application.systemResult === 'BELOW_STANDARD') {
    return '系统初判未达标：若破格通过必须在此写明依据；驳回 / 要求补证据也请说明原因'
  }
  return '驳回或要求补充证据时必须填写原因；通过可留空'
})

function statusType(status) {
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

function logDot(action) {
  return {
    APPROVE: 'success', REJECT: 'danger', REQUEST_MORE: 'warning', RESUBMIT: 'warning',
    REVOKE: 'danger', EXPIRE: 'info', SUPERSEDE: 'info'
  }[action] || 'primary'
}

function fmtTime(value) {
  return value ? String(value).slice(0, 16) : ''
}

function canPickRound(r) {
  return r.status === 'SUBMITTED'
    && pickedRule.value && r.bowType === pickedRule.value.bowType
}

const pickedRule = computed(() => rules.value.find((r) => r.id === applyForm.ruleId))

function roundBanReason(r) {
  if (r.status !== 'SUBMITTED') return '（未完成，不能作为证据）'
  if (pickedRule.value && r.bowType !== pickedRule.value.bowType) return '（弓种不匹配）'
  return ''
}

function resetWindow() {
  applyForm.range = null
  applyForm.roundIds = []
  windowRounds.value = []
  preview.value = null
}

async function loadWindow() {
  windowRounds.value = []
  applyForm.roundIds = []
  preview.value = null
  if (!applyForm.memberId || !applyForm.range || applyForm.range.length !== 2) return
  windowRounds.value = await certApi.windowRounds(
    applyForm.memberId, applyForm.range[0], applyForm.range[1]
  )
}

async function runPreview() {
  previewing.value = true
  try {
    preview.value = await certApi.preview({
      memberId: applyForm.memberId,
      ruleId: applyForm.ruleId,
      evidenceFrom: applyForm.range[0],
      evidenceTo: applyForm.range[1],
      roundIds: applyForm.roundIds,
      operator: operator.value
    })
  } finally {
    previewing.value = false
  }
}

async function submitApply() {
  await certApi.apply({
    memberId: applyForm.memberId,
    ruleId: applyForm.ruleId,
    evidenceFrom: applyForm.range[0],
    evidenceTo: applyForm.range[1],
    roundIds: applyForm.roundIds,
    operator: operator.value
  })
  ElMessage.success('认证申请已建立')
  applyForm.memberId = null
  applyForm.ruleId = null
  applyForm.range = null
  applyForm.roundIds = []
  windowRounds.value = []
  preview.value = null
  await loadApplications()
}

async function onPick(row) {
  if (!row) return
  await openDetail(row)
}

async function openDetail(row) {
  selectedId.value = row.id
  detail.value = await certApi.detail(row.id)
  decideNote.value = detail.value.application.reviewNote || ''
}

async function submitDecide(decision) {
  const label = { APPROVE: '通过', REJECT: '驳回', REQUEST_MORE: '要求补充证据' }[decision]
  if ((decision === 'REJECT' || decision === 'REQUEST_MORE') && !decideNote.value.trim()) {
    ElMessage.warning(label + '时必须填写原因')
    return
  }
  try {
    await ElMessageBox.confirm(`确认对该申请作出「${label}」决定？`, '复核确认', { type: 'warning' })
  } catch {
    return
  }
  acting.value = true
  try {
    detail.value = await certApi.decide(detail.value.application.id, {
      decision,
      operator: operator.value,
      note: decideNote.value,
      expectedVersion: detail.value.application.rowVersion
    })
    ElMessage.success('复核结论已记录')
    decideNote.value = ''
    await loadApplications()
  } finally {
    acting.value = false
  }
}

async function submitRevoke() {
  try {
    await ElMessageBox.confirm('撤回后该认证立即失效，会员将失去对应适用范围。确认撤回？', '撤回认证', { type: 'warning' })
  } catch {
    return
  }
  acting.value = true
  try {
    detail.value = await certApi.revoke(detail.value.application.id, operator.value)
    ElMessage.success('认证已撤回')
    await loadApplications()
  } finally {
    acting.value = false
  }
}

async function loadApplications() {
  const params = memberFilter.value ? { memberId: memberFilter.value } : {}
  applications.value = await certApi.list(params)
  if (selectedId.value) {
    const hit = applications.value.find((a) => a.id === selectedId.value)
    if (hit) {
      detail.value = await certApi.detail(hit.id)
      decideNote.value = detail.value.application.reviewNote || ''
    }
  }
}

function openRuleDialog() {
  Object.assign(ruleForm, { bowType: 'RECURVE', distance: 18, minRounds: 1, requiredArrows: 6, minAverage: 7, validMonths: 12, remark: '' })
  ruleVisible.value = true
}

function canPickResubmit(r) {
  if (!detail.value) return false
  return r.status === 'SUBMITTED' && r.bowType === detail.value.application.bowType
}

function resubmitBanReason(r) {
  if (r.status !== 'SUBMITTED') return '（未完成，不能作为证据）'
  if (detail.value && r.bowType !== detail.value.application.bowType) return '（弓种不匹配）'
  return ''
}

const canResubmitPreview = computed(
  () => resubmitForm.range && resubmitForm.range.length === 2 && resubmitForm.roundIds.length
)
const canResubmitSubmit = computed(() => resubmitPreview.value && resubmitPreview.value.allIncluded)

async function openResubmit() {
  const a = detail.value.application
  resubmitForm.range = [a.evidenceFrom, a.evidenceTo]
  resubmitForm.roundIds = []
  resubmitRounds.value = []
  resubmitPreview.value = null
  resubmitVisible.value = true
  await loadResubmitWindow()
}

async function loadResubmitWindow() {
  resubmitRounds.value = []
  resubmitForm.roundIds = []
  resubmitPreview.value = null
  if (!detail.value || !resubmitForm.range || resubmitForm.range.length !== 2) return
  resubmitRounds.value = await certApi.windowRounds(
    detail.value.application.memberId, resubmitForm.range[0], resubmitForm.range[1]
  )
}

async function runResubmitPreview() {
  const a = detail.value.application
  // 用同一规则版本做预检：ruleId 指向申请冻结的规则快照
  resubmitPreview.value = await certApi.preview({
    memberId: a.memberId,
    ruleId: a.ruleId,
    evidenceFrom: resubmitForm.range[0],
    evidenceTo: resubmitForm.range[1],
    roundIds: resubmitForm.roundIds,
    operator: operator.value
  })
}

async function submitResubmit() {
  acting.value = true
  try {
    detail.value = await certApi.resubmit(detail.value.application.id, {
      evidenceFrom: resubmitForm.range[0],
      evidenceTo: resubmitForm.range[1],
      roundIds: resubmitForm.roundIds,
      operator: operator.value
    })
    ElMessage.success('已按原规则快照重新评定，回到待复核')
    resubmitVisible.value = false
    await loadApplications()
  } finally {
    acting.value = false
  }
}

async function submitRule() {
  await certApi.publishRule({ ...ruleForm, minAverage: Number(ruleForm.minAverage), operator: operator.value })
  ElMessage.success('规则新版本已发布')
  ruleVisible.value = false
  await loadRules()
}

async function loadRules() {
  rules.value = await certApi.rules()
}

async function refreshAll() {
  await Promise.all([loadRules(), loadApplications()])
  ElMessage.success('已刷新')
}

onMounted(async () => {
  options.value = await certApi.options()
  members.value = await memberApi.list()
  await Promise.all([loadRules(), loadApplications()])
})
</script>

<style scoped>
.board {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 14px;
  align-items: start;
}

.only-active {
  margin-left: auto;
}

.c-warn {
  color: var(--el-color-warning);
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.strong {
  font-size: 15px;
  font-weight: 700;
  margin-right: 8px;
}

.valid-box {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  padding: 8px 12px;
  margin-bottom: 10px;
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  font-size: 13px;
}

.snapshot-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.res-box {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px 12px;
  background: #fafcfc;
}

.res-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}

.kv-row {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
  padding: 2px 0;
  color: var(--ink-mute);
}

.kv-row b {
  color: var(--ink);
}

.decide-box {
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px dashed var(--el-color-warning-light-5);
  border-radius: 10px;
  background: #fffdf5;
}

.decide-actions {
  display: flex;
  gap: 8px;
}

.reject-note {
  margin-top: 8px;
  color: #c62828;
}

.log-line {
  margin-top: 8px;
  padding-left: 4px;
  max-height: 260px;
  overflow: auto;
}

.apply-form {
  max-width: 900px;
}

.round-pick {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.round-item {
  margin-right: 0;
  font-size: 12.5px;
}

.pick-ban {
  color: #c62828;
  font-style: normal;
  margin-left: 4px;
}

.preview-box {
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  width: 100%;
}

.preview-box.is-ok {
  background: #eefaf2;
  border: 1px solid #b3e2c4;
}

.preview-box.is-bad {
  background: #fdeeee;
  border: 1px solid #f3c2c2;
}
</style>
