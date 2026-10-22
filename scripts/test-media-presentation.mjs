// Real Chromium + WebSocket + synthetic JPEGs. No phone or CarPlay hardware is simulated.
import assert from 'node:assert/strict';
import http from 'node:http';
import { readFile, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn, spawnSync } from 'node:child_process';
import WebSocket, { WebSocketServer } from 'ws';
import ffmpeg from 'ffmpeg-static';

const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const root = new URL('../common/src/main/assets/web/', import.meta.url);
const profile = await mkdtemp(join(tmpdir(), 'wheelplay-media-'));
function jpeg(width, height) {
  const result = spawnSync(process.env.FFMPEG_BIN || ffmpeg, ['-v', 'error', '-f', 'lavfi', '-i', `testsrc=size=${width}x${height}:rate=1`,
    '-frames:v', '1', '-f', 'image2pipe', '-vcodec', 'mjpeg', 'pipe:1']);
  assert.equal(result.status, 0, result.stderr.toString()); return result.stdout;
}
const landscape = jpeg(320, 180), portrait = jpeg(180, 320);
let viewer, connections = 0, acks = 0, paused = false;
const touches = [], errors = [];
let media = { type: 'status', mediaProtocol: 2, sessionId: 7, videoEpoch: 1, width: 320, height: 180,
  sourceState: 'ready', inputReady: true, streaming: true, stage: 'Synthetic browser test' };
