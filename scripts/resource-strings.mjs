// Desktop previews and browser tests read Android's resource source; no second translation catalog.
import { readFileSync, readdirSync } from 'node:fs';

export function resourceStrings(qualifier = 'values', module = 'shared') {
  const directory = new URL(`../${module}/src/main/res/${qualifier}/`, import.meta.url);
  const xml = readdirSync(directory).filter(name => name.endsWith('.xml')).map(name => readFileSync(new URL(name, directory), 'utf8')).join('\n');
  return Object.fromEntries([...xml.matchAll(/<string name="([^"]+)"([^>]*)>(.*?)<\/string>/gs)].flatMap(([, key, attributes, value]) => {
    if (attributes.includes('translatable="false"')) return [];
    value = value.replace(/^"|"$/g, '').replace(/\\([\\'"nt])/g, (_, ch) => ({ n: '\n', t: '\t' })[ch] ?? ch);
    value = value.replace(/&(?:amp|lt|gt|quot|apos);/g, entity => ({ '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&apos;': "'" })[entity]);
    return [[key, value]];
  }));
}

export function renderBrowserTemplate(template, messages, locale = 'en') {
  const escape = text => text.replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch]);
  return template.replace(/\{\{([a-z0-9_]+)}}/g, (_, key) => escape(key === 'locale' ? locale : messages[key]));
}
