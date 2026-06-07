/* ── i18n ────────────────────────────────────────────────────────── */
const LANG = {
  zh: {
    title: 'Catgirl Downloader',
    settings: '设置',
    language: '语言',
    source: '图片来源',
    nsfw: 'NSFW 过滤',
    nsfw_block: '屏蔽 NSFW',
    nsfw_only: '仅 NSFW',
    nsfw_all: '全部显示',
    danbooru_tags: 'Danbooru 标签',
    tags_placeholder: '例如：cat_ears solo 1girl',
    auto_reload: '自动刷新',
    on: '开',
    off: '关',
    sec: '秒',
    new_image: '新图片',
    save: '保存',
    placeholder: '点击「新图片」开始！',
    loading: '加载中...',
    about_art: '作品信息',
    artist: '艺术家',
    artist_unknown: '未知',
    source_label: '来源',
    open_page: '打开页面 ↗',
    filename: '文件名',
    about_app: '关于',
    about_desc: '浏览猫娘、Waifu 和 Danbooru 图片的网页应用。',
    about_credit: '基于 <strong>Catgirl Downloader</strong> by NyarchLinux',
    about_link: '在 GitHub 上查看 ↗',
    credit: 'Catgirl Downloader Web',
    error_load: '图片加载失败，请重试！',
    source_names: { catgirl: 'Catgirl (nekos.moe)',
                    waifu: 'Waifu (waifu.im)',
                    danbooru: 'Danbooru' },
  },
  en: {
    title: 'Catgirl Downloader',
    settings: 'Settings',
    language: 'Language / 语言',
    source: 'Source',
    nsfw: 'NSFW Filter',
    nsfw_block: 'Block NSFW',
    nsfw_only: 'Only NSFW',
    nsfw_all: 'Show All',
    danbooru_tags: 'Danbooru Tags',
    tags_placeholder: 'e.g. cat_ears solo 1girl',
    auto_reload: 'Auto Reload',
    on: 'On',
    off: 'Off',
    sec: 'sec',
    new_image: 'New Image',
    save: 'Save',
    placeholder: 'Click <strong>New Image</strong> to start!',
    loading: 'Loading...',
    about_art: 'About This Art',
    artist: 'Artist',
    artist_unknown: 'Unknown',
    source_label: 'Source',
    open_page: 'Open page ↗',
    filename: 'Filename',
    about_app: 'About',
    about_desc: 'A web-based image browser for catgirl, waifu, and Danbooru artwork.',
    about_credit: 'Based on <strong>Catgirl Downloader</strong> by NyarchLinux',
    about_link: 'View on GitHub ↗',
    credit: 'Catgirl Downloader Web',
    error_load: 'Could not load image. Try again!',
    source_names: { catgirl: 'Catgirl (nekos.moe)',
                    waifu: 'Waifu (waifu.im)',
                    danbooru: 'Danbooru' },
  },
};

/* ── State ──────────────────────────────────────────────────────── */
const state = {
  lang: 'zh',
  source: 'catgirl',
  nsfw: 'BLOCK_NSFW',
  autoReload: false,
  reloadInterval: 30,
  danbooruTags: '',
  currentKey: null,
  currentArtist: null,
  currentLink: null,
  currentFilename: null,
  isFetching: false,
  reloadTimer: null,
  progressTimer: null,
  progressStart: 0,
};

/* ── DOM refs ──────────────────────────────────────────────────── */
const $ = id => document.getElementById(id);

