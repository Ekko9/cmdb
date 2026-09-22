const { createApp } = Vue;

const api = async (path, options = {}) => {
  const headers = {
    ...(localStorage.token ? { Authorization: 'Bearer ' + localStorage.token } : {}),
    ...(options.headers || {})
  };
  if (options.body !== undefined && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }
  const response = await fetch('/api' + path, { ...options, headers });
  if (response.status === 204) return null;
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw Error(data.message || '请求失败');
  return data;
};

const loginTemplate = `
  <div class="login">
    <section class="login-art">
      <div class="eyebrow">ORBIT / CMDB</div>
      <h1>Know your<br>infrastructure.</h1>
      <p>让每一项资产都有归属、状态和清晰的生命周期。从项目到 IP，一处掌握整个基础设施版图。</p>
    </section>
    <section class="login-panel">
      <form class="login-card" @submit.prevent="login">
        <h2>欢迎回来</h2>
        <p class="sub">登录 Orbit CMDB 管理控制台</p>
        <div v-if="loginError" class="error">{{ loginError }}</div>
        <div class="field">
          <label>用户名</label>
          <input v-model.trim="loginForm.username" autocomplete="username" autofocus>
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="loginForm.password" type="password" autocomplete="current-password">
        </div>
        <button class="primary" type="submit" style="width:100%" :disabled="busy">
          {{ busy ? '登录中…' : '进入控制台' }}
        </button>
      </form>
    </section>
  </div>`;

