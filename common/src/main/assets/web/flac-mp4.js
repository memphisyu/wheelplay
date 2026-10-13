// Adapted from yizhixiaoheigou/DashPlay, GPL-3.0; see docs/DASHPLAY-NOTICE.md.
/**
 * Lossless ISO-BMFF framing for the wired iPhone's 16-bit LPCM music: each block becomes a FLAC frame
 * with VERBATIM subframes (the samples as they are, no compression), so the browser's media element
 * (MSE, "fLaC" in MP4) plays it like stream 102's AAC: buffered, no Web Audio stitching, and eligible
 * for the head unit's media controls.
 */
const join = (...parts) => { const b=new Uint8Array(parts.reduce((n,p)=>n+p.length,0));let i=0;for(const p of parts){b.set(p,i);i+=p.length;}return b; };
const text = (s) => new TextEncoder().encode(s);
const u32 = (...values) => { const b=new Uint8Array(values.length*4);const v=new DataView(b.buffer);values.forEach((x,i)=>v.setUint32(i*4,x));return b; };
const u16 = (...values) => { const b=new Uint8Array(values.length*2);const v=new DataView(b.buffer);values.forEach((x,i)=>v.setUint16(i*2,x));return b; };
const zeros = (n) => new Uint8Array(n);
const box = (type,...parts) => { const data=join(...parts);return join(u32(data.length+8),text(type),data); };
const full = (type,flags,...parts) => box(type,u32(flags),...parts);
const matrix = u32(0x10000,0,0,0,0x10000,0,0,0,0x40000000);

export const FLAC_MIME = 'audio/mp4; codecs="flac"';
/** Samples per FLAC frame / MP4 fragment: ~85 ms at 48 kHz, 11-12 of the iPhone's 352-sample packets. */
export const FLAC_BLOCK = 4096;

const RATE_CODES = { 88200:1, 176400:2, 192000:3, 8000:4, 16000:5, 22050:6, 24000:7, 32000:8, 44100:9, 48000:10, 96000:11 };

function crc8(data) { let c=0;for(const byte of data){c^=byte;for(let i=0;i<8;i++)c=c&0x80?((c<<1)^0x07)&0xff:(c<<1)&0xff;}return c; }
function crc16(data) { let c=0;for(const byte of data){c^=byte<<8;for(let i=0;i<8;i++)c=c&0x8000?((c<<1)^0x8005)&0xffff:(c<<1)&0xffff;}return c; }

/** FLAC's UTF-8-like coding of the frame number (fixed block size stream). */
function codedNumber(n) {
  if(n<0x80)return [n];
  const tail=[];let k=0;
  do { tail.unshift(0x80|(n&0x3f));n=Math.floor(n/64);k++; } while(n>=(1<<(6-k)));
  return [((0xff<<(7-k))&0xff)|n,...tail];
}

function streamInfo(rate,channels,block) {
  const b=new Uint8Array(34);const v=new DataView(b.buffer);
  v.setUint16(0,block);v.setUint16(2,block);   // min/max block size; frame sizes 0 = unknown
  // 20-bit sample rate, 3-bit channels-1, 5-bit bits-per-sample-1, 36-bit total samples (0 = unknown)
  b[10]=(rate>>>12)&0xff;b[11]=(rate>>>4)&0xff;b[12]=((rate&0x0f)<<4)|((channels-1)<<1)|((16-1)>>>4);b[13]=((16-1)&0x0f)<<4;
  return b;   // MD5 left zero: unknown
}

export function flacInit(rate,channels) {
  if(!(rate in RATE_CODES)||rate>65535||channels<1||channels>2) throw new Error('Unsupported LPCM configuration');
  const dfLa=full('dfLa',0,new Uint8Array([0x80,0,0,34]),streamInfo(rate,channels,FLAC_BLOCK));   // last block: STREAMINFO
  const fLaC=box('fLaC',zeros(6),u16(1),zeros(8),u16(channels,16,0,0),u32(rate*65536),dfLa);
  const stbl=box('stbl',full('stsd',0,u32(1),fLaC),full('stts',0,u32(0)),full('stsc',0,u32(0)),full('stsz',0,u32(0,0)),full('stco',0,u32(0)));
  const dinf=box('dinf',full('dref',0,u32(1),full('url ',1)));
  const mdia=box('mdia',full('mdhd',0,u32(0,0,rate,0),u16(0x55c4,0)),full('hdlr',0,u32(0),text('soun'),zeros(12),text('DashPlay Audio\0')),box('minf',full('smhd',0,u16(0,0)),dinf,stbl));
  const trak=box('trak',full('tkhd',7,u32(0,0,1,0,0),zeros(8),u16(0,0,0x100,0),matrix,u32(0,0)),mdia);
  const mvhd=full('mvhd',0,u32(0,0,rate,0,0x10000),u16(0x100,0),zeros(8),matrix,zeros(24),u32(2));
  return join(box('ftyp',text('isom'),u32(0x200),text('isomiso6mp41')),box('moov',mvhd,trak,box('mvex',full('trex',0,u32(1,1,FLAC_BLOCK,0,0)))));
}

/** One FLAC frame (always FLAC_BLOCK samples: a fixed-block-size stream) from interleaved little-endian
 *  16-bit samples, as the receiver sends LPCM. */
export function flacFrame(number,rate,channels,pcm) {
  const frames=Math.floor(pcm.length/(2*channels));
  if(frames<1||frames>65536)throw new Error('FLAC block size out of range');
  const header=new Uint8Array([0xff,0xf8,(0b0111<<4)|RATE_CODES[rate],((channels-1)<<4)|(0b100<<1),...codedNumber(number),((frames-1)>>8)&0xff,(frames-1)&0xff]);
  const out=new Uint8Array(header.length+1+channels*(1+2*frames)+2);
  out.set(header);out[header.length]=crc8(header);
  const src=new DataView(pcm.buffer,pcm.byteOffset,pcm.byteLength),dst=new DataView(out.buffer);
  let at=header.length+1;
  for(let channel=0;channel<channels;channel++){
    out[at++]=0x02;   // subframe: VERBATIM, no wasted bits
    for(let i=0;i<frames;i++,at+=2)dst.setInt16(at,src.getInt16((i*channels+channel)*2,true));
  }
  dst.setUint16(at,crc16(out.subarray(0,at)));
  return out;
}

export function flacFragment(sequence,sampleTime,frame,samples) {
  const make=(offset)=>box('moof',full('mfhd',0,u32(sequence)),box('traf',full('tfhd',0x020000,u32(1)),full('tfdt',0x01000000,u32(Math.floor(sampleTime/4294967296),sampleTime>>>0)),full('trun',0x301,u32(1,offset),u32(samples,frame.length))));
  const preliminary=make(0),moof=make(preliminary.length+8);
  return join(moof,box('mdat',frame));
}
