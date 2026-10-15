import test from 'node:test';
import assert from 'node:assert/strict';
import {createMseMusic} from '../common/src/main/assets/web/mse-music.js';

function fixture(millis, {deferred = false} = {}) {
  const oldDocument=globalThis.document,oldSource=globalThis.MediaSource;
  const oldCreate=URL.createObjectURL,oldRevoke=URL.revokeObjectURL;
  const events={},audioEvents={},bufferEvents={}; let end=0,plays=0,pauses=0, resolvePlay, rejectPlay; const errors=[],states=[];
  const element={currentTime:0,setAttribute(){},addEventListener(n,f){audioEvents[n]=f;},
    play(){plays++;return deferred ? new Promise((resolve,reject)=>{resolvePlay=resolve;rejectPlay=reject;}) : Promise.resolve();},
    pause(){pauses++;rejectPlay?.(Object.assign(new Error('The play() request was interrupted by a call to pause().'),{name:'AbortError'}));rejectPlay=null;},removeAttribute(){},load(){},remove(){}};
  const buffer={updating:false,buffered:{length:1,start(){return 0;},end(){return end;}},
    addEventListener(n,f){bufferEvents[n]=f;},appendBuffer(){},remove(){}};
  globalThis.document={body:{appendChild(){}},createElement(){return element;}};
  globalThis.MediaSource=class {static isTypeSupported(){return true;}constructor(){this.readyState='open';}
    addEventListener(n,f){events[n]=f;}addSourceBuffer(){return buffer;}};
  URL.createObjectURL=()=> 'blob:music';URL.revokeObjectURL=()=>{};
  const music=createMseMusic({rate:48000,channels:2,mediaBufferMillis:millis,onError:(...args)=>errors.push(args),onPlaying:()=>states.push("playing")});
  events.sourceopen();
  return {music,element,errors,states,resolve(){resolvePlay?.();},reject(name){rejectPlay?.(Object.assign(new Error(name),{name}));},playing(){audioEvents.playing?.();},get plays(){return plays;},get pauses(){return pauses;},
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

test('waiting while play is pending does not cancel the asynchronous start',async()=>{
 const f=fixture(300,{deferred:true});
 try{
  f.music.start();f.buffered(.31);assert.equal(f.plays,1);
  f.waiting();await flush();assert.equal(f.pauses,0);assert.deepEqual(f.errors,[]);
  f.resolve();f.playing();await flush();assert.equal(f.states.length,1);
 }finally{f.close();}
});
test('transient AbortError retries with new buffered data instead of requiring a gesture',async()=>{
 const f=fixture(300,{deferred:true});
 try{
  f.music.start();f.buffered(.31);f.reject('AbortError');await flush();
  assert.deepEqual(f.errors,[]);f.buffered(.5);assert.equal(f.plays,2);
  f.resolve();f.playing();await flush();assert.equal(f.states.length,1);
 }finally{f.close();}
});
test('a genuine permission denial waits for a user gesture and recovery signals playing',async()=>{
 const f=fixture(300,{deferred:true});
 try{
  f.music.start();f.buffered(.31);f.reject('NotAllowedError');await flush();
  assert.equal(f.errors.length,1);f.buffered(.5);assert.equal(f.plays,1);
  f.music.start();assert.equal(f.plays,2);f.resolve();f.playing();await flush();
  assert.equal(f.states.length,1);
 }finally{f.close();}
});
test('closing with a pending play ignores late rejection and playing callbacks',async()=>{
 const f=fixture(300,{deferred:true});
 try{
  f.music.start();f.buffered(.31);f.music.close();f.playing();await flush();
  assert.deepEqual(f.errors,[]);assert.deepEqual(f.states,[]);
 }finally{f.close();}
});

test('unsupported media failure triggers fallback instead of pretending permission was denied',async()=>{
 const f=fixture(300,{deferred:true});
 try{
  f.music.start();f.buffered(.31);f.reject('NotSupportedError');await flush();
  assert.equal(f.errors.length,1);assert.notEqual(f.errors[0][1],true);
  f.buffered(.6);assert.equal(f.plays,1);
 }finally{f.close();}
});
