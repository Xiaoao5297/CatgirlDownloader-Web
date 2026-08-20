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
    category: '分类',
    api_key: 'API Key',
    auto_reload: '自动刷新',
    on: '开',
    off: '关',
    sec: '秒',
    new_image: '新图片',
    save: '保存',
    favorite: '收藏',
    favorited: '已收藏',
    favorites: '收藏夹',
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
    keyboard: '键盘快捷键',
    kbd_next: '下一张',
    kbd_prev: '上一张',
    kbd_save: '下载',
    kbd_fav: '收藏',
    kbd_hint: '点击输入框后按下按键以重新绑定',
    kbd_listening: '按下按键...',
    no_favs: '还没有收藏。点击 ☆ 按钮收藏图片。',
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
    category: 'Category',
    api_key: 'API Key',
    auto_reload: 'Auto Reload',
    on: 'On',
    off: 'Off',
    sec: 'sec',
    new_image: 'New Image',
    save: 'Save',
    favorite: 'Favorite',
    favorited: 'Favorited',
    favorites: 'Favorites',
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
    keyboard: 'Keyboard Shortcuts',
    kbd_next: 'Next',
    kbd_prev: 'Prev',
    kbd_save: 'Save',
    kbd_fav: 'Favorite',
    kbd_hint: 'Click input then press a key to rebind',
    kbd_listening: 'Press a key...',
    no_favs: 'No favorites yet. Click ☆ to save an image.',
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
  currentFavId: null,
  currentArtist: null,
  currentLink: null,
  currentFilename: null,
  isFetching: false,
  reloadTimer: null,
  progressTimer: null,
  progressStart: 0,
  history: [],
  historyIndex: -1,
  favorites: [],
  sources: [],
  category: '',
  fluxpointKey: '',
  zoom: { scale: 1, tx: 0, ty: 0 },
  keyboardEnabled: true,
  keyNext: 'Enter',
  keyPrev: 'ArrowLeft',
  keyDownload: 'Space',
  keyFavorite: 'KeyF',
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
const keyGroup = $('keyGroup');
const keyInput = $('keyInput');
const autoToggle = $('autoReloadToggle');
const autoLabel = $('autoLabel');
const intervalInput = $('reloadInterval');
const refreshBtn = $('refreshBtn');
const downloadBtn = $('downloadBtn');
const prevBtn = $('prevBtn');
const nextBtn = $('nextBtn');
const favBtn = $('favBtn');
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

const favsBtn = $('favsBtn');
const favsModal = $('favsModal');
const favsModalClose = $('favsModalClose');
const favsList = $('favsList');

const keyboardToggle = $('keyboardToggle');
const kbdStatus = $('kbdStatus');
const keyBindings = $('keyBindings');
const keyNextInput = $('keyNextInput');
const keyPrevInput = $('keyPrevInput');
const keySaveInput = $('keySaveInput');
const keyFavInput = $('keyFavInput');

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
  $('keyLabel').textContent = m.api_key;
  $('reloadLabel').textContent = m.auto_reload;
  autoLabel.textContent = state.autoReload ? m.on : m.off;
  $('unitLabel').textContent = m.sec;
  $('refreshText').textContent = m.new_image;
  $('saveText').textContent = m.save;
  $('favText').textContent = m.favorite;
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
  $('favsModalTitle').textContent = m.favorites;
  $('kbdLabel').textContent = m.keyboard;
  $('kbdNextLabel').textContent = m.kbd_next;
  $('kbdPrevLabel').textContent = m.kbd_prev;
  $('kbdSaveLabel').textContent = m.kbd_save;
  $('kbdFavLabel').textContent = m.kbd_fav;
  $('kbdHint').textContent = m.kbd_hint;

  sourceSelect.innerHTML = '';
  state.sources.forEach(s => {
    const opt = document.createElement('option');
    opt.value = s.key;
    opt.textContent = s.name;
    sourceSelect.appendChild(opt);
  });
  sourceSelect.value = state.source;

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

  if (state.currentArtist) {
    modalArtist.textContent = state.currentArtist;
  }

  updateFavButton();
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
  resetZoom();
}

function sourceDisplayName(key) {
  const meta = state.sources.find(s => s.key === key);
  return meta ? meta.name : key;
}

function showInfo(artist, source) {
  if (artist) {
    artistName.textContent = artist;
    imageInfo.classList.remove('hidden');
  } else {
    imageInfo.classList.add('hidden');
  }
  sourceName.textContent = sourceDisplayName(source);
}

function updateNavButtons() {
  prevBtn.disabled = state.historyIndex <= 0;
  nextBtn.disabled = state.historyIndex >= state.history.length - 1;
}

