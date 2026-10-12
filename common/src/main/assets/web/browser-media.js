// Only the current paired browser owns this media session. No commands survive reconnection.
export function createBrowserMedia({ send, session = globalThis.navigator?.mediaSession,
  Metadata = globalThis.MediaMetadata, now = () => Date.now() } = {}) {
  const diagnostics = { supported: !!session, metadata: null, changes: [], actions: {}, registered: [], commands: [], lastAction: null, error: null };
  let enabled = false, suspended = false, state = null, key = '', registered = false, requestSequence = 0, handlerEpoch = 0;
  const actions = { play: 'PLAY', pause: 'PAUSE', nexttrack: 'NEXT', previoustrack: 'PREVIOUS' };
  const attempt = operation => { try { operation(); } catch (error) { diagnostics.error = String(error?.message || error); } };
  function command(command, origin = 'page') {
    if (!enabled || suspended || !state?.available || !Object.values(actions).includes(command)) return false;
    const record = { requestId: ++requestSequence, command, generation: state.generation, origin, at: now(), result: 'PENDING' };
    diagnostics.lastAction = command;
    if (origin === 'media-session') diagnostics.actions[command] = (diagnostics.actions[command] || 0) + 1;
    diagnostics.commands.push(record); if (diagnostics.commands.length > 40) diagnostics.commands.shift();
    record.result = send({ type: 'media-command', command, generation: record.generation, requestId: record.requestId }) === false ? 'NOT_SENT' : 'PENDING';
    return record.result !== 'NOT_SENT';
  }
  function clear() {
    state = null; key = ''; registered = false; handlerEpoch++; diagnostics.registered = [];
    for (const record of diagnostics.commands) if (record.result === 'PENDING') record.result = 'DISCONNECTED';
    if (!session) return;
    attempt(() => { session.metadata = null; session.playbackState = 'none'; });
    attempt(() => session.setPositionState());
    for (const action of Object.keys(actions)) attempt(() => session.setActionHandler(action, null));
  }
  function render() {
    if (suspended) return;
    if (!session || !enabled || !state?.available) { clear(); return; }
    if (!registered) {
      const epoch = handlerEpoch;
      for (const [action, value] of Object.entries(actions)) attempt(() => {
        session.setActionHandler(action, () => { if (handlerEpoch === epoch) command(value, 'media-session'); }); diagnostics.registered.push(action);
      });
      registered = true;
    }
    const nextKey = JSON.stringify([state.title, state.artist, state.album]);
    if (Metadata && key !== nextKey) attempt(() => {
      session.metadata = new Metadata({ title: state.title, artist: state.artist, album: state.album }); key = nextKey;
    });
    attempt(() => { session.playbackState = state.playing ? 'playing' : 'paused'; });
    attempt(() => {
      if (Number.isFinite(state.duration) && state.duration > 0 && Number.isFinite(state.position))
        session.setPositionState({ duration: state.duration, position: Math.max(0, Math.min(state.position, state.duration)), playbackRate: 1 });
      else session.setPositionState();
    });
  }
  return {
    diagnostics,
    command,
    suspend(value) {
      const saved = state; clear(); state = saved; suspended = value; if (!value) render();
    },
    expire() { for (const record of diagnostics.commands) if (record.result === 'PENDING' && now() - record.at >= 5000) record.result = 'TIMEOUT'; },
    control(data) {
      if (data.type === 'media-command-result') {
        const record = diagnostics.commands.find(r => r.requestId === data.requestId && r.generation === data.generation);
        if (record && ['PENDING', 'TIMEOUT'].includes(record.result)) { record.result = data.result; record.completedAt = now(); }
      }
      if (data.type === 'audio-route') { enabled = !!data.playback; if (!suspended) { if (!enabled) clear(); else render(); } }
      if (data.type === 'now-playing') {
        state = data;
        const next = { available: !!data.available, generation: data.generation, title: data.title, artist: data.artist, album: data.album, playing: data.playing };
        if (JSON.stringify(next) !== JSON.stringify(diagnostics.metadata)) {
          diagnostics.changes.push({ at: now(), ...next }); if (diagnostics.changes.length > 40) diagnostics.changes.shift();
        }
        diagnostics.metadata = next; render();
      }
    },
    clear() { enabled = false; suspended = false; clear(); },
  };
}
