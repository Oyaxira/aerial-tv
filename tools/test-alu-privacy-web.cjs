// Executes the shipped phone-page renderers with a tiny DOM, without sockets or image loading.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync('app/src/main/assets/web/index.html', 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
new vm.Script(script); // Check the whole shared page, not just the exercised functions.
class Element {
  constructor(tag = '') { this.tag = tag; this.children = []; this.style = {}; this.dataset = {}; this.queries = {}; }
  append(...children) { this.children.push(...children); }
  appendChild(child) { this.append(child); return child; }
  replaceChildren(...children) { this.children = children; }
  setAttribute() {}
  querySelector(selector) { return this.queries[selector] ||= new Element(selector); }
}
const elements = new Map();
const $ = selector => { if (!elements.has(selector)) elements.set(selector, new Element(selector)); return elements.get(selector); };
const requests = [];
const sandbox = {
  $, document: { querySelector: $, querySelectorAll: () => [] },
  el: tag => new Element(tag), base: '/synthetic/', ICON: {}, weatherNames: {},
  t: key => key, titleOf: () => '', toast: () => {}, take: () => {},
  api: async (path, body) => { requests.push({ path, body }); return {}; },
  groups: [['daily', 'daily', 'caption']], state: {},
};
vm.createContext(sandbox);
for (const [from, to] of [
  ['function card(w)', 'function renderWallpapers()'],
  ['function renderWallpapers()', 'let uploading ='],
  ['function renderMore()', 'function renderInputs()'],
]) {
  vm.runInContext(script.slice(script.indexOf(from), script.indexOf(to)), sandbox);
}
(async () => {
  for (const personal of [true, false, undefined]) {
    sandbox.state = { fixedHome: personal, wallpapers: [], selected: 'photo:local', latestDaily: 'daily:latest', weather: { chosen: true, place: 'Shanghai' } };
    sandbox.renderWallpapers();
    const grids = $('#sections').children.filter(child => child.tag === 'div');
    assert.equal(grids.length, personal ? 1 : 0, 'Empty-cache daily entry is personal-only');
    if (personal) {
      await grids[0].children[0].onclick();
      assert.equal(requests.length, 1);
      assert.equal(requests[0].path, 'api/wallpaper');
      assert.equal(requests[0].body.id, 'daily:latest');
    }
    sandbox.renderMore();
    const autoButtons = $('#wxHint').children.filter(child => child instanceof Element);
    assert.equal(autoButtons.length, personal ? 0 : 1, 'No fake personal automatic-location button; upstream/Lite retain it');
  }
  console.log('PASS: shipped JS parses; fresh personal daily card submits daily:latest; automatic-location button absent; ordinary/Lite UI preserved');
})().catch(error => { console.error(error); process.exitCode = 1; });
