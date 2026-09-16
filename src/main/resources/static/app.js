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
          <div class="toolbar">
            <input v-model.trim="keyword" placeholder="搜索名称 / IP / 主机名">
            <button class="ghost" type="button" @click="downloadTemplate">下载 Excel 模板</button>
            <button class="ghost" type="button" @click="downloadCsv">导出 CSV</button>
            <button class="ghost" type="button" @click="downloadExcel">导出 Excel</button>
            <label v-if="canWrite" class="ghost file-btn">导入 CSV / Excel<input type="file" accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" @change="importFile"></label>
            <button v-if="canWrite" class="primary" type="button" @click="open('asset')" :disabled="!projects.length">+ 新建资产</button>
          </div>
        </div>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>资产名称</th><th>资产类型</th><th>项目</th><th>环境</th><th>内网 IP</th><th>外网 IP</th><th>状态</th><th v-if="canWrite">操作</th></tr></thead>
            <tbody>
              <tr v-for="asset in filteredAssets" :key="asset.id">
                <td><b>{{ asset.name }}</b><small class="sub-cell">{{ asset.hostname || '' }}</small></td>
                <td>{{ assetTypeLabel(asset.assetType) }}</td><td>{{ asset.projectName }}</td><td>{{ environmentLabel(asset.environment) }}</td>
                <td>{{ asset.privateIp || '-' }}</td><td>{{ asset.publicIp || '-' }}</td>
                <td><span :class="['pill', statusClass(asset.status)]">{{ statusLabel(asset.status) }}</span></td>
                <td v-if="canWrite" class="actions"><button class="ghost action-edit" type="button" @click="open('asset', asset)">编辑</button><button class="ghost danger action-delete" type="button" @click="remove('assets', asset.id, asset.name)">删除</button></td>
              </tr>
              <tr v-if="!filteredAssets.length"><td colspan="8" class="empty">没有匹配的资产</td></tr>
            </tbody>
          </table>
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
            <div class="field"><label>区域</label><input v-model.trim="form.region" placeholder="杭州 / 华东"></div>
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
          tab: 'overview', stats: {}, projects: [], assets: [], users: [], assetTypes: [], keyword: '',
          showModal: false, modalType: '', editing: null, form: {}, loading: false, saving: false,
          notice: '', noticeType: 'success', importing: false
        };
      },
      computed: {
        title() { return { overview: '资产总览', assets: '资产清单', projects: '项目空间', users: '用户与权限' }[this.tab]; },
        user() { return this.$root.user || {}; },
        userInitial() { return (this.user.displayName || this.user.username || 'U').substring(0, 1); },
        canManageUsers() { return this.user.role === 'ADMIN'; },
        canWrite() { return this.user.role === 'ADMIN' || this.user.role === 'OPERATOR'; },
        filteredAssets() {
          const query = this.keyword.toLowerCase();
          return this.assets.filter(asset => !query || [asset.name, asset.privateIp, asset.publicIp, asset.hostname, asset.assetType, this.assetTypeLabel(asset.assetType), this.environmentLabel(asset.environment), this.statusLabel(asset.status)].join(' ').toLowerCase().includes(query));
        }
      },
      mounted() { this.load(); },
      methods: {
        assetTypeLabel(value) { return { SERVER: '服务器', DATABASE: '数据库', NETWORK: '网络设备', STORAGE: '存储设备', APPLICATION: '应用系统', OTHER: '其他' }[value] || value || '-'; },
        environmentLabel(value) { return { PRODUCTION: '生产环境', STAGING: '预发布环境', DEVELOPMENT: '开发环境' }[value] || value || '-'; },
        statusLabel(value) { return { ONLINE: '在线', OFFLINE: '离线', MAINTENANCE: '维护中' }[value] || value || '-'; },
        roleLabel(value) { return { ADMIN: '管理员', OPERATOR: '运维人员', VIEWER: '只读用户' }[value] || value || '-'; },
        statusClass(value) { return value === 'OFFLINE' ? 'off' : value === 'MAINTENANCE' ? 'maintenance' : ''; },
        async load() {
          this.loading = true;
          try {
            const [stats, projects, assets, assetTypes] = await Promise.all([api('/dashboard'), api('/projects'), api('/assets'), api('/assets/types')]);
            this.stats = stats;
            this.projects = projects;
            this.assets = assets;
            this.assetTypes = assetTypes;
            if (this.tab === 'users' && this.canManageUsers) this.users = await api('/users');
          } catch (error) {
            this.notify(error.message, 'error');
          } finally {
            this.loading = false;
          }
        },
        async go(tab) {
          if (tab === 'users' && !this.canManageUsers) return;
          this.tab = tab;
          await this.load();
        },
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
          } else if (type === 'project') {
            this.form = item ? { name: item.name, code: item.code, owner: item.owner, description: item.description } : {};
          } else {
            this.form = item ? { username: item.username, displayName: item.displayName, role: item.role, enabled: item.enabled, password: '' } : { role: 'OPERATOR', password: '' };
          }
          this.showModal = true;
        },
        close() { this.showModal = false; },
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
          await this.downloadFile('/assets/export', 'cmdb-assets.csv', 'CSV 已导出');
        },
        async downloadExcel() {
          await this.downloadFile('/assets/export.xlsx', 'cmdb-assets.xlsx', 'Excel 已导出');
        },
        async downloadTemplate() {
          await this.downloadFile('/assets/template.xlsx', 'cmdb-asset-import-template.xlsx', 'Excel 模板已下载');
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
