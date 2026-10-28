// CrunchyMate Frontend Application

const state = {
  currentTab: 'search', // 'search' | 'watched' | 'meh' | 'settings'
  query: '',
  genre: '',
  language: 'all',
  watched: 'all',
  meh: 'exclude',
  sort: 'title',
  tagStates: new Map(), // tagName -> 'blue' | 'green' | 'orange'
  allTags: [],
  animeMap: new Map(),
  activeDetailId: null,
  isCrSyncing: false,
  isAniSyncing: false,
};

// DOM Elements
const searchControlsCard = document.getElementById('searchControlsCard');
const catalogContainer = document.getElementById('catalogContainer');
const settingsView = document.getElementById('settingsView');
const viewBanner = document.getElementById('viewBanner');
const viewBannerTitle = document.getElementById('viewBannerTitle');
const viewBannerSubtitle = document.getElementById('viewBannerSubtitle');

const searchInput = document.getElementById('searchInput');
const clearSearchBtn = document.getElementById('clearSearchBtn');
const genreSelect = document.getElementById('genreSelect');
const langSelect = document.getElementById('langSelect');
const sortSelect = document.getElementById('sortSelect');
const watchedFilter = document.getElementById('watchedFilter');
const animeGrid = document.getElementById('animeGrid');
const resultsCount = document.getElementById('resultsCount');
const emptyState = document.getElementById('emptyState');
const emptyTitle = document.getElementById('emptyTitle');
const emptySubtitle = document.getElementById('emptySubtitle');

// Bottom Nav Elements
const navSearch = document.getElementById('navSearch');
const navWatched = document.getElementById('navWatched');
const navMeh = document.getElementById('navMeh');
const navSettings = document.getElementById('navSettings');
const badgeWatched = document.getElementById('badgeWatched');
const badgeMeh = document.getElementById('badgeMeh');
const badgeSettingsSync = document.getElementById('badgeSettingsSync');

// Tag Filter Elements
const clearTagsBtn = document.getElementById('clearTagsBtn');
const activeTagsBar = document.getElementById('activeTagsBar');
const tagSearchInput = document.getElementById('tagSearchInput');
const tagCloud = document.getElementById('tagCloud');

// Settings Elements
const startCrSyncBtn = document.getElementById('startCrSyncBtn');
const crProgressBar = document.getElementById('crProgressBar');
const crSyncStatusText = document.getElementById('crSyncStatusText');
const crSyncCountText = document.getElementById('crSyncCountText');
const crLastSyncText = document.getElementById('crLastSyncText');

const startAniSyncBtn = document.getElementById('startAniSyncBtn');
const aniProgressBar = document.getElementById('aniProgressBar');
const aniSyncStatusText = document.getElementById('aniSyncStatusText');
const aniSyncCountText = document.getElementById('aniSyncCountText');
const aniLastSyncText = document.getElementById('aniLastSyncText');

const statTotalAnime = document.getElementById('statTotalAnime');
const statWatchedAnime = document.getElementById('statWatchedAnime');
const statMehAnime = document.getElementById('statMehAnime');
const statEnrichedAnime = document.getElementById('statEnrichedAnime');
const statTotalTags = document.getElementById('statTotalTags');
const settingHideWatched = document.getElementById('settingHideWatched');

// Modal Elements
const detailModal = document.getElementById('detailModal');
const closeModalBtn = document.getElementById('closeModalBtn');
const modalBanner = document.getElementById('modalBanner');
const modalPoster = document.getElementById('modalPoster');
const modalTitle = document.getElementById('modalTitle');
const modalSubtitles = document.getElementById('modalSubtitles');
const modalBadges = document.getElementById('modalBadges');
const modalGenres = document.getElementById('modalGenres');
const modalTags = document.getElementById('modalTags');
const modalTagsBox = document.getElementById('modalTagsBox');
const modalDescription = document.getElementById('modalDescription');
const modalWatchedBtn = document.getElementById('modalWatchedBtn');
const modalMehBtn = document.getElementById('modalMehBtn');
const modalAniListSection = document.getElementById('modalAniListSection');
const modalAniListScore = document.getElementById('modalAniListScore');
const modalAniListStatus = document.getElementById('modalAniListStatus');
const modalAniListLink = document.getElementById('modalAniListLink');
const modalAniListDesc = document.getElementById('modalAniListDesc');

function saveSearchState() {
  const tagObj = {};
  state.tagStates.forEach((val, key) => {
    tagObj[key] = val;
  });

  const saved = {
    tab: state.currentTab,
    query: state.query,
    genre: state.genre,
    language: state.language,
    sort: state.sort,
    watched: state.watched,
    tags: tagObj,
  };

  try {
    localStorage.setItem('crunchymate_search_state', JSON.stringify(saved));
  } catch (e) {}

  const params = new URLSearchParams();
  if (state.currentTab && state.currentTab !== 'search') {
    params.set('tab', state.currentTab);
  }
  if (state.query) params.set('q', state.query);
  if (state.genre) params.set('genre', state.genre);
  if (state.language && state.language !== 'all') params.set('lang', state.language);
  if (state.sort && state.sort !== 'title') params.set('sort', state.sort);
  if (state.watched && state.watched !== 'all') params.set('watched', state.watched);

  const blueTags = [];
  const greenTags = [];
  const orangeTags = [];
  state.tagStates.forEach((val, key) => {
    if (val === 'blue') blueTags.push(key);
    else if (val === 'green') greenTags.push(key);
    else if (val === 'orange') orangeTags.push(key);
  });
  if (blueTags.length > 0) params.set('prefer_tags', blueTags.join(','));
  if (greenTags.length > 0) params.set('must_tags', greenTags.join(','));
  if (orangeTags.length > 0) params.set('exclude_tags', orangeTags.join(','));

  const searchStr = params.toString();
  const newUrl = window.location.pathname + (searchStr ? '?' + searchStr : '');
  window.history.replaceState(null, '', newUrl);
}

