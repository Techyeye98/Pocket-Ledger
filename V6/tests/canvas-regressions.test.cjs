const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),backend=path.join(__dirname,'ledger-backend.test.cjs');
const source=fs.readFileSync(backend,'utf8');
const server={require:require('node:module').createRequire(backend),__dirname,structuredClone,console};
vm.createContext(server);
vm.runInContext(source.slice(0,source.indexOf("assert.equal(request('LEDGER_FETCH')"))+'\nthis.tabs=tabs;this.call=request;',server);
const src=fs.readFileSync(path.join(root,'Backend/Canvas.jsx'),'utf8');
const start=src.indexOf('// Helper'),app=src.indexOf('function App('),end=src.indexOf('\n  return (',app);
let stateIndex=0,editing=null,updatedRow=null,updatedIndex=null,error='';
const ctx={console,useMemo:f=>f(),useState:v=>{const i=++stateIndex;return [i===14?editing:(typeof v==='function'?v():v),v=>{if(i===18)error=v;}];},useRef:v=>({current:v}),useEffect:()=>{}};
for(const line of src.slice(0,start).split('\n')){const name=line.trim().replace(/,$/,'').split(' as ').pop();if(/^[A-Z]\w*$/.test(name))ctx[name]=()=>null;}
vm.createContext(ctx);vm.runInContext(src.slice(start,end)+'\nreturn {transactions,stats,overallBalances,accountsList,peopleList,categoryChartData,paymentChartData,dailyTimelineData,handleSaveEdit,handleSaveAdd};}\nthis.compute=App;',ctx);
function compute(rows,edit=null){editing=edit;stateIndex=0;updatedRow=null;error='';return ctx.compute({data:[{index_:0,data:rows.map((row,index_)=>({row,index_}))}],updateItem:(range,index,row)=>{updatedIndex=index;updatedRow=row;},insertItem:(range,index,row)=>{updatedRow=row;},deleteItem(){}});}
const headers=server.tabs.Transactions.data[0];
function row(kind,amount,extra={}){const values=Array(headers.length).fill('');for(const [name,value] of Object.entries({Date:'15-10-2026',Time:'12:00:00',Category:'Food',Amount:amount,'Transaction Id':kind+amount,'Updated At':1,Deleted:false,Type:kind,...extra}))values[headers.indexOf(name)]=value;return values;}
const mixed=compute([headers,row('EXPENSE',300,{'Personal amount':100}),row('INCOME',10000),row('TRANSFER',5000),row('LEND',200),row('FRIEND_PAID',50,{'Personal amount':50}),row('REFUND',30,{'Personal amount':20})]);
const sum=data=>data.reduce((sum,x)=>sum+x.amount,0);
assert.equal(mixed.stats.personalSpending,130);assert.equal(sum(mixed.categoryChartData),130);assert.equal(sum(mixed.paymentChartData),130);
const refunds=compute([headers,row('REFUND',30,{'Personal amount':20}),row('EXPENSE',10)]);
assert.equal(sum(refunds.categoryChartData),-10);assert.equal(sum(refunds.paymentChartData),-10);assert.equal(refunds.stats.personalSpending,-10);assert.equal(refunds.dailyTimelineData[0].expense,-10);
const duplicate=compute([headers,row('ACCOUNT_SNAPSHOT',0,{Account:'Cash','Account ID':'cash1','Account type':'CASH','Account balance':2000}),row('ACCOUNT_SNAPSHOT',0,{Account:'Cash','Account ID':'cash2','Account type':'CASH','Account balance':1000}),row('ACCOUNT_SNAPSHOT',0,{Account:'Cash','Account ID':'cash1','Account type':'CASH','Account balance':2500,'Updated At':2}),row('ACCOUNT_SNAPSHOT',0,{Person:'Sam','Person ID':'p1','To receive':10}),row('ACCOUNT_SNAPSHOT',0,{Person:'Sam','Person ID':'p2','To receive':20})]);
assert.equal(duplicate.overallBalances.netBalance,3500);assert.equal(duplicate.accountsList.length,2);assert.equal(duplicate.peopleList.length,2);
const legacy=compute([headers,row('ACCOUNT_SNAPSHOT',0,{Account:'Cash','Account balance':2000}),row('ACCOUNT_SNAPSHOT',0,{Account:'Cash','Account balance':1000,'Updated At':2})]);
assert.equal(legacy.accountsList.length,1);assert.equal(legacy.overallBalances.netBalance,1000);
const data=server.tabs.Transactions.data,initial=compute(data),entry=initial.transactions.find(t=>t.rawRow[8]==='purchase');
const form={date:entry.rawRow[0],month:'October',time:entry.rawRow[2],amount:'600',category:'Other',paymentMode:'card',remarks:'Edited amount',deviceName:'test',spendingType:'UNTAGGED'};
compute(data,entry).handleSaveEdit(form);
assert.ok(updatedRow);assert.equal(updatedRow[4],600);assert.equal(updatedRow[headers.indexOf('Personal amount')],600);
assert.equal(updatedRow[8],'purchase');assert.equal(updatedRow[9],entry.rawRow[9]);
server.tabs.Transactions.data[updatedIndex]=updatedRow;
for(const action of ['LEDGER_FETCH','LEDGER_PUSH']){const result=server.call(action);assert.equal(result.status,'success',result.message);assert.equal(result.ledger.transactions.find(t=>t.transactionId==='purchase').personalMinor,60000);}
for(const target of [initial.transactions.find(t=>t.rawRow[8]==='dinner'),initial.transactions.find(t=>t.type==='TRANSFER')]){compute(data,target).handleSaveEdit(form);assert.equal(updatedRow,null);assert.match(error,/app/);}
for(const amount of ['0','-1','NaN','Infinity','']){compute(data,entry).handleSaveEdit({...form,amount});assert.equal(updatedRow,null);assert.match(error,/positive amount/);}
const linkedRows=[headers,row('EXPENSE',100),row('REFUND',10,{'Original entry ID':'EXPENSE100'})];
const linked=compute(linkedRows).transactions[0];compute(linkedRows,linked).handleSaveEdit(form);assert.equal(updatedRow,null);assert.match(error,/app/);
compute([headers]).handleSaveAdd(form);assert.equal(updatedRow[4],600);assert.equal(updatedRow[headers.indexOf('Personal amount')],600);assert.equal(updatedRow[headers.indexOf('Type')],'EXPENSE');
console.log('PASS: Canvas edit/backend fetch+push round trip, unsafe/invalid edit guards, personal spending/refunds, stable snapshot IDs and legacy fallback.');
