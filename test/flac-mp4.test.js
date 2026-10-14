import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,writeFileSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {execFileSync} from 'node:child_process';
import {FLAC_BLOCK,flacInit,flacFrame,flacFragment} from '../common/src/main/assets/web/flac-mp4.js';
test('FLAC MP4 decodes bit-exact stereo and mono at both music sample rates',()=>{
 const dir=mkdtempSync(join(tmpdir(),'wheelplay-flac-'));
 try { for(const rate of [44100,48000]) for(const channels of [1,2]) {
 const pcm=Buffer.alloc(FLAC_BLOCK*channels*2*150),parts=[flacInit(rate,channels)];
 for(let i=0;i<pcm.length/2;i++) pcm.writeInt16LE(Math.round(Math.sin(i*.031)*30000),i*2);
 const size=FLAC_BLOCK*channels*2;
 for(let n=0;n<150;n++) parts.push(flacFragment(n+1,n*FLAC_BLOCK,flacFrame(n,rate,channels,pcm.subarray(n*size,(n+1)*size)),FLAC_BLOCK));
 writeFileSync(join(dir,'music.mp4'),Buffer.concat(parts));
 execFileSync('ffmpeg',['-v','error','-y','-i',join(dir,'music.mp4'),'-f','s16le',join(dir,'decoded.pcm')]);
 assert.deepEqual(readFileSync(join(dir,'decoded.pcm')),pcm);
 }}finally{rmSync(dir,{recursive:true,force:true});}
});
import {createMseMusic} from '../common/src/main/assets/web/mse-music.js';
test('MSE buffers real music and releases its source on close',()=>{
 const old={document:globalThis.document,MediaSource:globalThis.MediaSource};
 const originalCreate=URL.createObjectURL,originalRevoke=URL.revokeObjectURL;
 const appended=[],events={};let revoked=false,removed=false,played=0;
 const buffer={updating:false,buffered:{length:0},addEventListener(){},appendBuffer(b){appended.push(b);}};
 globalThis.MediaSource=class {static isTypeSupported(){return true;} constructor(){this.readyState='open';} addEventListener(n,f){events[n]=f;} addSourceBuffer(){return buffer;}};
 globalThis.document={body:{appendChild(){}},createElement(){return {setAttribute(){},addEventListener(){},play(){played++;return Promise.resolve();},pause(){},removeAttribute(){},load(){},remove(){removed=true;}};}};
 URL.createObjectURL=()=> 'blob:test';URL.revokeObjectURL=()=>{revoked=true;};
 try {const output=createMseMusic({rate:48000,channels:2});output.start();events.sourceopen();output.packet({pcm:new Int16Array(4096*2)});assert.equal(appended.length,2);assert.equal(played,0);output.close();output.packet({pcm:new Int16Array(4096*2)});assert.equal(appended.length,2);assert.ok(removed&&revoked);assert.equal(createMseMusic({rate:16000,channels:1}),null);}finally {Object.assign(globalThis,old);URL.createObjectURL=originalCreate;URL.revokeObjectURL=originalRevoke;}
});