function updateFavButton() {
  const m = LANG[state.lang];
  const isFaved = state.currentFavId
    ? true
    : !!(state.currentKey && state.favorites.some(f => f.cacheKey === state.currentKey));
  $('favText').textContent = isFaved ? m.favorited : m.favorite;
  if (isFaved) {
    favBtn.querySelector('svg').setAttribute('fill', 'currentColor');
    favBtn.querySelector('svg').setAttribute('stroke', 'none');
  } else {
    favBtn.querySelector('svg').setAttribute('fill', 'none');
    favBtn.querySelector('svg').setAttribute('stroke', 'currentColor');
  }
}

/* ── History navigation ────────────────────────────────────────── */
function pushHistory(key, artist, link, filename, source, isFav = false) {
  if (state.historyIndex < state.history.length - 1) {
    state.history = state.history.slice(0, state.historyIndex + 1);
  }
  state.history.push({ key, artist, link, filename, source, isFav });
  state.historyIndex = state.history.length - 1;
  updateNavButtons();
}

function goPrev() {
  if (state.historyIndex <= 0 || state.isFetching) return;
  state.historyIndex--;
  showFromHistory(state.history[state.historyIndex]);
}

function goNext() {
  if (state.isFetching) return;
  if (state.historyIndex < state.history.length - 1) {
    state.historyIndex++;
    showFromHistory(state.history[state.historyIndex]);
  } else {
    fetchImage();
  }
}

function showFromHistory(item) {
  state.currentKey = item.key;
  state.currentFavId = item.isFav ? item.key : null;
  state.currentArtist = item.artist;
  state.currentLink = item.link;
  state.currentFilename = item.filename;

  const src = item.isFav ? `/api/favorites/${item.key}/image` : `/api/image/${item.key}`;
  showImage(src);
  showInfo(item.artist, item.source);
  downloadBtn.disabled = false;
  favBtn.disabled = false;
  updateNavButtons();
  updateFavButton();
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
    state.currentFavId = null;
    state.currentArtist = data.artist;
    state.currentLink = data.link;
    state.currentFilename = data.filename;

    pushHistory(data.key, data.artist, data.link, data.filename, data.source);

    showImage(`/api/image/${data.key}`);
    showInfo(data.artist, data.source);
    downloadBtn.disabled = false;
    favBtn.disabled = false;
    updateFavButton();
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
  a.href = state.currentFavId
    ? `/api/favorites/${state.currentFavId}/download`
    : `/api/download/${state.currentKey}`;
  a.download = state.currentFilename || 'image';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}

/* ── Zoom & Pan ─────────────────────────────────────────────────── */
const imageWrapper = $('imageWrapper');
const zoomBadge = $('zoomBadge');
const ZOOM_MIN = 1;
const ZOOM_MAX = 5;
const pointers = new Map();
let panStart = null;
let pinchStart = null;

function clamp(v, lo, hi) { return Math.min(hi, Math.max(lo, v)); }

function resetZoom() {
  const z = state.zoom;
  z.scale = 1;
  z.tx = 0;
  z.ty = 0;
  applyZoom();
}

function applyZoom() {
  const z = state.zoom;
  const zoomed = z.scale > 1;
  if (zoomed) {
    mainImage.style.transform = `translate(${z.tx}px, ${z.ty}px) scale(${z.scale})`;
  } else {
    mainImage.style.transform = '';
  }
  imageWrapper.classList.toggle('zoomed', zoomed);
  zoomBadge.classList.toggle('hidden', !zoomed);
  zoomBadge.textContent = `${Math.round(z.scale * 100)}%`;
}

function clampPan() {
  const z = state.zoom;
  const rect = mainImage.getBoundingClientRect();
  const maxX = Math.max(0, (rect.width - imageWrapper.clientWidth) / 2);
  const maxY = Math.max(0, (rect.height - imageWrapper.clientHeight) / 2);
  z.tx = clamp(z.tx, -maxX, maxX);
  z.ty = clamp(z.ty, -maxY, maxY);
}

function zoomTo(newScale, px, py) {
  const z = state.zoom;
  newScale = clamp(newScale, ZOOM_MIN, ZOOM_MAX);
  if (newScale === z.scale) return;
  const rect = mainImage.getBoundingClientRect();
  const Cx = rect.left + rect.width / 2 - z.tx;
  const Cy = rect.top + rect.height / 2 - z.ty;
  const r = newScale / z.scale;
  z.tx = (px - Cx) - ((px - Cx) - z.tx) * r;
  z.ty = (py - Cy) - ((py - Cy) - z.ty) * r;
  z.scale = newScale;
  clampPan();
  applyZoom();
}

