import { t } from './text.js';
// A finite, audible WAV uses the normal file player rather than srcObject/MediaStream.
export function createProbeWav(frequency = 440, seconds = 30) {
  const rate = 16000, samples = rate * seconds, buffer = new ArrayBuffer(44 + samples * 2), view = new DataView(buffer);
  const text = (at, value) => [...value].forEach((c, i) => view.setUint8(at + i, c.charCodeAt(0)));
  text(0, 'RIFF'); view.setUint32(4, buffer.byteLength - 8, true); text(8, 'WAVE'); text(12, 'fmt ');
  view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true);
  view.setUint32(24, rate, true); view.setUint32(28, rate * 2, true); view.setUint16(32, 2, true); view.setUint16(34, 16, true);
  text(36, 'data'); view.setUint32(40, samples * 2, true);
  for (let i = 0; i < samples; i++) {
    // Gentle tone bursts and short fades keep the test recognizable without loud clicks.
    const phase = i % rate, fade = Math.min(1, phase / 160, Math.max(0, rate * .5 - phase) / 160);
    view.setInt16(44 + i * 2, Math.round(2400 * Math.max(0, fade) * Math.sin(2 * Math.PI * frequency * i / rate)), true);
  }
  return buffer;
}

export function createMediaProbe({ element, session = globalThis.navigator?.mediaSession,
  Metadata = globalThis.MediaMetadata, urls = globalThis.URL,
  onEnter = () => {}, onExit = () => {}, now = () => Date.now() } = {}) {
  const diagnostics = { active: false, registered: [], events: [], error: null, track: 0 };
  let active = false, generation = 0, url = null, track = 0, timer = null;
  const actions = ['play', 'pause', 'nexttrack', 'previoustrack'];
  const event = (type, detail = {}) => {
    diagnostics.events.push({ at: now(), type, ...detail }); if (diagnostics.events.length > 60) diagnostics.events.shift();
  };
  const attempt = fn => { try { fn(); } catch (error) { diagnostics.error = String(error.message || error); event('error', { message: diagnostics.error }); } };
  function metadata() {
    attempt(() => {
      if (!session) return;
      if (Metadata) session.metadata = new Metadata({ title: t('xp_web_probe_title', track + 1), artist: t('xp_web_probe_artist'), album: t('xp_web_probe_album') });
      session.playbackState = element.paused ? 'paused' : 'playing';
      session.setPositionState?.();
    });
  }
  function load() {
    element.pause(); element.removeAttribute('src'); element.load();
    if (url) urls.revokeObjectURL(url);
    url = urls.createObjectURL(new Blob([createProbeWav(track ? 660 : 440)], { type: 'audio/wav' }));
    element.src = url; diagnostics.track = track; metadata(); event('track', { track });
  }
  async function play() {
    const token = generation;
    try { await element.play(); if (active && token === generation) metadata(); }
    catch (error) { if (active && token === generation) { diagnostics.error = String(error.message || error); event('play-error', { message: diagnostics.error }); } }
  }
  function action(name, origin = 'page') {
    if (!active || !actions.includes(name)) return false;
    event('action', { action: name, origin });
    if (name === 'pause') { element.pause(); metadata(); }
    else { if (name !== 'play') { track = 1 - track; load(); } play(); }
    return true;
  }
  function stop(reason = 'user') {
    if (!active) return;
    active = diagnostics.active = false; generation++; clearTimeout(timer); timer = null;
    element.pause(); element.removeAttribute('src'); element.load();
    if (url) urls.revokeObjectURL(url); url = null;
    for (const name of actions) attempt(() => session?.setActionHandler(name, null));
    attempt(() => { if (session) { session.metadata = null; session.playbackState = 'none'; session.setPositionState?.(); } });
    event('stop', { reason }); onExit();
  }
  for (const type of ['playing', 'pause', 'waiting', 'stalled', 'ended', 'error', 'loadedmetadata']) element.addEventListener(type, () => {
    if (!active) return;
    event(type, { paused: element.paused, readyState: element.readyState, currentTime: element.currentTime, error: element.error?.code ?? null });
    if (type === 'ended') stop('ended'); else if (type === 'playing' || type === 'pause') metadata();
  });
  return { diagnostics, action, stop,
    start() {
      if (active) return;
      onEnter(); active = diagnostics.active = true; generation++; diagnostics.error = null; diagnostics.registered = []; track = 0;
      const token = generation;
      for (const name of actions) attempt(() => { if (session) { session.setActionHandler(name, () => { if (generation === token) action(name, 'media-session'); }); diagnostics.registered.push(name); } });
      load(); play(); timer = setTimeout(() => stop('timeout'), 120000); event('start');
    },
    snapshot() { return { ...diagnostics, paused: element.paused, readyState: element.readyState, currentTime: element.currentTime,
      source: 'wav-blob', visible: !element.hidden, mediaError: element.error?.code ?? null }; },
    get active() { return active; },
  };
}

export function mediaCapabilities(scope = globalThis, element) {
  const formats = ['audio/mp4; codecs="mp4a.40.2"', 'audio/webm; codecs="opus"', 'audio/wav'];
  return formats.map(type => {
    try { return { type, file: element.canPlayType(type), mse: !!scope.MediaSource?.isTypeSupported(type) }; }
    catch (error) { return { type, error: String(error.message || error) }; }
  });
}