function restoreSearchState() {
  const urlParams = new URLSearchParams(window.location.search);
  let saved = null;
  try {
    const raw = localStorage.getItem('crunchymate_search_state');
    if (raw) saved = JSON.parse(raw);
  } catch (e) {}

  // 1. Tab
  const tabFromUrl = urlParams.get('tab');
  if (tabFromUrl && ['search', 'watched', 'meh', 'settings'].includes(tabFromUrl)) {
    state.currentTab = tabFromUrl;
  } else if (saved && saved.tab && ['search', 'watched', 'meh', 'settings'].includes(saved.tab)) {
    state.currentTab = saved.tab;
  }

  // 2. Query
  if (urlParams.has('q')) {
    state.query = urlParams.get('q') || '';
  } else if (saved && typeof saved.query === 'string') {
    state.query = saved.query;
  }
  if (searchInput) {
    searchInput.value = state.query;
    clearSearchBtn.style.display = state.query ? 'block' : 'none';
  }

  // 3. Genre
  if (urlParams.has('genre')) {
    state.genre = urlParams.get('genre') || '';
  } else if (saved && typeof saved.genre === 'string') {
    state.genre = saved.genre;
  }
  if (genreSelect && state.genre) genreSelect.value = state.genre;

  // 4. Language
  if (urlParams.has('lang')) {
    state.language = urlParams.get('lang') || 'all';
  } else if (saved && typeof saved.language === 'string') {
    state.language = saved.language;
  }
  if (langSelect && state.language) langSelect.value = state.language;

  // 5. Sort
  if (urlParams.has('sort')) {
    state.sort = urlParams.get('sort') || 'title';
  } else if (saved && typeof saved.sort === 'string') {
    state.sort = saved.sort;
  }
  if (sortSelect && state.sort) sortSelect.value = state.sort;

  // 6. Watched
  if (urlParams.has('watched')) {
    state.watched = urlParams.get('watched') || 'all';
  } else if (saved && typeof saved.watched === 'string') {
    state.watched = saved.watched;
  } else {
    const hideWatchedPref = localStorage.getItem('crunchymate_hide_watched') === 'true';
    state.watched = hideWatchedPref ? 'unwatched' : 'all';
  }
  if (watchedFilter) watchedFilter.value = state.watched;
  if (settingHideWatched) settingHideWatched.checked = (state.watched === 'unwatched');

  // 7. Tags
  state.tagStates.clear();
  const parseList = (str) => str ? str.split(',').map(s => s.trim()).filter(Boolean) : [];
  if (urlParams.has('prefer_tags') || urlParams.has('must_tags') || urlParams.has('exclude_tags')) {
    parseList(urlParams.get('prefer_tags')).forEach(t => state.tagStates.set(t, 'blue'));
    parseList(urlParams.get('must_tags')).forEach(t => state.tagStates.set(t, 'green'));
    parseList(urlParams.get('exclude_tags')).forEach(t => state.tagStates.set(t, 'orange'));
  } else if (saved && saved.tags && typeof saved.tags === 'object') {
    Object.entries(saved.tags).forEach(([t, s]) => {
      if (['blue', 'green', 'orange'].includes(s)) {
        state.tagStates.set(t, s);
      }
    });
  }
}

// Initialize
document.addEventListener('DOMContentLoaded', () => {
  setupEventListeners();
  restoreSearchState();
  switchTab(state.currentTab || 'search');
  loadGenres();
  loadTags();
  updateBadgeCounts();
  checkInitialSyncStatuses();
});

window.addEventListener('popstate', () => {
  restoreSearchState();
  switchTab(state.currentTab || 'search');
  updateTagUI();
});

function setupEventListeners() {
  // Navigation tabs
  navSearch.addEventListener('click', () => switchTab('search'));
  navWatched.addEventListener('click', () => switchTab('watched'));
  navMeh.addEventListener('click', () => switchTab('meh'));
  navSettings.addEventListener('click', () => switchTab('settings'));

  // Search with debounce
  let debounceTimeout;
  searchInput.addEventListener('input', (e) => {
    state.query = e.target.value.trim();
    clearSearchBtn.style.display = state.query ? 'block' : 'none';
    clearTimeout(debounceTimeout);
    debounceTimeout = setTimeout(() => {
      loadAnime();
    }, 250);
  });

  clearSearchBtn.addEventListener('click', () => {
    searchInput.value = '';
    state.query = '';
    clearSearchBtn.style.display = 'none';
    loadAnime();
  });

  // Genre select
  genreSelect.addEventListener('change', (e) => {
    state.genre = e.target.value;
    loadAnime();
  });

  // Language select
  if (langSelect) {
    langSelect.addEventListener('change', (e) => {
      state.language = e.target.value;
      loadAnime();
    });
  }

  // Sort select
  sortSelect.addEventListener('change', (e) => {
    state.sort = e.target.value;
    loadAnime();
  });

  // Watched filter select
  if (watchedFilter) {
    watchedFilter.addEventListener('change', (e) => {
      state.watched = e.target.value;
      const hide = state.watched === 'unwatched';
      localStorage.setItem('crunchymate_hide_watched', hide ? 'true' : 'false');
      if (settingHideWatched) settingHideWatched.checked = hide;
      loadAnime();
    });
  }

  // Settings toggle for hiding watched anime
  if (settingHideWatched) {
    settingHideWatched.addEventListener('change', (e) => {
      const hide = e.target.checked;
      state.watched = hide ? 'unwatched' : 'all';
      if (watchedFilter) watchedFilter.value = state.watched;
      localStorage.setItem('crunchymate_hide_watched', hide ? 'true' : 'false');
      loadAnime();
    });
  }

  // Inline tag search input
  tagSearchInput.addEventListener('input', (e) => {
    const q = e.target.value.toLowerCase().trim();
    filterTagCloud(q);
  });

  // Clear all tags button
  clearTagsBtn.addEventListener('click', () => {
    state.tagStates.clear();
    updateTagUI();
    loadAnime();
  });

  // Settings sync buttons
  startCrSyncBtn.addEventListener('click', triggerCrunchyrollSync);
  startAniSyncBtn.addEventListener('click', triggerAniListSync);

  // Modal events
  closeModalBtn.addEventListener('click', closeDetail);
  detailModal.addEventListener('click', (e) => {
    if (e.target === detailModal) closeDetail();
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && detailModal.style.display !== 'none') {
      closeDetail();
    }
  });

  // Modal watched button
  modalWatchedBtn.addEventListener('click', () => {
    if (!state.activeDetailId) return;
    const anime = state.animeMap.get(state.activeDetailId);
    if (!anime) return;
    toggleWatched(anime.id, !anime.watched);
  });

  // Modal meh button
  modalMehBtn.addEventListener('click', () => {
    if (!state.activeDetailId) return;
    const anime = state.animeMap.get(state.activeDetailId);
    if (!anime) return;
    toggleMeh(anime.id, !anime.is_meh);
  });
}

