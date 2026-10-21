// Exercise the shipped Apps Script report builder and produce a native Sheets edit batch.
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=()=>fs.readFileSync(path.join(__dirname,'../Backend/AppsScript.gs'),'utf8');
function buildReports(ledger){
 const tabs={},ids={},requests=[],charts=[];let next=6201001;
 const rgb=hex=>({red:parseInt(hex.slice(1,3),16)/255,green:parseInt(hex.slice(3,5),16)/255,blue:parseInt(hex.slice(5,7),16)/255});
 function coords(a,b,c=1,d=1){if(typeof a==='number')return {r:a-1,c:b-1,h:c,w:d};const m=a.match(/^([A-Z]+)(\d+)(?::([A-Z]+)(\d+))?$/);if(!m)throw Error('Unsupported range '+a);const col=s=>[...s].reduce((n,x)=>n*26+x.charCodeAt(0)-64,0)-1;let r=+m[2]-1,k=col(m[1]);return {r,c:k,h:m[4]?+m[4]-r:1,w:m[3]?col(m[3])-k+1:1};}
 function makeSheet(name){
  const s={name,id:next++,rows:1000,cols:26,cells:new Map(),metadata:[],merges:[],widths:[],heights:[],formulas:[],chartSpecs:[],filter:null};ids[name]=s.id;
  const grid=p=>({sheetId:s.id,startRowIndex:p.r,endRowIndex:p.r+p.h,startColumnIndex:p.c,endColumnIndex:p.c+p.w});
  const cell=(r,c)=>{let key=r+','+c;if(!s.cells.has(key))s.cells.set(key,{});return s.cells.get(key);};
  s.getRange=(...args)=>{let p=coords(...args);const x={};const every=fn=>{for(let r=p.r;r<p.r+p.h;r++)for(let c=p.c;c<p.c+p.w;c++)fn(cell(r,c));return x;};
   const format=(key,value)=>every(v=>{v.userEnteredFormat||={};v.userEnteredFormat[key]=value;});
   const text=(key,value)=>every(v=>{v.userEnteredFormat||={};v.userEnteredFormat.textFormat||={};v.userEnteredFormat.textFormat[key]=value;});
   const value=v=>{if(v instanceof Date||Object.prototype.toString.call(v)==='[object Date]')return {numberValue:(v.getTime()+330*60000)/86400000+25569};if(typeof v==='number')return {numberValue:v};if(typeof v==='boolean')return {boolValue:v};let t=String(v??'');if(/^'[=+@-]/.test(t))t=t.slice(1);return {stringValue:t};};
   x.getValue=()=>cell(p.r,p.c).userEnteredValue?.numberValue??'';
   x.setValue=v=>{cell(p.r,p.c).userEnteredValue=value(v);return x;};
   x.setValues=matrix=>{if(matrix.length!==p.h||matrix.some(row=>row.length!==p.w))throw Error('Matrix mismatch '+JSON.stringify(p));for(let r=0;r<p.h;r++)for(let c=0;c<p.w;c++)cell(p.r+r,p.c+c).userEnteredValue=value(matrix[r][c]);return x;};
   x.setFormula=f=>{cell(p.r,p.c).userEnteredValue={formulaValue:f};s.formulas.push(f);return x;};
   x.setFontFamily=v=>text('fontFamily',v);x.setFontSize=v=>text('fontSize',v);x.setFontColor=v=>text('foregroundColor',rgb(v));x.setFontWeight=v=>text('bold',v==='bold');
   x.setBackground=v=>format('backgroundColor',rgb(v));x.setHorizontalAlignment=v=>format('horizontalAlignment',v.toUpperCase());x.setVerticalAlignment=v=>format('verticalAlignment',v.toUpperCase());x.setWrap=v=>format('wrapStrategy',v?'WRAP':'OVERFLOW_CELL');
   x.setNumberFormat=pattern=>format('numberFormat',{type:pattern.includes('₹')?'CURRENCY':/[dyh]/i.test(pattern)?'DATE_TIME':'NUMBER',pattern});
   x.merge=()=>{s.merges.push(grid(p));return x;};x.breakApart=()=>x;x.setDataValidation=rule=>every(v=>v.dataValidation=rule);x.createFilter=()=>{s.filter=grid(p);return x;};return x;
  };
  s.getFilter=()=>null;s.clear=()=>{s.cells.clear();s.merges=[];return s;};s.getMaxRows=()=>s.rows;s.getMaxColumns=()=>s.cols;s.insertRowsAfter=(n,count)=>s.rows+=count;s.insertColumnsAfter=(n,count)=>s.cols+=count;
  s.getDeveloperMetadata=()=>s.metadata.map(m=>({getKey:()=>m.key,getValue:()=>m.value}));s.addDeveloperMetadata=(key,value)=>s.metadata.push({key,value});
  s.setFrozenRows=n=>s.frozen=n;s.setHiddenGridlines=v=>s.hideGrid=v;s.setColumnWidths=(a,n,pixels)=>s.widths.push({start:a-1,end:a-1+n,pixels});s.setColumnWidth=(a,pixels)=>s.widths.push({start:a-1,end:a,pixels});
  s.setRowHeights=(a,n,pixels)=>s.heights.push({start:a-1,end:a-1+n,pixels});s.setRowHeight=(a,pixels)=>s.heights.push({start:a-1,end:a,pixels});s.autoResizeRows=()=>{};s.setTabColor=hex=>s.tabColor=rgb(hex);
  s.getCharts=()=>[];s.removeChart=()=>{};s.newChart=()=>{const chart={ranges:[],options:{}};const b={setChartType:type=>{chart.type=type;return b;},addRange:range=>{chart.ranges.push(range);return b;},setPosition:(row,col,x,y)=>{chart.anchor={rowIndex:row-1,columnIndex:col-1,offsetXPixels:x,offsetYPixels:y};return b;},setOption:(key,value)=>{chart.options[key]=value;return b;},setNumHeaders:n=>{chart.headers=n;return b;},build:()=>chart};return b;};
  // Retain source range coordinates for chart translation without reading cell content.
  const get=s.getRange;s.getRange=(...args)=>{const x=get(...args);x.grid=grid(coords(...args));return x;};s.insertChart=chart=>s.chartSpecs.push(chart);return s;
 }
 const book={getSheetByName:n=>tabs[n]||null,insertSheet:n=>(tabs[n]=makeSheet(n))};
 const context=vm.createContext({SpreadsheetApp:{getActiveSpreadsheet:()=>book,flush(){},newDataValidation:()=>{const b={requireDate:()=>b,setAllowInvalid:()=>b,build:()=>({condition:{type:'DATE_IS_VALID'},strict:true,showCustomUi:true})};return b;}},Charts:{ChartType:{COLUMN:'COLUMN'}}});vm.runInContext(source(),context);context.ledgerRefreshReports(ledger);
 for(const s of Object.values(tabs)){
  requests.push({addSheet:{properties:{sheetId:s.id,title:s.name,gridProperties:{rowCount:Math.max(s.rows,100),columnCount:s.cols,hideGridlines:!!s.hideGrid,frozenRowCount:s.frozen||0},tabColorStyle:{rgbColor:s.tabColor}}}});
  for(const m of s.metadata)requests.push({createDeveloperMetadata:{developerMetadata:{metadataKey:m.key,metadataValue:m.value,location:{sheetId:s.id},visibility:'DOCUMENT'}}});
 }
 for(const s of Object.values(tabs)){
  let maxR=0,maxC=0;for(const key of s.cells.keys()){const [r,c]=key.split(',').map(Number);maxR=Math.max(maxR,r);maxC=Math.max(maxC,c);}
  const rows=Array.from({length:maxR+1},(_,r)=>({values:Array.from({length:maxC+1},(_,c)=>{const v=s.cells.get(r+','+c)||{};return v.userEnteredValue?{userEnteredValue:v.userEnteredValue}:{};})}));
  requests.push({updateCells:{range:{sheetId:s.id,startRowIndex:0,endRowIndex:maxR+1,startColumnIndex:0,endColumnIndex:maxC+1},rows,fields:'userEnteredValue'}});
  const bands=new Map();
  for(let r=0;r<=maxR;r++){let c=0;while(c<=maxC){const v=s.cells.get(r+','+c)||{},style={};if(v.userEnteredFormat)style.userEnteredFormat=v.userEnteredFormat;if(v.dataValidation)style.dataValidation=v.dataValidation;const key=JSON.stringify(style),start=c++;while(c<=maxC){const next=s.cells.get(r+','+c)||{},ns={};if(next.userEnteredFormat)ns.userEnteredFormat=next.userEnteredFormat;if(next.dataValidation)ns.dataValidation=next.dataValidation;if(JSON.stringify(ns)!==key)break;c++;}if(key==='{}')continue;const bk=key+':'+start+':'+c,list=bands.get(bk)||[],last=list[list.length-1];if(last&&last.endRowIndex===r)last.endRowIndex++;else list.push({sheetId:s.id,startRowIndex:r,endRowIndex:r+1,startColumnIndex:start,endColumnIndex:c});bands.set(bk,list);}}
  for(const [key,ranges] of bands){const style=JSON.parse(key.slice(0,key.lastIndexOf('}:')+1));for(const range of ranges)requests.push({repeatCell:{range,cell:style,fields:Object.keys(style).join(',')}});}
  for(const range of s.merges)requests.push({mergeCells:{range,mergeType:'MERGE_ALL'}});
  for(const w of s.widths)requests.push({updateDimensionProperties:{range:{sheetId:s.id,dimension:'COLUMNS',startIndex:w.start,endIndex:w.end},properties:{pixelSize:w.pixels},fields:'pixelSize'}});
  for(const h of s.heights)requests.push({updateDimensionProperties:{range:{sheetId:s.id,dimension:'ROWS',startIndex:h.start,endIndex:h.end},properties:{pixelSize:h.pixels},fields:'pixelSize'}});
  if(s.filter)requests.push({setBasicFilter:{filter:{range:s.filter}}});
  for(const c of s.chartSpecs){const ranges=c.ranges.map(r=>r.grid),domain={sheetId:s.id,startRowIndex:ranges[0].startRowIndex,endRowIndex:ranges[0].endRowIndex,startColumnIndex:ranges[0].startColumnIndex,endColumnIndex:ranges[0].startColumnIndex+1};const series=[];
   for(let col=domain.endColumnIndex;col<ranges[0].endColumnIndex;col++)series.push({series:{sourceRange:{sources:[{...domain,startColumnIndex:col,endColumnIndex:col+1}]}},targetAxis:'LEFT_AXIS',colorStyle:{rgbColor:rgb(c.options.colors[series.length]||c.options.colors[0])}});
   requests.push({addChart:{chart:{spec:{title:c.options.title,fontName:'Arial',basicChart:{chartType:'COLUMN',legendPosition:c.options.legend.position==='none'?'NO_LEGEND':'BOTTOM_LEGEND',headerCount:1,domains:[{domain:{sourceRange:{sources:[domain]}}}],series,axis:[{position:'LEFT_AXIS',title:'INR'}]}},position:{overlayPosition:{anchorCell:{sheetId:s.id,rowIndex:c.anchor.rowIndex,columnIndex:c.anchor.columnIndex},offsetXPixels:c.anchor.offsetXPixels,offsetYPixels:c.anchor.offsetYPixels,widthPixels:c.options.width,heightPixels:c.options.height}}}}});
  }
 }
 requests.push({updateSheetProperties:{properties:{sheetId:ids['PL Dashboard'],index:0},fields:'index'}});
 return {requests,ids,tabs,model:context.ledgerReportModel(ledger,Date.now())};
}
function legacyLedger(rows){return {ledgerSchema:1,accounts:[],paymentRoutes:[],people:[],transactions:rows.slice(1).filter(r=>r[8]).map(r=>{const at=Math.round((Number(r[0])+Number(r[2]||0)-25569)*86400000-330*60000),amountMinor=Math.round(Number(r[4])*100);return {transactionId:String(r[8]),amount:Number(r[4]),amountMinor,personalMinor:amountMinor,kind:'EXPENSE',effectiveAt:at,accountId:'',destinationId:'',routeId:'',personId:'',originalId:'',shares:{},category:String(r[3]||''),paymentMode:String(r[5]||''),remarks:String(r[6]||''),spendingType:['NEED','WANT'].includes(r[12])?r[12]:'UNTAGGED',createdAt:Number(r[9]||at),updatedAt:Number(r[10]||at),deleted:r[11]===true};})};}
if(require.main===module){const rows=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));const result=buildReports(legacyLedger(rows));fs.writeFileSync(process.argv[3],JSON.stringify({requests:result.requests,ids:result.ids}));console.log(JSON.stringify({requests:result.requests.length,ids:result.ids,entries:result.model.activity.length}));}
module.exports={buildReports,legacyLedger};
