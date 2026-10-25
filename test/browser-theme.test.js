import test from 'node:test';
import assert from 'node:assert/strict';
import { createBrowserTheme } from '../common/src/main/assets/web/browser-theme.js';
function fixture(query = { matches: false, addEventListener(_, cb) { this.changed = cb; } }) {
 const sent = [], target = { dataset: {} };
 const theme = createBrowserTheme({ query, target, send: x => sent.push(x) });
 return { sent, target, query, theme };
}
test('car-browser changes update both local theme and the authenticated host report', () => {
 const f = fixture(); f.theme.open();
 assert.deepEqual(f.sent, [{ type: 'browser-theme', night: false }]);
 f.theme.receive({ appearance: 'BROWSER', night: false });
 f.query.matches = true; f.query.changed();
 assert.equal(f.target.dataset.theme, 'night'); assert.equal(f.sent.at(-1).night, true);
 f.theme.close(); const count = f.sent.length; f.query.matches = false; f.query.changed();
 assert.equal(f.sent.length, count, 'No reports after closing the connection');
});
test('scheduled and fixed modes follow host decisions rather than browser preference', () => {
 const f = fixture(); f.theme.open();
 f.theme.receive({ appearance: 'SCHEDULE', night: true }); assert.equal(f.target.dataset.theme, 'night');
 f.query.changed(); assert.equal(f.target.dataset.theme, 'night');
 f.theme.receive({ appearance: 'SCHEDULE', night: false }); assert.equal(f.target.dataset.theme, 'day');
 f.theme.receive({ appearance: 'NIGHT', night: true }); assert.equal(f.target.dataset.theme, 'night');
});
test('unavailable browser theme falls back to the host schedule without inventing a signal', () => {
 for (const query of [null, { media: 'not all', matches: false }]) {
  const f = fixture(query); f.theme.open(); f.theme.receive({ appearance: 'BROWSER', night: true });
  assert.equal(f.sent.length, 0); assert.equal(f.target.dataset.theme, 'night');
 }
});


test('browser theme signals pass through the actual control outbox', async () => {
 const { createControlOutbox } = await import('../common/src/main/assets/web/app.js');
 const sent = [], socket = { readyState: 1, bufferedAmount: 0, send: value => sent.push(JSON.parse(value)) };
 const outbox = createControlOutbox(socket);
 const target = { dataset: {} }, query = { matches: true, addEventListener(_, callback) { this.changed = callback; } };
 const theme = createBrowserTheme({ target, query, send: value => outbox.send(value) });
 theme.open(); assert.deepEqual(sent, [{ type: 'browser-theme', night: true }]);
 query.matches = false; query.changed(); assert.equal(sent.at(-1).night, false);
 outbox.close();
});
