const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
function sheet(){
 const s={data:[],maxRows:1000,columns:20};
 s.getLastRow=()=>{for(let i=s.data.length-1;i>=0;i--)if(s.data[i].some(v=>v!==''&&v!=null))return i+1;return 0;};s.getMaxRows=()=>s.maxRows;s.insertRowsAfter=(n,count)=>s.maxRows+=count;
 s.getName=()=>Object.keys(tabs).find(n=>tabs[n]===s);
 s.setName=name=>{assert.ok(!tabs[name]||tabs[name]===s);const old=s.getName();delete tabs[old];tabs[name]=s;return s;};
 s.hideSheet=()=>{s.hidden=true;return s;};s.setFrozenRows=n=>s.frozenRows=n;
 s.getLastColumn=()=>Math.max(0,...s.data.map(r=>r.length));s.getMaxColumns=()=>s.columns;s.insertColumnsAfter=(n,count)=>s.columns+=count;
 s.appendRow=r=>s.data.push([...r]);
 s.getRange=(row,col,rows=1,cols=1)=>({getValue:()=>s.data[row-1]?.[col-1]??'',setValue(v){while(s.data.length<row)s.data.push([]);s.data[row-1][col-1]=v;return this;},getValues:()=>Array.from({length:rows},(_,i)=>Array.from({length:cols},(_,j)=>s.data[row+i-1]?.[col+j-1]??'')),clearContent(){for(let i=0;i<rows;i++)for(let j=0;j<cols;j++)if(s.data[row+i-1])s.data[row+i-1][col+j-1]='';return this;},setNote(){return this;},setFontWeight(){return this;},setBackground(){return this;},setWrap(){return this;},setNumberFormat(){return this;},setValues(values){if(s.failWrite)throw Error('simulated interrupted staging');for(let i=0;i<rows;i++){while(s.data.length<row+i)s.data.push([]);for(let j=0;j<cols;j++)s.data[row+i-1][col+j-1]=values[i][j];}return this;}});
 s.deleteRows=(start,count)=>s.data.splice(start-1,count);
 s.getDataRange=()=>s.getRange(1,1,s.getLastRow(),s.getLastColumn());return s;
}
const tabs={'Transactions':sheet()};
tabs.Transactions.appendRow(['Date','Month','Time','Category','Amount','Payment Mode','Remarks','Device Name','Transaction Id','Created At','Updated At','Deleted','Spending Type']);
const book={getSheetByName:n=>tabs[n]||null,insertSheet:n=>(tabs[n]=sheet())};
let seq=0;
const context=vm.createContext({SpreadsheetApp:{getActiveSpreadsheet:()=>book,flush(){}},ContentService:{MimeType:{JSON:'json'},createTextOutput:text=>({text,getContent(){return text;},setMimeType(){return this;}})},LockService:{getScriptLock:()=>({waitLock(){},releaseLock(){}})},Utilities:{DigestAlgorithm:{MD5:'md5'},computeDigest:(kind,text)=>[...require('node:crypto').createHash(kind).update(text).digest()],getUuid:()=>`revision-${++seq}`}});
vm.runInContext(fs.readFileSync(path.join(root,'Backend/AppsScript.gs'),'utf8'),context);
const now=Date.now()-60000;
const account=(id,type,openingMinor)=>({accountId:id,name:id,type,openingMinor,openingAt:now-1000,position:0,archived:false,deleted:false,updatedAt:now});
function tx(id,kind,n,accountId,extra={}){return {transactionId:id,kind,amountMinor:n,personalMinor:kind==='EXPENSE'?n:0,amount:n/100,accountId,destinationId:'',personId:'',originalId:'',routeId:'',shares:{},effectiveAt:now,createdAt:now,updatedAt:now,deleted:false,spendingType:'UNTAGGED',date:'15-10-2026',time:'12:00:00',category:'Other',paymentMode:accountId,remarks:'',deviceName:'test',...extra};}
const l={ledgerSchema:1,accounts:[account('sbi','BANK',1000000),account('cash','CASH',100000),account('card','CARD',-200000)],paymentRoutes:[],people:[{personId:'rahul',name:'Rahul',updatedAt:now,deleted:false,archived:false}],transactions:[]};
l.transactions.push(tx('salary','INCOME',2000000,'sbi'),tx('atm','TRANSFER',100000,'sbi',{destinationId:'cash'}),tx('dinner','EXPENSE',120000,'sbi',{personalMinor:40000,shares:{rahul:80000}}),tx('repay','REPAYMENT_IN',80000,'cash',{originalId:'dinner',personId:'rahul'}),tx('purchase','EXPENSE',50000,'card'),tx('cardpay','TRANSFER',200000,'sbi',{destinationId:'card'}));
const request=(action,ledger=l)=>JSON.parse(context.doPost({postData:{contents:JSON.stringify({action,ledgerSchema:1,requestId:'request-1',ledger})}}).text);
const firstPush=request('LEDGER_PUSH');assert.equal(firstPush.status,'success');assert.equal(firstPush.dashboardWarning,'');assert.deepEqual(Object.keys(tabs),['Transactions']);assert.equal(firstPush.dashboardSchema,1);assert.equal(context.ledgerIsActive(),true);
assert.equal(request('LEDGER_FETCH').ledger.transactions.length,6);
assert.equal(request('LEDGER_PUSH').ledger.transactions.length,6,'Retry must not duplicate operations');
const pointerColumn=tabs.Transactions.data[0].indexOf('PL Snapshot Pointer');const pointer=tabs.Transactions.data[1][pointerColumn];
const broken=structuredClone(l);broken.transactions.find(t=>t.transactionId==='repay').amountMinor=80001;
assert.equal(request('LEDGER_PUSH',broken).status,'error');assert.equal(tabs.Transactions.data[1][pointerColumn],pointer);
tabs.Transactions.failWrite=true;
assert.equal(request('LEDGER_PUSH').status,'error');assert.equal(tabs.Transactions.data[1][pointerColumn],pointer,'Failed staging must leave committed pointer unchanged');tabs.Transactions.failWrite=false;
assert.equal(request('LEDGER_FETCH').ledger.transactions.length,6,'Restore ignores incomplete staging');
const legacy=request('CREATE');assert.equal(legacy.status,'error','Legacy writers must not overwrite rich ledger');
const missing=structuredClone(l);missing.transactions[3].originalId='missing';assert.equal(request('LEDGER_PUSH',missing).status,'error');
const badTransfer=structuredClone(l);badTransfer.transactions[1].destinationId='sbi';assert.equal(request('LEDGER_PUSH',badTransfer).status,'error');
const split=structuredClone(l);split.transactions[2].personalMinor=1;assert.equal(request('LEDGER_PUSH',split).status,'error');
const before=structuredClone(l);before.transactions[3].effectiveAt=now-1;assert.equal(request('LEDGER_PUSH',before).status,'error');
const deleted=structuredClone(l);deleted.transactions[2].deleted=true;deleted.transactions[2].updatedAt=now+1;assert.equal(request('LEDGER_PUSH',deleted).status,'error');
const cap=JSON.parse(context.doGet({parameter:{action:'CAPABILITIES'}}).text);assert.equal(cap.ledgerSchema,1);
console.log('PASS: ledger validation, full snapshot sync/restore, retry identity, invalid references/splits/transfers/settlements, interrupted-write recovery, and legacy-client gating.');
const headers=tabs.Transactions.data[0],column=n=>headers.indexOf(n),records=()=>tabs.Transactions.data.slice(1);
const rowFor=id=>records().find(r=>r[8]===id);
assert.equal(rowFor('PL_ACCOUNT:sbi')[column('Account balance')],25800);
assert.equal(rowFor('PL_ACCOUNT:cash')[column('Account balance')],2800);
assert.equal(rowFor('PL_ACCOUNT:card')[column('Account balance')],-500);
assert.equal(rowFor('PL_ACCOUNT:card')[column('Card dues')],500);
assert.equal(rowFor('PL_PERSON:rahul')[column('To receive')],0);
assert.equal(rowFor('dinner')[column('Personal amount')],400);
assert.equal(rowFor('salary')[column('Income')],20000);
const height=tabs.Transactions.data.length;
headers.push('My custom formula');rowFor('dinner')[headers.length-1]='=1+2';
assert.equal(request('LEDGER_PUSH').status,'success');
assert.equal(rowFor('dinner')[headers.length-1],'=1+2');
assert.equal(tabs.Transactions.data.length,height,'Retries reuse bounded snapshot buffers');
// Canvas edits with newer timestamps must survive the next app merge.
rowFor('purchase')[6]='edited in Canvas';rowFor('purchase')[10]=now+100;
assert.equal(request('LEDGER_PUSH').ledger.transactions.find(t=>t.transactionId==='purchase').remarks,'edited in Canvas');
const unpaid=structuredClone(l);unpaid.transactions[3].deleted=true;unpaid.transactions[3].updatedAt=now+200;
assert.equal(request('LEDGER_PUSH',unpaid).status,'success');
assert.equal(rowFor('PL_PERSON:rahul')[column('To receive')],800);
const hostile=structuredClone(unpaid);hostile.transactions[2].remarks='=IMPORTXML("example")';hostile.transactions[2].updatedAt=now+300;
assert.equal(request('LEDGER_PUSH',hostile).status,'success');assert.equal(rowFor('dinner')[6],"'=IMPORTXML(\"example\")");
assert.deepEqual(Object.keys(tabs),['Transactions']);
console.log('PASS: same-tab Canvas projection, account/card/people balances, custom columns, bounded snapshots, Canvas edits, and formula safety.');
const canvasPath=path.join(root,'Backend/Canvas.jsx');
assert.ok(fs.existsSync(canvasPath),'Maintained Canvas source must exist');
{
 const src=fs.readFileSync(canvasPath,'utf8'),start=src.indexOf('// Helper'),app=src.indexOf('function App('),end=src.indexOf('\n  return (',app);
 const ctx={useMemo:f=>f(),useState:v=>[typeof v==='function'?v():v,()=>{}],useRef:v=>({current:v}),useEffect:()=>{},console};
 for(const line of src.slice(0,start).split('\n')){const n=line.trim().replace(/,$/,'').split(' as ').pop();if(/^[A-Z]\w*$/.test(n))ctx[n]=()=>null;}
 vm.createContext(ctx);vm.runInContext(src.slice(start,end)+'\nreturn {transactions,stats,overallBalances,peopleList};}\nthis.compute=App;',ctx);
 const view=ctx.compute({data:[{index_:0,data:tabs.Transactions.data.map((row,index_)=>({row,index_}))}],updateItem(){},insertItem(){},deleteItem(){}});
 assert.equal(view.transactions.length,5);assert.equal(view.stats.periodIncome,20000);assert.equal(view.stats.personalSpending,900);assert.equal(view.overallBalances.netBalance,27300);assert.equal(view.overallBalances.totalDues,500);
 console.log('PASS: actual saved Canvas consumes the new script projection without extra transactions or incorrect balances.');
}
const cleared=structuredClone(l);cleared.transactions=cleared.transactions.map(t=>({transactionId:t.transactionId,kind:'EXPENSE',amount:0,amountMinor:0,personalMinor:0,effectiveAt:0,accountId:'',destinationId:'',routeId:'',personId:'',originalId:'',shares:{},category:'',paymentMode:'',remarks:'',date:'',time:'',deviceName:'',spendingType:'UNTAGGED',createdAt:0,updatedAt:Date.now(),deleted:true,syncStatus:'SYNC_PENDING'}));
assert.equal(request('LEDGER_PUSH',cleared).status,'success');
assert.equal(request('LEDGER_PUSH',l).ledger.transactions.filter(t=>!t.deleted).length,0,'An old app snapshot must not resurrect cleared history');
assert.equal(rowFor('PL_ACCOUNT:sbi')[column('Account balance')],10000,'Fresh start keeps account opening balances');
assert.equal(rowFor('PL_PERSON:rahul')[column('To receive')],0);
console.log('PASS: cleared history syncs with the existing script and stale snapshots cannot restore it.');
// A user may clear every Sheet data cell after clearing history on the phone.
tabs.Transactions.data=[headers.slice()];
for(const type of ['WALLET','BANK','CARD']){
 const leftover=Array(headers.length).fill('');leftover[column('Account type')]=type;
 tabs.Transactions.data.push(leftover);
}
const freshSync=request('LEDGER_PUSH',cleared);
assert.equal(freshSync.status,'success',freshSync.message);
assert.equal(freshSync.ledger.transactions.filter(t=>!t.deleted).length,0);
assert.equal(freshSync.ledger.transactions.length,cleared.transactions.length,'Hidden account metadata must not generate zero-amount expenses');
assert.equal(freshSync.ledger.accounts.length,3,'A blank Sheet must recover accounts from the phone');
assert.equal(rowFor('PL_ACCOUNT:sbi')[column('Account balance')],10000);
assert.equal(rowFor('PL_PERSON:rahul')[column('To receive')],0);
const badZero=structuredClone(cleared);badZero.transactions[0].deleted=false;
assert.equal(request('LEDGER_PUSH',badZero).status,'error','A live zero-amount entry must still be rejected');
console.log('PASS: a fully cleared Sheet accepts phone deletion markers, restores account balances, and still rejects live zero-amount entries.');
const originalHeaders=['Date','Month','Time','Category','Amount','Payment Mode','Remarks','Device Name','Transaction Id','Created At','Updated At','Deleted','Spending Type','My note'];
tabs.Transactions=sheet();tabs.Transactions.appendRow(originalHeaders);
tabs.Transactions.appendRow(['15-10-2026','October','12:00:00','Food',99,'Cash','original','phone','original-id',now,now,false,'NEED','=2+3']);
context.setupPocketLedger();context.setupPocketLedger();
const restored=request('LEDGER_FETCH').ledger;
assert.equal(restored.transactions.length,1);assert.equal(restored.transactions[0].transactionId,'original-id');assert.equal(restored.transactions[0].amountMinor,9900);
assert.equal(tabs.Transactions.data[1][13],'=2+3');assert.equal(tabs.Transactions.data[1][9],now);assert.equal(tabs.Transactions.data[1][10],now);assert.deepEqual(Object.keys(tabs),['Transactions']);
console.log('PASS: repeat setup preserves original expenses, IDs, timestamps and custom formulas without creating tabs.');
