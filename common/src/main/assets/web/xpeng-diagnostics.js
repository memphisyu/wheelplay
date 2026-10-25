import { createMediaProbe, mediaCapabilities } from './media-probe.js';
export function createXpengDiagnostics({ browserAudio, browserMedia, audio, stats }) {
  const el = id => document.getElementById(id);
  const panel = el('media-test-panel'), output = el('media-diagnostics-output');
  const probe = createMediaProbe({ element: el('probe-audio'),
    onEnter: () => { browserMedia.suspend(true); browserAudio.setTestMode(true); },
    onExit: () => { browserAudio.setTestMode(false); browserMedia.suspend(false); } });
  const snapshot = () => ({ media: browserMedia.snapshot(), audio: audio(), performance: stats(),
    probe: probe.snapshot(), capabilities: mediaCapabilities(globalThis, el('probe-audio')) });
  function render() { output.textContent = JSON.stringify(snapshot(), null, 2); }
  el('media-diagnostics').addEventListener('click', () => { output.hidden = !output.hidden; panel.hidden = output.hidden; if (!output.hidden) render(); });
  el('probe-start').addEventListener('click', () => { probe.start(); render(); });
  el('probe-stop').addEventListener('click', () => { probe.stop('user'); render(); });
  el('probe-visible').addEventListener('change', () => { el('probe-audio').hidden = !el('probe-visible').checked; });
  el('media-panel-close').addEventListener('click', () => { probe.stop('close'); panel.hidden = true; output.hidden = true; });
  for (const button of document.querySelectorAll?.('[data-media-action]') || []) button.addEventListener('click', () => {
    const action = button.dataset.mediaAction;
    if (probe.active) probe.action(action, 'page'); else browserMedia.trigger(action);
    render();
  });
  el('media-export').addEventListener('click', () => {
    const url = URL.createObjectURL(new Blob([JSON.stringify(snapshot(), null, 2)], { type: 'application/json' }));
    const link = document.createElement('a'); link.href = url; link.download = 'WheelPlay-media-diagnostics.json'; link.click(); URL.revokeObjectURL(url);
  });
  return { stop() { probe.stop('disconnect'); } };
}
