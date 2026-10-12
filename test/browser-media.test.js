import test from 'node:test';
import assert from 'node:assert/strict';
import { createBrowserMedia } from '../common/src/main/assets/web/browser-media.js';
test('paired media metadata, commands and disconnect cleanup', () => {
 const handlers = {}, sent = [];
 const session = { setActionHandler: (a,h) => handlers[a]=h, setPositionState: p => session.position=p };
 const media = createBrowserMedia({session, Metadata: class {constructor(v){Object.assign(this,v)}},send: x => sent.push(x)});
 media.control({type:'audio-route',playback:true});
 media.control({type:'now-playing',available:true,generation:7,title:'Song',artist:'Artist',album:'Album',playing:true,duration:90,position:200});
 assert.equal(session.metadata.title,'Song'); assert.equal(session.playbackState,'playing'); assert.equal(session.position.position,90);
 handlers.nexttrack(); assert.deepEqual(sent,[{type:'media-command',command:'NEXT',generation:7,requestId:1}]);
 const stale=handlers.nexttrack; media.clear(); stale(); assert.equal(sent.length,1); assert.equal(session.metadata,null); assert.equal(handlers.nexttrack,null);
});
test('unsupported APIs and local audio do not seize media controls', () => {
 const media=createBrowserMedia({session:{setActionHandler(){throw Error()},setPositionState(){throw Error()}},send(){throw Error()}});
 media.control({type:'now-playing',available:true}); media.clear();
 createBrowserMedia({session:null}).control({type:'audio-route',playback:true});
});
test('pause, metadata changes, and disabled browser audio clear ownership', () => {
 const handlers={}, session={setActionHandler:(a,h)=>handlers[a]=h,setPositionState(){}};
 const media=createBrowserMedia({session,Metadata:class {constructor(v){Object.assign(this,v)}},send(){}});
 media.control({type:'audio-route',playback:true});
 media.control({type:'now-playing',available:true,generation:1,title:'First',playing:true});
 media.control({type:'now-playing',available:true,generation:1,title:'Second',playing:false});
 assert.equal(session.metadata.title,'Second'); assert.equal(session.playbackState,'paused');
 media.control({type:'audio-route',playback:false}); assert.equal(session.metadata,null); assert.equal(handlers.pause,null);
});
test('command results match request and generation; timeout and disconnect never replay', () => {
 let time=0; const sent=[], session={setActionHandler(){},setPositionState(){}};
 const media=createBrowserMedia({session,send:data=>{sent.push(data);return true},now:()=>time});
 media.control({type:'audio-route',playback:true}); media.control({type:'now-playing',available:true,generation:8});
 assert.equal(media.command('NEXT'),true); assert.equal(media.diagnostics.commands[0].origin,'page');
 media.control({type:'media-command-result',requestId:1,generation:7,result:'SENT'});
 assert.equal(media.diagnostics.commands[0].result,'PENDING');
 time=5001; media.expire(); assert.equal(media.diagnostics.commands[0].result,'TIMEOUT');
 media.control({type:'media-command-result',requestId:1,generation:8,result:'SENT'});
 assert.equal(media.diagnostics.commands[0].result,'SENT');
 media.command('PAUSE'); media.clear(); assert.equal(media.diagnostics.commands[1].result,'DISCONNECTED');
 assert.equal(media.command('NEXT'),false); assert.equal(sent.length,2);
});
test('probe suspension preserves newest metadata without seizing test player session', () => {
 const session={setActionHandler(){},setPositionState(){}};
 const media=createBrowserMedia({session,Metadata:class {constructor(v){Object.assign(this,v)}},send(){}});
 media.control({type:'audio-route',playback:true}); media.control({type:'now-playing',available:true,generation:1,title:'First'});
 media.suspend(true); session.metadata={title:'Probe'};
 media.control({type:'now-playing',available:true,generation:1,title:'Latest'});
 media.control({type:'audio-route',playback:false}); assert.equal(session.metadata.title,'Probe');
 assert.equal(media.command('NEXT'),false);
 media.control({type:'audio-route',playback:true}); media.suspend(false);
 assert.equal(session.metadata.title,'Latest');
});
test('old media handlers cannot control a newly connected session', () => {
 const handlers={}, sent=[], session={setActionHandler:(a,h)=>handlers[a]=h,setPositionState(){}};
 const media=createBrowserMedia({session,send:data=>sent.push(data)});
 media.control({type:'audio-route',playback:true}); media.control({type:'now-playing',available:true,generation:1});
 const stale=handlers.nexttrack; media.clear();
 media.control({type:'audio-route',playback:true}); media.control({type:'now-playing',available:true,generation:2});
 stale(); assert.equal(sent.length,0); handlers.nexttrack(); assert.equal(sent[0].generation,2);
});
