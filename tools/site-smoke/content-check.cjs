const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..'),site=path.join(root,'distribution');
const html=fs.readFileSync(path.join(site,'index.html'),'utf8'),js=fs.readFileSync(path.join(site,'main.js'),'utf8');
const pages=['index.html','features.html','your-data.html','download.html','guide.html','404.html',...fs.readdirSync(site).filter(file=>/^guide-.+\.html$/.test(file))];
const allHtml=pages.map(file=>fs.readFileSync(path.join(site,file),'utf8')).join('\n');
const build=fs.readFileSync(path.join(root,'V6/NativeExpenseEntry/app/build.gradle'),'utf8');
const version=build.match(/versionName '([^']+)'/)[1];assert.ok(allHtml.includes(version));
const android='https://docs.google.com/spreadsheets/d/1rxBf4afldt5WeRTu_Lxue1OeDSCDepbNpJz45OeTdAA/copy';
const ios='https://docs.google.com/spreadsheets/d/16j8RzujL-Z_zTnZ8ST50UopyROpsU-yM21yhWZf2IxY/copy';
for(const url of [android,ios])assert.ok(allHtml.includes(url),'Missing platform template');
assert.doesNotMatch(html,/1vu140|iPhone\s*\/\s*V1|iPhone\/V1|Installs separately from V2|6\.0\.8/);
assert.doesNotMatch(html+js,/Nothing leaves the Android app in the background|only when you choose/);
for(const phrase of ['Lena / Dena','Split bills','opening balance','Your spending','Sync Support','earlier Android','iOS script'])assert.ok(allHtml.includes(phrase),phrase);
assert.ok(allHtml.includes('excluded from JSON backup, Sheets sync'));
assert.ok((allHtml+js).includes('No scheduled background sync'));
for(const page of pages){
 const text=fs.readFileSync(path.join(site,page),'utf8');
 assert.equal([...text.matchAll(/<h1\b/g)].length,1,page+' must have one h1');
 const ownIds=[...text.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]);
 assert.equal(new Set(ownIds).size,ownIds.length,page+' duplicate IDs');
 for(const [,url] of text.matchAll(/(?:href|src)="([^"]+)"/g)){
  if(/^(?:https?:|data:|mailto:)/.test(url))continue;
  const [file,fragment]=url.replace(/^\/Pocket-Ledger\//,'').split('#'),target=file.split('?')[0]||page;
  assert.ok(fs.existsSync(path.resolve(site,target)), 'Missing local resource '+url);
  if(fragment){
   const targetHtml=fs.readFileSync(path.join(site,target),'utf8');
   assert.ok([...targetHtml.matchAll(/\bid="([^"]+)"/g)].some(m=>m[1]===fragment),'Unknown anchor '+url);
  }
 }
}
assert.ok(html.includes('vendor/lenis/lenis.min.js'));
assert.ok(js.includes('autoRaf:false'));
assert.doesNotMatch(js,/requestAnimationFrame\(|addEventListener\('scroll'/,'Motion uses the shared GSAP clock');
assert.doesNotMatch(allHtml+js,/â‚|Â·/,'No mojibake in website content');
for (const cssFile of ['style.css','guide.css','fonts/fonts.css','vendor/lenis/lenis.css']) {
 const css=fs.readFileSync(path.join(site,cssFile),'utf8');
 for (const [,raw] of css.matchAll(/url\(([^)]+)\)/g)) {
  const url=raw.replace(/["']/g,'');
  if (!url.startsWith('data:') && !url.startsWith('#')) assert.ok(fs.existsSync(path.resolve(site,path.dirname(cssFile),url)), 'Missing CSS asset '+url);
 }
}
for (const file of ['fonts/archivo-latin.woff2','fonts/dm-sans-latin.woff2']) assert.equal(fs.readFileSync(path.join(site,file)).subarray(0,4).toString(),'wOF2');
assert.ok(html.includes('<noscript>'),'Static chart fallback available');
assert.equal(fs.readFileSync(path.join(site,'apps-script/Pocket-Ledger-Apps-Script.gs.txt'),'utf8'),fs.readFileSync(path.join(root,'V6/Backend/AppsScript.gs'),'utf8'));
const iosScript=fs.readFileSync(path.join(site,'apps-script/Pocket-Ledger-iOS.gs.txt'),'utf8');assert.ok(iosScript.includes('function doPost(e)'));assert.ok(!iosScript.includes('ledgerEndpoint'));
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
assert.equal(hash(path.join(site,'downloads/Pocket-Ledger.apk')),hash(path.join(root,'V6/NativeExpenseEntry/app/build/outputs/apk/debug/app-debug.apk')));
for(const file of ['README.md','V6/README.md','distribution/README.md']){
 const text=fs.readFileSync(path.join(root,file),'utf8');assert.ok(text.includes(version),file+' version');assert.ok(text.includes(android),file+' Android URL');
 assert.doesNotMatch(text,/1vu140|version code 9|\*\*6\.0\.8\*\*/);
}
const publicCopy=html+fs.readFileSync(path.join(root,'README.md'),'utf8');
assert.doesNotMatch(publicCopy,/AKfycb/,'Private endpoints must not be distributed');
for(const [,id] of publicCopy.matchAll(/docs\.google\.com\/spreadsheets\/(?:u\/\d+\/)?d\/([A-Za-z0-9_-]+)/g))assert.ok([android,ios].some(url=>url.includes('/d/'+id+'/')),'Only public template workbooks belong in the website and README');
console.log('PASS: current Android/iOS links, feature and backup/sync copy, local resources, private-data exclusion, README versions, APK and Android script parity.');