const server = http.createServer(async (req, res) => {
  if (req.url.startsWith('/pair/')) {
    const result = req.url === '/pair/remember' ? { id: 'd'.repeat(32), secret: 's'.repeat(32), name: 'Test' } :
      req.url === '/pair/request' ? { id: 'qr', secret: 'poll', expiresIn: 120 } :
        req.url === '/pair/status' ? { state: 'pending' } : { token: 'session', name: 'Test' };
    res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(result)); return;
  }
  const file = { '/': 'index.html', '/app.js': 'app.js', '/browser-audio.js': 'browser-audio.js',
    '/browser-media.js': 'browser-media.js', '/media-probe.js': 'media-probe.js',
    '/flac-mp4.js': 'flac-mp4.js', '/mse-music.js': 'mse-music.js',
    '/style.css': 'style.css', '/audio-worklet.js': 'audio-worklet.js' }[req.url];
  if (!file) { res.writeHead(404); res.end(); return; }
  let body = await readFile(new URL(file, root));
  res.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'text/html; charset=utf-8');
  res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob:; media-src 'self' blob:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
  res.setHeader('Cache-Control', 'no-store'); res.end(body);
});
const wss = new WebSocketServer({ noServer: true });
server.on('upgrade', (req, socket, head) => {
  assert.equal(new URL(req.url, 'http://localhost').searchParams.get('media'), '2');
  wss.handleUpgrade(req, socket, head, ws => wss.emit('connection', ws));
});
function status(extra = {}) { media = { ...media, ...extra }; viewer.send(JSON.stringify(media)); }
function frame(epoch = media.videoEpoch, width = media.width, height = media.height) {
  const image = width === 320 ? landscape : portrait, packet = Buffer.alloc(36 + image.length);
  packet.writeUInt32LE(0x32565057); packet.writeDoubleLE(7, 4); packet.writeDoubleLE(epoch, 12);
  packet.writeDoubleLE(acks + 1, 20); packet.writeUInt32LE(width, 28); packet.writeUInt32LE(height, 32);
  image.copy(packet, 36); viewer.send(packet);
}
wss.on('connection', ws => {
  viewer = ws; connections++;
  let initial = true;
  ws.on('message', data => {
    const message = JSON.parse(data);
    if (message.type === 'ack') acks++;
    if (message.type === 'touch') touches.push(message);
    if (message.type === 'ping' && !paused) {
      ws.send(JSON.stringify(media));
      if (initial && connections === 1) frame();
      initial = false;
    }
  });
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const browser = spawn(process.env.BROWSER_BIN || 'chromium', ['--headless', '--no-sandbox', '--disable-gpu',
  '--disable-background-networking', '--no-first-run', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'],
{ stdio: ['ignore', 'ignore', 'ignore'] });
let devtools;
try {
  let port;
  for (let attempt = 0; attempt < 100; attempt++) {
    if (browser.exitCode !== null) throw new Error(`Browser exited: ${browser.exitCode}`);
    try { port = (await readFile(join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]; break; } catch {}
    await pause(50);
  }
  assert.ok(port, 'Chromium DevTools must start');
  const targets = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  devtools = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl);
  await new Promise(resolve => devtools.once('open', resolve));
  let id = 0;
  const pending = new Map();
  devtools.on('message', data => {
    const message = JSON.parse(data);
    if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails);
    if (message.id) {
      const promise = pending.get(message.id); pending.delete(message.id);
      if (message.error) promise.reject(new Error(JSON.stringify(message.error))); else promise.resolve(message.result);
    }
  });
  const command = (method, params = {}) => new Promise((resolve, reject) => {
    pending.set(++id, { resolve, reject }); devtools.send(JSON.stringify({ id, method, params }));
  });
  const evaluate = async expression => {
    const result = await command('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
    return result.result.value;
  };
  const wait = async expression => {
    for (let attempt = 0; attempt < 100; attempt++) {
      if (await evaluate(expression)) return;
      await pause(50);
    }
    const diagnostics = await evaluate(`({status:document.getElementById('status')?.textContent,
      waiting:document.getElementById('waiting')?.hidden,exceptions:${JSON.stringify(errors)}})`);
    throw new Error(`Browser condition timed out: ${expression}\n${JSON.stringify(diagnostics)}`);
  };
  const showing = `!!document.getElementById('waiting') && document.getElementById('waiting').hidden && !document.getElementById('touch-surface').classList.contains('stale')`;
  const touch = type => command('Input.dispatchTouchEvent', {
    type: type === 'pointerdown' ? 'touchStart' : 'touchEnd',
    touchPoints: type === 'pointerdown' ? [{ x: 100, y: 100, id: 1 }] : [],
  });
  await command('Runtime.enable');
  await command('Page.navigate', { url: `http://127.0.0.1:${server.address().port}/` });
  await wait(`!!window.wheelplayStats && !!document.getElementById('connect') && !document.getElementById('connect').disabled`);
  await evaluate(`document.getElementById('code').value='123456';document.getElementById('connect-form').requestSubmit()`);
  await wait(showing);
  await evaluate(`window.overlayFlashes=0;new MutationObserver(()=>{if(!document.getElementById('waiting').hidden)overlayFlashes++}).observe(document.getElementById('waiting'),{attributes:true,attributeFilter:['hidden']})`);
  for (let index = 0; index < 10; index++) {
    const previous = acks; frame(); status();
    for (let attempt = 0; acks === previous && attempt < 100; attempt++) await pause(10);
    assert.ok(acks > previous, 'JPEG must commit and ACK');
  }
  assert.equal(await evaluate('overlayFlashes'), 0, 'Decoding must not flash the overlay');
  await touch('pointerdown'); paused = true;
  status({ sourceState: 'recovering', streaming: false, inputReady: false });
  await wait(`!document.getElementById('media-message').hidden`);
  for (let attempt = 0; touches.at(-1)?.contacts.length !== 0 && attempt < 100; attempt++) await pause(10);
  assert.deepEqual(touches.at(-1).contacts, []);
  await touch('pointerup');
  const count = touches.length; await touch('pointerdown'); await pause(50);
  assert.equal(touches.length, count, 'Gap must block new touch');
  await pause(2100);
  assert.equal(await evaluate(`document.getElementById('waiting').hidden`), false, 'Deadline expires without status');
  assert.equal(viewer.readyState, WebSocket.OPEN);
  paused = false; status({ sourceState: 'ready', streaming: true, inputReady: true }); await wait(showing);
  status({ sourceState: 'recovering', inputReady: false }); await pause(100);
  status({ sourceState: 'ready', inputReady: true }); await wait(showing); await pause(2100);
  assert.equal(await evaluate(showing), true, 'Cancelled deadline must not hide recovered picture');
  status({ videoEpoch: 2 });
  await wait(`document.getElementById('touch-surface').classList.contains('stale')`);
  frame(1); await pause(100);
  assert.equal(await evaluate(showing), false, 'Old epoch cannot restore touch');
  frame(2); await wait(showing);
  status({ videoEpoch: 3, width: 180, height: 320 }); frame(3);
  await wait(showing);
  assert.equal(await evaluate(`[...document.querySelectorAll('#screen,#screen-next')].find(image=>!image.hidden).naturalWidth`), 180);
  viewer.close();
  for (let attempt = 0; connections < 2 && attempt < 100; attempt++) await pause(50);
  assert.equal(connections, 2);
  assert.equal(await evaluate(`document.getElementById('waiting').hidden`), false, 'Reconnect must wait for its own frame');
  frame(); await wait(showing);
  assert.deepEqual(errors, []);
  console.log(JSON.stringify({ passed: true, connections, jpegAcks: acks,
    checks: ['decode without overlay flashes', 'gap releases touch', 'deadline without status', 'cancelled deadline',
      'same-size rebuild', 'old frame rejection', 'rotation', 'real socket reconnect'] }, null, 2));
} finally {
  devtools?.close(); browser.kill('SIGTERM');
  for (const client of wss.clients) client.terminate();
  wss.close(); await new Promise(resolve => server.close(resolve));
  await new Promise(resolve => browser.exitCode !== null ? resolve() : browser.once('exit', resolve));
  await rm(profile, { recursive: true, force: true, maxRetries: 3 });
}