function imageVisible() {
  return !mainImage.classList.contains('hidden');
}

function pinchData() {
  const pts = [...pointers.values()];
  const dx = pts[0].x - pts[1].x;
  const dy = pts[0].y - pts[1].y;
  return {
    dist: Math.hypot(dx, dy),
    midX: (pts[0].x + pts[1].x) / 2,
    midY: (pts[0].y + pts[1].y) / 2,
  };
}

imageWrapper.addEventListener('wheel', e => {
  if (!imageVisible()) return;
  e.preventDefault();
  const factor = e.deltaY < 0 ? 1.12 : 1 / 1.12;
  zoomTo(state.zoom.scale * factor, e.clientX, e.clientY);
}, { passive: false });

imageWrapper.addEventListener('pointerdown', e => {
  if (!imageVisible()) return;
  pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
  if (pointers.size === 1) {
    panStart = state.zoom.scale > 1
      ? { x: e.clientX, y: e.clientY, tx: state.zoom.tx, ty: state.zoom.ty }
      : null;
    pinchStart = null;
  } else if (pointers.size === 2) {
    panStart = null;
    pinchStart = { ...pinchData(), scale: state.zoom.scale };
  }
  if (panStart) imageWrapper.classList.add('panning');
  try { imageWrapper.setPointerCapture(e.pointerId); } catch (err) {}
});

imageWrapper.addEventListener('pointermove', e => {
  if (!pointers.has(e.pointerId)) return;
  pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
  const z = state.zoom;

  if (pointers.size === 2 && pinchStart) {
    const now = pinchData();
    const target = clamp(pinchStart.scale * (now.dist / pinchStart.dist), ZOOM_MIN, ZOOM_MAX);
    zoomTo(target, now.midX, now.midY);
  } else if (pointers.size === 1 && panStart) {
    z.tx = panStart.tx + (e.clientX - panStart.x);
    z.ty = panStart.ty + (e.clientY - panStart.y);
    clampPan();
    applyZoom();
  }
});

function endPointer(e) {
  pointers.delete(e.pointerId);
  panStart = null;
  pinchStart = null;
  imageWrapper.classList.remove('panning');
  if (pointers.size === 1 && state.zoom.scale > 1) {
    const p = [...pointers.values()][0];
    panStart = { x: p.x, y: p.y, tx: state.zoom.tx, ty: state.zoom.ty };
  }
}

imageWrapper.addEventListener('pointerup', endPointer);
imageWrapper.addEventListener('pointercancel', endPointer);

imageWrapper.addEventListener('dblclick', () => {
  if (imageVisible()) resetZoom();
});

/* ── Favorites ──────────────────────────────────────────────────── */
async function loadFavorites() {
  try {
    state.favorites = await apiJson('/api/favorites');
  } catch {
    state.favorites = [];
  }
}

async function toggleFavorite() {
  if (!state.currentKey) return;

  if (state.currentFavId) {
    const id = state.currentFavId;
    try {
      await api(`/api/favorites/${id}`, { method: 'DELETE' });
      const fav = state.favorites.find(f => f.id === id);
      state.favorites = state.favorites.filter(f => f.id !== id);
      state.currentKey = (fav && fav.cacheKey) || state.currentKey;
      state.currentFavId = null;
      updateFavButton();
    } catch (e) {
      console.error('Failed to remove favorite:', e);
    }
    return;
  }

  const existing = state.favorites.find(f => f.cacheKey === state.currentKey);
  if (existing) {
    try {
      await api(`/api/favorites/${existing.id}`, { method: 'DELETE' });
      state.favorites = state.favorites.filter(f => f.id !== existing.id);
      updateFavButton();
    } catch (e) {
      console.error('Failed to remove favorite:', e);
    }
    return;
  }
  try {
    const fav = await apiJson(`/api/favorites/${state.currentKey}`, { method: 'POST' });
    state.favorites.unshift(fav);
    updateFavButton();
  } catch (e) {
    console.error('Failed to add favorite:', e);
  }
}

