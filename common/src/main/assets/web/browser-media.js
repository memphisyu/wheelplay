// Only the current paired browser owns this media session. No commands survive reconnection.
export function createBrowserMedia({ send, session = globalThis.navigator?.mediaSession,
  Metadata = globalThis.MediaMetadata } = {}) {
  const diagnostics = { supported: !!session, metadata: null, actions: {}, lastAction: null, error: null };
  let enabled = false, state = null, key = '', registered = false;
  const actions = { play: 'PLAY', pause: 'PAUSE', nexttrack: 'NEXT', previoustrack: 'PREVIOUS' };
  const attempt = operation => { try { operation(); } catch (error) { diagnostics.error = String(error?.message || error); } };
  function clear() {
    state = null; key = ''; registered = false;
    if (!session) return;
    attempt(() => { session.metadata = null; session.playbackState = 'none'; });
    attempt(() => session.setPositionState());
    for (const action of Object.keys(actions)) attempt(() => session.setActionHandler(action, null));
  }
  function render() {
    if (!session || !enabled || !state?.available) { clear(); return; }
    if (!registered) {
      for (const [action, command] of Object.entries(actions)) attempt(() => session.setActionHandler(action, () => {
        if (enabled && state?.available) { diagnostics.lastAction = command; diagnostics.actions[command] = (diagnostics.actions[command] || 0) + 1; send({ type: 'media-command', command, generation: state.generation }); }
      }));
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
    control(data) {
      if (data.type === 'audio-route') { enabled = !!data.playback; if (!enabled) clear(); else render(); }
      if (data.type === 'now-playing') { state = data; diagnostics.metadata = { available: !!data.available, title: data.title, artist: data.artist, playing: data.playing }; render(); }
    },
    clear() { enabled = false; clear(); },
  };
}
