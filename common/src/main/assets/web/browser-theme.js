export function createBrowserTheme({ send, target = globalThis.document?.documentElement,
  query = globalThis.matchMedia?.('(prefers-color-scheme: dark)') ?? null } = {}) {
  if (query?.media === "not all") query = null;
  let opened = false, mode = null, serverNight = false;
  const render = () => { if (target?.dataset) target.dataset.theme = ((mode === null || mode === 'BROWSER') && query ? query.matches : serverNight) ? 'night' : 'day'; };
  const report = () => { if (opened && query) send({ type: 'browser-theme', night: query.matches === true }); };
  const changed = () => { render(); report(); };
  if (query?.addEventListener) query.addEventListener('change', changed);
  else query?.addListener?.(changed);
  render();
  return {
    open() { opened = true; report(); },
    close() { opened = false; },
    receive(data) { if (typeof data.night !== 'boolean') return; mode = data.appearance; serverNight = data.night; render(); },
  };
}