function renderFavorites() {
  const m = LANG[state.lang];
  if (state.favorites.length === 0) {
    favsList.innerHTML = `<p class="favs-empty">${m.no_favs}</p>`;
    return;
  }

  favsList.innerHTML = state.favorites.map(f => `
    <div class="fav-item" data-id="${f.id}">
      <img class="fav-thumb" src="/api/favorites/${f.id}/image" alt="${f.artist || ''}" loading="lazy" />
      <div class="fav-info">
        <span class="fav-artist">${f.artist || m.artist_unknown}</span>
        <span class="fav-source">${sourceDisplayName(f.source)}</span>
      </div>
      <button class="btn-icon fav-del" data-id="${f.id}" title="Delete">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
        </svg>
      </button>
    </div>
  `).join('');

  favsList.querySelectorAll('.fav-item').forEach(el => {
    el.addEventListener('click', e => {
      if (e.target.closest('.fav-del')) return;
      const fav = state.favorites.find(f => f.id === el.dataset.id);
      if (fav) {
        state.currentKey = fav.id;
        state.currentFavId = fav.id;
        state.currentArtist = fav.artist;
        state.currentLink = fav.link;
        state.currentFilename = fav.filename;
        pushHistory(fav.id, fav.artist, fav.link, fav.filename, fav.source, true);
        showImage(`/api/favorites/${fav.id}/image`);
        showInfo(fav.artist, fav.source);
        downloadBtn.disabled = false;
        favBtn.disabled = false;
        updateFavButton();
        favsModal.classList.add('hidden');
      }
    });
  });

  favsList.querySelectorAll('.fav-del').forEach(btn => {
    btn.addEventListener('click', async e => {
      e.stopPropagation();
      const id = btn.dataset.id;
      try {
        await api(`/api/favorites/${id}`, { method: 'DELETE' });
        state.favorites = state.favorites.filter(f => f.id !== id);
        updateFavButton();
        renderFavorites();
      } catch (err) {
        console.error('Failed to delete favorite:', err);
      }
    });
  });
}

async function openFavorites() {
  await loadFavorites();
  renderFavorites();
  favsModal.classList.remove('hidden');
}

/* ── Keyboard ───────────────────────────────────────────────────── */
function keyDisplayName(code) {
  const map = {
    'Enter': 'Enter ↵',
    'Space': 'Space',
    'ArrowLeft': '←',
    'ArrowRight': '→',
    'ArrowUp': '↑',
    'ArrowDown': '↓',
    'KeyF': 'F',
    'KeyS': 'S',
    'KeyR': 'R',
    'KeyD': 'D',
  };
  return map[code] || code.replace('Key', '');
}

function updateKeyInputs() {
  keyNextInput.value = keyDisplayName(state.keyNext);
  keyPrevInput.value = keyDisplayName(state.keyPrev);
  keySaveInput.value = keyDisplayName(state.keyDownload);
  keyFavInput.value = keyDisplayName(state.keyFavorite);
}

function enableKeyCapture(input, configKey) {
  const m = LANG[state.lang];
  input.addEventListener('focus', () => {
    input.value = m.kbd_listening;
    input.dataset.capturing = 'true';
  });
  input.addEventListener('blur', () => {
    input.dataset.capturing = 'false';
    updateKeyInputs();
  });
  input.addEventListener('keydown', e => {
    e.preventDefault();
    e.stopPropagation();
    state[configKey] = e.code;
    input.value = keyDisplayName(e.code);
    input.blur();
    const field = configKey.replace(/^key/, '');
    const snake = field.charAt(0).toLowerCase() + field.slice(1);
    saveConfig({ [`key_${snake}`]: e.code });
  });
}

document.addEventListener('keydown', e => {
  if (e.target.closest('input,select,textarea')) return;
  if (document.activeElement && document.activeElement.dataset.capturing === 'true') return;
  if (!state.keyboardEnabled) return;

  const code = e.code;
  if (code === state.keyNext) { e.preventDefault(); goNext(); }
  else if (code === state.keyPrev) { e.preventDefault(); goPrev(); }
  else if (code === state.keyDownload) { e.preventDefault(); downloadImage(); }
  else if (code === state.keyFavorite) { e.preventDefault(); toggleFavorite(); }
});

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
  state.lang = (cfg.lang && cfg.lang !== 'auto')
    ? cfg.lang
    : (navigator.language.startsWith('zh') ? 'zh' : 'en');
  state.source = cfg.source || 'catgirl';
  state.nsfw = cfg.nsfw_mode || 'BLOCK_NSFW';
  state.autoReload = cfg.auto_reload || false;
  state.reloadInterval = cfg.auto_reload_interval || 30;
  state.danbooruTags = cfg.danbooru_tags || '';
  state.category = cfg.category || '';
  state.fluxpointKey = cfg.fluxpoint_key || '';
  state.keyboardEnabled = cfg.keyboard_enabled !== false;
  state.keyNext = cfg.key_next || 'Enter';
  state.keyPrev = cfg.key_prev || 'ArrowLeft';
  state.keyDownload = cfg.key_download || 'Space';
  state.keyFavorite = cfg.key_favorite || 'KeyF';

  langSelect.value = state.lang;
  applyLang();
  sourceSelect.value = state.source;
  nsfwSelect.value = state.nsfw;
  autoToggle.checked = state.autoReload;
  autoLabel.textContent = state.autoReload ? t('on') : t('off');
  intervalInput.value = state.reloadInterval;
  keyInput.value = state.fluxpointKey;
  toggleSourceGroups(state.source);
  keyboardToggle.checked = state.keyboardEnabled;
  kbdStatus.textContent = state.keyboardEnabled ? t('on') : t('off');
  keyBindings.classList.toggle('hidden', !state.keyboardEnabled);
  updateKeyInputs();

  if (state.autoReload) startAutoReload();
}