const appTemplate = `
  <div class="shell">
    <aside class="side">
      <div class="brand">orbit<span>.</span></div>
      <nav class="nav">
        <button type="button" :class="{active:tab==='overview'}" @click="go('overview')">总览</button>
        <button type="button" :class="{active:tab==='assets'}" @click="go('assets')">资产清单</button>
        <button type="button" :class="{active:tab==='projects'}" @click="go('projects')">项目空间</button>
        <button v-if="canWrite" type="button" :class="{active:tab==='imports'}" @click="go('imports')">导入审计</button>
        <button v-if="canManageUsers" type="button" :class="{active:tab==='audits'}" @click="go('audits')">操作审计</button>
        <button v-if="canManageUsers" type="button" :class="{active:tab==='users'}" @click="go('users')">用户权限</button>
      </nav>
      <div class="side-foot">CMDB · v1.0.0</div>
    </aside>

    <main class="main">
      <header class="top">
        <h1>{{ title }}</h1>
        <div class="user">
          <div class="avatar">{{ userInitial }}</div>
          <span>{{ user.displayName }}</span>
          <button class="ghost" type="button" @click="open('password')">修改密码</button>
          <button class="ghost" type="button" @click="logout">退出</button>
        </div>
      </header>

      <div v-if="notice" :class="['notice', noticeType]">{{ notice }}</div>
      <div v-if="loading" class="loading">正在加载…</div>

      <section v-if="tab==='overview' && !loading">
        <div class="metrics">
          <div class="metric"><small>项目总数</small><strong>{{ stats.projects || 0 }}</strong></div>
          <div class="metric"><small>资产总数</small><strong>{{ stats.assets || 0 }}</strong></div>
          <div class="metric"><small>在线资产</small><strong>{{ stats.online || 0 }}</strong></div>
          <div class="metric"><small>生产环境</small><strong>{{ stats.production || 0 }}</strong></div>
        </div>
        <div class="panel">
          <div class="section-head">
            <h3>最近资产</h3>
            <button class="ghost" type="button" @click="go('assets')">查看全部 →</button>
          </div>
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>名称</th><th>项目</th><th>内网 IP</th><th>外网 IP</th><th>状态</th></tr></thead>
              <tbody>
                <tr v-for="asset in assets.slice(0, 8)" :key="asset.id">
                  <td><b>{{ asset.name }}</b></td><td>{{ asset.projectName }}</td>
                  <td>{{ asset.privateIp || '-' }}</td><td>{{ asset.publicIp || '-' }}</td>
                  <td><span :class="['pill', statusClass(asset.status)]">{{ statusLabel(asset.status) }}</span></td>
                </tr>
                <tr v-if="!assets.length"><td colspan="5" class="empty">还没有资产</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section v-if="tab==='assets' && !loading" class="panel">
        <div class="section-head">
          <div>
            <h3>全部资产</h3>
            <p class="import-hint">导入支持 CSV 和 Excel（.xlsx）。Excel 模板支持下拉选择，CSV 文件不支持下拉列表。</p>
          </div>
          <div class="asset-toolbar">
            <div class="search-box">
              <input v-model.trim="filters.keyword" placeholder="搜索内网 IP / 外网 IP" @keyup.enter="searchAssets">
              <select v-model="filters.projectId" @change="searchAssets"><option value="">全部项目</option><option v-for="project in projects" :value="String(project.id)" :key="project.id">{{ project.name }}</option></select>
              <button class="primary" type="button" @click="searchAssets">搜索</button>
            </div>
            <div class="toolbar">
              <button class="ghost" type="button" @click="downloadTemplate">下载 Excel 模板</button>
              <button class="ghost" type="button" @click="downloadCsv">导出 CSV</button>
              <button class="ghost" type="button" @click="downloadExcel">导出 Excel</button>
              <label v-if="canWrite" class="ghost file-btn">导入 CSV / Excel<input type="file" accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" @change="importFile"></label>
              <button v-if="canWrite" class="primary" type="button" @click="open('asset')" :disabled="!projects.length">+ 新建资产</button>
            </div>
          </div>
        </div>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th @click="setSort('name')">资产名称</th><th @click="setSort('assetType')">资产类型</th><th>项目</th><th @click="setSort('environment')">环境</th><th>内网 IP</th><th>外网 IP</th><th>区域</th><th @click="setSort('status')">状态</th><th v-if="canWrite">操作</th></tr></thead>
            <tbody>
              <tr v-for="asset in assets" :key="asset.id">
                <td><button class="link-btn" type="button" @click="openDetail(asset.id)"><b>{{ asset.name }}</b></button><small class="sub-cell">{{ asset.hostname || '' }}</small></td>
                <td>{{ assetTypeLabel(asset.assetType) }}</td><td>{{ asset.projectName }}</td><td>{{ environmentLabel(asset.environment) }}</td>
                <td>{{ asset.privateIp || '-' }}</td><td>{{ asset.publicIp || '-' }}</td><td>{{ asset.region || '-' }}</td>
                <td><span :class="['pill', statusClass(asset.status)]">{{ statusLabel(asset.status) }}</span></td>
                <td v-if="canWrite" class="actions"><button class="ghost action-edit" type="button" @click="open('asset', asset)">编辑</button><button class="ghost danger action-delete" type="button" @click="remove('assets', asset.id, asset.name)">删除</button></td>
              </tr>
              <tr v-if="!assets.length"><td colspan="9" class="empty">没有匹配的资产</td></tr>
            </tbody>
          </table>
        </div>
        <div class="pager"><button class="ghost" type="button" @click="prevAssetPage" :disabled="assetPage.page<=0">上一页</button><span>第 {{ assetPage.page + 1 }} / {{ Math.max(assetPage.totalPages, 1) }} 页，共 {{ assetPage.totalElements }} 条</span><button class="ghost" type="button" @click="nextAssetPage" :disabled="assetPage.page + 1 >= assetPage.totalPages">下一页</button><select v-model.number="assetPage.size" @change="loadAssets"><option :value="10">10 条</option><option :value="20">20 条</option><option :value="50">50 条</option></select></div>
      </section>

      <section v-if="tab==='imports' && canWrite && !loading" class="panel">
        <div class="section-head"><h3>导入审计</h3><button class="ghost" type="button" @click="loadImports">刷新</button></div>
        <div class="table-wrap"><table class="table">
          <thead><tr><th>文件名</th><th>类型</th><th>状态</th><th>总行数</th><th>成功</th><th>失败</th><th>操作人</th><th>时间</th><th>失败行</th></tr></thead>
          <tbody>
            <tr v-for="item in imports" :key="item.id"><td><b>{{ item.filename }}</b></td><td>{{ item.fileType }}</td><td>{{ importStatusLabel(item.status) }}</td><td>{{ item.totalRows }}</td><td>{{ item.successCount }}</td><td>{{ item.failedCount }}</td><td>{{ item.operator || '-' }}</td><td>{{ formatTime(item.createdAt) }}</td><td><button v-if="item.failedCount" class="ghost" type="button" @click="downloadImportFailures(item.id)">下载</button><span v-else>-</span></td></tr>
            <tr v-if="!imports.length"><td colspan="9" class="empty">暂无导入记录</td></tr>
          </tbody>
        </table></div>
        <div class="pager">
          <button class="ghost" type="button" @click="prevImportPage" :disabled="importPage.page<=0">上一页</button>
          <span>第 {{ importPage.page + 1 }} / {{ Math.max(importPage.totalPages, 1) }} 页，共 {{ importPage.totalElements }} 条</span>
          <button class="ghost" type="button" @click="nextImportPage" :disabled="importPage.page + 1 >= importPage.totalPages">下一页</button>
          <select v-model.number="importPage.size" @change="changeImportPageSize">
            <option :value="5">5 条</option><option :value="10">10 条</option><option :value="20">20 条</option><option :value="50">50 条</option>
          </select>
        </div>
      </section>

      <section v-if="tab==='audits' && canManageUsers && !loading" class="panel">
        <div class="section-head"><h3>操作审计日志</h3><button class="ghost" type="button" @click="loadAudits">刷新</button></div>
        <div class="table-wrap"><table class="table">
          <thead><tr><th>时间</th><th>操作人</th><th>角色</th><th>动作</th><th>资源</th><th>结果</th><th>来源 IP</th><th>说明</th></tr></thead>
          <tbody>
            <tr v-for="item in audits" :key="item.id"><td>{{ formatTime(item.createdAt) }}</td><td>{{ item.operator || '-' }}</td><td>{{ roleLabel(item.role) }}</td><td>{{ actionLabel(item.action) }}</td><td>{{ resourceLabel(item.resourceType) }} / {{ item.resourceName || item.resourceId || '-' }}</td><td>{{ item.result }}</td><td>{{ item.clientIp || '-' }}</td><td>{{ item.message || '-' }}</td></tr>
            <tr v-if="!audits.length"><td colspan="8" class="empty">暂无审计日志</td></tr>
          </tbody>
        </table></div>
        <div class="pager">
          <button class="ghost" type="button" @click="prevAuditPage" :disabled="auditPage.page<=0">上一页</button>
          <span>第 {{ auditPage.page + 1 }} / {{ Math.max(auditPage.totalPages, 1) }} 页，共 {{ auditPage.totalElements }} 条</span>
          <button class="ghost" type="button" @click="nextAuditPage" :disabled="auditPage.page + 1 >= auditPage.totalPages">下一页</button>
          <select v-model.number="auditPage.size" @change="changeAuditPageSize">
            <option :value="5">5 条</option><option :value="10">10 条</option><option :value="20">20 条</option><option :value="50">50 条</option>
          </select>
        </div>
      </section>

      <section v-if="tab==='projects' && !loading" class="panel">
        <div class="section-head"><h3>项目空间</h3><button v-if="canWrite" class="primary" type="button" @click="open('project')">+ 新建项目</button></div>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>项目名称</th><th>编码</th><th>负责人</th><th>资产数</th><th v-if="canWrite">操作</th></tr></thead>
            <tbody>
              <tr v-for="project in projects" :key="project.id">
                <td><b>{{ project.name }}</b></td><td>{{ project.code || '-' }}</td><td>{{ project.owner || '-' }}</td>
                <td>{{ project.assetCount }}</td>
                <td v-if="canWrite" class="actions"><button class="ghost action-edit" type="button" @click="open('project', project)">编辑</button><button class="ghost danger action-delete" type="button" @click="remove('projects', project.id, project.name)">删除</button></td>
              </tr>
              <tr v-if="!projects.length"><td colspan="5" class="empty">还没有项目空间</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-if="tab==='users' && canManageUsers && !loading" class="panel">
        <div class="section-head"><h3>用户与权限</h3><button class="primary" type="button" @click="open('user')">+ 新建用户</button></div>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>用户名</th><th>显示名称</th><th>角色</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in users" :key="item.id">
                <td><b>{{ item.username }}</b></td><td>{{ item.displayName }}</td><td>{{ roleLabel(item.role) }}</td><td>{{ item.enabled ? '启用' : '停用' }}</td>
                <td class="actions"><button class="ghost action-edit" type="button" @click="open('user', item)">编辑</button><button v-if="item.username.toLowerCase() !== 'admin'" class="ghost danger action-delete" type="button" @click="remove('users', item.id, item.displayName || item.username)">删除</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </main>

    <div v-if="showModal" class="modal-back" @click.self="close">
      <form class="modal" @submit.prevent="save">
        <div class="section-head">
          <h3>{{ modalType==='password' ? '修改密码' : (editing ? '编辑' : '新建') + (modalType==='asset' ? '资产' : modalType==='project' ? '项目' : '用户') }}</h3>
          <button class="ghost close" type="button" @click="close">×</button>
        </div>
        <div class="form-grid">
          <template v-if="modalType==='password'">
            <div class="field wide"><label>当前密码 *</label><input type="password" v-model="form.currentPassword" autocomplete="current-password" required></div>
            <div class="field"><label>新密码 *</label><input type="password" v-model="form.newPassword" minlength="8" pattern="(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,}" autocomplete="new-password" required></div>
            <div class="field"><label>确认新密码 *</label><input type="password" v-model="form.confirmPassword" minlength="8" autocomplete="new-password" required></div>
            <p class="import-hint wide">密码至少 8 位，必须包含大写字母、小写字母、数字和特殊符号。</p>
          </template>
          <template v-else-if="modalType==='asset'">
            <div class="field"><label>资产名称 *</label><input v-model.trim="form.name" required></div>
            <div class="field"><label>资产类型</label><select v-model="form.assetType"><option value="">请选择</option><option v-if="form.assetType && !assetTypes.includes(form.assetType)" :value="form.assetType">{{ assetTypeLabel(form.assetType) }}</option><option v-for="type in assetTypes" :value="type" :key="type">{{ assetTypeLabel(type) }}</option></select></div>
            <div class="field"><label>项目 *</label><select v-model="form.projectId" required><option v-for="project in projects" :value="project.id" :key="project.id">{{ project.name }}</option></select></div>
            <div class="field"><label>环境</label><select v-model="form.environment"><option value="PRODUCTION">生产环境</option><option value="STAGING">预发布环境</option><option value="DEVELOPMENT">开发环境</option></select></div>
            <div class="field"><label>状态</label><select v-model="form.status"><option value="ONLINE">在线</option><option value="OFFLINE">离线</option><option value="MAINTENANCE">维护中</option></select></div>
            <div class="field"><label>洲</label><select v-model="regionDraft.continent" @change="onRegionContinentChange"><option value="">请选择</option><option v-for="continent in regionContinents" :value="continent" :key="continent">{{ continent }}</option></select></div>
            <div class="field"><label>国家 / 地区</label><select v-model="regionDraft.country" @change="onRegionCountryChange" :disabled="!regionDraft.continent"><option value="">请选择</option><option v-for="country in regionCountries" :value="country" :key="country">{{ country }}</option></select></div>
            <div class="field"><label>城市 / 区域</label><select v-model="form.region" :disabled="!regionDraft.country"><option value="">请选择</option><option v-if="form.region && !knownRegionValues.includes(form.region)" :value="form.region">{{ form.region }}</option><option v-for="item in regionCities" :value="item.label" :key="item.id">{{ item.region }}</option></select></div>
            <div class="field"><label>内网 IP</label><input v-model.trim="form.privateIp"></div>
            <div class="field"><label>外网 IP</label><input v-model.trim="form.publicIp"></div>
            <div class="field wide"><label>主机名</label><input v-model.trim="form.hostname"></div>
            <div class="field wide"><label>描述</label><textarea v-model.trim="form.description" rows="3"></textarea></div>
          </template>
          <template v-else-if="modalType==='project'">
            <div class="field"><label>项目名称 *</label><input v-model.trim="form.name" required></div>
            <div class="field"><label>项目编码</label><input v-model.trim="form.code" placeholder="唯一编码"></div>
            <div class="field wide"><label>负责人</label><input v-model.trim="form.owner"></div>
            <div class="field wide"><label>描述</label><textarea v-model.trim="form.description" rows="3"></textarea></div>
          </template>
          <template v-else>
            <div class="field"><label>用户名 *</label><input v-model.trim="form.username" :disabled="!!editing" required></div>
            <div class="field"><label>显示名称</label><input v-model.trim="form.displayName"></div>
            <div class="field"><label>{{ editing ? '重置密码' : '密码' }}{{ editing ? '（填写时生效）' : ' *' }}</label><input type="password" v-model="form.password" minlength="8" pattern="(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,}" :required="!editing"></div>
            <div class="field"><label>角色</label><select v-model="form.role"><option value="OPERATOR">运维人员</option><option value="ADMIN">管理员</option><option value="VIEWER">只读用户</option></select></div>
            <div v-if="editing" class="field"><label>状态</label><select v-model="form.enabled"><option :value="true">启用</option><option :value="false">停用</option></select></div>
          </template>
        </div>
        <div class="modal-actions"><button class="ghost" type="button" @click="close">取消</button><button class="primary" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button></div>
      </form>
    </div>

    <div v-if="detailAsset" class="drawer-back" @click.self="detailAsset=null">
      <aside class="drawer">
        <div class="section-head"><h3>{{ detailAsset.name }}</h3><button class="ghost close" type="button" @click="detailAsset=null">×</button></div>
        <div class="detail-grid">
          <div><small>项目</small><b>{{ detailAsset.projectName }}</b></div>
          <div><small>类型</small><b>{{ assetTypeLabel(detailAsset.assetType) }}</b></div>
          <div><small>环境</small><b>{{ environmentLabel(detailAsset.environment) }}</b></div>
          <div><small>状态</small><b>{{ statusLabel(detailAsset.status) }}</b></div>
          <div><small>内网 IP</small><b>{{ detailAsset.privateIp || '-' }}</b></div>
          <div><small>外网 IP</small><b>{{ detailAsset.publicIp || '-' }}</b></div>
          <div><small>主机名</small><b>{{ detailAsset.hostname || '-' }}</b></div>
          <div><small>区域</small><b>{{ detailAsset.region || '-' }}</b></div>
        </div>
        <p class="detail-desc">{{ detailAsset.description || '暂无描述' }}</p>
        <h4>变更记录</h4>
        <div class="timeline">
          <div v-for="change in detailAsset.changes || []" :key="change.id" class="timeline-item">
            <b>{{ changeTypeLabel(change.changeType) }} {{ change.fieldName ? fieldLabel(change.fieldName) : '' }}</b>
            <span>{{ formatTime(change.createdAt) }} · {{ change.operator || '-' }}</span>
            <small v-if="change.fieldName">{{ change.oldValue || '-' }} → {{ change.newValue || '-' }}</small>
          </div>
          <div v-if="!detailAsset.changes || !detailAsset.changes.length" class="empty">暂无变更记录</div>
        </div>
      </aside>
    </div>
  </div>`;

