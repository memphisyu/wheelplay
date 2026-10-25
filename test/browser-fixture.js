import {readFileSync} from 'node:fs';
export const browserFixtureSource = ['text', 'flac-mp4', 'mse-music', 'browser-audio', 'browser-media',
 'media-probe', 'desktop-input', 'browser-location', 'browser-media-session', 'xpeng-diagnostics', 'app'].map(name =>
 readFileSync(new URL(`../common/src/main/assets/web/${name}.js`, import.meta.url), 'utf8')
 .replace(/^import .*;$/gm, '').replace(/\bexport (?=(function|const|class)\b)/g, '')).join('\n');