const sidebar = $('sidebar');
const sidebarOverlay = $('sidebarOverlay');
const menuBtn = $('menuBtn');
const sidebarClose = $('sidebarClose');
const langSelect = $('langSelect');
const sourceSelect = $('sourceSelect');
const nsfwSelect = $('nsfwSelect');
const tagsGroup = $('tagsGroup');
const tagsInput = $('tagsInput');
const autoToggle = $('autoReloadToggle');
const autoLabel = $('autoLabel');
const intervalInput = $('reloadInterval');
const refreshBtn = $('refreshBtn');
const downloadBtn = $('downloadBtn');
const mainImage = $('mainImage');
const placeholder = $('placeholder');
const placeholderText = $('placeholderText');
const spinner = $('spinner');
const imageInfo = $('imageInfo');
const artistName = $('artistName');
const sourceName = $('sourceName');
const artInfoBtn = $('artInfoBtn');
const progressBar = $('progressBar');
const progressFill = $('progressFill');

const artModal = $('artModal');
const artModalClose = $('artModalClose');
const modalArtist = $('modalArtist');
const modalLink = $('modalLink');
const modalFilename = $('modalFilename');
const aboutBtn = $('aboutBtn');
const aboutModal = $('aboutModal');
const aboutModalClose = $('aboutModalClose');

/* ── i18n apply ─────────────────────────────────────────────────── */
function t(key) { return LANG[state.lang][key] || LANG.en[key] || key; }

function applyLang() {
  const l = state.lang;
  const m = LANG[l];

  document.documentElement.lang = l;
  document.title = m.title;

  $('titleText').textContent = m.title;
  $('settingsTitle').textContent = m.settings;
  $('langLabel').textContent = m.language;
  $('srcLabel').textContent = m.source;
  $('nsfwLabel').textContent = m.nsfw;
  $('tagsLabel').textContent = m.danbooru_tags;
  tagsInput.placeholder = m.tags_placeholder;
  $('reloadLabel').textContent = m.auto_reload;
  autoLabel.textContent = state.autoReload ? m.on : m.off;
  $('unitLabel').textContent = m.sec;
  $('refreshText').textContent = m.new_image;
  $('saveText').textContent = m.save;
  placeholderText.innerHTML = m.placeholder;
  $('loadingText').textContent = m.loading;
  $('artModalTitle').textContent = m.about_art;
  $('artistLabel').textContent = m.artist;
  $('srcInfoLabel').textContent = m.source_label;
  $('fileLabel').textContent = m.filename;
  $('aboutModalTitle').textContent = m.about_app;
  $('aboutDesc').textContent = m.about_desc;
  $('aboutCredit').innerHTML = m.about_credit;
  $('aboutLink').textContent = m.about_link;
  $('creditText').textContent = m.credit;

  // Source options
  sourceSelect.innerHTML = '';
  ['catgirl', 'waifu', 'danbooru'].forEach(key => {
    const opt = document.createElement('option');
    opt.value = key;
    opt.textContent = m.source_names[key];
    sourceSelect.appendChild(opt);
  });
  sourceSelect.value = state.source;

  // NSFW options
  nsfwSelect.innerHTML = '';
  [
    ['BLOCK_NSFW', m.nsfw_block],
    ['ONLY_NSFW', m.nsfw_only],
    ['SHOW_EVERYTHING', m.nsfw_all],
  ].forEach(([val, label]) => {
    const opt = document.createElement('option');
    opt.value = val;
    opt.textContent = label;
    nsfwSelect.appendChild(opt);
  });
  nsfwSelect.value = state.nsfw;

  // Modal links
  if (state.currentArtist) {
    modalArtist.textContent = state.currentArtist;
  }
}

/* ── Sidebar ───────────────────────────────────────────────────── */
function openSidebar() {
  sidebar.classList.add('open');
  sidebarOverlay.classList.remove('hidden');
}

function closeSidebar() {
  sidebar.classList.remove('open');
  sidebarOverlay.classList.add('hidden');
}

menuBtn.addEventListener('click', openSidebar);
sidebarClose.addEventListener('click', closeSidebar);
sidebarOverlay.addEventListener('click', closeSidebar);

/* ── API helpers ───────────────────────────────────────────────── */
async function api(url, opts = {}) {
  const res = await fetch(url, opts);
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(err.error || `HTTP ${res.status}`);
  }
  return res;
}

