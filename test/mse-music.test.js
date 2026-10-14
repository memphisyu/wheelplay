import test from 'node:test';
import assert from 'node:assert/strict';
import {createMseMusic} from '../common/src/main/assets/web/mse-music.js';

function fixture(millis) {
  const oldDocument=globalThis.document,oldSource=globalThis.MediaSource;
  const oldCreate=URL.createObjectURL,oldRevoke=URL.revokeObjectURL;
  const events={},audioEvents={},bufferEvents={}; let end=0,plays=0,pauses=0;
  const element={currentTime:0,setAttribute(){},addEventListener(n,f){audioEvents[n]=f;},
    play(){plays++;return Promise.resolve();},pause(){pauses++;},removeAttribute(){},load(){},remove(){}};
  const buffer={updating:false,buffered:{length:1,start(){return 0;},end(){return end;}},
    addEventListener(n,f){bufferEvents[n]=f;},appendBuffer(){},remove(){}};
  globalThis.document={body:{appendChild(){}},createElement(){return element;}};
  globalThis.MediaSource=class {static isTypeSupported(){return true;}constructor(){this.readyState='open';}
    addEventListener(n,f){events[n]=f;}addSourceBuffer(){return buffer;}};
  URL.createObjectURL=()=> 'blob:music';URL.revokeObjectURL=()=>{};
  const music=createMseMusic({rate:48000,channels:2,mediaBufferMillis:millis});
  events.sourceopen();
  return {music,element,get plays(){return plays;},get pauses(){return pauses;},
    buffered(value){end=value;bufferEvents.updateend();},waiting(){audioEvents.waiting();},
    close(){music.close();globalThis.document=oldDocument;globalThis.MediaSource=oldSource;URL.createObjectURL=oldCreate;URL.revokeObjectURL=oldRevoke;}};
}
const flush=()=>new Promise(setImmediate);
test('all buffer presets gate MSE startup and refill after starvation',async()=>{
 for(const millis of [300,500,1000]){
  const f=fixture(millis);
  try{
   f.music.start();f.buffered(millis/1000-.02);assert.equal(f.plays,0);
   f.buffered(millis/1000+.01);await flush();assert.equal(f.plays,1);
   f.element.currentTime=millis/1000+.01;f.waiting();assert.ok(f.pauses>0);
   f.buffered(f.element.currentTime+millis/1000-.02);assert.equal(f.plays,1);
   f.buffered(f.element.currentTime+millis/1000+.01);await flush();assert.equal(f.plays,2);
  }finally{f.close();}
 }
});
test('live buffer changes preserve the player and adjust its next refill target',async()=>{
 const f=fixture(300);
 try{
  f.music.start();f.buffered(.31);await flush();assert.equal(f.plays,1);
  f.music.setBufferMillis(1000);f.element.currentTime=.31;f.waiting();
  f.buffered(.9);assert.equal(f.plays,1);
  f.music.setBufferMillis(500);await flush();assert.equal(f.plays,2);
 }finally{f.close();}
});
