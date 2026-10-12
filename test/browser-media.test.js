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
 handlers.nexttrack(); assert.deepEqual(sent,[{type:'media-command',command:'NEXT',generation:7}]);
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