async function apiJson(url, opts = {}) {
  const res = await api(url, opts);
  return res.json();
}

/* ── UI ─────────────────────────────────────────────────────────── */
function showLoading(show) {
  spinner.classList.toggle('hidden', !show);
  if (show) {
    mainImage.classList.add('hidden');
    placeholder.classList.add('hidden');
  }
}

function showImage(src) {
  mainImage.src = src;
  mainImage.classList.remove('hidden');
  placeholder.classList.add('hidden');
  spinner.classList.add('hidden');
}

function showInfo(artist, source) {
  if (artist) {
    artistName.textContent = artist;
    imageInfo.classList.remove('hidden');
  } else {
    imageInfo.classList.add('hidden');
  }
  const names = t('source_names');
  sourceName.textContent = names[source] || source;
}

/* ── Image fetch ────────────────────────────────────────────────── */
async function fetchImage() {
  if (state.isFetching) return;
  state.isFetching = true;
  refreshBtn.disabled = true;

  stopProgressBar();
  showLoading(true);

  try {
    const params = new URLSearchParams({ source: state.source, nsfw: state.nsfw });
    const data = await apiJson(`/api/fetch?${params}`);

    state.currentKey = data.key;
    state.currentArtist = data.artist;
    state.currentLink = data.link;
    state.currentFilename = data.filename;

    showImage(`/api/image/${data.key}`);
    showInfo(data.artist, data.source);
    downloadBtn.disabled = false;
  } catch {
    showLoading(false);
    placeholder.classList.remove('hidden');
    placeholderText.innerHTML = t('error_load');
  } finally {
    state.isFetching = false;
    refreshBtn.disabled = false;
    if (state.autoReload) scheduleNextReload();
  }
}

/* ── Download ───────────────────────────────────────────────────── */
function downloadImage() {
  if (!state.currentKey) return;
  const a = document.createElement('a');
  a.href = `/api/download/${state.currentKey}`;
  a.download = state.currentFilename || 'image';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}

/* ── Auto-reload ────────────────────────────────────────────────── */
function scheduleNextReload() {
  cancelPendingReload();
  if (!state.autoReload) return;
  startProgressBar();
  state.reloadTimer = setTimeout(fetchImage, state.reloadInterval * 1000);
}

function cancelPendingReload() {
  if (state.reloadTimer !== null) {
    clearTimeout(state.reloadTimer);
    state.reloadTimer = null;
  }
}

function startAutoReload() {
  stopAutoReload();
  if (!state.autoReload) return;
  scheduleNextReload();
}

function stopAutoReload() {
  cancelPendingReload();
  stopProgressBar();
}

function startProgressBar() {
  stopProgressBar();
  progressBar.classList.remove('hidden');
  state.progressStart = Date.now();
  tickProgress();
}

function tickProgress() {
  if (!state.autoReload) return;
  const elapsed = Date.now() - state.progressStart;
  const total = state.reloadInterval * 1000;
  const pct = Math.min((elapsed / total) * 100, 99);
  progressFill.style.width = `${pct}%`;
  if (pct < 99) state.progressTimer = requestAnimationFrame(tickProgress);
}

function stopProgressBar() {
  cancelAnimationFrame(state.progressTimer);
  state.progressTimer = null;
  progressFill.style.width = '0%';
  progressBar.classList.add('hidden');
}