// Tab Switching
function switchTab(tab) {
  state.currentTab = tab;

  // Update nav item active states
  navSearch.classList.toggle('active', tab === 'search');
  navWatched.classList.toggle('active', tab === 'watched');
  navMeh.classList.toggle('active', tab === 'meh');
  navSettings.classList.toggle('active', tab === 'settings');

  if (tab === 'search') {
    searchControlsCard.style.display = 'block';
    catalogContainer.style.display = 'block';
    settingsView.style.display = 'none';
    viewBanner.style.display = 'none';
    state.meh = 'exclude';
    state.watched = watchedFilter ? watchedFilter.value : 'all';
    emptyTitle.textContent = 'Keine Anime gefunden';
    emptySubtitle.textContent = 'Passe deine Suchbegriffe oder Filter an oder starte einen Sync in den Einstellungen.';
    loadAnime();
  } else if (tab === 'watched') {
    searchControlsCard.style.display = 'none';
    catalogContainer.style.display = 'block';
    settingsView.style.display = 'none';
    viewBanner.style.display = 'block';
    viewBannerTitle.textContent = '👁️ Gesehene Anime';
    viewBannerSubtitle.textContent = 'Alle Serien, die du als bereits gesehen markiert hast.';
    state.meh = 'all';
    state.watched = 'watched';
    emptyTitle.textContent = 'Noch keine gesehenen Anime';
    emptySubtitle.textContent = 'Tippe bei einem Anime auf das Häkchen (✓), um ihn zu deiner Gesehen-Liste hinzuzufügen.';
    loadAnime();
  } else if (tab === 'meh') {
    searchControlsCard.style.display = 'none';
    catalogContainer.style.display = 'block';
    settingsView.style.display = 'none';
    viewBanner.style.display = 'block';
    viewBannerTitle.textContent = '😒 Meh Anime (Ausgeblendet)';
    viewBannerSubtitle.textContent = 'Diese Anime sind in der normalen Suche ausgeblendet. Klicke auf "Wiederherstellen", um sie wieder anzuzeigen.';
    state.meh = 'only';
    state.watched = 'all';
    emptyTitle.textContent = 'Keine Meh-Anime vorhanden';
    emptySubtitle.textContent = 'Tippe bei einem Anime auf 😒, wenn er dir nicht gefällt und du ihn ausblenden möchtest.';
    loadAnime();
  } else if (tab === 'settings') {
    searchControlsCard.style.display = 'none';
    catalogContainer.style.display = 'none';
    viewBanner.style.display = 'none';
    settingsView.style.display = 'block';
    refreshSettingsData();
    saveSearchState();
  }
}

// Multi-state Tag Logic
function cycleTag(tagName) {
  const current = state.tagStates.get(tagName);
  if (!current) {
    state.tagStates.set(tagName, 'blue');
  } else if (current === 'blue') {
    state.tagStates.set(tagName, 'green');
  } else if (current === 'green') {
    state.tagStates.set(tagName, 'orange');
  } else {
    state.tagStates.delete(tagName);
  }

  updateTagUI();
  loadAnime();
}

function updateTagUI() {
  tagCloud.querySelectorAll('.tag-chip').forEach(chip => {
    const tag = chip.dataset.tag;
    const st = state.tagStates.get(tag);
    chip.className = `tag-chip ${st ? 'state-' + st : ''}`;
    
    const iconSpan = chip.querySelector('.tag-state-icon');
    if (iconSpan) {
      if (st === 'blue') iconSpan.textContent = '🔵 ';
      else if (st === 'green') iconSpan.textContent = '🟢 ';
      else if (st === 'orange') iconSpan.textContent = '🟠 ';
      else iconSpan.textContent = '';
    }
  });

  activeTagsBar.innerHTML = '';
  if (state.tagStates.size > 0) {
    activeTagsBar.style.display = 'flex';
    clearTagsBtn.style.display = 'inline-flex';

    state.tagStates.forEach((st, tagName) => {
      const chip = document.createElement('span');
      chip.className = `tag-chip state-${st}`;
      
      let icon = '🔵';
      let label = 'Bevorzugt';
      if (st === 'green') { icon = '🟢'; label = 'Muss'; }
      if (st === 'orange') { icon = '🟠'; label = 'Ausgeschlossen'; }

      chip.innerHTML = `<span>${icon} <strong>${escapeHtml(tagName)}</strong> (${label})</span> <span class="tag-remove">✕</span>`;
      chip.title = 'Klicken zum Wechseln oder Entfernen';
      chip.addEventListener('click', () => {
        cycleTag(tagName);
      });
      activeTagsBar.appendChild(chip);
    });
  } else {
    activeTagsBar.style.display = 'none';
    clearTagsBtn.style.display = 'none';
  }

  if (state.activeDetailId) {
    modalTags.querySelectorAll('.tag-chip').forEach(chip => {
      const tag = chip.dataset.tag;
      const st = state.tagStates.get(tag);
      chip.className = `tag-chip ${st ? 'state-' + st : ''}`;
      const iconSpan = chip.querySelector('.tag-state-icon');
      if (iconSpan) {
        if (st === 'blue') iconSpan.textContent = '🔵 ';
        else if (st === 'green') iconSpan.textContent = '🟢 ';
        else if (st === 'orange') iconSpan.textContent = '🟠 ';
        else iconSpan.textContent = '';
      }
    });
  }
}

