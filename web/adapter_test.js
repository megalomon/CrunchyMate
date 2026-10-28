const assert = require('assert');

// Setup minimal browser mocks for loading app.js in Node
global.window = {
  addEventListener: () => {},
};
global.document = {
  getElementById: () => ({
    addEventListener: () => {},
    classList: { add: () => {}, remove: () => {}, toggle: () => {} },
    style: {},
    appendChild: () => {},
    innerHTML: '',
    textContent: '',
  }),
  addEventListener: () => {},
};

const app = require('./static/app.js');
const apiClient = app.apiClient || global.window.apiClient;

if (!apiClient) {
  console.error('FAIL: apiClient is not defined');
  process.exit(1);
}

async function runTests() {
  console.log('Running adapter tests...');

  // ==========================================
  // Suite 1: Fallback to fetch when AndroidBridge is undefined
  // ==========================================
  delete global.window.AndroidBridge;

  const fetchCalls = [];
  global.window.fetch = async (url, options = {}) => {
    fetchCalls.push({ url, options });
    if (url.startsWith('/api/error')) {
      return {
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        json: async () => ({ error: 'fail' }),
        text: async () => JSON.stringify({ error: 'fail' }),
      };
    }
    return {
      ok: true,
      status: 200,
      statusText: 'OK',
      json: async () => ({ source: 'fetch', url, body: options.body }),
      text: async () => JSON.stringify({ source: 'fetch', url, body: options.body }),
    };
  };

  // Test 1.1: GET request without params
  {
    fetchCalls.length = 0;
    const res = await apiClient.get('/api/genres');
    assert.strictEqual(fetchCalls.length, 1);
    assert.strictEqual(fetchCalls[0].url, '/api/genres');
    assert.strictEqual(res.source, 'fetch');
  }

  // Test 1.2: GET request with params object
  {
    fetchCalls.length = 0;
    const res = await apiClient.get('/api/anime', { watched: 'all', limit: 10 });
    assert.strictEqual(fetchCalls.length, 1);
    assert.strictEqual(fetchCalls[0].url, '/api/anime?watched=all&limit=10');
    assert.strictEqual(res.source, 'fetch');
  }

  // Test 1.3: GET request with URLSearchParams
  {
    fetchCalls.length = 0;
    const usp = new URLSearchParams({ q: 'frieren' });
    const res = await apiClient.get('/api/anime', usp);
    assert.strictEqual(fetchCalls.length, 1);
    assert.strictEqual(fetchCalls[0].url, '/api/anime?q=frieren');
  }

  // Test 1.4: GET request with inline query string in URL
  {
    fetchCalls.length = 0;
    const res = await apiClient.get('/api/anime?meh=only&limit=1');
    assert.strictEqual(fetchCalls.length, 1);
    assert.strictEqual(fetchCalls[0].url, '/api/anime?meh=only&limit=1');
  }

  // Test 1.5: POST request with JSON body
  {
    fetchCalls.length = 0;
    const res = await apiClient.post('/api/anime/123/watched', { watched: true });
    assert.strictEqual(fetchCalls.length, 1);
    assert.strictEqual(fetchCalls[0].url, '/api/anime/123/watched');
    assert.strictEqual(fetchCalls[0].options.method, 'POST');
    assert.strictEqual(fetchCalls[0].options.headers['Content-Type'], 'application/json');
    assert.strictEqual(fetchCalls[0].options.body, '{"watched":true}');
    assert.strictEqual(res.source, 'fetch');
  }

  // Test 1.6: POST request without body
  {
    fetchCalls.length = 0;
    const res = await apiClient.post('/api/sync');
    assert.strictEqual(fetchCalls.length, 1);
    assert.strictEqual(fetchCalls[0].url, '/api/sync');
    assert.strictEqual(fetchCalls[0].options.method, 'POST');
  }

  // Test 1.7: Error handling on fetch non-ok response
  {
    let threw = false;
    try {
      await apiClient.get('/api/error');
    } catch (err) {
      threw = true;
      assert(err.message.includes('500'));
    }
    assert.strictEqual(threw, true, 'Expected apiClient to throw on !res.ok');
  }

  // ==========================================
  // Suite 2: Bridge invocation when AndroidBridge is defined
  // ==========================================
  const bridgeCalls = [];
  global.window.AndroidBridge = {
    getGenres: () => {
      bridgeCalls.push('getGenres');
      return JSON.stringify([{ name: 'Action', count: 5 }]);
    },
    getTags: () => {
      bridgeCalls.push('getTags');
      return JSON.stringify([{ name: 'Magic', count: 3 }]);
    },
    getStats: () => {
      bridgeCalls.push('getStats');
      return JSON.stringify({ total_anime: 42 });
    },
    getCrSyncStatus: () => {
      bridgeCalls.push('getCrSyncStatus');
      return JSON.stringify({ is_running: false });
    },
    getAniSyncStatus: () => {
      bridgeCalls.push('getAniSyncStatus');
      return JSON.stringify({ is_running: false });
    },
    getAnime: (id) => {
      bridgeCalls.push(['getAnime', id]);
      return JSON.stringify({ id, title: 'Test Anime' });
    },
    queryAnime: (filterJson) => {
      bridgeCalls.push(['queryAnime', filterJson]);
      return JSON.stringify({ total: 1, items: [{ id: '1', title: 'Filtered' }] });
    },
    setWatched: (id, watched) => {
      bridgeCalls.push(['setWatched', id, watched]);
      return JSON.stringify({ success: true, anime: { id, watched } });
    },
    setMeh: (id, meh) => {
      bridgeCalls.push(['setMeh', id, meh]);
      return JSON.stringify({ success: true, anime: { id, is_meh: meh } });
    },
    startCrSync: () => {
      bridgeCalls.push('startCrSync');
      return JSON.stringify({ status: 'started' });
    },
    startAniSync: () => {
      bridgeCalls.push('startAniSync');
      return JSON.stringify({ status: 'started' });
    },
  };

  // Test 2.1: getGenres
  {
    bridgeCalls.length = 0;
    const res = await apiClient.get('/api/genres');
    assert.deepStrictEqual(bridgeCalls, ['getGenres']);
    assert.deepStrictEqual(res, [{ name: 'Action', count: 5 }]);
  }

  // Test 2.2: getTags
  {
    bridgeCalls.length = 0;
    const res = await apiClient.get('/api/tags');
    assert.deepStrictEqual(bridgeCalls, ['getTags']);
    assert.deepStrictEqual(res, [{ name: 'Magic', count: 3 }]);
  }

  // Test 2.3: getStats
  {
    bridgeCalls.length = 0;
    const res = await apiClient.get('/api/stats');
    assert.deepStrictEqual(bridgeCalls, ['getStats']);
    assert.deepStrictEqual(res, { total_anime: 42 });
  }

  // Test 2.4: getCrSyncStatus
  {
    bridgeCalls.length = 0;
    const res = await apiClient.get('/api/sync/status');
    assert.deepStrictEqual(bridgeCalls, ['getCrSyncStatus']);
    assert.deepStrictEqual(res, { is_running: false });
  }

  // Test 2.5: getAniSyncStatus / getAniListSyncStatus
  {
    bridgeCalls.length = 0;
    const res = await apiClient.get('/api/sync/anilist/status');
    assert.deepStrictEqual(bridgeCalls, ['getAniSyncStatus']);
    assert.deepStrictEqual(res, { is_running: false });
  }

  // Test 2.6: getAnime by id
  {
    bridgeCalls.length = 0;
    const res = await apiClient.get('/api/anime/G62P407P6');
    assert.deepStrictEqual(bridgeCalls, [['getAnime', 'G62P407P6']]);
    assert.deepStrictEqual(res, { id: 'G62P407P6', title: 'Test Anime' });
  }

  // Test 2.7: queryAnime via query string
  {
    bridgeCalls.length = 0;
    const res = await apiClient.get('/api/anime?watched=watched&meh=all&limit=1');
    assert.strictEqual(bridgeCalls.length, 1);
    assert.strictEqual(bridgeCalls[0][0], 'queryAnime');
    const filter = JSON.parse(bridgeCalls[0][1]);
    assert.strictEqual(filter.watched, 'watched');
    assert.strictEqual(filter.meh, 'all');
    assert.strictEqual(filter.limit, '1');
    assert.deepStrictEqual(res, { total: 1, items: [{ id: '1', title: 'Filtered' }] });
  }

  // Test 2.8: queryAnime via params object
  {
    bridgeCalls.length = 0;
    const res = await apiClient.get('/api/anime', { q: 'Titan', sort: 'score' });
    assert.strictEqual(bridgeCalls.length, 1);
    assert.strictEqual(bridgeCalls[0][0], 'queryAnime');
    const filter = JSON.parse(bridgeCalls[0][1]);
    assert.strictEqual(filter.q, 'Titan');
    assert.strictEqual(filter.sort, 'score');
  }

  // Test 2.9: setWatched
  {
    bridgeCalls.length = 0;
    const res = await apiClient.post('/api/anime/G62P407P6/watched', { watched: true });
    assert.deepStrictEqual(bridgeCalls, [['setWatched', 'G62P407P6', true]]);
    assert.deepStrictEqual(res, { success: true, anime: { id: 'G62P407P6', watched: true } });
  }

  // Test 2.10: setMeh
  {
    bridgeCalls.length = 0;
    const res = await apiClient.post('/api/anime/G62P407P6/meh', { meh: true });
    assert.deepStrictEqual(bridgeCalls, [['setMeh', 'G62P407P6', true]]);
    assert.deepStrictEqual(res, { success: true, anime: { id: 'G62P407P6', is_meh: true } });
  }

  // Test 2.11: startCrSync
  {
    bridgeCalls.length = 0;
    const res = await apiClient.post('/api/sync');
    assert.deepStrictEqual(bridgeCalls, ['startCrSync']);
    assert.deepStrictEqual(res, { status: 'started' });
  }

  // Test 2.12: startAniSync
  {
    bridgeCalls.length = 0;
    const res = await apiClient.post('/api/sync/anilist');
    assert.deepStrictEqual(bridgeCalls, ['startAniSync']);
    assert.deepStrictEqual(res, { status: 'started' });
  }

  // Test 2.13: bridge method returning Promise or object directly
  {
    global.window.AndroidBridge.getStats = async () => ({ total_anime: 99 });
    const res = await apiClient.get('/api/stats');
    assert.deepStrictEqual(res, { total_anime: 99 });
  }

  console.log('All adapter tests PASSED!');
}

runTests().catch(err => {
  console.error('Test FAILED:', err);
  process.exit(1);
});