/* ── Config ──────────────────────────────────────────────────────── */
async function loadConfig() {
  const cfg = await apiJson('/api/config');
  state.lang = cfg.lang || (navigator.language.startsWith('zh') ? 'zh' : 'en');
  state.source = cfg.source || 'catgirl';
  state.nsfw = cfg.nsfw_mode || 'BLOCK_NSFW';
  state.autoReload = cfg.auto_reload || false;
  state.reloadInterval = cfg.auto_reload_interval || 30;
  state.danbooruTags = cfg.danbooru_tags || '';

  langSelect.value = state.lang;
  applyLang();
  sourceSelect.value = state.source;
  nsfwSelect.value = state.nsfw;
  autoToggle.checked = state.autoReload;
  autoLabel.textContent = state.autoReload ? t('on') : t('off');
  intervalInput.value = state.reloadInterval;
  tagsInput.value = state.danbooruTags;
  toggleTagsGroup(state.source);

  if (state.autoReload) startAutoReload();
}

async function saveConfig(partial) {
  await apiJson('/api/config', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(partial),
  });
}

function toggleTagsGroup(source) {
  tagsGroup.classList.toggle('hidden', source !== 'danbooru');
}

/* ── Event handlers ─────────────────────────────────────────────── */
langSelect.addEventListener('change', () => {
  state.lang = langSelect.value;
  applyLang();
  // re-apply dynamic text
  autoLabel.textContent = state.autoReload ? t('on') : t('off');
  saveConfig({ lang: state.lang });
});

sourceSelect.addEventListener('change', () => {
  state.source = sourceSelect.value;
  toggleTagsGroup(state.source);
  saveConfig({ source: state.source });
  fetchImage();
});

nsfwSelect.addEventListener('change', () => {
  state.nsfw = nsfwSelect.value;
  saveConfig({ nsfw_mode: state.nsfw });
  fetchImage();
});

tagsInput.addEventListener('change', () => {
  state.danbooruTags = tagsInput.value;
  saveConfig({ danbooru_tags: state.danbooruTags });
  if (state.source === 'danbooru') fetchImage();
});

autoToggle.addEventListener('change', () => {
  state.autoReload = autoToggle.checked;
  autoLabel.textContent = state.autoReload ? t('on') : t('off');
  saveConfig({ auto_reload: state.autoReload });
  state.autoReload ? startAutoReload() : stopAutoReload();
});

intervalInput.addEventListener('change', () => {
  let val = parseInt(intervalInput.value, 10);
  if (isNaN(val) || val < 1) val = 1;
  if (val > 3600) val = 3600;
  intervalInput.value = val;
  state.reloadInterval = val;
  saveConfig({ auto_reload_interval: val });
  if (state.autoReload) startAutoReload();
});

refreshBtn.addEventListener('click', fetchImage);
downloadBtn.addEventListener('click', downloadImage);

/* ── Modals ────────────────────────────────────────────────────── */
artInfoBtn.addEventListener('click', () => {
  modalArtist.textContent = state.currentArtist || t('artist_unknown');
  modalLink.href = state.currentLink || '#';
  modalLink.querySelector('span').textContent = state.currentLink ? t('open_page') : '—';
  modalFilename.textContent = state.currentFilename || '—';
  artModal.classList.remove('hidden');
});

artModalClose.addEventListener('click', () => artModal.classList.add('hidden'));
artModal.addEventListener('click', e => { if (e.target === artModal) artModal.classList.add('hidden'); });

aboutBtn.addEventListener('click', () => aboutModal.classList.remove('hidden'));
aboutModalClose.addEventListener('click', () => aboutModal.classList.add('hidden'));
aboutModal.addEventListener('click', e => { if (e.target === aboutModal) aboutModal.classList.add('hidden'); });

document.addEventListener('keydown', e => {
  if (e.key === 'Escape') { artModal.classList.add('hidden'); aboutModal.classList.add('hidden'); }
  if (e.key === 'r' && !e.ctrlKey && !e.metaKey && !e.target.closest('input,select')) { e.preventDefault(); fetchImage(); }
  if (e.key === 's' && !e.ctrlKey && !e.metaKey && !e.target.closest('input,select')) { e.preventDefault(); downloadImage(); }
});

/* ── Init ────────────────────────────────────────────────────────── */
async function init() {
  await loadConfig();
  fetchImage();
}

init();