// =============================================================
// Dual-Mode API Client (Browser fetch <-> AndroidBridge)
// =============================================================
const apiClient = {
  async get(endpoint, params) {
    if (typeof window !== 'undefined' && window.AndroidBridge) {
      return apiClient._bridgeGet(endpoint, params);
    }
    return apiClient._fetchGet(endpoint, params);
  },

  async post(endpoint, body) {
    if (typeof window !== 'undefined' && window.AndroidBridge) {
      return apiClient._bridgePost(endpoint, body);
    }
    return apiClient._fetchPost(endpoint, body);
  },

  _buildUrl(endpoint, params) {
    let url = endpoint;
    const query = new URLSearchParams();

    if (params) {
      if (params instanceof URLSearchParams) {
        for (const [k, v] of params.entries()) {
          query.set(k, v);
        }
      } else if (typeof params === 'object') {
        for (const [k, v] of Object.entries(params)) {
          if (v !== undefined && v !== null && v !== '') {
            query.set(k, v);
          }
        }
      }
    }

    const qs = query.toString();
    if (qs) {
      url += (url.includes('?') ? '&' : '?') + qs;
    }
    return url;
  },

  async _fetchGet(endpoint, params) {
    const url = apiClient._buildUrl(endpoint, params);
    const fetchFn = (typeof window !== 'undefined' && window.fetch) ? window.fetch.bind(window) : fetch;
    const res = await fetchFn(url);
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    }
    return await res.json();
  },

  async _fetchPost(endpoint, body) {
    const options = { method: 'POST' };
    if (body !== undefined) {
      options.headers = { 'Content-Type': 'application/json' };
      options.body = typeof body === 'string' ? body : JSON.stringify(body);
    }
    const fetchFn = (typeof window !== 'undefined' && window.fetch) ? window.fetch.bind(window) : fetch;
    const res = await fetchFn(endpoint, options);
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    }
    const text = await res.text();
    return text ? JSON.parse(text) : {};
  },

  async _bridgeGet(endpoint, params) {
    const bridge = window.AndroidBridge;
    const qIndex = endpoint.indexOf('?');
    const path = qIndex !== -1 ? endpoint.slice(0, qIndex) : endpoint;
    const inlineQs = qIndex !== -1 ? endpoint.slice(qIndex + 1) : '';

    if (path === '/api/genres') {
      return apiClient._parseBridgeResult(await bridge.getGenres());
    }
    if (path === '/api/tags') {
      return apiClient._parseBridgeResult(await bridge.getTags());
    }
    if (path === '/api/stats') {
      return apiClient._parseBridgeResult(await bridge.getStats());
    }
    if (path === '/api/sync/status') {
      return apiClient._parseBridgeResult(await bridge.getCrSyncStatus());
    }
    if (path === '/api/sync/anilist/status') {
      const fn = bridge.getAniSyncStatus ? bridge.getAniSyncStatus.bind(bridge) : bridge.getAniListSyncStatus.bind(bridge);
      return apiClient._parseBridgeResult(await fn());
    }

    // Single anime: /api/anime/:id
    const singleMatch = path.match(/^\/api\/anime\/([^/?#]+)$/);
    if (singleMatch) {
      const id = decodeURIComponent(singleMatch[1]);
      return apiClient._parseBridgeResult(await bridge.getAnime(id));
    }

    // List/query anime: /api/anime
    if (path === '/api/anime') {
      const filterObj = {};
      if (inlineQs) {
        const usp = new URLSearchParams(inlineQs);
        for (const [k, v] of usp.entries()) {
          filterObj[k] = v;
        }
      }
      if (params) {
        if (params instanceof URLSearchParams) {
          for (const [k, v] of params.entries()) {
            filterObj[k] = v;
          }
        } else if (typeof params === 'object') {
          for (const [k, v] of Object.entries(params)) {
            if (v !== undefined && v !== null && v !== '') {
              filterObj[k] = v;
            }
          }
        }
      }
      return apiClient._parseBridgeResult(await bridge.queryAnime(JSON.stringify(filterObj)));
    }

    throw new Error(`Unsupported AndroidBridge GET endpoint: ${endpoint}`);
  },

  async _bridgePost(endpoint, body) {
    const bridge = window.AndroidBridge;
    const path = endpoint.split('?')[0];

    // Single anime watched: /api/anime/:id/watched
    const watchedMatch = path.match(/^\/api\/anime\/([^/?#]+)\/watched$/);
    if (watchedMatch) {
      const id = decodeURIComponent(watchedMatch[1]);
      let watchedVal = false;
      if (body) {
        const parsed = typeof body === 'string' ? JSON.parse(body) : body;
        watchedVal = Boolean(parsed.watched);
      }
      return apiClient._parseBridgeResult(await bridge.setWatched(id, watchedVal));
    }

    // Single anime meh: /api/anime/:id/meh
    const mehMatch = path.match(/^\/api\/anime\/([^/?#]+)\/meh$/);
    if (mehMatch) {
      const id = decodeURIComponent(mehMatch[1]);
      let mehVal = false;
      if (body) {
        const parsed = typeof body === 'string' ? JSON.parse(body) : body;
        mehVal = Boolean(parsed.meh);
      }
      return apiClient._parseBridgeResult(await bridge.setMeh(id, mehVal));
    }

    if (path === '/api/sync') {
      return apiClient._parseBridgeResult(await bridge.startCrSync());
    }

    if (path === '/api/sync/anilist') {
      const fn = bridge.startAniSync ? bridge.startAniSync.bind(bridge) : bridge.startAniListSync.bind(bridge);
      return apiClient._parseBridgeResult(await fn());
    }

    throw new Error(`Unsupported AndroidBridge POST endpoint: ${endpoint}`);
  },

  _parseBridgeResult(res) {
    if (res === null || res === undefined || res === '') {
      return null;
    }
    if (typeof res === 'string') {
      try {
        return JSON.parse(res);
      } catch (e) {
        return res;
      }
    }
    return res;
  }
};

// API Calls
async function loadGenres() {
  try {
    const genres = await apiClient.get('/api/genres');
    if (!genres) return;
    genreSelect.innerHTML = '<option value="">Alle Genres</option>';
    genres.forEach(g => {
      const opt = document.createElement('option');
      opt.value = g.name;
      opt.textContent = `${g.name} (${g.count})`;
      genreSelect.appendChild(opt);
    });
    if (state.genre) {
      genreSelect.value = state.genre;
    }
  } catch (err) {
    console.error('Failed to load genres:', err);
  }
}

async function loadTags() {
  try {
    const tags = await apiClient.get('/api/tags');
    if (!tags) return;
    state.allTags = tags;
    renderTagCloud(state.allTags);
    updateTagUI();
  } catch (err) {
    console.error('Failed to load tags:', err);
  }
}

function renderTagCloud(tags) {
  tagCloud.innerHTML = '';
  tags.forEach(t => {
    const chip = document.createElement('span');
    const st = state.tagStates.get(t.name);
    chip.className = `tag-chip ${st ? 'state-' + st : ''}`;
    chip.dataset.tag = t.name;

    let icon = '';
    if (st === 'blue') icon = '🔵 ';
    else if (st === 'green') icon = '🟢 ';
    else if (st === 'orange') icon = '🟠 ';

    chip.innerHTML = `<span class="tag-state-icon">${icon}</span><span>${escapeHtml(t.name)}</span> <span class="tag-count">(${t.count})</span>`;
    chip.addEventListener('click', () => {
      cycleTag(t.name);
    });
    tagCloud.appendChild(chip);
  });
}

function filterTagCloud(q) {
  const filtered = state.allTags.filter(t => t.name.toLowerCase().includes(q));
  renderTagCloud(filtered);
}

async function loadAnime() {
  saveSearchState();
  resultsCount.textContent = 'Lade...';

  const preferTags = [];
  const mustTags = [];
  const excludeTags = [];

  state.tagStates.forEach((st, tag) => {
    if (st === 'blue') preferTags.push(tag);
    else if (st === 'green') mustTags.push(tag);
    else if (st === 'orange') excludeTags.push(tag);
  });

  const params = new URLSearchParams({
    q: state.currentTab === 'search' ? state.query : '',
    genre: state.currentTab === 'search' ? state.genre : '',
    lang: state.currentTab === 'search' ? state.language : 'all',
    watched: state.watched,
    meh: state.meh,
    sort: state.sort,
    limit: 150,
  });

  if (preferTags.length > 0 && state.currentTab === 'search') params.set('prefer_tags', preferTags.join(','));
  if (mustTags.length > 0 && state.currentTab === 'search') params.set('must_tags', mustTags.join(','));
  if (excludeTags.length > 0 && state.currentTab === 'search') params.set('exclude_tags', excludeTags.join(','));

  try {
    const data = await apiClient.get('/api/anime', params);
    
    state.animeMap.clear();
    ((data && data.items) || []).forEach(a => state.animeMap.set(a.id, a));

    renderGrid((data && data.items) || []);
    resultsCount.textContent = `${(data && data.total) || 0} Anime gefunden`;
  } catch (err) {
    console.error('Failed to load anime:', err);
    resultsCount.textContent = 'Fehler beim Laden';
  }
}

function getLanguageFlagBadge(anime) {
  const hasDub = Boolean(anime.has_gerdub);
  const hasSub = Boolean(anime.has_gersub);

  if (hasDub) {
    // 1. GerDub vorhanden (oder beides): Vollfarbige Deutschland-Flagge
    const title = hasSub 
      ? 'Deutsche Synchro (GerDub) & Deutsche Untertitel (GerSub) vorhanden'
      : 'Deutsche Synchro (GerDub) vorhanden';
    return `
      <div class="badge-flag" title="${title}">
        <svg class="flag-svg" width="20" height="13" viewBox="0 0 20 13">
          <rect x="0" y="0" width="20" height="4.33" fill="#161616"/>
          <rect x="0" y="4.33" width="20" height="4.34" fill="#de0000"/>
          <rect x="0" y="8.67" width="20" height="4.33" fill="#ffce00"/>
        </svg>
      </div>`;
  } else if (hasSub) {
    // 2. Nur GerSub: Eine Hälfte grau, ein Strich durch
    return `
      <div class="badge-flag" title="Nur Deutsche Untertitel (GerSub) - Keine deutsche Synchro">
        <svg class="flag-svg" width="20" height="13" viewBox="0 0 20 13">
          <!-- Linke Hälfte: Schwarz, Rot, Gold -->
          <rect x="0" y="0" width="10" height="4.33" fill="#161616"/>
          <rect x="0" y="4.33" width="10" height="4.34" fill="#de0000"/>
          <rect x="0" y="8.67" width="10" height="4.33" fill="#ffce00"/>
          <!-- Rechte Hälfte: Grautöne -->
          <rect x="10" y="0" width="10" height="4.33" fill="#3a3a3a"/>
          <rect x="10" y="4.33" width="10" height="4.34" fill="#6a6a6a"/>
          <rect x="10" y="8.67" width="10" height="4.33" fill="#9a9a9a"/>
          <!-- Roter Strich durch die rechte Seite -->
          <line x1="10" y1="1" x2="19" y2="12" stroke="#ff3b30" stroke-width="1.6" stroke-linecap="round"/>
        </svg>
      </div>`;
  } else {
    // 3. Weder GerDub noch GerSub: Komplett ausgegraut
    return `
      <div class="badge-flag badge-flag-disabled" title="Keine deutsche Fassung (weder Dub noch Sub)">
        <svg class="flag-svg" width="20" height="13" viewBox="0 0 20 13">
          <rect x="0" y="0" width="20" height="4.33" fill="#3a3a3a"/>
          <rect x="0" y="4.33" width="20" height="4.34" fill="#6a6a6a"/>
          <rect x="0" y="8.67" width="20" height="4.33" fill="#9a9a9a"/>
        </svg>
      </div>`;
  }
}

function renderGrid(items) {
  animeGrid.innerHTML = '';
  if (items.length === 0) {
    emptyState.style.display = 'block';
    return;
  }
  emptyState.style.display = 'none';

  const blueTags = new Set();
  state.tagStates.forEach((st, tag) => {
    if (st === 'blue') blueTags.add(tag);
  });

  items.forEach(anime => {
    const card = document.createElement('div');
    card.className = `anime-card ${anime.watched ? 'is-watched' : ''} ${anime.is_meh ? 'is-meh' : ''}`;
    card.id = `card-${anime.id}`;

    const poster = anime.poster_tall || anime.poster_wide || '/placeholder.png';
    const scoreBadge = anime.anilist_score ? `<div class="badge-score">★ ${anime.anilist_score}%</div>` : '';
    const flagBadge = getLanguageFlagBadge(anime);
    const epCount = anime.episode_count ? `${anime.episode_count} Ep.` : 'Serie';

    let matchingBlueCount = 0;
    if (blueTags.size > 0 && anime.tags) {
      anime.tags.forEach(t => {
        if (blueTags.has(t.name)) matchingBlueCount++;
      });
    }
    const blueBadge = matchingBlueCount > 0 ? `<div class="badge-score" style="border-color:#3db4f2; color:#3db4f2;">🔵 +${matchingBlueCount} Tag${matchingBlueCount > 1 ? 's' : ''}</div>` : '';

    const restoreBtnHtml = state.currentTab === 'meh' 
      ? `<button class="card-restore-meh-btn"><span>↩</span> Wiederherstellen</button>` 
      : '';

    card.innerHTML = `
      <div class="card-poster-wrap">
        <img src="${escapeHtml(poster)}" alt="${escapeHtml(anime.title)}" class="card-poster" loading="lazy">
        <div class="card-badges">
          ${scoreBadge}
          ${blueBadge}
        </div>
        <div class="card-actions-wrap">
          <button class="card-action-btn btn-watched ${anime.watched ? 'watched' : ''}" title="${anime.watched ? 'Gesehen' : 'Als gesehen markieren'}">
            ✓
          </button>
          <button class="card-action-btn btn-meh ${anime.is_meh ? 'meh' : ''}" title="${anime.is_meh ? 'Meh entfernen' : 'Als Meh markieren (ausblenden)'}">
            😒
          </button>
        </div>
      </div>
      <div class="card-content">
        <h3 class="card-title">${escapeHtml(anime.title)}</h3>
        <div class="card-meta">
          <span>${epCount}</span>
          ${flagBadge}
        </div>
        ${restoreBtnHtml}
      </div>
    `;

    const watchBtn = card.querySelector('.btn-watched');
    watchBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      toggleWatched(anime.id, !anime.watched);
    });

    const mehBtn = card.querySelector('.btn-meh');
    mehBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      toggleMeh(anime.id, !anime.is_meh);
    });

    const restoreBtn = card.querySelector('.card-restore-meh-btn');
    if (restoreBtn) {
      restoreBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleMeh(anime.id, false);
      });
    }

    card.addEventListener('click', () => {
      openDetail(anime.id);
    });

    animeGrid.appendChild(card);
  });
}

async function toggleWatched(id, newStatus) {
  try {
    const result = await apiClient.post(`/api/anime/${id}/watched`, { watched: newStatus });
    const updated = result && result.anime;
    if (!updated) return;

    state.animeMap.set(id, updated);
    updateBadgeCounts();

    const card = document.getElementById(`card-${id}`);
    if (card) {
      card.classList.toggle('is-watched', updated.watched);
      const watchBtn = card.querySelector('.btn-watched');
      if (watchBtn) watchBtn.classList.toggle('watched', updated.watched);
    }

    if (state.activeDetailId === id) {
      updateModalButtons(updated);
    }

    if (state.currentTab === 'watched' || (state.currentTab === 'search' && state.watched !== 'all')) {
      loadAnime();
    }
  } catch (err) {
    console.error('Failed to toggle watched:', err);
  }
}

async function toggleMeh(id, newStatus) {
  try {
    const result = await apiClient.post(`/api/anime/${id}/meh`, { meh: newStatus });
    const updated = result && result.anime;
    if (!updated) return;

    state.animeMap.set(id, updated);
    updateBadgeCounts();

    if (state.activeDetailId === id) {
      updateModalButtons(updated);
    }

    if (state.currentTab === 'search' && updated.is_meh) {
      const card = document.getElementById(`card-${id}`);
      if (card) {
        card.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
        card.style.opacity = '0';
        card.style.transform = 'scale(0.8)';
        setTimeout(() => {
          card.remove();
          const currentCount = animeGrid.children.length;
          resultsCount.textContent = `${currentCount} Anime gefunden`;
          if (currentCount === 0) emptyState.style.display = 'block';
        }, 300);
      }
    } else {
      loadAnime();
    }
  } catch (err) {
    console.error('Failed to toggle meh:', err);
  }
}

async function updateBadgeCounts() {
  try {
    const dataWatched = await apiClient.get('/api/anime?watched=watched&meh=all&limit=1');
    if (dataWatched) {
      badgeWatched.textContent = dataWatched.total;
      badgeWatched.style.display = dataWatched.total > 0 ? 'inline-block' : 'none';
    }

    const dataMeh = await apiClient.get('/api/anime?meh=only&limit=1');
    if (dataMeh) {
      badgeMeh.textContent = dataMeh.total;
      badgeMeh.style.display = dataMeh.total > 0 ? 'inline-block' : 'none';
    }
  } catch (err) {
    console.error('Failed to update badges:', err);
  }
}

async function openDetail(id) {
  state.activeDetailId = id;
  const cached = state.animeMap.get(id);
  if (cached) {
    renderModalData(cached);
  }

  detailModal.style.display = 'flex';
  document.body.style.overflow = 'hidden';

  try {
    const fresh = await apiClient.get(`/api/anime/${id}`);
    if (fresh) {
      state.animeMap.set(id, fresh);
      renderModalData(fresh);
      loadTags();
    }
  } catch (err) {
    console.error('Failed to fetch detailed anime:', err);
  }
}

function renderModalData(anime) {
  modalTitle.textContent = anime.title;
  
  const subtitles = [];
  if (anime.anilist_title_romaji && anime.anilist_title_romaji !== anime.title) {
    subtitles.push(anime.anilist_title_romaji);
  }
  if (anime.anilist_title_english && anime.anilist_title_english !== anime.title) {
    subtitles.push(anime.anilist_title_english);
  }
  modalSubtitles.textContent = subtitles.join(' • ');

  const banner = anime.anilist_banner_image || anime.poster_wide;
  if (banner) {
    modalBanner.style.backgroundImage = `url('${banner}')`;
  } else {
    modalBanner.style.backgroundImage = 'none';
  }
  modalPoster.src = anime.poster_tall || anime.poster_wide || '';

  modalBadges.innerHTML = '';
  if (anime.episode_count) {
    modalBadges.innerHTML += `<span class="meta-badge">${anime.episode_count} Episoden</span>`;
  }
  if (anime.launch_year) {
    modalBadges.innerHTML += `<span class="meta-badge">Jahr: ${anime.launch_year}</span>`;
  }
  if (anime.has_gerdub) {
    modalBadges.innerHTML += `<span class="meta-badge badge-gerdub">🎙️ Deutsche Synchro (GerDub)</span>`;
  } else if (anime.is_dubbed) {
    modalBadges.innerHTML += `<span class="meta-badge">Synchro (Andere Sprache)</span>`;
  }
  if (anime.has_gersub) {
    modalBadges.innerHTML += `<span class="meta-badge badge-gersub">💬 Deutsche Untertitel (GerSub)</span>`;
  } else if (anime.is_subbed) {
    modalBadges.innerHTML += `<span class="meta-badge">Untertitel (Andere Sprache)</span>`;
  }

  modalGenres.innerHTML = '';
  (anime.genres || []).forEach(g => {
    const chip = document.createElement('span');
    chip.className = 'genre-chip';
    chip.textContent = g;
    modalGenres.appendChild(chip);
  });

  modalTags.innerHTML = '';
  if (anime.tags && anime.tags.length > 0) {
    modalTagsBox.style.display = 'block';
    anime.tags.forEach(t => {
      const chip = document.createElement('span');
      const st = state.tagStates.get(t.name);
      chip.className = `tag-chip ${st ? 'state-' + st : ''}`;
      chip.dataset.tag = t.name;

      let icon = '';
      if (st === 'blue') icon = '🔵 ';
      else if (st === 'green') icon = '🟢 ';
      else if (st === 'orange') icon = '🟠 ';

      chip.innerHTML = `<span class="tag-state-icon">${icon}</span><span>${escapeHtml(t.name)}</span> <span class="tag-count">(${t.rank}%)</span>`;
      chip.addEventListener('click', () => {
        cycleTag(t.name);
      });
      modalTags.appendChild(chip);
    });
  } else {
    modalTagsBox.style.display = 'none';
  }

  modalDescription.textContent = anime.description || 'Keine Beschreibung verfügbar.';

  updateModalButtons(anime);

  if (anime.anilist_id) {
    modalAniListSection.style.display = 'block';
    modalAniListScore.textContent = `★ ${anime.anilist_score || 0}% Score`;
    modalAniListStatus.textContent = anime.anilist_status || '';
    modalAniListLink.href = anime.anilist_site_url || `https://anilist.co/anime/${anime.anilist_id}`;
    
    const tempDiv = document.createElement('div');
    tempDiv.innerHTML = anime.anilist_description || '';
    modalAniListDesc.textContent = tempDiv.textContent || tempDiv.innerText || '';
  } else {
    modalAniListSection.style.display = 'none';
  }
}

function updateModalButtons(anime) {
  if (anime.watched) {
    modalWatchedBtn.classList.add('watched');
    modalWatchedBtn.querySelector('.watch-text').textContent = 'Bereits gesehen ✓';
  } else {
    modalWatchedBtn.classList.remove('watched');
    modalWatchedBtn.querySelector('.watch-text').textContent = 'Als gesehen markieren';
  }

  if (anime.is_meh) {
    modalMehBtn.classList.add('meh');
    modalMehBtn.querySelector('.meh-text').textContent = 'Meh entfernen (wieder anzeigen)';
  } else {
    modalMehBtn.classList.remove('meh');
    modalMehBtn.querySelector('.meh-text').textContent = 'Als Meh markieren (ausblenden)';
  }
}

function closeDetail() {
  detailModal.style.display = 'none';
  document.body.style.overflow = '';
  state.activeDetailId = null;
}

// -------------------------------------------------------------
// Settings & Sync Logic
// -------------------------------------------------------------
async function refreshSettingsData() {
  await updateCrunchyrollSyncStatus();
  await updateAniListSyncStatus();
  await loadStatistics();
}

async function loadStatistics() {
  try {
    const stats = await apiClient.get('/api/stats');
    if (!stats) return;
    statTotalAnime.textContent = stats.total_anime || 0;
    statWatchedAnime.textContent = stats.watched_anime || 0;
    statMehAnime.textContent = stats.meh_anime || 0;
    statEnrichedAnime.textContent = stats.anilist_enriched || 0;
    statTotalTags.textContent = stats.tags_count || 0;
  } catch (err) {
    console.error('Failed to load stats:', err);
  }
}

// Crunchyroll Sync
async function triggerCrunchyrollSync() {
  if (state.isCrSyncing) return;
  startCrSyncBtn.disabled = true;
  startCrSyncBtn.classList.add('syncing');

  try {
    await apiClient.post('/api/sync');
    pollCrunchyrollSync();
  } catch (err) {
    console.error('CR Sync trigger failed:', err);
    startCrSyncBtn.disabled = false;
    startCrSyncBtn.classList.remove('syncing');
  }
}

async function updateCrunchyrollSyncStatus() {
  try {
    const status = await apiClient.get('/api/sync/status');
    if (!status) return;

    state.isCrSyncing = status.is_running;
    startCrSyncBtn.disabled = status.is_running;
    startCrSyncBtn.classList.toggle('syncing', status.is_running);

    if (status.is_running) {
      crSyncStatusText.textContent = 'Synchronisiert...';
      const pct = status.total_items > 0 ? Math.min(100, Math.round((status.synced_count / status.total_items) * 100)) : 20;
      crProgressBar.style.width = `${pct}%`;
      crSyncCountText.textContent = `${status.synced_count} / ${status.total_items || '?'}`;
    } else {
      crSyncStatusText.textContent = status.last_error ? 'Fehler' : 'Bereit';
      crProgressBar.style.width = status.synced_count > 0 ? '100%' : '0%';
      crSyncCountText.textContent = `${status.synced_count} Serien`;
    }

    if (status.last_sync_time) {
      const date = new Date(status.last_sync_time);
      crLastSyncText.textContent = `Letzter Sync: ${date.toLocaleDateString()} ${date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
    }

    updateSyncBadge();
  } catch (err) {
    console.error('Error getting CR sync status:', err);
  }
}

function pollCrunchyrollSync() {
  state.isCrSyncing = true;
  updateSyncBadge();
  const interval = setInterval(async () => {
    try {
      const status = await apiClient.get('/api/sync/status');
      if (!status) return;

      updateCrunchyrollSyncStatus();

      if (!status.is_running) {
        clearInterval(interval);
        state.isCrSyncing = false;
        updateSyncBadge();
        loadGenres();
        loadTags();
        loadAnime();
        updateBadgeCounts();
        loadStatistics();
      }
    } catch (err) {
      clearInterval(interval);
      state.isCrSyncing = false;
      updateSyncBadge();
    }
  }, 1200);
}

// AniList Sync
async function triggerAniListSync() {
  if (state.isAniSyncing) return;
  startAniSyncBtn.disabled = true;
  startAniSyncBtn.classList.add('syncing');

  try {
    await apiClient.post('/api/sync/anilist');
    pollAniListSync();
  } catch (err) {
    console.error('AniList Sync trigger failed:', err);
    startAniSyncBtn.disabled = false;
    startAniSyncBtn.classList.remove('syncing');
  }
}

async function updateAniListSyncStatus() {
  try {
    const status = await apiClient.get('/api/sync/anilist/status');
    if (!status) return;

    state.isAniSyncing = status.is_running;
    startAniSyncBtn.disabled = status.is_running;
    startAniSyncBtn.classList.toggle('syncing', status.is_running);

    if (status.is_running) {
      aniSyncStatusText.textContent = status.status_message || 'Wird angereichert...';
      const pct = status.total_in_db > 0 ? Math.min(100, Math.round((status.already_done / status.total_in_db) * 100)) : 10;
      aniProgressBar.style.width = `${pct}%`;
      aniSyncCountText.textContent = `${status.already_done} / ${status.total_in_db} (${status.enriched_count} neu)`;
    } else {
      aniSyncStatusText.textContent = status.last_error ? 'Fehler' : 'Bereit';
      const pct = status.total_in_db > 0 ? Math.round((status.already_done / status.total_in_db) * 100) : 0;
      aniProgressBar.style.width = `${pct}%`;
      aniSyncCountText.textContent = `${status.already_done || 0} / ${status.total_in_db || 0}`;
    }

    if (status.last_sync_time) {
      const date = new Date(status.last_sync_time);
      aniLastSyncText.textContent = `Letzter Lauf: ${date.toLocaleDateString()} ${date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
    }

    updateSyncBadge();
  } catch (err) {
    console.error('Error getting AniList sync status:', err);
  }
}

function pollAniListSync() {
  state.isAniSyncing = true;
  updateSyncBadge();
  const interval = setInterval(async () => {
    try {
      const status = await apiClient.get('/api/sync/anilist/status');
      if (!status) return;

      updateAniListSyncStatus();

      if (!status.is_running) {
        clearInterval(interval);
        state.isAniSyncing = false;
        updateSyncBadge();
        loadTags();
        loadAnime();
        loadStatistics();
      }
    } catch (err) {
      clearInterval(interval);
      state.isAniSyncing = false;
      updateSyncBadge();
    }
  }, 1200);
}

function updateSyncBadge() {
  const isAnySyncing = state.isCrSyncing || state.isAniSyncing;
  badgeSettingsSync.style.display = isAnySyncing ? 'inline-block' : 'none';
}

async function checkInitialSyncStatuses() {
  await updateCrunchyrollSyncStatus();
  await updateAniListSyncStatus();
  if (state.isCrSyncing) pollCrunchyrollSync();
  if (state.isAniSyncing) pollAniListSync();
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/[&<>"']/g, m => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;'
  })[m]);
}

// Export apiClient for testing and window attachment
if (typeof window !== 'undefined') {
  window.apiClient = apiClient;
}
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { apiClient };
}