async function saveConfig(partial) {
  await apiJson('/api/config', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(partial),
  });
}

function sourceMeta(source) {
  return state.sources.find(s => s.key === source) || {};
}

function toggleSourceGroups(source) {
  const meta = sourceMeta(source);
  const showTags = !!meta.has_tags;
  tagsGroup.classList.toggle('hidden', !showTags);
  if (showTags) {
    if (source === 'danbooru') {
      $('tagsLabel').textContent = t('danbooru_tags');
      tagsInput.value = state.danbooruTags;
      tagsInput.placeholder = t('tags_placeholder');
    } else {
      $('tagsLabel').textContent = meta.tags_label || t('category');
      tagsInput.value = state.category;
      tagsInput.placeholder = '';
    }
  }
  keyGroup.classList.toggle('hidden', source !== 'fluxpoint');
}

/* ── Event handlers ─────────────────────────────────────────────── */
langSelect.addEventListener('change', () => {
  state.lang = langSelect.value;
  applyLang();
  autoLabel.textContent = state.autoReload ? t('on') : t('off');
  kbdStatus.textContent = state.keyboardEnabled ? t('on') : t('off');
  updateKeyInputs();
  saveConfig({ lang: state.lang });
});

sourceSelect.addEventListener('change', () => {
  state.source = sourceSelect.value;
  toggleSourceGroups(state.source);
  saveConfig({ source: state.source });
  fetchImage();
});

nsfwSelect.addEventListener('change', () => {
  state.nsfw = nsfwSelect.value;
  saveConfig({ nsfw_mode: state.nsfw });
  fetchImage();
});

tagsInput.addEventListener('change', () => {
  if (state.source === 'danbooru') {
    state.danbooruTags = tagsInput.value;
    saveConfig({ danbooru_tags: state.danbooruTags });
  } else {
    state.category = tagsInput.value.trim();
    saveConfig({ category: state.category });
  }
  if (state.source === 'danbooru' || sourceMeta(state.source).has_tags) fetchImage();
});

keyInput.addEventListener('change', () => {
  state.fluxpointKey = keyInput.value.trim();
  saveConfig({ fluxpoint_key: state.fluxpointKey });
  if (state.source === 'fluxpoint') fetchImage();
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

keyboardToggle.addEventListener('change', () => {
  state.keyboardEnabled = keyboardToggle.checked;
  kbdStatus.textContent = state.keyboardEnabled ? t('on') : t('off');
  keyBindings.classList.toggle('hidden', !state.keyboardEnabled);
  saveConfig({ keyboard_enabled: state.keyboardEnabled });
});

enableKeyCapture(keyNextInput, 'keyNext');
enableKeyCapture(keyPrevInput, 'keyPrev');
enableKeyCapture(keySaveInput, 'keyDownload');
enableKeyCapture(keyFavInput, 'keyFavorite');

refreshBtn.addEventListener('click', fetchImage);
prevBtn.addEventListener('click', goPrev);
nextBtn.addEventListener('click', goNext);
downloadBtn.addEventListener('click', downloadImage);
favBtn.addEventListener('click', toggleFavorite);
favsBtn.addEventListener('click', openFavorites);

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

favsModalClose.addEventListener('click', () => favsModal.classList.add('hidden'));
favsModal.addEventListener('click', e => { if (e.target === favsModal) favsModal.classList.add('hidden'); });

document.addEventListener('keydown', e => {
  if (e.key === 'Escape') {
    artModal.classList.add('hidden');
    aboutModal.classList.add('hidden');
    favsModal.classList.add('hidden');
  }
});

/* ── Init ────────────────────────────────────────────────────────── */
async function loadSources() {
  try {
    const data = await apiJson('/api/sources');
    state.sources = Array.isArray(data) ? data : [];
  } catch {
    state.sources = [];
  }
}

async function init() {
  await loadSources();
  await loadConfig();
  await loadFavorites();
  fetchImage();
}

init();