createApp({
  template: `<component :is="isLogin ? 'main-app' : 'login-form'" :login-form="loginForm" :login-error="loginError"></component>`,
  data() {
    return {
      token: localStorage.token || '',
      user: JSON.parse(localStorage.user || 'null'),
      loginForm: { username: '', password: '' },
      loginError: ''
    };
  },
  computed: { isLogin() { return Boolean(this.token && this.user); } },
  components: {
    'login-form': {
      template: loginTemplate,
      props: { loginForm: Object, loginError: String },
      data() { return { busy: false }; },
      methods: {
        async login() {
          this.busy = true;
          this.$root.loginError = '';
          try {
            const data = await api('/auth/login', { method: 'POST', body: JSON.stringify(this.loginForm) });
            this.$root.token = data.token;
            this.$root.user = data.user;
            localStorage.token = data.token;
            localStorage.user = JSON.stringify(data.user);
          } catch (error) {
            this.$root.loginError = error.message;
          } finally {
            this.busy = false;
          }
        }
      }
    },
    'main-app': {
      template: appTemplate,
      data() {
        return {
          tab: 'overview', stats: {}, projects: [], assets: [], users: [], assetTypes: [], regions: [],
          filters: { keyword: '', projectId: '' },
          assetPage: { page: 0, size: 10, totalElements: 0, totalPages: 0, sort: 'updatedAt', direction: 'desc' },
          imports: [], audits: [], detailAsset: null,
          importPage: { page: 0, size: 10, totalElements: 0, totalPages: 0 },
          auditPage: { page: 0, size: 5, totalElements: 0, totalPages: 0 },
          regionDraft: { continent: '', country: '' },
          showModal: false, modalType: '', editing: null, form: {}, loading: false, saving: false,
          notice: '', noticeType: 'success', importing: false
        };
      },
      computed: {
        title() { return { overview: '资产总览', assets: '资产清单', projects: '项目空间', imports: '导入审计', audits: '操作审计', users: '用户与权限' }[this.tab]; },
        user() { return this.$root.user || {}; },
        userInitial() { return (this.user.displayName || this.user.username || 'U').substring(0, 1); },
        canManageUsers() { return this.user.role === 'ADMIN'; },
        canWrite() { return this.user.role === 'ADMIN' || this.user.role === 'OPERATOR'; },
        regionContinents() { return [...new Set(this.regions.map(item => item.continent).filter(Boolean))]; },
        regionCountries() {
          return [...new Set(this.regions.filter(item => item.continent === this.regionDraft.continent).map(item => item.country).filter(Boolean))];
        },
        regionCities() {
          return this.regions.filter(item => item.continent === this.regionDraft.continent && item.country === this.regionDraft.country);
        },
        knownRegionValues() { return this.regions.map(item => item.label); },
      },
      mounted() { this.load(); },
      methods: {
        assetTypeLabel(value) { return { SERVER: '服务器', DATABASE: '数据库', NETWORK: '网络设备', STORAGE: '存储设备', APPLICATION: '应用系统', OTHER: '其他' }[value] || value || '-'; },
        environmentLabel(value) { return { PRODUCTION: '生产环境', STAGING: '预发布环境', DEVELOPMENT: '开发环境' }[value] || value || '-'; },
        statusLabel(value) { return { ONLINE: '在线', OFFLINE: '离线', MAINTENANCE: '维护中' }[value] || value || '-'; },
        roleLabel(value) { return { ADMIN: '管理员', OPERATOR: '运维人员', VIEWER: '只读用户' }[value] || value || '-'; },
        importStatusLabel(value) { return { SUCCESS: '成功', PARTIAL: '部分成功', FAILED: '失败', RUNNING: '处理中' }[value] || value || '-'; },
        actionLabel(value) { return { CREATE: '新增', UPDATE: '编辑', DELETE: '删除', IMPORT: '导入', CHANGE_PASSWORD: '修改密码' }[value] || value || '-'; },
        resourceLabel(value) { return { ASSET: '资产', PROJECT: '项目', USER: '用户', ACCOUNT: '账号' }[value] || value || '-'; },
        changeTypeLabel(value) { return { CREATE: '创建', UPDATE: '更新', DELETE: '删除', IMPORT: '导入' }[value] || value || '-'; },
        fieldLabel(value) { return { name: '名称', assetType: '类型', environment: '环境', privateIp: '内网 IP', publicIp: '外网 IP', hostname: '主机名', status: '状态', region: '区域', description: '描述', projectName: '项目' }[value] || value; },
        formatTime(value) { return value ? String(value).replace('T', ' ').substring(0, 19) : '-'; },
        statusClass(value) { return value === 'OFFLINE' ? 'off' : value === 'MAINTENANCE' ? 'maintenance' : ''; },
        async load() {
          this.loading = true;
          try {
            const [stats, projects, assetTypes, regions] = await Promise.all([api('/dashboard'), api('/projects'), api('/assets/types'), api('/assets/regions')]);
            this.stats = stats;
            this.projects = projects;
            this.assetTypes = assetTypes;
            this.regions = regions;
            await this.loadAssets();
            if (this.tab === 'users' && this.canManageUsers) this.users = await api('/users');
            if (this.tab === 'imports' && this.canWrite) await this.loadImports();
            if (this.tab === 'audits' && this.canManageUsers) await this.loadAudits();
          } catch (error) {
            this.notify(error.message, 'error');
          } finally {
            this.loading = false;
          }
        },
        async go(tab) {
          if (tab === 'users' && !this.canManageUsers) return;
          if (tab === 'audits' && !this.canManageUsers) return;
          if (tab === 'imports' && !this.canWrite) return;
          this.tab = tab;
          await this.load();
        },
        async loadAssets() {
          const params = new URLSearchParams();
          Object.entries(this.filters).forEach(([key, value]) => { if (value !== '' && value !== null && value !== undefined) params.set(key, value); });
          params.set('page', this.assetPage.page);
          params.set('size', this.assetPage.size);
          params.set('sort', this.assetPage.sort);
          params.set('direction', this.assetPage.direction);
          const data = await api('/assets?' + params.toString());
          this.assets = data.content || [];
          this.assetPage = { ...this.assetPage, page: data.page || 0, size: data.size || this.assetPage.size, totalElements: data.totalElements || 0, totalPages: data.totalPages || 0 };
        },
        async loadImports() {
          const params = new URLSearchParams({ page: this.importPage.page, size: this.importPage.size });
          const data = await api('/assets/imports?' + params.toString());
          this.imports = data.content || [];
          this.importPage = {
            ...this.importPage,
            page: data.page || 0,
            size: data.size || this.importPage.size,
            totalElements: data.totalElements || 0,
            totalPages: data.totalPages || 0
          };
        },
        async loadAudits() {
          const params = new URLSearchParams({ page: this.auditPage.page, size: this.auditPage.size });
          const data = await api('/audits?' + params.toString());
          this.audits = data.content || [];
          this.auditPage = {
            ...this.auditPage,
            page: data.page || 0,
            size: data.size || this.auditPage.size,
            totalElements: data.totalElements || 0,
            totalPages: data.totalPages || 0
          };
        },
        async openDetail(id) {
          this.detailAsset = await api('/assets/' + id);
        },
        async searchAssets() {
          this.assetPage.page = 0;
          await this.loadAssets();
        },
        async setSort(field) {
          if (this.assetPage.sort === field) this.assetPage.direction = this.assetPage.direction === 'asc' ? 'desc' : 'asc';
          else { this.assetPage.sort = field; this.assetPage.direction = 'asc'; }
          await this.loadAssets();
        },
        async prevAssetPage() { if (this.assetPage.page > 0) { this.assetPage.page--; await this.loadAssets(); } },
        async nextAssetPage() { if (this.assetPage.page + 1 < this.assetPage.totalPages) { this.assetPage.page++; await this.loadAssets(); } },
        async prevImportPage() { if (this.importPage.page > 0) { this.importPage.page--; await this.loadImports(); } },
        async nextImportPage() { if (this.importPage.page + 1 < this.importPage.totalPages) { this.importPage.page++; await this.loadImports(); } },
        async prevAuditPage() { if (this.auditPage.page > 0) { this.auditPage.page--; await this.loadAudits(); } },
        async nextAuditPage() { if (this.auditPage.page + 1 < this.auditPage.totalPages) { this.auditPage.page++; await this.loadAudits(); } },
        async changeImportPageSize() { this.importPage.page = 0; await this.loadImports(); },
        async changeAuditPageSize() { this.auditPage.page = 0; await this.loadAudits(); },
        logout() {
          localStorage.clear();
          this.$root.token = '';
          this.$root.user = null;
        },
        open(type, item) {
          this.modalType = type;
          this.editing = item || null;
          if (type === 'password') {
            this.form = { currentPassword: '', newPassword: '', confirmPassword: '' };
          } else if (type === 'asset') {
            this.form = item ? { ...item } : { status: 'ONLINE', environment: 'PRODUCTION', projectId: this.projects[0] && this.projects[0].id };
            this.syncRegionDraft(this.form.region);
          } else if (type === 'project') {
            this.form = item ? { name: item.name, code: item.code, owner: item.owner, description: item.description } : {};
          } else {
            this.form = item ? { username: item.username, displayName: item.displayName, role: item.role, enabled: item.enabled, password: '' } : { role: 'OPERATOR', password: '' };
          }
          this.showModal = true;
        },
        close() { this.showModal = false; },
        syncRegionDraft(value) {
          const match = this.regions.find(item => item.label === value || item.region === value);
          this.regionDraft = match ? { continent: match.continent, country: match.country } : { continent: '', country: '' };
          if (match) this.form.region = match.label;
        },
        onRegionContinentChange() {
          this.regionDraft.country = '';
          this.form.region = '';
        },
        onRegionCountryChange() {
          this.form.region = '';
        },
        async save() {
          this.saving = true;
          try {
            if (this.modalType === 'password') {
              await api('/account/password', { method: 'PUT', body: JSON.stringify(this.form) });
              this.close();
              this.notify('密码修改成功，请使用新密码登录');
              return;
            }
            const path = this.modalType === 'asset' ? '/assets' : this.modalType === 'project' ? '/projects' : '/users';
            const url = this.editing ? path + '/' + this.editing.id : path;
            await api(url, { method: this.editing ? 'PUT' : 'POST', body: JSON.stringify(this.form) });
            this.close();
            this.notify('保存成功');
            await this.load();
          } catch (error) {
            this.notify(error.message, 'error');
          } finally {
            this.saving = false;
          }
        },
        async remove(type, id, label) {
          if (!confirm(`确认删除“${label || '这条记录'}”吗？删除后数据不可恢复，请再次确认。`)) return;
          try {
            await api('/' + type + '/' + id, { method: 'DELETE' });
            this.notify('删除成功');
            await this.load();
          } catch (error) {
            this.notify(error.message, 'error');
          }
        },
        async importFile(event) {
          const file = event.target.files[0];
          event.target.value = '';
          if (!file) return;
          this.importing = true;
          try {
            const data = new FormData();
            data.append('file', file);
            const result = await api('/assets/import', { method: 'POST', body: data });
            this.notify(`导入 ${result.count} 条，跳过 ${result.skipped} 条${result.errors && result.errors.length ? '，请检查文件' : ''}`, result.skipped ? 'error' : 'success');
            await this.load();
          } catch (error) {
            this.notify(error.message, 'error');
          } finally {
            this.importing = false;
          }
        },
        async downloadCsv() {
          await this.downloadFile('/assets/export' + this.filterQuery(), 'cmdb-assets.csv', 'CSV 已导出');
        },
        async downloadExcel() {
          await this.downloadFile('/assets/export.xlsx' + this.filterQuery(), 'cmdb-assets.xlsx', 'Excel 已导出');
        },
        async downloadTemplate() {
          await this.downloadFile('/assets/template.xlsx', 'cmdb-asset-import-template.xlsx', 'Excel 模板已下载');
        },
        async downloadImportFailures(id) {
          await this.downloadFile('/assets/imports/' + id + '/failures.csv', 'cmdb-import-failures-' + id + '.csv', '失败行已导出');
        },
        filterQuery() {
          const params = new URLSearchParams();
          Object.entries(this.filters).forEach(([key, value]) => {
            if (value !== '' && value !== null && value !== undefined) params.set(key, value);
          });
          const query = params.toString();
          return query ? '?' + query : '';
        },
        async downloadFile(path, filename, message) {
          try {
            const response = await fetch('/api' + path, { headers: { Authorization: 'Bearer ' + localStorage.token } });
            if (!response.ok) throw Error('导出失败');
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = filename;
            link.style.display = 'none';
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
            this.notify(message);
          } catch (error) {
            this.notify(error.message, 'error');
          }
        },
        notify(message, type = 'success') {
          this.notice = message;
          this.noticeType = type;
          window.clearTimeout(this.noticeTimer);
          this.noticeTimer = window.setTimeout(() => { this.notice = ''; }, 3500);
        }
      }
    }
  }
}).mount('#app');
