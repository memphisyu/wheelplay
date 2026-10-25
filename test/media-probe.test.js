import test from 'node:test';
import { resourceStrings } from '../scripts/resource-strings.mjs';
globalThis.wheelplayMessages = resourceStrings('values-zh-rCN');
import assert from 'node:assert/strict';
import { createMediaProbe, createProbeWav, mediaCapabilities } from '../common/src/main/assets/web/media-probe.js';
function fixture(reject = false) {
  const events = {}, handlers = {}, revoked = [], order = [];
  const element = { paused: true, readyState: 4, currentTime: 0, hidden: false,
    addEventListener: (name, handler) => events[name] = handler,
    pause() { this.paused = true; events.pause?.(); }, load() {}, removeAttribute() {},
    async play() { if (reject) throw Error('autoplay blocked'); this.paused = false; events.playing?.(); },
    canPlayType() { return 'probably'; } };
  const session = { setActionHandler: (name, handler) => handlers[name] = handler, setPositionState() {} };
  let next = 0;
  const probe = createMediaProbe({element, session, Metadata: class { constructor(v) { Object.assign(this, v); } },
    urls: {createObjectURL: () => `blob:${++next}`, revokeObjectURL: url => revoked.push(url)},
    onEnter: () => order.push('enter'), onExit: () => order.push('exit')});
  return {element, session, handlers, revoked, order, probe, events};
}
test('probe WAV has correct finite duration, audible samples, and bounded amplitude', () => {
  const bytes = createProbeWav(440, 1), view = new DataView(bytes);
  assert.equal(bytes.byteLength, 32044); assert.equal(view.getUint32(24,true),16000);
  assert.equal(view.getUint32(40,true),32000);
  const pcm = new Int16Array(bytes,44); assert.ok(pcm.some(v => v !== 0));
  assert.ok(pcm.every(v => Math.abs(v) <= 2400));
});
test('file probe owns media controls, switches real sources and cleans up stale handlers', async () => {
  const {probe,element,session,handlers,revoked,order} = fixture();
  probe.start(); await Promise.resolve();
  assert.deepEqual(order,['enter']); assert.equal(element.src,'blob:1'); assert.equal(element.srcObject,undefined);
  assert.equal(session.metadata.title,'WheelPlay 音频检查 1'); assert.equal(session.playbackState,'playing');
  handlers.nexttrack(); await Promise.resolve(); assert.equal(element.src,'blob:2'); assert.equal(session.metadata.title,'WheelPlay 音频检查 2');
  assert.ok(probe.diagnostics.events.some(e => e.origin === 'media-session'));
  const stale = handlers.nexttrack; probe.stop(); stale();
  assert.deepEqual(revoked,['blob:1','blob:2']); assert.deepEqual(order,['enter','exit']);
  assert.equal(handlers.nexttrack,null); assert.equal(session.metadata,null); assert.equal(element.paused,true);
  assert.equal(probe.active,false); probe.stop(); assert.equal(order.length,2);
});
test('autoplay failure is recorded and exit still restores ownership', async () => {
  const {probe,order} = fixture(true); probe.start(); await Promise.resolve();
  assert.equal(probe.diagnostics.error,'autoplay blocked'); probe.stop(); assert.deepEqual(order,['enter','exit']);
});
test('ended probe exits automatically; capabilities distinguish file and MSE support', () => {
  const {probe,events,element} = fixture(); probe.start(); events.ended(); assert.equal(probe.active,false);
  const caps = mediaCapabilities({MediaSource:{isTypeSupported:t=>t.startsWith('audio/mp4')}},element);
  assert.equal(caps[0].mse,true); assert.equal(caps[1].mse,false); assert.equal(caps[0].file,'probably');
});
test('old probe handlers do not act on a restarted test', () => {
 const {probe,handlers}=fixture(); probe.start(); const stale=handlers.nexttrack; probe.stop();
 probe.start(); stale(); assert.equal(probe.diagnostics.track,0); handlers.nexttrack(); assert.equal(probe.diagnostics.track,1); probe.stop();
});
