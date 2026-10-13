import {FLAC_MIME, FLAC_BLOCK, flacInit, flacFrame, flacFragment} from './flac-mp4.js';
// A real media timeline, rather than a MediaStream, enables browser system media controls.
export function createMseMusic({rate, channels, onError = () => {}}) {
  if (typeof MediaSource === 'undefined' || !MediaSource.isTypeSupported(FLAC_MIME) || ![44100,48000].includes(rate)) return null;
  const element = document.createElement('audio');
  const source = new MediaSource(), url = URL.createObjectURL(source);
  element.hidden = true; element.setAttribute('playsinline',''); element.src = url; document.body.appendChild(element);
  let buffer = null, closed = false, sequence = 1, block = 0, samples = 0, fill = 0;
  let pcm = new Uint8Array(FLAC_BLOCK * channels * 2);
  const pending = [flacInit(rate, channels)];
  function fail(error) { if (!closed) { close(); onError(String(error)); } }
  function pump() {
    if (closed || !buffer || buffer.updating || source.readyState !== 'open') return;
    try {
      if (buffer.buffered.length) {
        const start = buffer.buffered.start(0), end = buffer.buffered.end(buffer.buffered.length-1);
        if (end-element.currentTime > 2) element.currentTime = Math.max(start,end-.5);
        if (element.currentTime-start > 15) { buffer.remove(0,element.currentTime-10); return; }
      }
      const next = pending.shift(); if (next) buffer.appendBuffer(next);
    } catch (error) { fail(error); }
  }
  source.addEventListener('sourceopen', () => {
    if (closed) return;
    try { buffer=source.addSourceBuffer(FLAC_MIME); buffer.addEventListener('updateend',pump); buffer.addEventListener('error',()=>fail('FLAC decode error')); pump(); }
    catch(error) { fail(error); }
  }, {once:true});
  element.addEventListener('error',()=>fail(element.error?.message || 'Music playback error'));
  function close() { if (closed) return; closed=true; pending.length=0; element.pause(); element.removeAttribute('src'); element.load(); element.remove(); URL.revokeObjectURL(url); }
  return {
    rate, channels,
    start() { const promise=element.play(); promise?.catch(error=>{ if (!closed) onError(error.name === 'NotAllowedError' ? '请点按画面允许播放音乐' : error.message, true); }); },
    packet(frame) {
      if (closed) return;
      const data=new Uint8Array(frame.pcm.buffer,frame.pcm.byteOffset,frame.pcm.byteLength);
      let offset=0;
      while(offset<data.length) {
        const count=Math.min(pcm.length-fill,data.length-offset); pcm.set(data.subarray(offset,offset+count),fill); fill+=count; offset+=count;
        if(fill===pcm.length) { pending.push(flacFragment(sequence++,samples,flacFrame(block++,rate,channels,pcm),FLAC_BLOCK)); samples+=FLAC_BLOCK; fill=0; }
      }
      if(pending.length>24) { fail('音乐缓冲未推进，请点按画面或重新连接'); return; } pump();
    }, close,
  };
}
