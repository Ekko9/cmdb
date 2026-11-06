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
  if (!response.ok) {
    if (response.status === 401 && path !== '/auth/login') {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.reload();
    }
    const error = Error(data.message || '请求失败');
    error.status = response.status;
    throw error;
  }
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
        <h2>{{ mode === 'register' ? '申请账号' : '欢迎回来' }}</h2>
        <p class="sub">{{ mode === 'register' ? '注册后由管理员审核启用' : '登录 Orbit CMDB 管理控制台' }}</p>
        <div v-if="loginError" class="error">{{ loginError }}</div>
        <div v-if="loginSuccess || successMessage" class="notice">{{ loginSuccess || successMessage }}</div>
        <div class="field">
          <label>用户名</label>
          <input v-model.trim="loginForm.username" autocomplete="username" minlength="3" maxlength="50" required autofocus>
        </div>
        <div v-if="mode === 'register'" class="field">
          <label>显示名称</label>
          <input v-model.trim="registerForm.displayName" maxlength="100" autocomplete="name">
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="loginForm.password" type="password" :autocomplete="mode === 'register' ? 'new-password' : 'current-password'" :minlength="mode === 'register' ? 8 : null" :maxlength="mode === 'register' ? 72 : null" :pattern="mode === 'register' ? '(?=\\S{8,72}$)(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9])' : null" required>
        </div>
        <button class="primary" type="submit" style="width:100%" :disabled="busy">
          {{ busy ? '请稍候…' : (mode === 'register' ? '提交注册申请' : '进入控制台') }}
        </button>
        <p class="auth-links" v-if="mode === 'login'"><span class="auth-hint">密码遗忘请联系管理员重置</span><button class="link-btn" type="button" @click="mode = 'register'; clearMessages()">注册账号</button></p>
        <p class="auth-hint" v-if="mode === 'register'">密码需为 8 至 72 位，包含大小写字母、数字和特殊符号。新账号默认为停用的只读账号，等待管理员审核启用。</p>
        <p class="auth-links" v-if="mode === 'register'"><button class="link-btn" type="button" @click="mode = 'login'; clearMessages()">返回登录</button></p>
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
        <button type="button" :class="{active:tab==='topology'}" @click="go('topology')">资产拓扑</button>
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
              <select v-if="canWrite" v-model="duplicateStrategy" title="导入重复资产处理策略"><option value="SKIP">重复时跳过</option><option value="MERGE">重复时合并非空字段</option></select>
              <button v-if="canWrite" class="ghost" type="button" @click="selectImportFile">导入 CSV / Excel</button>
              <button v-if="canWrite" class="primary" type="button" @click="open('asset')" :disabled="!projects.length">+ 新建资产</button>
            </div>
          </div>
        </div>
        <div v-if="canWrite" class="selection-toolbar"><span>已选择 {{ selectedAssetIds.length }} 项</span><button class="ghost" type="button" @click="togglePageSelection(!allVisibleAssetsSelected)">{{ allVisibleAssetsSelected ? '取消全选' : '选择本页' }}</button><button class="ghost danger" type="button" @click="deleteSelectedAssets" :disabled="!selectedAssetIds.length">批量删除</button></div>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th v-if="canWrite"><input type="checkbox" aria-label="选择当前页资产" :checked="allVisibleAssetsSelected" @change="togglePageSelection"></th><th @click="setSort('name')">资产名称</th><th @click="setSort('assetType')">资产类型</th><th>项目</th><th @click="setSort('environment')">环境</th><th>内网 IP</th><th>外网 IP</th><th>区域</th><th @click="setSort('status')">运行状态</th><th>生命周期</th><th v-if="canWrite">操作</th></tr></thead>
            <tbody>
              <tr v-for="asset in assets" :key="asset.id">
                <td v-if="canWrite"><input type="checkbox" :aria-label="'选择资产 '+asset.name" :checked="selectedAssetIds.includes(asset.id)" @change="toggleAssetSelection(asset.id)"></td>
                <td><button class="link-btn" type="button" @click="openDetail(asset.id)"><b>{{ asset.name }}</b></button><small class="sub-cell">{{ asset.hostname || '' }}</small></td>
                <td>{{ assetTypeLabel(asset.assetType) }}</td><td>{{ asset.projectName }}</td><td>{{ environmentLabel(asset.environment) }}</td>
                <td>{{ asset.privateIp || '-' }}</td><td>{{ asset.publicIp || '-' }}</td><td>{{ asset.region || '-' }}</td>
                <td><span :class="['pill', statusClass(asset.status)]">{{ statusLabel(asset.status) }}</span></td><td>{{ lifecycleLabel(asset.lifecycleState) }}</td>
                <td v-if="canWrite" class="actions"><button class="ghost action-edit" type="button" @click="open('asset', asset)">编辑</button><button class="ghost danger action-delete" type="button" @click="remove('assets', asset.id, asset.name)">删除</button></td>
              </tr>
              <tr v-if="!assets.length"><td :colspan="canWrite ? 11 : 10" class="empty">没有匹配的资产</td></tr>
            </tbody>
          </table>
        </div>
        <div class="pager"><button class="ghost" type="button" @click="prevAssetPage" :disabled="assetPage.page<=0">上一页</button><span>第 {{ assetPage.page + 1 }} / {{ Math.max(assetPage.totalPages, 1) }} 页，共 {{ assetPage.totalElements }} 条</span><button class="ghost" type="button" @click="nextAssetPage" :disabled="assetPage.page + 1 >= assetPage.totalPages">下一页</button><select v-model.number="assetPage.size" @change="loadAssets"><option :value="10">10 条</option><option :value="20">20 条</option><option :value="50">50 条</option></select></div>
      </section>

      <section v-if="tab==='topology' && !loading" class="panel">
        <div class="section-head"><h3>项目资产拓扑</h3><div class="toolbar"><select v-model="topologyProjectId" @change="loadTopology"><option value="">选择项目</option><option v-for="project in projects" :value="String(project.id)" :key="project.id">{{ project.name }}</option></select><button class="ghost" type="button" @click="loadTopology" :disabled="!topologyProjectId">刷新</button></div></div>
        <div v-if="canWrite && topologyProjectId" class="form-grid">
          <div class="field"><label>源资产</label><select v-model="relationDraft.sourceId"><option v-for="asset in topology.nodes" :value="asset.id" :key="asset.id">{{ asset.name }}</option></select></div>
          <div class="field"><label>目标资产</label><select v-model="relationDraft.targetId"><option v-for="asset in topology.nodes" :value="asset.id" :key="asset.id">{{ asset.name }}</option></select></div>
          <div class="field"><label>关系类型</label><select v-model="relationDraft.type"><option value="DEPENDS_ON">依赖</option><option value="HOSTED_ON">运行于</option><option value="CONNECTS_TO">连接</option><option value="CONTAINS">包含</option><option value="BACKUP_OF">备份自</option></select></div>
          <div class="field"><label>说明</label><input v-model.trim="relationDraft.description" maxlength="500"></div>
          <div class="field"><label>&nbsp;</label><button class="primary" type="button" @click="createRelation">添加关系</button></div>
        </div>
        <div class="table-wrap"><table class="table"><thead><tr><th>资产</th><th>类型</th><th>运行状态</th><th>生命周期</th><th>内网 IP</th></tr></thead><tbody>
          <tr v-for="node in topology.nodes" :key="node.id"><td><b>{{ node.name }}</b></td><td>{{ assetTypeLabel(node.assetType) }}</td><td>{{ statusLabel(node.status) }}</td><td>{{ lifecycleLabel(node.lifecycleState) }}</td><td>{{ node.privateIp || '-' }}</td></tr>
          <tr v-if="!topology.nodes.length"><td colspan="5" class="empty">选择项目查看资产节点</td></tr>
        </tbody></table></div>
        <div v-if="topology.nodes.length" class="topology-canvas"><svg :viewBox="'0 0 '+topologyLayout.width+' '+topologyLayout.height" role="img" aria-label="项目资产关系图">
          <line v-for="edge in topologyLayout.edges" :key="'line-'+edge.id" :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2" class="topology-edge"></line>
          <text v-for="edge in topologyLayout.edges" :key="'label-'+edge.id" :x="(edge.x1+edge.x2)/2" :y="(edge.y1+edge.y2)/2-8" class="topology-edge-label">{{ relationLabel(edge.type) }}</text>
          <g v-for="node in topologyLayout.nodes" :key="'node-'+node.id" class="topology-node"><rect :x="node.x" :y="node.y" width="172" height="58" rx="4"></rect><text :x="node.x+12" :y="node.y+23" class="topology-node-name">{{ node.name }}</text><text :x="node.x+12" :y="node.y+43" class="topology-node-meta">{{ assetTypeLabel(node.assetType) }} · {{ lifecycleLabel(node.lifecycleState) }}</text></g>
        </svg></div>
        <h4>关系</h4>
        <div class="table-wrap"><table class="table"><thead><tr><th>源资产</th><th>关系</th><th>目标资产</th><th>说明</th><th v-if="canWrite">操作</th></tr></thead><tbody>
          <tr v-for="edge in topology.edges" :key="edge.id"><td>{{ nodeName(edge.sourceId) }}</td><td>{{ relationLabel(edge.type) }}</td><td>{{ nodeName(edge.targetId) }}</td><td>{{ edge.description || '-' }}</td><td v-if="canWrite"><button class="ghost danger" type="button" @click="removeRelation(edge)">删除</button></td></tr>
          <tr v-if="!topology.edges.length"><td :colspan="canWrite ? 5 : 4" class="empty">暂无资产关系</td></tr>
        </tbody></table></div>
      </section>

      <section v-if="tab==='imports' && canWrite && !loading" class="panel">
        <div class="section-head"><h3>导入审计</h3><div class="toolbar"><button class="primary" type="button" @click="selectImportFile">导入 CSV / Excel</button><button class="ghost" type="button" @click="loadImports">刷新</button></div></div>
        <div v-if="previewResult" class="notice"><b>预检：共 {{ previewResult.totalRows }} 行，可处理 {{ previewResult.validRows }} 行，失败 {{ previewResult.failedRows }} 行；重复策略 {{ previewResult.duplicateStrategy==='MERGE' ? '合并非空字段' : '跳过' }}。</b>
          <div v-for="error in previewResult.errors" :key="error.rowNumber">第 {{ error.rowNumber }} 行：{{ error.reason }}</div>
          <button class="primary" type="button" @click="confirmImport" :disabled="importing || !previewFile">确认导入</button><button class="ghost" type="button" @click="clearImportPreview">取消</button>
        </div>
        <div class="table-wrap"><table class="table">
          <thead><tr><th>文件名</th><th>类型</th><th>状态</th><th>总行数</th><th>成功</th><th>失败</th><th>操作人</th><th>时间</th><th>失败行</th></tr></thead>
          <tbody>
            <tr v-for="item in imports" :key="item.id"><td><b>{{ item.filename }}</b></td><td>{{ item.fileType }}</td><td>{{ importStatusLabel(item.status) }}</td><td>{{ item.totalRows }}</td><td>{{ item.successCount }}</td><td>{{ item.failedCount }}</td><td>{{ item.operator || '-' }}</td><td>{{ formatTime(item.createdAt) }}</td><td><button v-if="item.failedCount" class="ghost" type="button" @click="openImportFailures(item)">修正 {{ item.failedCount }} 行</button><button v-if="item.failedCount" class="ghost" type="button" @click="downloadImportFailures(item.id)">下载</button><span v-if="!item.failedCount">-</span></td></tr>
            <tr v-if="!imports.length"><td colspan="9" class="empty">暂无导入记录</td></tr>
          </tbody>
        </table></div>
        <div v-if="failureImport" class="audit-failures">
          <div class="section-head"><h3>{{ failureImport.filename }} · 失败行修正</h3><button class="ghost" type="button" @click="failureImport=null">关闭</button></div>
          <div class="table-wrap"><table class="table"><thead><tr><th>行号</th><th>失败原因</th><th>原始行</th><th>操作</th></tr></thead><tbody>
            <tr v-for="failure in importFailures" :key="failure.id"><td>{{ failure.rowNumber }}</td><td>{{ failure.reason }}</td><td>{{ failure.rawData }}</td><td><button class="ghost" type="button" @click="editImportFailure(failure)">修正并重试</button></td></tr>
            <tr v-if="!importFailures.length"><td colspan="4" class="empty">失败行已全部处理</td></tr>
          </tbody></table></div>
        </div>
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
        <div class="section-head audit-head"><div><h3>操作审计日志</h3><p class="import-hint">按条件筛选、导出或查看历史归档记录。</p></div><div class="audit-actions"><label class="audit-scope"><span>日志范围</span><select v-model="auditFilters.archived" @change="searchAudits"><option :value="false">当前日志</option><option :value="true">已归档日志</option></select></label><button class="ghost" type="button" @click="downloadAudits">导出 CSV</button><button class="ghost" type="button" @click="archiveAudits">归档 1 年前记录</button><button class="ghost" type="button" @click="loadAudits">刷新</button></div></div>
        <div class="form-grid audit-filter-grid">
          <div class="field"><label>操作人</label><input v-model.trim="auditFilters.operator"></div>
          <div class="field"><label>动作</label><select v-model="auditFilters.action"><option value="">全部动作</option><option value="CREATE">新增</option><option value="UPDATE">编辑</option><option value="DELETE">删除</option><option value="IMPORT">导入</option><option value="IMPORT_RETRY">导入失败行重试</option><option value="LIFECYCLE">生命周期流转</option><option value="CREATE_RELATION">创建资产关系</option><option value="DELETE_RELATION">删除资产关系</option><option value="ARCHIVE">审计归档</option><option value="REGISTER">用户注册</option><option value="CHANGE_PASSWORD">修改密码</option></select></div>
          <div class="field"><label>资源类型</label><select v-model="auditFilters.resourceType"><option value="">全部资源</option><option value="ASSET">资产</option><option value="ASSET_RELATION">资产关系</option><option value="PROJECT">项目</option><option value="USER">用户</option><option value="ACCOUNT">账号</option><option value="AUDIT">审计日志</option></select></div>
          <div class="field"><label>结果</label><select v-model="auditFilters.result"><option value="">全部结果</option><option value="SUCCESS">成功</option><option value="PARTIAL">部分成功</option><option value="FAILED">失败</option><option value="PENDING">待审核</option></select></div>
          <div class="field"><label>关键词</label><input v-model.trim="auditFilters.keyword" placeholder="名称、ID、说明或 IP"></div>
          <div class="field"><label>起始时间</label><input type="datetime-local" v-model="auditFilters.from"></div>
          <div class="field"><label>结束时间</label><input type="datetime-local" v-model="auditFilters.to"></div>
          <div class="field"><label>&nbsp;</label><button class="primary" type="button" @click="searchAudits">筛选</button></div>
        </div>
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

    <div v-if="showModal" class="modal-back" @click.self="!passwordExpired && close()">
      <form class="modal" @submit.prevent="save">
        <div class="section-head">
          <h3>{{ modalType==='password' ? '修改密码' : modalType==='importFailure' ? '修正导入失败行' : (editing ? '编辑' : '新建') + (modalType==='asset' ? '资产' : modalType==='project' ? '项目' : '用户') }}</h3>
          <button v-if="!passwordExpired" class="ghost close" type="button" @click="close">×</button>
        </div>
        <div v-if="passwordExpired" class="notice error">密码已超过 3 个月有效期，请先修改密码后继续使用。</div>
        <div class="form-grid">
          <template v-if="modalType==='password'">
            <div class="field wide"><label>当前密码 *</label><input type="password" v-model="form.currentPassword" autocomplete="current-password" required></div>
            <div class="field"><label>新密码 *</label><input type="password" v-model="form.newPassword" minlength="8" maxlength="72" pattern="(?=\\S{8,72}$)(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9])" autocomplete="new-password" required></div>
            <div class="field"><label>确认新密码 *</label><input type="password" v-model="form.confirmPassword" minlength="8" maxlength="72" autocomplete="new-password" required></div>
            <p class="import-hint wide">密码需为 8 至 72 位，必须包含大写字母、小写字母、数字和特殊符号。</p>
          </template>
          <template v-else-if="modalType==='asset'">
            <div class="field"><label>资产名称 *</label><input v-model.trim="form.name" maxlength="100" required></div>
            <div class="field"><label>资产类型</label><select v-model="form.assetType"><option value="">请选择</option><option v-if="form.assetType && !assetTypes.includes(form.assetType)" :value="form.assetType">{{ assetTypeLabel(form.assetType) }}</option><option v-for="type in assetTypes" :value="type" :key="type">{{ assetTypeLabel(type) }}</option></select></div>
            <div class="field"><label>项目 *</label><select v-model="form.projectId" required><option v-for="project in projects" :value="project.id" :key="project.id">{{ project.name }}</option></select></div>
            <div class="field"><label>环境</label><select v-model="form.environment"><option value="PRODUCTION">生产环境</option><option value="STAGING">预发布环境</option><option value="DEVELOPMENT">开发环境</option></select></div>
            <div class="field"><label>状态</label><select v-model="form.status"><option value="ONLINE">在线</option><option value="OFFLINE">离线</option><option value="MAINTENANCE">维护中</option></select></div>
            <div v-if="!editing" class="field"><label>初始生命周期</label><select v-model="form.lifecycleState"><option value="PLANNED">规划中</option><option value="IN_SERVICE">在用</option></select></div>
            <div class="field"><label>洲</label><select v-model="regionDraft.continent" @change="onRegionContinentChange"><option value="">请选择</option><option v-for="continent in regionContinents" :value="continent" :key="continent">{{ continent }}</option></select></div>
            <div class="field"><label>国家 / 地区</label><select v-model="regionDraft.country" @change="onRegionCountryChange" :disabled="!regionDraft.continent"><option value="">请选择</option><option v-for="country in regionCountries" :value="country" :key="country">{{ country }}</option></select></div>
            <div class="field"><label>城市 / 区域</label><select v-model="form.region" :disabled="!regionDraft.country"><option value="">请选择</option><option v-if="form.region && !knownRegionValues.includes(form.region)" :value="form.region">{{ form.region }}</option><option v-for="item in regionCities" :value="item.label" :key="item.id">{{ item.region }}</option></select></div>
            <div class="field"><label>内网 IP</label><input v-model.trim="form.privateIp" maxlength="45"></div>
            <div class="field"><label>外网 IP</label><input v-model.trim="form.publicIp" maxlength="45"></div>
            <div class="field wide"><label>主机名</label><input v-model.trim="form.hostname" maxlength="100"></div>
            <div class="field wide"><label>描述</label><textarea v-model.trim="form.description" maxlength="500" rows="3"></textarea></div>
          </template>
          <template v-else-if="modalType==='importFailure'">
            <div class="field"><label>资产名称 *</label><input v-model.trim="form.name" required></div>
            <div class="field"><label>资产类型</label><select v-model="form.assetType"><option value="">请选择</option><option v-for="type in assetTypes" :value="type" :key="type">{{ assetTypeLabel(type) }}</option></select></div>
            <div class="field"><label>项目 *</label><select v-model="form.projectName" required><option v-for="project in projects" :value="project.name" :key="project.id">{{ project.name }}</option></select></div>
            <div class="field"><label>环境</label><select v-model="form.environment"><option value="">保持不变</option><option value="PRODUCTION">生产环境</option><option value="STAGING">预发布环境</option><option value="DEVELOPMENT">开发环境</option></select></div>
            <div class="field"><label>内网 IP</label><input v-model.trim="form.privateIp"></div><div class="field"><label>外网 IP</label><input v-model.trim="form.publicIp"></div>
            <div class="field"><label>主机名</label><input v-model.trim="form.hostname"></div><div class="field"><label>运行状态</label><select v-model="form.status"><option value="">保持不变</option><option value="ONLINE">在线</option><option value="OFFLINE">离线</option><option value="MAINTENANCE">维护中</option></select></div>
            <div class="field wide"><label>区域</label><input v-model.trim="form.region"></div><div class="field wide"><label>描述</label><textarea v-model.trim="form.description" rows="3"></textarea></div>
          </template>
          <template v-else-if="modalType==='project'">
            <div class="field"><label>项目名称 *</label><input v-model.trim="form.name" maxlength="100" required></div>
            <div class="field"><label>项目编码</label><input v-model.trim="form.code" maxlength="30" placeholder="唯一编码"></div>
            <div class="field wide"><label>负责人</label><input v-model.trim="form.owner" maxlength="50"></div>
            <div class="field wide"><label>描述</label><textarea v-model.trim="form.description" maxlength="500" rows="3"></textarea></div>
          </template>
          <template v-else>
            <div class="field"><label>用户名 *</label><input v-model.trim="form.username" minlength="3" maxlength="50" pattern="[A-Za-z0-9._-]{3,50}" :disabled="!!editing" required></div>
            <div class="field"><label>显示名称</label><input v-model.trim="form.displayName" maxlength="100"></div>
            <div class="field"><label>{{ editing ? '重置密码' : '密码' }}{{ editing ? '（填写时生效）' : ' *' }}</label><input type="password" v-model="form.password" minlength="8" maxlength="72" pattern="(?=\\S{8,72}$)(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9])" :required="!editing"></div>
            <div class="field"><label>角色</label><select v-model="form.role"><option value="OPERATOR">运维人员</option><option value="ADMIN">管理员</option><option value="VIEWER">只读用户</option></select></div>
            <div v-if="editing" class="field"><label>状态</label><select v-model="form.enabled"><option :value="true">启用</option><option :value="false">停用</option></select></div>
          </template>
        </div>
        <div class="modal-actions"><button v-if="!passwordExpired" class="ghost" type="button" @click="close">取消</button><button class="primary" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button></div>
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
          <div><small>生命周期</small><b>{{ lifecycleLabel(detailAsset.lifecycleState) }}</b></div>
          <div><small>内网 IP</small><b>{{ detailAsset.privateIp || '-' }}</b></div>
          <div><small>外网 IP</small><b>{{ detailAsset.publicIp || '-' }}</b></div>
          <div><small>主机名</small><b>{{ detailAsset.hostname || '-' }}</b></div>
          <div><small>区域</small><b>{{ detailAsset.region || '-' }}</b></div>
        </div>
        <p class="detail-desc">{{ detailAsset.description || '暂无描述' }}</p>
        <div v-if="canWrite" class="toolbar"><select v-model="lifecycleTarget"><option v-for="state in lifecycleOptions(detailAsset.lifecycleState)" :value="state" :key="state">{{ lifecycleLabel(state) }}</option></select><input v-model.trim="lifecycleReason" maxlength="500" placeholder="流转原因"><button class="primary" type="button" @click="changeLifecycle(detailAsset)">流转状态</button></div>
        <h4>生命周期记录</h4><div class="timeline"><div v-for="item in lifecycleHistory" :key="item.id" class="timeline-item"><b>{{ lifecycleLabel(item.fromState) }} → {{ lifecycleLabel(item.toState) }}</b><span>{{ formatTime(item.createdAt) }} · {{ item.operator || '-' }}</span><small>{{ item.reason || '未填写原因' }}</small></div><div v-if="!lifecycleHistory.length" class="empty">暂无生命周期记录</div></div>
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
  template: `<component :is="isLogin ? 'main-app' : 'login-form'" :login-form="loginForm" :login-error="loginError" :login-success="loginSuccess"></component>`,
  data() {
    return {
      token: localStorage.token || '',
      user: JSON.parse(localStorage.user || 'null'),
      loginForm: { username: '', password: '' },
      loginError: '',
      loginSuccess: ''
    };
  },
  computed: { isLogin() { return Boolean(this.token && this.user); } },
  components: {
    'login-form': {
      template: loginTemplate,
      props: { loginForm: Object, loginError: String, loginSuccess: String },
      data() { return { busy: false, mode: 'login', successMessage: '', registerForm: { displayName: '' } }; },
      methods: {
        clearMessages() { this.$root.loginError = ''; this.$root.loginSuccess = ''; this.successMessage = ''; },
        async login() {
          this.busy = true;
          this.$root.loginError = '';
          this.$root.loginSuccess = '';
          try {
            if (this.mode === 'register') {
              const data = await api('/auth/register', { method: 'POST', body: JSON.stringify({ ...this.registerForm, username: this.loginForm.username, password: this.loginForm.password }) });
              this.successMessage = data.message;
              this.mode = 'login';
              this.loginForm.password = '';
              this.registerForm.displayName = '';
              return;
            }
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
          selectedAssetIds: [],
          assetPage: { page: 0, size: 10, totalElements: 0, totalPages: 0, sort: 'updatedAt', direction: 'desc' },
          imports: [], importFailures: [], audits: [], detailAsset: null, lifecycleHistory: [], lifecycleTarget: '', lifecycleReason: '',
          topologyProjectId: '', topology: { nodes: [], edges: [] }, relationDraft: { sourceId: '', targetId: '', type: 'DEPENDS_ON', description: '' },
          duplicateStrategy: 'SKIP', previewFile: null, previewResult: null, failureImport: null,
          auditFilters: { operator: '', action: '', resourceType: '', result: '', keyword: '', from: '', to: '', archived: false },
          importPage: { page: 0, size: 10, totalElements: 0, totalPages: 0 },
          auditPage: { page: 0, size: 5, totalElements: 0, totalPages: 0 },
          regionDraft: { continent: '', country: '' },
          showModal: false, modalType: '', editing: null, form: {}, loading: false, saving: false,
          passwordExpired: Boolean(this.$root.user && this.$root.user.passwordExpired),
          notice: '', noticeType: 'success', importing: false
        };
      },
      computed: {
        title() { return { overview: '资产总览', assets: '资产清单', topology: '资产拓扑', projects: '项目空间', imports: '导入审计', audits: '操作审计', users: '用户与权限' }[this.tab]; },
        user() { return this.$root.user || {}; },
        userInitial() { return (this.user.displayName || this.user.username || 'U').substring(0, 1); },
        canManageUsers() { return this.user.role === 'ADMIN'; },
        canWrite() { return this.user.role === 'ADMIN' || this.user.role === 'OPERATOR'; },
        allVisibleAssetsSelected() { return this.assets.length > 0 && this.assets.every(asset => this.selectedAssetIds.includes(asset.id)); },
        regionContinents() { return [...new Set(this.regions.map(item => item.continent).filter(Boolean))]; },
        regionCountries() {
          return [...new Set(this.regions.filter(item => item.continent === this.regionDraft.continent).map(item => item.country).filter(Boolean))];
        },
        regionCities() {
          return this.regions.filter(item => item.continent === this.regionDraft.continent && item.country === this.regionDraft.country);
        },
        topologyLayout() {
          const columns = 4, cellWidth = 210, cellHeight = 100, width = Math.max(220, Math.min(this.topology.nodes.length, columns) * cellWidth), rows = Math.ceil(this.topology.nodes.length / columns);
          const nodes = this.topology.nodes.map((node, index) => ({ ...node, x: (index % columns) * cellWidth + 12, y: Math.floor(index / columns) * cellHeight + 12 }));
          const byId = new Map(nodes.map(node => [node.id, node]));
          const edges = this.topology.edges.map(edge => { const source = byId.get(edge.sourceId), target = byId.get(edge.targetId); return source && target ? { ...edge, x1: source.x + 86, y1: source.y + 58, x2: target.x + 86, y2: target.y } : null; }).filter(Boolean);
          return { nodes, edges, width, height: Math.max(90, rows * cellHeight + 20) };
        },
        knownRegionValues() { return this.regions.map(item => item.label); },
      },
      mounted() {
        if (this.user.passwordChangeRecommended && !this.user.passwordExpired) {
          this.notice = `密码将在 ${this.user.passwordDaysRemaining} 天内到期，请及时修改。`;
        }
        if (this.passwordExpired) this.open('password');
        this.load();
      },
      methods: {
        assetTypeLabel(value) { return { SERVER: '服务器', DATABASE: '数据库', NETWORK: '网络设备', STORAGE: '存储设备', APPLICATION: '应用系统', OTHER: '其他' }[value] || value || '-'; },
        environmentLabel(value) { return { PRODUCTION: '生产环境', STAGING: '预发布环境', DEVELOPMENT: '开发环境' }[value] || value || '-'; },
        statusLabel(value) { return { ONLINE: '在线', OFFLINE: '离线', MAINTENANCE: '维护中' }[value] || value || '-'; },
        roleLabel(value) { return { ADMIN: '管理员', OPERATOR: '运维人员', VIEWER: '只读用户' }[value] || value || '-'; },
        importStatusLabel(value) { return { SUCCESS: '成功', PARTIAL: '部分成功', FAILED: '失败', RUNNING: '处理中' }[value] || value || '-'; },
        lifecycleLabel(value) { return { PLANNED: '规划中', IN_SERVICE: '在用', RETIRING: '退役中', RETIRED: '已退役' }[value] || value || '-'; },
        lifecycleOptions(value) { return { PLANNED: ['IN_SERVICE', 'RETIRED'], IN_SERVICE: ['RETIRING'], RETIRING: ['IN_SERVICE', 'RETIRED'], RETIRED: [] }[value] || []; },
        relationLabel(value) { return { DEPENDS_ON: '依赖', HOSTED_ON: '运行于', CONNECTS_TO: '连接', CONTAINS: '包含', BACKUP_OF: '备份自' }[value] || value; },
        nodeName(id) { const node = this.topology.nodes.find(item => item.id === id); return node ? node.name : `#${id}`; },
        actionLabel(value) { return { CREATE: '新增', UPDATE: '编辑', DELETE: '删除', IMPORT: '导入', IMPORT_RETRY: '导入失败行重试', LIFECYCLE: '生命周期流转', CREATE_RELATION: '创建资产关系', DELETE_RELATION: '删除资产关系', ARCHIVE: '审计归档', REGISTER: '用户注册', CHANGE_PASSWORD: '修改密码' }[value] || value || '-'; },
        resourceLabel(value) { return { ASSET: '资产', ASSET_RELATION: '资产关系', PROJECT: '项目', USER: '用户', ACCOUNT: '账号', AUDIT: '审计日志' }[value] || value || '-'; },
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
            if (this.tab === 'topology' && this.topologyProjectId) await this.loadTopology();
          } catch (error) {
            if (error.status === 428 || (this.user.passwordExpired && error.status === 401)) {
              this.passwordExpired = true;
              if (!(this.showModal && this.modalType === 'password')) this.open('password');
            } else this.notify(error.message, 'error');
          } finally {
            this.loading = false;
          }
        },
        async go(tab) {
          if (tab === 'users' && !this.canManageUsers) return;
          if (tab === 'audits' && !this.canManageUsers) return;
          if (tab === 'imports' && !this.canWrite) return;
          this.tab = tab;
          if (tab === 'topology' && !this.topologyProjectId && this.projects.length) this.topologyProjectId = String(this.projects[0].id);
          await this.load();
        },
        async loadAssets() {
          this.selectedAssetIds = [];
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
          Object.entries(this.auditFilters).forEach(([key, value]) => { if (value !== '' && value !== null && value !== undefined) params.set(key, value); });
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
          this.lifecycleHistory = await api('/assets/' + id + '/lifecycle/history');
          const options = this.lifecycleOptions(this.detailAsset.lifecycleState);
          this.lifecycleTarget = options[0] || '';
          this.lifecycleReason = '';
        },
        async changeLifecycle(asset) {
          if (!this.lifecycleTarget) return;
          try {
            await api('/assets/' + asset.id + '/lifecycle', { method: 'PUT', body: JSON.stringify({ state: this.lifecycleTarget, reason: this.lifecycleReason }) });
            this.notify('生命周期已更新'); await this.openDetail(asset.id); await this.loadAssets();
          } catch (error) { this.notify(error.message, 'error'); }
        },
        async loadTopology() {
          if (!this.topologyProjectId) { this.topology = { nodes: [], edges: [] }; return; }
          try { this.topology = await api('/assets/topology?projectId=' + encodeURIComponent(this.topologyProjectId)); }
          catch (error) { this.notify(error.message, 'error'); }
        },
        async createRelation() {
          if (!this.relationDraft.sourceId || !this.relationDraft.targetId) return this.notify('请选择关系两端资产', 'error');
          try {
            await api('/assets/' + this.relationDraft.sourceId + '/relations', { method: 'POST', body: JSON.stringify({ targetId: this.relationDraft.targetId, type: this.relationDraft.type, description: this.relationDraft.description }) });
            this.relationDraft.description = ''; this.notify('资产关系已添加'); await this.loadTopology();
          } catch (error) { this.notify(error.message, 'error'); }
        },
        async removeRelation(edge) {
          if (!confirm('确认删除这条资产关系？')) return;
          try { await api('/assets/' + edge.sourceId + '/relations/' + edge.id, { method: 'DELETE' }); this.notify('关系已删除'); await this.loadTopology(); }
          catch (error) { this.notify(error.message, 'error'); }
        },
        async searchAudits() { this.auditPage.page = 0; await this.loadAudits(); },
        auditQuery() {
          const params = new URLSearchParams();
          Object.entries(this.auditFilters).forEach(([key, value]) => { if (value !== '' && value !== null && value !== undefined) params.set(key, value); });
          return '?' + params.toString();
        },
        async downloadAudits() { await this.downloadFile('/audits/export' + this.auditQuery(), 'cmdb-operation-audits.csv', '审计记录已导出'); },
        async archiveAudits() {
          if (!confirm('将归档 1 年以前的审计记录？归档记录仍可在“已归档日志”范围中检索和导出。')) return;
          const cutoff = new Date(); cutoff.setFullYear(cutoff.getFullYear() - 1);
          const localCutoff = `${cutoff.getFullYear()}-${String(cutoff.getMonth() + 1).padStart(2, '0')}-${String(cutoff.getDate()).padStart(2, '0')}T${String(cutoff.getHours()).padStart(2, '0')}:${String(cutoff.getMinutes()).padStart(2, '0')}:${String(cutoff.getSeconds()).padStart(2, '0')}`;
          try { const result = await api('/audits/archive?before=' + encodeURIComponent(localCutoff), { method: 'POST' }); this.notify(`已归档 ${result.archivedCount} 条记录`); await this.loadAudits(); }
          catch (error) { this.notify(error.message, 'error'); }
        },
        async openImportFailures(item) {
          this.failureImport = item;
          try { this.importFailures = await api('/assets/imports/' + item.id + '/failures'); }
          catch (error) { this.notify(error.message, 'error'); }
        },
        editImportFailure(failure) {
          this.editing = { id: failure.id };
          this.form = { ...(failure.asset || {}), status: (failure.asset && failure.asset.status) || '' };
          this.modalType = 'importFailure'; this.showModal = true;
        },
        async searchAssets() {
          this.assetPage.page = 0;
          await this.loadAssets();
        },
        toggleAssetSelection(id) {
          this.selectedAssetIds = this.selectedAssetIds.includes(id)
            ? this.selectedAssetIds.filter(selectedId => selectedId !== id)
            : [...this.selectedAssetIds, id];
        },
        togglePageSelection(event) {
          const select = typeof event === 'boolean' ? event : Boolean(event && event.target && event.target.checked);
          const pageIds = this.assets.map(asset => asset.id);
          this.selectedAssetIds = select
            ? [...new Set([...this.selectedAssetIds, ...pageIds])]
            : this.selectedAssetIds.filter(id => !pageIds.includes(id));
        },
        async deleteSelectedAssets() {
          const ids = [...this.selectedAssetIds];
          if (!ids.length) return;
          if (!confirm(`确认删除选中的 ${ids.length} 项资产？此操作不可撤销。`)) return;
          try {
            const result = await api('/assets/batch', { method: 'DELETE', body: JSON.stringify(ids) });
            this.selectedAssetIds = [];
            this.notify(`已删除 ${result.deletedCount} 项资产`);
            if (this.assets.length === ids.length && this.assetPage.page > 0) this.assetPage.page--;
            await this.load();
          } catch (error) { this.notify(error.message, 'error'); }
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
          localStorage.removeItem('token');
          localStorage.removeItem('user');
          this.$root.token = '';
          this.$root.user = null;
          this.$root.loginForm = { username: '', password: '' };
          this.$root.loginError = '';
        },
        open(type, item) {
          this.modalType = type;
          this.editing = item || null;
          if (type === 'password') {
            this.form = { currentPassword: '', newPassword: '', confirmPassword: '' };
          } else if (type === 'asset') {
            this.form = item ? { ...item } : { status: 'ONLINE', lifecycleState: 'IN_SERVICE', environment: 'PRODUCTION', projectId: this.projects[0] && this.projects[0].id };
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
              localStorage.removeItem('token');
              localStorage.removeItem('user');
              this.$root.loginForm = { username: '', password: '' };
              this.$root.loginError = '';
              this.$root.loginSuccess = '密码修改成功，请使用新密码重新登录';
              this.$root.token = '';
              this.$root.user = null;
              return;
            }
            if (this.modalType === 'importFailure') {
              await api('/assets/imports/' + this.failureImport.id + '/failures/' + this.editing.id + '/retry?duplicateStrategy=MERGE', { method: 'PUT', body: JSON.stringify(this.form) });
              this.close(); this.notify('修正成功，失败行已重新导入'); this.importFailures = await api('/assets/imports/' + this.failureImport.id + '/failures'); await this.load(); return;
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
        async previewImport(event) {
          const file = event.target.files[0];
          event.target.value = '';
          if (!file) return;
          this.importing = true;
          try {
            const data = new FormData();
            data.append('file', file);
            this.previewResult = await api('/assets/import/validate?duplicateStrategy=' + this.duplicateStrategy, { method: 'POST', body: data });
            this.previewFile = file;
            this.tab = 'imports';
            await this.loadImports();
            this.notify('预检完成，请确认结果后导入', this.previewResult.failedRows ? 'error' : 'success');
          } catch (error) {
            this.notify(error.message, 'error');
          } finally {
            this.importing = false;
          }
        },
        selectImportFile() {
          if (!this.canWrite) return;
          const input = document.createElement('input');
          input.type = 'file';
          input.accept = '.csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
          input.addEventListener('change', this.previewImport, { once: true });
          input.click();
        },
        async confirmImport() {
          if (!this.previewFile) return;
          this.importing = true;
          try {
            const data = new FormData(); data.append('file', this.previewFile);
            const result = await api('/assets/import?duplicateStrategy=' + this.duplicateStrategy, { method: 'POST', body: data });
            this.notify(`导入 ${result.count} 条，失败 ${result.skipped} 条`, result.skipped ? 'error' : 'success');
            this.clearImportPreview(); await this.load();
          } catch (error) { this.notify(error.message, 'error'); }
          finally { this.importing = false; }
        },
        clearImportPreview() { this.previewFile = null; this.previewResult = null; },
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
            if (response.status === 401) {
              localStorage.removeItem('token'); localStorage.removeItem('user'); window.location.reload(); return;
            }
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
