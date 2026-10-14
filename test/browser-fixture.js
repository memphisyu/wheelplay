import {readFileSync} from 'node:fs';
// Execute real browser modules together in the VM's simulated browser realm.
// Keep dependency order explicit so nested imports cannot silently escape coverage.
export const browserFixtureSource = ['flac-mp4', 'mse-music', 'browser-audio',
  'browser-media', 'media-probe', 'app'].map(name =>
  readFileSync(new URL(`../common/src/main/assets/web/${name}.js`, import.meta.url), 'utf8')
    .replace(/^import .*;$/gm, '').replace(/\bexport (?=(function|const|class)\b)/g, '')
).join('\n');
