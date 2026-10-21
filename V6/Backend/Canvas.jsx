import React, { useState, useMemo, useRef, useEffect } from 'react';
import * as d3 from 'd3';
import {
  Calendar,
  CreditCard,
  PieChart as PieChartIcon,
  BarChart3,
  TrendingUp,
  TrendingDown,
  Plus,
  Trash2,
  Edit2,
  Search,
  ArrowUpDown,
  SlidersHorizontal,
  ShoppingBag,
  DollarSign,
  Smartphone,
  Check,
  X,
  Wallet,
  CalendarDays,
  Clock,
  Sparkles,
  FileText,
  Building,
  Film,
  UtensilsCrossed,
  Car,
  PhoneCall,
  Activity,
  Home,
  BookOpen,
  Gift,
  Plane,
  Coins,
  Receipt,
  ArrowDownLeft,
  ArrowUpRight,
  Users,
  Layers,
  Landmark,
  HelpCircle,
  Info,
  RefreshCw,
  AlertCircle
} from 'lucide-react';

// Helper functions to clean and normalize text without emoji or encoding artifacts
function cleanText(str) {
  if (!str) return '';
  return String(str)
    .replace(/[\u{1F300}-\u{1FAFF}\u{1F600}-\u{1F64F}\u{1F680}-\u{1F6FF}\u{2600}-\u{27BF}\u{FE00}-\u{FE0F}\u{D800}-\u{DFFF}]/gu, '')
    .replace(/[^\x20-\x7E\u00A0-\u024F\u0400-\u04FF\u0900-\u097F]/g, '')
    .trim();
}

// Normalizes any incoming date string or numeric serial date from Apps Script / Sheets into standard YYYY-MM-DD format
function normalizeDate(raw, monthHint = '') {
  if (raw === null || raw === undefined || raw === '') return '';

  const rawStr = cleanText(String(raw));
  if (!rawStr) return '';

  const monthNames = ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"];
  const targetMonthIdx = monthHint ? monthNames.indexOf(monthHint.toLowerCase()) : -1;
  const targetMonthNum = targetMonthIdx >= 0 ? targetMonthIdx + 1 : 0;

  // Check if it's a numeric serial number (e.g. 46090, 46090.0, 46264, or string "46090")
  const isNumeric = typeof raw === 'number' || (!rawStr.includes('-') && !rawStr.includes('/') && !isNaN(Number(rawStr)));
  if (isNumeric) {
    const num = Number(raw);
    // If it's just a day number (1-31)
    if (num >= 1 && num <= 31 && !rawStr.includes('.')) {
      const actualMonth = targetMonthNum > 0 ? targetMonthNum : 8;
      const y = 2026;
      return `${y}-${String(actualMonth).padStart(2, '0')}-${String(Math.floor(num)).padStart(2, '0')}`;
    }
    // If it's a Google Sheets / Excel serial number (e.g., 30000 to 70000)
    if (num > 30000 && num < 70000) {
      const days = Math.floor(num);
      const ms = (days - 25569) * 86400 * 1000;
      const dateObj = new Date(ms);
      const y = dateObj.getUTCFullYear();
      let m = dateObj.getUTCMonth() + 1;
      let d = dateObj.getUTCDate();

      // Disambiguate if month was inverted
      if (targetMonthNum > 0 && m !== targetMonthNum && d === targetMonthNum && m <= 31) {
        const temp = m;
        m = d;
        d = temp;
      }

      return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    }
  }

  // Match YYYY-MM-DD or YYYY/MM/DD or YYYY.MM.DD
  const ymdMatch = rawStr.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})/);
  if (ymdMatch) {
    const y = ymdMatch[1];
    let m = parseInt(ymdMatch[2], 10);
    let d = parseInt(ymdMatch[3], 10);

    // If month and day were inverted (e.g., 2026-03-09 when Month column is September)
    if (targetMonthNum > 0) {
      if (m !== targetMonthNum && d === targetMonthNum && m <= 31) {
        const temp = m;
        m = d;
        d = temp;
      }
    }

    const mStr = String(m).padStart(2, '0');
    const dStr = String(d).padStart(2, '0');
    return `${y}-${mStr}-${dStr}`;
  }

  // Match DD/MM/YYYY, DD-MM-YYYY, or MM/DD/YYYY
  const dmyMatch = rawStr.match(/^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})/);
  if (dmyMatch) {
    const p1 = parseInt(dmyMatch[1], 10);
    const p2 = parseInt(dmyMatch[2], 10);
    const y = dmyMatch[3];
    let day = p1;
    let month = p2;

    if (targetMonthNum > 0) {
      if (p1 === targetMonthNum && p2 !== targetMonthNum && p2 <= 31) {
        // MM/DD/YYYY format matched with monthHint
        month = p1;
        day = p2;
      } else if (p2 === targetMonthNum) {
        // DD/MM/YYYY format matched with monthHint
        day = p1;
        month = p2;
      }
    } else {
      // Default to DD/MM/YYYY unless p2 > 12 (meaning p1 must be the month)
      if (p1 <= 12 && p2 > 12) {
        month = p1;
        day = p2;
      }
    }

    const mStr = String(month).padStart(2, '0');
    const dStr = String(day).padStart(2, '0');
    return `${y}-${mStr}-${dStr}`;
  }

  // Match single day number string like "30" or "30.0"
  const singleDayMatch = rawStr.match(/^(\d{1,2})(\.0+)?$/);
  if (singleDayMatch) {
    const day = parseInt(singleDayMatch[1], 10);
    if (day >= 1 && day <= 31) {
      const actualMonth = targetMonthNum > 0 ? targetMonthNum : 8;
      const y = 2026;
      return `${y}-${String(actualMonth).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    }
  }

  // Generic date fallback
  const parsed = new Date(rawStr);
  if (!isNaN(parsed.getTime())) {
    const y = parsed.getFullYear();
    let m = parsed.getMonth() + 1;
    let d = parsed.getDate();
    if (targetMonthNum > 0 && m !== targetMonthNum && d === targetMonthNum && m <= 31) {
      const temp = m;
      m = d;
      d = temp;
    }
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
  }

  return rawStr;
}

// Helper to format date strictly into DD/MM/YYYY for spreadsheet storage (e.g. 30/08/2026)
function formatDateForSheet(dateStr, monthHint = '') {
  if (!dateStr) return '';
  const s = String(dateStr).trim();
  // If already in DD/MM/YYYY format
  const dmyMatch = s.match(/^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})$/);
  if (dmyMatch) {
    const day = dmyMatch[1].padStart(2, '0');
    const month = dmyMatch[2].padStart(2, '0');
    const year = dmyMatch[3];
    return `${day}/${month}/${year}`;
  }
  // If in YYYY-MM-DD format
  const ymdMatch = s.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$/);
  if (ymdMatch) {
    const year = ymdMatch[1];
    const month = ymdMatch[2].padStart(2, '0');
    const day = ymdMatch[3].padStart(2, '0');
    return `${day}/${month}/${year}`;
  }
  const iso = normalizeDate(dateStr, monthHint);
  if (iso && /^\d{4}-\d{2}-\d{2}$/.test(iso)) {
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }
  return s;
}

// Formats YYYY-MM-DD or numeric serial dates strictly to DD/MM/YYYY for display in UI
function formatDisplayDate(dateInput, monthHint = '') {
  if (!dateInput) return '—';
  const s = String(dateInput).trim();
  const dmyMatch = s.match(/^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})$/);
  if (dmyMatch) {
    const day = dmyMatch[1].padStart(2, '0');
    const month = dmyMatch[2].padStart(2, '0');
    const year = dmyMatch[3];
    return `${day}/${month}/${year}`;
  }
  const ymdMatch = s.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$/);
  if (ymdMatch) {
    const year = ymdMatch[1];
    const month = ymdMatch[2].padStart(2, '0');
    const day = ymdMatch[3].padStart(2, '0');
    return `${day}/${month}/${year}`;
  }
  const iso = normalizeDate(dateInput, monthHint);
  if (iso && /^\d{4}-\d{2}-\d{2}$/.test(iso)) {
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }
  return s || '—';
}

// Converts any date string or serial number to YYYY-MM-DD for HTML5 date inputs
function toIsoDate(str, monthHint = '') {
  if (!str) return new Date().toISOString().split('T')[0];
  const iso = normalizeDate(str, monthHint);
  if (iso && /^\d{4}-\d{2}-\d{2}$/.test(iso)) {
    return iso;
  }
  return new Date().toISOString().split('T')[0];
}

// Helper to format time or decimal time (e.g. 0.5 -> 12:00:00)
function normalizeTime(raw) {
  if (raw === null || raw === undefined || raw === '') return '12:00:00';
  if (typeof raw === 'number' || (!isNaN(Number(raw)) && !String(raw).includes(':'))) {
    const num = Number(raw);
    if (num >= 0 && num < 1) {
      const totalSeconds = Math.round(num * 86400);
      const hours = Math.floor(totalSeconds / 3600);
      const minutes = Math.floor((totalSeconds % 3600) / 60);
      const seconds = totalSeconds % 60;
      return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
    }
  }
  return cleanText(String(raw)) || '12:00:00';
}

function normalizeCategory(raw) {
  if (!raw) return 'Other';
  const s = String(raw).toLowerCase();
  if (s.includes('grocer')) return 'Groceries';
  if (s.includes('entertain') || s.includes('movie')) return 'Entertainment';
  if (s.includes('food') || s.includes('dining')) return 'Food & Dining';
  if (s.includes('transport') || s.includes('auto') || s.includes('cab')) return 'Transportation';
  if (s.includes('shop')) return 'Shopping';
  if (s.includes('recharge') || s.includes('subscript')) return 'Recharge & Subscriptions';
  if (s.includes('health') || s.includes('medic') || s.includes('doctor')) return 'Health & Medical';
  if (s.includes('rent') || s.includes('bill')) return 'Rent & Bills';
  if (s.includes('edu') || s.includes('course')) return 'Education';
  if (s.includes('gift')) return 'Gifts';
  if (s.includes('travel') || s.includes('flight')) return 'Travel';
  if (s.includes('other')) return 'Other';
  const cleaned = cleanText(raw);
  return cleaned || 'Other';
}

function normalizePayment(raw) {
  if (!raw) return 'UPI';
  const s = String(raw).toLowerCase();
  if (s.includes('bank') || s.includes('transfer')) return 'Bank Transfer';
  if (s.includes('credit')) return 'Credit Card';
  if (s.includes('debit')) return 'Debit Card';
  if (s.includes('upi')) return 'UPI';
  if (s.includes('cash')) return 'Cash';
  const cleaned = cleanText(raw);
  return cleaned || 'UPI';
}

// Icon mapper for cleaned category names
function getCategoryIcon(catName) {
  const c = (catName || '').toLowerCase();
  if (c.includes('grocer')) return ShoppingBag;
  if (c.includes('entertain') || c.includes('movie')) return Film;
  if (c.includes('food') || c.includes('dining')) return UtensilsCrossed;
  if (c.includes('transport') || c.includes('auto') || c.includes('cab')) return Car;
  if (c.includes('shop')) return ShoppingBag;
  if (c.includes('recharge') || c.includes('subscript')) return PhoneCall;
  if (c.includes('health') || c.includes('medic') || c.includes('doctor')) return Activity;
  if (c.includes('rent') || c.includes('bill')) return Home;
  if (c.includes('edu') || c.includes('course')) return BookOpen;
  if (c.includes('gift')) return Gift;
  if (c.includes('travel') || c.includes('flight')) return Plane;
  return Coins;
}

// Icon mapper for cleaned payment methods
function getPaymentIcon(payName) {
  const p = (payName || '').toLowerCase();
  if (p.includes('bank') || p.includes('transfer')) return Building;
  if (p.includes('credit') || p.includes('debit')) return CreditCard;
  if (p.includes('upi')) return Smartphone;
  if (p.includes('cash')) return Coins;
  return CreditCard;
}

function App({ data, updateItem, deleteItem, insertItem, moveItem, followLink }) {
  // Slicer & Filter states
  const [selectedMonth, setSelectedMonth] = useState('All');
  const [selectedAccountFilter, setSelectedAccountFilter] = useState('All');
  const [selectedTypeFilter, setSelectedTypeFilter] = useState('All');
  const [selectedSpendingTypeFilter, setSelectedSpendingTypeFilter] = useState('All');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategoryFilter, setSelectedCategoryFilter] = useState('All');
  const [selectedPaymentFilter, setSelectedPaymentFilter] = useState('All');
  const [sortField, setSortField] = useState('date');
  const [sortOrder, setSortOrder] = useState('desc'); // 'asc' | 'desc'
  const [accountViewMode, setAccountViewMode] = useState('Overall'); // 'Overall' | 'By account'
  
  // Modals & Active Edit States
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState(null);
  const [deletingItem, setDeletingItem] = useState(null);
  
  // Chart Hover & Interactivity
  const [hoveredSlice, setHoveredSlice] = useState(null);
  const [activeChartTab, setActiveChartTab] = useState('donut'); // 'donut' | 'bars'

  const [editError, setEditError] = useState('');

  // Robust flattening supporting both range envelopes `[{ index_: 0, data: [...] }]` and direct row envelopes
  const flattenedRows = useMemo(() => {
    if (!data || !Array.isArray(data)) return [];
    if (data.length > 0 && Array.isArray(data[0]?.data)) {
      return data.flatMap(range => (range.data || []).map(r => ({ ...r, rangeIndex_: range.index_ ?? 0 })));
    }
    return data.map(r => ({ ...r, rangeIndex_: 0 }));
  }, [data]);

  // Dynamic Header and Rich Column Mapping
  const { headerRowIndex, headerRangeIndex, colMap, rawHeaders } = useMemo(() => {
    let hIdx = 0;
    let hRangeIdx = 0;
    let headersArr = [];
    let map = {
      date: 0,
      month: 1,
      time: 2,
      category: 3,
      amount: 4,
      paymentMode: 5,
      remarks: 6,
      deviceName: 7,
      transactionId: 8,
      createdAt: 9,
      updatedAt: 10,
      deleted: 11,
      spendingType: 12,
      // Rich optional headers
      type: -1,
      accountId: -1,
      personId: -1,
      originalId: -1,
      account: -1,
      accountType: -1,
      destAccount: -1,
      personalAmount: -1,
      income: -1,
      accountBalance: -1,
      cardDues: -1,
      person: -1,
      toReceive: -1,
      toPay: -1
    };

    if (flattenedRows && flattenedRows.length > 0) {
      const foundHeaderItem = flattenedRows.find(d => 
        d.row && d.row.some(cell => typeof cell === 'string' && (
          cell.toLowerCase() === 'date' || 
          cell.toLowerCase() === 'amount' ||
          cell.toLowerCase() === 'category'
        ))
      );

      if (foundHeaderItem && Array.isArray(foundHeaderItem.row)) {
        hIdx = foundHeaderItem.index_;
        hRangeIdx = foundHeaderItem.rangeIndex_ ?? 0;
        headersArr = foundHeaderItem.row;
        foundHeaderItem.row.forEach((col, idx) => {
          if (!col) return;
          const s = String(col).trim().toLowerCase();
          if (s === 'date') map.date = idx;
          else if (s === 'month') map.month = idx;
          else if (s === 'time') map.time = idx;
          else if (s === 'category') map.category = idx;
          else if (s === 'amount') map.amount = idx;
          else if (s.includes('payment')) map.paymentMode = idx;
          else if (s.includes('remark')) map.remarks = idx;
          else if (s.includes('device')) map.deviceName = idx;
          else if (s.includes('transaction id')) map.transactionId = idx;
          else if (s.includes('created at')) map.createdAt = idx;
          else if (s.includes('updated at')) map.updatedAt = idx;
          else if (s === 'deleted') map.deleted = idx;
          else if (s.includes('spending type')) map.spendingType = idx;
          // Rich headers detection
          else if (s === 'type' || s === 'kind' || s.includes('tx type') || s.includes('entry type')) map.type = idx;
          else if (s === 'account id') map.accountId = idx;
          else if (s === 'person id') map.personId = idx;
          else if (s === 'original entry id') map.originalId = idx;
          else if (s === 'account type' || s === 'acc type') map.accountType = idx;
          else if (s === 'account' || s.includes('source account') || s.includes('from account') || s === 'bank') map.account = idx;
          else if (s.includes('destination') || s.includes('to account')) map.destAccount = idx;
          else if (s.includes('personal') && (s.includes('amount') || s.includes('share') || s.includes('spent'))) map.personalAmount = idx;
          else if (s === 'income' || s.includes('income amount')) map.income = idx;
          else if (s.includes('account balance') || s.includes('current balance') || s.includes('bank balance') || s === 'balance') map.accountBalance = idx;
          else if (s.includes('card dues') || s.includes('credit dues') || s.includes('dues')) map.cardDues = idx;
          else if (s === 'person' || s.includes('contact') || s.includes('party')) map.person = idx;
          else if (s.includes('to receive') || s.includes('receivable') || s.includes('lena')) map.toReceive = idx;
          else if (s.includes('to pay') || s.includes('payable') || s.includes('dena')) map.toPay = idx;
        });
      }
    }
    return { headerRowIndex: hIdx, headerRangeIndex: hRangeIdx, colMap: map, rawHeaders: headersArr };
  }, [flattenedRows]);

  // Parse raw rows into typed transaction objects with cleaned string displays & strict business rules
  const { transactions, hasAccountsData, accountsList, hasPeopleData, peopleList, overallBalances } = useMemo(() => {
    if (!flattenedRows || !Array.isArray(flattenedRows)) {
      return { transactions: [], hasAccountsData: false, accountsList: [], hasPeopleData: false, peopleList: [], overallBalances: null };
    }

    // Exact recognized financial kinds
    const EXACT_KINDS = [
      'EXPENSE',
      'INCOME',
      'TRANSFER',
      'LEND',
      'BORROW',
      'REPAYMENT_IN',
      'REPAYMENT_OUT',
      'REFUND',
      'FRIEND_PAID',
      'ADJUSTMENT',
      'WRITE_OFF_RECEIVABLE',
      'FORGIVE_PAYABLE'
    ];

    // Helper to extract timestamp (numeric or parseable date string)
    const getRowTimestamp = (row) => {
      if (!row) return 0;
      if (colMap.updatedAt !== -1 && row[colMap.updatedAt] != null) {
        const val = row[colMap.updatedAt];
        const num = typeof val === 'number' ? val : Number(val);
        if (!isNaN(num) && num > 0) return num;
        const d = new Date(val).getTime();
        if (!isNaN(d)) return d;
      }
      if (colMap.createdAt !== -1 && row[colMap.createdAt] != null) {
        const val = row[colMap.createdAt];
        const num = typeof val === 'number' ? val : Number(val);
        if (!isNaN(num) && num > 0) return num;
        const d = new Date(val).getTime();
        if (!isNaN(d)) return d;
      }
      return 0;
    };

    // Filter valid non-header data rows
    const validDataRows = flattenedRows.filter(d => {
      if (!d || !d.row || d.index_ === headerRowIndex) return false;
      const hasData = d.row.some(c => c !== null && c !== undefined && String(c).trim() !== '');
      if (!hasData) return false;

      // Ignore Deleted rows
      if (colMap.deleted !== -1) {
        const delVal = d.row[colMap.deleted];
        if (delVal === true || String(delVal).toLowerCase() === 'true' || String(delVal).trim() === '1') {
          return false;
        }
      }
      return true;
    });

    // 1. Separate Rich Account Snapshots from transaction rows
    // Only detect if real account balance headers are present in source schema
    const accountSnapshotMap = new Map();
    let hasRealAccountSnapshots = false;

    if (colMap.accountBalance !== -1) {
      validDataRows.forEach(d => {
        const balCell = d.row[colMap.accountBalance];
        const rawKind = colMap.type !== -1 && d.row[colMap.type] != null ? cleanText(String(d.row[colMap.type])).trim().toUpperCase() : '';
        const isExplicitAccRow = rawKind === 'ACCOUNT' || rawKind === 'ACCOUNT_SNAPSHOT' || (balCell !== null && balCell !== undefined && String(balCell).trim() !== '');

        if (isExplicitAccRow && colMap.account !== -1 && d.row[colMap.account] != null) {
          const accName = cleanText(String(d.row[colMap.account]));
          if (!accName) return;

          const rawBal = typeof balCell === 'number' ? balCell : parseFloat(String(balCell).replace(/[^0-9.-]+/g, ''));
          if (isNaN(rawBal)) return;

          hasRealAccountSnapshots = true;
          const ts = getRowTimestamp(d.row);
          const rawAccType = colMap.accountType !== -1 && d.row[colMap.accountType] != null 
            ? cleanText(String(d.row[colMap.accountType])).toLowerCase() 
            : '';

          const isCard = rawAccType === 'card' || rawAccType.includes('credit') || accName.toLowerCase().includes('card');

          const accountKey = colMap.accountId !== -1 && d.row[colMap.accountId] ? 'id:' + d.row[colMap.accountId] : 'name:' + accName;
          const existing = accountSnapshotMap.get(accountKey);
          if (!existing || ts >= existing.timestamp) {
            accountSnapshotMap.set(accountKey, {
              id: accountKey,
              name: accName,
              accountType: isCard ? 'credit_card' : (rawAccType || 'bank'),
              balance: rawBal,
              timestamp: ts,
              isCard
            });
          }
        }
      });
    }

    // 2. Separate Person Snapshot Rows (Lena / Dena) using latest timestamp
    const personSnapshotMap = new Map();
    let hasRealPeopleSnapshots = false;

    if (colMap.person !== -1 && (colMap.toReceive !== -1 || colMap.toPay !== -1)) {
      validDataRows.forEach(d => {
        const pCell = d.row[colMap.person];
        if (pCell !== null && pCell !== undefined && String(pCell).trim() !== '') {
          const personName = cleanText(String(pCell));
          const recVal = colMap.toReceive !== -1 && d.row[colMap.toReceive] != null ? parseFloat(d.row[colMap.toReceive]) : 0;
          const payVal = colMap.toPay !== -1 && d.row[colMap.toPay] != null ? parseFloat(d.row[colMap.toPay]) : 0;

          if (!isNaN(recVal) || !isNaN(payVal)) {
            hasRealPeopleSnapshots = true;
            const ts = getRowTimestamp(d.row);
            const personKey = colMap.personId !== -1 && d.row[colMap.personId] ? 'id:' + d.row[colMap.personId] : 'name:' + personName;
            const existing = personSnapshotMap.get(personKey);
            if (!existing || ts >= existing.timestamp) {
              personSnapshotMap.set(personKey, {
                id: personKey,
                name: personName,
                toReceive: isNaN(recVal) ? 0 : Math.max(0, recVal),
                toPay: isNaN(payVal) ? 0 : Math.max(0, payVal),
                timestamp: ts
              });
            }
          }
        }
      });
    }

    // 3. Parse standard Transaction Rows (excluding pure account snapshots)
    const transactionRows = validDataRows
      .filter(d => {
        const rawKind = colMap.type !== -1 && d.row[colMap.type] != null ? cleanText(String(d.row[colMap.type])).trim().toUpperCase() : '';
        if (rawKind === 'ACCOUNT' || rawKind === 'ACCOUNT_SNAPSHOT') return false;
        return (d.row[colMap.date] != null || d.row[colMap.amount] != null || d.row[colMap.category] != null);
      })
      .map(d => {
        const rawMonthVal = d.row[colMap.month] ? cleanText(String(d.row[colMap.month]).trim()) : '';
        const rawDate = d.row[colMap.date] !== undefined && d.row[colMap.date] !== null ? d.row[colMap.date] : '';
        const normDate = normalizeDate(rawDate, rawMonthVal);
        const rawAmount = d.row[colMap.amount];
        const numAmount = typeof rawAmount === 'number' ? rawAmount : parseFloat(String(rawAmount).replace(/[^0-9.-]+/g, '')) || 0;
        
        let rawMonth = rawMonthVal;
        if ((!rawMonth || rawMonth === 'Unspecified') && normDate) {
          const parts = normDate.split('-');
          if (parts.length >= 2) {
            const mNum = parseInt(parts[1], 10);
            const monthNames = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
            if (mNum >= 1 && mNum <= 12) {
              rawMonth = monthNames[mNum - 1];
            }
          }
        }

        const rawCat = d.row[colMap.category] ? String(d.row[colMap.category]).trim() : 'Other';
        const cleanCat = normalizeCategory(rawCat);

        const rawPay = d.row[colMap.paymentMode] ? String(d.row[colMap.paymentMode]).trim() : 'UPI';
        const cleanPay = normalizePayment(rawPay);

        const rawRemarks = d.row[colMap.remarks] ? String(d.row[colMap.remarks]).trim() : '';
        const cleanRem = cleanText(rawRemarks);

        const rawDevice = d.row[colMap.deviceName] !== undefined && d.row[colMap.deviceName] !== null ? cleanText(String(d.row[colMap.deviceName]).trim()) : '';
        const cleanDev = rawDevice || '—';

        const rawTime = d.row[colMap.time];
        const cleanTime = normalizeTime(rawTime);

        // Map qualitative Spending Type column (WANT, NEED, etc.)
        const rawSpendingType = (colMap.spendingType !== -1 && d.row[colMap.spendingType] != null)
          ? cleanText(String(d.row[colMap.spendingType])).trim()
          : '';

        // Financial Kind Detection:
        // - Unknown kinds must NEVER default to expenses
        // - ONLY legacy BLANK kind defaults to EXPENSE
        let rawTypeStr = '';
        if (colMap.type !== -1 && d.row[colMap.type] != null) {
          rawTypeStr = cleanText(String(d.row[colMap.type])).trim().toUpperCase().replace(/\s+/g, '_');
        }

        let txType = 'EXPENSE';
        if (!rawTypeStr) {
          // Legacy blank kind defaults to EXPENSE
          txType = 'EXPENSE';
        } else if (EXACT_KINDS.includes(rawTypeStr)) {
          txType = rawTypeStr;
        } else {
          // Unknown kinds must never default to EXPENSE!
          txType = rawTypeStr;
        }

        // Account column (if richer header present, otherwise fallback to payment channel label for display/filter)
        const rawAccount = (colMap.account !== -1 && d.row[colMap.account] != null) 
          ? cleanText(String(d.row[colMap.account]).trim()) 
          : cleanPay;

        // Personal amount check: Use personal share for shared expenses & refunds when supplied
        let personalShare = numAmount;
        if (colMap.personalAmount !== -1 && d.row[colMap.personalAmount] != null && d.row[colMap.personalAmount] !== '') {
          const pVal = d.row[colMap.personalAmount];
          const parsedP = typeof pVal === 'number' ? pVal : parseFloat(String(pVal).replace(/[^0-9.-]+/g, ''));
          if (!isNaN(parsedP)) {
            personalShare = parsedP;
          }
        }

        return {
          rangeIndex_: d.rangeIndex_ ?? 0,
          index_: d.index_,
          date: normDate,
          displayDate: formatDisplayDate(rawDate || normDate, rawMonthVal),
          month: cleanText(rawMonth) || 'Unspecified',
          time: cleanTime,
          category: cleanCat,
          rawCategory: rawCat,
          amount: numAmount,
          personalShare,
          type: txType,
          spendingType: rawSpendingType,
          account: rawAccount || cleanPay,
          paymentMode: cleanPay,
          rawPaymentMode: rawPay,
          remarks: cleanRem,
          deviceName: cleanDev,
          rawRow: d.row
        };
      });

    // Compute Account Balances & Card Dues adhering strictly to rules:
    // - Available money excludes credit cards
    // - Net balance sums signed account balances directly (never subtracting card dues twice)
    // - For credit cards: negative balance is dues, positive balance is card credit
    let calculatedOverallBalances = null;
    const finalAccountsList = Array.from(accountSnapshotMap.values()).map(acc => {
      let cardDues = null;
      let displayBal = acc.balance;
      if (acc.isCard) {
        if (acc.balance < 0) {
          cardDues = Math.abs(acc.balance);
          displayBal = 0;
        } else {
          cardDues = 0;
          displayBal = acc.balance; // positive card credit
        }
      }
      return {
        name: acc.name,
        accountType: acc.accountType,
        balance: displayBal,
        cardDues,
        signedBalance: acc.balance,
        isCard: acc.isCard
      };
    });

    if (hasRealAccountSnapshots && finalAccountsList.length > 0) {
      const totalAvailable = finalAccountsList
        .filter(a => !a.isCard)
        .reduce((sum, a) => sum + (a.signedBalance > 0 ? a.signedBalance : 0), 0);

      const totalDues = finalAccountsList
        .filter(a => a.isCard && a.cardDues !== null)
        .reduce((sum, a) => sum + (a.cardDues || 0), 0);

      const netBalance = finalAccountsList.reduce((sum, a) => sum + a.signedBalance, 0);

      calculatedOverallBalances = {
        totalAvailable,
        totalDues,
        netBalance
      };
    }

    return {
      transactions: transactionRows,
      hasAccountsData: hasRealAccountSnapshots,
      accountsList: finalAccountsList,
      hasPeopleData: hasRealPeopleSnapshots,
      peopleList: Array.from(personSnapshotMap.values()),
      overallBalances: calculatedOverallBalances
    };
  }, [flattenedRows, headerRowIndex, colMap]);

  // Extract distinct months and category/payment lists for slicers & dropdowns
  const monthList = useMemo(() => {
    const monthsMap = new Map();
    transactions.forEach(t => {
      if (t.month) {
        if (!monthsMap.has(t.month)) {
          monthsMap.set(t.month, { count: 0, total: 0 });
        }
        const curr = monthsMap.get(t.month);
        curr.count += 1;
        curr.total += t.amount;
      }
    });

    const standardOrder = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
    const sortedKeys = Array.from(monthsMap.keys()).sort((a, b) => {
      const idxA = standardOrder.indexOf(a);
      const idxB = standardOrder.indexOf(b);
      if (idxA !== -1 && idxB !== -1) return idxA - idxB;
      return a.localeCompare(b);
    });

    return sortedKeys.map(name => ({
      name,
      count: monthsMap.get(name).count,
      total: monthsMap.get(name).total
    }));
  }, [transactions]);

  const allCategories = useMemo(() => {
    const set = new Set();
    transactions.forEach(t => {
      if (t.category) set.add(t.category);
    });
    return Array.from(set);
  }, [transactions]);

  const allPaymentModes = useMemo(() => {
    const set = new Set();
    transactions.forEach(t => {
      if (t.paymentMode) set.add(t.paymentMode);
    });
    return Array.from(set);
  }, [transactions]);

  const allAccounts = useMemo(() => {
    const set = new Set();
    transactions.forEach(t => {
      if (t.account) set.add(t.account);
    });
    return Array.from(set);
  }, [transactions]);

  // Min and max date detected from the dataset for datepicker bounds and placeholders
  const { minAvailableDate, maxAvailableDate } = useMemo(() => {
    const dates = transactions.map(t => t.date).filter(Boolean).sort();
    return {
      minAvailableDate: dates[0] || '',
      maxAvailableDate: dates[dates.length - 1] || ''
    };
  }, [transactions]);

  // Filtered dataset based on Month Slicer, From-To Dates, Account filter, Type filter and secondary filters
  const filteredTransactions = useMemo(() => {
    return transactions.filter(item => {
      // Date Range check (From Date & To Date)
      if (startDate && item.date && item.date < startDate) {
        return false;
      }
      if (endDate && item.date && item.date > endDate) {
        return false;
      }
      // Month slicer check
      if (selectedMonth !== 'All' && item.month.toLowerCase() !== selectedMonth.toLowerCase()) {
        return false;
      }
      // Account filter check
      if (selectedAccountFilter !== 'All' && item.account !== selectedAccountFilter) {
        return false;
      }
      // Type filter check
      if (selectedTypeFilter !== 'All' && item.type !== selectedTypeFilter) {
        return false;
      }
      // Spending Type / Priority filter check
      if (selectedSpendingTypeFilter !== 'All') {
        if (selectedSpendingTypeFilter === 'NONE') {
          if (item.spendingType) return false;
        } else if (item.spendingType.toUpperCase() !== selectedSpendingTypeFilter.toUpperCase()) {
          return false;
        }
      }
      // Category filter check
      if (selectedCategoryFilter !== 'All' && item.category !== selectedCategoryFilter) {
        return false;
      }
      // Payment filter check
      if (selectedPaymentFilter !== 'All' && item.paymentMode !== selectedPaymentFilter) {
        return false;
      }
      // Search query check
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchRemarks = item.remarks.toLowerCase().includes(q);
        const matchCat = item.category.toLowerCase().includes(q);
        const matchPay = item.paymentMode.toLowerCase().includes(q);
        const matchAcc = (item.account || '').toLowerCase().includes(q);
        const matchType = (item.type || '').toLowerCase().includes(q);
        const matchDate = item.date.toLowerCase().includes(q);
        const matchDevice = item.deviceName.toLowerCase().includes(q);
        const matchAmount = item.amount.toString().includes(q);
        const matchSpendingType = (item.spendingType || '').toLowerCase().includes(q);
        if (!matchRemarks && !matchCat && !matchPay && !matchAcc && !matchType && !matchDate && !matchDevice && !matchAmount && !matchSpendingType) {
          return false;
        }
      }
      return true;
    });
  }, [transactions, selectedMonth, selectedAccountFilter, selectedTypeFilter, selectedSpendingTypeFilter, startDate, endDate, selectedCategoryFilter, selectedPaymentFilter, searchQuery]);

  // Sorted filtered dataset
  const sortedTransactions = useMemo(() => {
    return [...filteredTransactions].sort((a, b) => {
      let comparison = 0;
      if (sortField === 'date') {
        comparison = (a.date || '').localeCompare(b.date || '');
      } else if (sortField === 'amount') {
        comparison = a.amount - b.amount;
      } else if (sortField === 'category') {
        comparison = (a.category || '').localeCompare(b.category || '');
      } else if (sortField === 'remarks') {
        comparison = (a.remarks || '').localeCompare(b.remarks || '');
      }
      return sortOrder === 'asc' ? comparison : -comparison;
    });
  }, [filteredTransactions, sortField, sortOrder]);

  const signedSpending = t => ['EXPENSE', 'FRIEND_PAID'].includes(t.type) ? t.personalShare : t.type === 'REFUND' ? -t.personalShare : 0;

  // KPI Calculations adhering strictly to real rules:
  // - Income: only INCOME
  // - Spending: EXPENSE (using personalShare when supplied) minus REFUND
  // - Transfers, loans, repayments do not count as income or expenses
  const stats = useMemo(() => {
    let periodIncome = 0;
    let personalSpending = 0;
    let grossExpense = 0;
    let refundTotal = 0;
    let expenseCount = 0;

    filteredTransactions.forEach(t => {
      if (t.type === 'INCOME') {
        periodIncome += t.amount;
      } else if (t.type === 'EXPENSE' || t.type === 'FRIEND_PAID') {
        // Friend-paid personal expenses count as spending!
        grossExpense += t.personalShare;
        personalSpending += t.personalShare;
        expenseCount += 1;
      } else if (t.type === 'REFUND') {
        // Refunds subtract the PERSONAL refunded share, not the full shared amount!
        refundTotal += t.personalShare;
        personalSpending -= t.personalShare;
      }
      // TRANSFERS, LOANS, REPAYMENTS, ADJUSTMENTS, and unknown kinds do not count as income or expenses
    });

    const netChange = periodIncome - personalSpending;
    const avgExpense = expenseCount > 0 ? grossExpense / expenseCount : 0;
    
    // Top category (calculated over expense transactions)
    const catMap = {};
    filteredTransactions.forEach(t => {
      if (signedSpending(t) !== 0) {
        catMap[t.category] = (catMap[t.category] || 0) + signedSpending(t);
      }
    });
    let topCategory = 'None';
    let maxCatAmount = 0;
    Object.entries(catMap).forEach(([cat, val]) => {
      if (val > maxCatAmount) {
        maxCatAmount = val;
        topCategory = cat;
      }
    });

    // Top payment mode
    const payMap = {};
    filteredTransactions.forEach(t => {
      if (signedSpending(t) !== 0) {
        payMap[t.paymentMode] = (payMap[t.paymentMode] || 0) + signedSpending(t);
      }
    });
    let topPayment = 'None';
    let maxPayAmount = 0;
    Object.entries(payMap).forEach(([pm, val]) => {
      if (val > maxPayAmount) {
        maxPayAmount = val;
        topPayment = pm;
      }
    });

    return {
      periodIncome,
      personalSpending,
      netChange,
      totalSpent: personalSpending,
      count: filteredTransactions.length,
      expenseCount,
      avgExpense,
      topCategory,
      maxCatAmount,
      topPayment,
      maxPayAmount
    };
  }, [filteredTransactions]);

  // Category breakdown data for charts
  const categoryChartData = useMemo(() => {
    const map = {};
    filteredTransactions.forEach(t => {
      if (signedSpending(t) === 0) return;
      if (!map[t.category]) {
        map[t.category] = { category: t.category, amount: 0, count: 0 };
      }
      map[t.category].amount += signedSpending(t);
      map[t.category].count += 1;
    });
    return Object.values(map).sort((a, b) => b.amount - a.amount);
  }, [filteredTransactions]);

  // Payment mode breakdown data for charts
  const paymentChartData = useMemo(() => {
    const map = {};
    filteredTransactions.forEach(t => {
      if (signedSpending(t) === 0) return;
      if (!map[t.paymentMode]) {
        map[t.paymentMode] = { mode: t.paymentMode, amount: 0, count: 0 };
      }
      map[t.paymentMode].amount += signedSpending(t);
      map[t.paymentMode].count += 1;
    });
    return Object.values(map).sort((a, b) => b.amount - a.amount);
  }, [filteredTransactions]);

  // Income & Spending Timeline breakdown (Green Income, Purple Spending)
  const dailyTimelineData = useMemo(() => {
    const map = {};
    filteredTransactions.forEach(t => {
      const key = t.date || 'Unknown';
      if (!map[key]) {
        map[key] = { date: key, income: 0, expense: 0, count: 0, items: [] };
      }
      if (t.type === 'INCOME') {
        map[key].income += t.amount;
      } else if (t.type === 'EXPENSE' || t.type === 'FRIEND_PAID') {
        map[key].expense += t.personalShare;
      } else if (t.type === 'REFUND') {
        map[key].expense -= t.personalShare;
      }
      map[key].count += 1;
      map[key].items.push(t);
    });
    return Object.values(map).sort((a, b) => a.date.localeCompare(b.date));
  }, [filteredTransactions]);

  // Currency Formatter
  const formatCurrency = (val) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0
    }).format(val);
  };

  // Color Palette mapping for categories
  const categoryColors = useMemo(() => {
    const defaultColorMap = {
      'Rent & Bills': '#4F46E5',
      'Health & Medical': '#0891B2',
      'Shopping': '#DB2777',
      'Travel': '#059669',
      'Groceries': '#D97706',
      'Entertainment': '#7C3AED',
      'Food & Dining': '#EA580C',
      'Education': '#0D9488',
      'Recharge & Subscriptions': '#2563EB',
      'Gifts': '#E11D48',
      'Transportation': '#475569',
      'Other': '#64748B'
    };
    const palette = [
      '#4F46E5', '#0891B2', '#059669', '#D97706', '#DB2777',
      '#7C3AED', '#2563EB', '#0D9488', '#EA580C', '#E11D48',
      '#475569', '#64748B'
    ];
    const colorMap = { ...defaultColorMap };
    allCategories.forEach((cat, i) => {
      if (!colorMap[cat]) {
        colorMap[cat] = palette[i % palette.length];
      }
    });
    return colorMap;
  }, [allCategories]);

  // Handlers for CRUD Operations synced back to sheet
  // Preserve stable Transaction Id, Created At, unknown custom fields, and update Updated At
  // Delete must set Deleted=true rather than physically remove rows
  const handleSaveAdd = (formData) => {
    setEditError('');
    if (!Number.isFinite(Number(formData.amount)) || Number(formData.amount) <= 0 || !normalizeDate(formData.date)) {
      setEditError('Enter a positive amount and a valid date.'); return;
    }

    let m = formData.month;
    if (formData.date) {
      const iso = normalizeDate(formData.date);
      if (iso) {
        const parts = iso.split('-');
        if (parts.length >= 2) {
          const mNum = parseInt(parts[1], 10);
          const monthNames = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
          if (mNum >= 1 && mNum <= 12) m = monthNames[mNum - 1];
        }
      }
    }

    const formattedDate = formatDateForSheet(formData.date, m);
    const rowLen = Math.max(rawHeaders.length, 13, ...Object.values(colMap).map(v => v + 1));
    const rowArray = new Array(rowLen).fill('');

    rowArray[colMap.date] = formattedDate;
    rowArray[colMap.month] = m || 'September';
    rowArray[colMap.time] = formData.time || '12:00:00';
    rowArray[colMap.category] = formData.category || 'Other';
    rowArray[colMap.amount] = Number(formData.amount);
    if (colMap.personalAmount !== -1) rowArray[colMap.personalAmount] = Number(formData.amount);
    if (colMap.type !== -1) rowArray[colMap.type] = 'EXPENSE';
    rowArray[colMap.paymentMode] = formData.paymentMode || 'UPI';
    rowArray[colMap.remarks] = formData.remarks || '';
    rowArray[colMap.deviceName] = formData.deviceName || '';

    // Generate stable Transaction Id
    if (colMap.transactionId !== -1) {
      rowArray[colMap.transactionId] = typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : 'tx_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
    }

    // Set Created At & Updated At timestamps
    const nowTs = Date.now();
    if (colMap.createdAt !== -1) rowArray[colMap.createdAt] = nowTs;
    if (colMap.updatedAt !== -1) rowArray[colMap.updatedAt] = nowTs;
    if (colMap.deleted !== -1) rowArray[colMap.deleted] = false;
    if (colMap.spendingType !== -1) rowArray[colMap.spendingType] = formData.spendingType || '';

    // Find first available empty row in flattenedRows
    const firstEmptyItem = flattenedRows.find(d => {
      if (!d || d.index_ <= headerRowIndex) return false;
      if (!d.row) return true;
      return d.row.every(cell => cell === null || cell === undefined || String(cell).trim() === '');
    });

    const targetRangeIdx = firstEmptyItem ? (firstEmptyItem.rangeIndex_ ?? 0) : headerRangeIndex;

    if (firstEmptyItem) {
      updateItem(targetRangeIdx, firstEmptyItem.index_, rowArray);
    } else {
      insertItem(targetRangeIdx, undefined, rowArray);
    }
    setIsAddModalOpen(false);
  };

  const handleSaveEdit = (formData) => {
    if (!editingItem) return;
    setEditError('');
    const id = editingItem.rawRow?.[colMap.transactionId];
    const linked = colMap.originalId !== -1 && (editingItem.rawRow?.[colMap.originalId] || transactions.some(t => t.rawRow?.[colMap.originalId] === id));
    if (!['EXPENSE', 'INCOME'].includes(editingItem.type) || linked || (editingItem.type === 'EXPENSE' && editingItem.personalShare !== editingItem.amount)) {
      setEditError('Edit shared, linked and advanced entries in the Pocket Ledger app.'); return;
    }
    if (!Number.isFinite(Number(formData.amount)) || Number(formData.amount) <= 0 || !normalizeDate(formData.date)) {
      setEditError('Enter a positive amount and a valid date.'); return;
    }

    let m = formData.month;
    if (formData.date) {
      const iso = normalizeDate(formData.date);
      if (iso) {
        const parts = iso.split('-');
        if (parts.length >= 2) {
          const mNum = parseInt(parts[1], 10);
          const monthNames = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
          if (mNum >= 1 && mNum <= 12) m = monthNames[mNum - 1];
        }
      }
    }

    const formattedDate = formatDateForSheet(formData.date, m);

    // Preserve original row to maintain stable Transaction Id, Created At, and unknown custom fields
    const rowArray = editingItem.rawRow ? [...editingItem.rawRow] : [];
    const maxCol = Math.max(rowArray.length, 13, ...Object.values(colMap).map(v => v + 1));
    while (rowArray.length < maxCol) {
      rowArray.push('');
    }

    rowArray[colMap.date] = formattedDate;
    rowArray[colMap.month] = m;
    rowArray[colMap.time] = formData.time;
    rowArray[colMap.category] = formData.category;
    rowArray[colMap.amount] = Number(formData.amount);
    if (colMap.personalAmount !== -1 && editingItem.type === 'EXPENSE') rowArray[colMap.personalAmount] = Number(formData.amount);
    rowArray[colMap.paymentMode] = formData.paymentMode;
    rowArray[colMap.remarks] = formData.remarks;
    rowArray[colMap.deviceName] = formData.deviceName;
    if (colMap.spendingType !== -1) rowArray[colMap.spendingType] = formData.spendingType || '';

    // Always update Updated At timestamp on edit
    if (colMap.updatedAt !== -1) rowArray[colMap.updatedAt] = Date.now();

    updateItem(editingItem.rangeIndex_ ?? 0, editingItem.index_, rowArray);
    setEditingItem(null);
  };

  const handleConfirmDelete = () => {
    if (!deletingItem) return;

    // Delete must set Deleted=true rather than physically remove rows
    if (colMap.deleted !== -1 && deletingItem.rawRow) {
      const updatedRow = [...deletingItem.rawRow];
      updatedRow[colMap.deleted] = true;
      if (colMap.updatedAt !== -1) updatedRow[colMap.updatedAt] = Date.now();
      updateItem(deletingItem.rangeIndex_ ?? 0, deletingItem.index_, updatedRow);
    } else {
      deleteItem(deletingItem.rangeIndex_ ?? 0, deletingItem.index_);
    }
    setDeletingItem(null);
  };

  // Toggle sorting
  const handleSort = (field) => {
    if (sortField === field) {
      setSortOrder(prev => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortOrder('desc');
    }
  };

  const grandTotal = useMemo(() => {
    return transactions.reduce((acc, t) => acc + t.amount, 0);
  }, [transactions]);

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col antialiased selection:bg-indigo-500 selection:text-white">
      {editError && <p role="alert" className="p-4 text-red-700">{editError}</p>}
      {/* Top Header Navbar */}
      <header className="bg-white border-b border-slate-200 sticky top-0 z-30 shadow-xs">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3.5 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3.5">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 to-purple-600 flex items-center justify-center text-white shadow-sm shadow-indigo-200">
              <Wallet className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-base sm:text-lg font-bold text-slate-900 tracking-tight">
                  Pocket Ledger
                </h1>
                <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-purple-50 text-purple-700 border border-purple-200/60">
                  Interactive
                </span>
              </div>
              <p className="text-xs text-slate-500">
                Money Analytics & Slicer • Live spreadsheet connection
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2.5">
            <button
              onClick={() => setIsAddModalOpen(true)}
              className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 rounded-lg shadow-sm transition-all focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1"
            >
              <Plus className="w-4 h-4" />
              Add entry
            </button>
          </div>
        </div>
      </header>

      {/* Main Content Body */}
      <main className="max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 py-6 space-y-6 flex-1">
        
        {/* Date Range Slicer Bar plus All accounts selector */}
        <section className="bg-white p-4 sm:p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-100">
            <div className="flex items-center gap-2 text-slate-800 font-bold text-sm">
              <SlidersHorizontal className="w-4 h-4 text-indigo-600" />
              <span>Date Range Slicer & Accounts Slicer</span>
              <span className="text-xs text-slate-400 font-normal hidden sm:inline">
                (Filter by period and account — timeline, category & metrics sync automatically)
              </span>
            </div>
            {(startDate || endDate || selectedAccountFilter !== 'All') && (
              <button
                onClick={() => {
                  setStartDate('');
                  setEndDate('');
                  setSelectedAccountFilter('All');
                }}
                className="text-xs text-indigo-600 hover:text-indigo-800 font-semibold inline-flex items-center gap-1 self-start sm:self-auto bg-indigo-50 hover:bg-indigo-100 px-2.5 py-1 rounded-md transition-colors"
              >
                Reset Slicers
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>

          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
            <div className="flex flex-wrap items-center gap-3">
              {/* From & To Date Range Inputs */}
              <div className="flex flex-wrap items-center gap-2.5 p-2 bg-slate-50 border border-slate-200 rounded-xl">
                <div className="flex items-center gap-1 px-1 text-xs font-bold text-slate-700">
                  <Calendar className="w-3.5 h-3.5 text-indigo-600" />
                  <span>Dates:</span>
                </div>
                
                <div className="flex items-center gap-1.5">
                  <label className="text-[11px] font-semibold text-slate-500">From</label>
                  <input
                    type="date"
                    value={startDate}
                    min={minAvailableDate}
                    max={endDate || maxAvailableDate}
                    onChange={(e) => setStartDate(e.target.value)}
                    className="px-2.5 py-1 text-xs font-medium text-slate-800 bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 shadow-2xs"
                  />
                </div>

                <div className="flex items-center gap-1.5">
                  <label className="text-[11px] font-semibold text-slate-500">To</label>
                  <input
                    type="date"
                    value={endDate}
                    min={startDate || minAvailableDate}
                    max={maxAvailableDate}
                    onChange={(e) => setEndDate(e.target.value)}
                    className="px-2.5 py-1 text-xs font-medium text-slate-800 bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 shadow-2xs"
                  />
                </div>
              </div>

              {/* All accounts selector */}
              <div className="flex items-center gap-2 p-2 bg-slate-50 border border-slate-200 rounded-xl">
                <Landmark className="w-3.5 h-3.5 text-indigo-600 ml-1" />
                <span className="text-xs font-bold text-slate-700">Account:</span>
                <select
                  value={selectedAccountFilter}
                  onChange={(e) => setSelectedAccountFilter(e.target.value)}
                  className="px-3 py-1 text-xs font-semibold text-slate-800 bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 shadow-2xs"
                >
                  <option value="All">All accounts</option>
                  {allAccounts.map(acc => (
                    <option key={acc} value={acc}>{acc}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="text-xs text-slate-500 flex items-center gap-1.5 px-1">
              <CalendarDays className="w-4 h-4 text-slate-400" />
              <span>
                {startDate && endDate
                  ? `Period: ${formatDisplayDate(startDate)} to ${formatDisplayDate(endDate)}`
                  : startDate
                    ? `From ${formatDisplayDate(startDate)} onwards`
                    : endDate
                      ? `Up to ${formatDisplayDate(endDate)}`
                      : 'Showing all dates recorded'}
                {selectedAccountFilter !== 'All' ? ` • Account: ${selectedAccountFilter}` : ' • All accounts'}
              </span>
            </div>
          </div>
        </section>

        {/* Approved Four Summary Cards: Overall net balance, Period income, Personal spending, Net change */}
        <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Card 1: Overall net balance (with available money/card dues) */}
          <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs relative overflow-hidden group hover:border-indigo-300 transition-colors">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                Overall net balance
              </span>
              <div className="w-8 h-8 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center">
                <Wallet className="w-4 h-4" />
              </div>
            </div>
            
            {hasAccountsData && overallBalances ? (
              <>
                <div className="mt-2 flex items-baseline gap-2">
                  <span className="text-2xl font-extrabold text-slate-900 tracking-tight">
                    {formatCurrency(overallBalances.netBalance)}
                  </span>
                </div>
                <div className="mt-2 text-[11px] text-slate-500 flex items-center justify-between">
                  <span className="text-emerald-600 font-semibold">Available: {formatCurrency(overallBalances.totalAvailable)}</span>
                  <span className="text-rose-500 font-semibold">Dues: {formatCurrency(overallBalances.totalDues)}</span>
                </div>
              </>
            ) : (
              <div className="mt-3">
                <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium bg-slate-100 text-slate-600 border border-slate-200/70">
                  <Info className="w-3.5 h-3.5 text-slate-500" />
                  Not synced yet
                </span>
                <p className="text-[11px] mt-2" style={{ color: '#657487' }}>
                  Add accounts in Pocket Ledger, then sync
                </p>
              </div>
            )}
          </div>

          {/* Card 2: Period income */}
          <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs relative overflow-hidden group hover:border-emerald-300 transition-colors">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                Period income
              </span>
              <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
                <ArrowDownLeft className="w-4 h-4" />
              </div>
            </div>
            <div className="mt-2 flex items-baseline gap-2">
              <span className="text-2xl font-extrabold text-emerald-600 tracking-tight">
                {formatCurrency(stats.periodIncome)}
              </span>
            </div>
            <div className="mt-2 text-xs text-slate-500 truncate">
              {stats.periodIncome > 0 ? 'Verified credits in selected period' : 'No income entries logged for period'}
            </div>
          </div>

          {/* Card 3: Personal spending */}
          <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs relative overflow-hidden group hover:border-purple-300 transition-colors">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                Personal spending
              </span>
              <div className="w-8 h-8 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center">
                <ArrowUpRight className="w-4 h-4" />
              </div>
            </div>
            <div className="mt-2 flex items-baseline gap-2">
              <span className="text-2xl font-extrabold text-slate-900 tracking-tight">
                {formatCurrency(stats.personalSpending)}
              </span>
            </div>
            <div className="mt-2 text-xs text-slate-500 flex items-center justify-between">
              <span>{stats.expenseCount} expenses</span>
              {stats.avgExpense > 0 && (
                <span className="text-slate-400 font-medium">Avg: {formatCurrency(stats.avgExpense)}</span>
              )}
            </div>
          </div>

          {/* Card 4: Net change */}
          <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs relative overflow-hidden group hover:border-indigo-300 transition-colors">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                Net change
              </span>
              <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${
                stats.netChange >= 0 ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'
              }`}>
                {stats.netChange >= 0 ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
              </div>
            </div>
            <div className="mt-2 flex items-baseline gap-2">
              <span className={`text-2xl font-extrabold tracking-tight ${
                stats.netChange >= 0 ? 'text-emerald-600' : 'text-slate-900'
              }`}>
                {stats.netChange < 0 ? `- ${formatCurrency(Math.abs(stats.netChange))}` : `+ ${formatCurrency(stats.netChange)}`}
              </span>
            </div>
            <div className="mt-2 text-xs text-slate-500 truncate">
              {stats.netChange >= 0 ? 'Positive period cash surplus' : 'Net expenditure in period'}
            </div>
          </div>
        </section>

        {/* Your accounts Section: individual balances and credit-card dues with Overall / By account switch */}
        <section className="bg-white p-5 sm:p-6 rounded-2xl border border-slate-200/80 shadow-xs">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4 pb-3 border-b border-slate-100">
            <div>
              <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                <Landmark className="w-4 h-4 text-indigo-600" />
                Your accounts
              </h2>
              <p className="text-xs text-slate-500">
                Bank, wallet, cash balances and credit-card dues (balances stay current regardless of date slicer)
              </p>
            </div>
            <div className="flex items-center bg-slate-100 p-0.5 rounded-lg text-xs">
              <button
                onClick={() => setAccountViewMode('Overall')}
                className={`px-3 py-1 rounded-md font-medium transition-all ${
                  accountViewMode === 'Overall' ? 'bg-white text-slate-900 shadow-xs font-semibold' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Overall
              </button>
              <button
                onClick={() => setAccountViewMode('By account')}
                className={`px-3 py-1 rounded-md font-medium transition-all ${
                  accountViewMode === 'By account' ? 'bg-white text-slate-900 shadow-xs font-semibold' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                By account
              </button>
            </div>
          </div>

          {hasAccountsData && accountsList.length > 0 ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {accountsList.map(acc => (
                <div key={acc.id || acc.name} className="p-4 rounded-xl border border-slate-200 bg-slate-50/50 flex flex-col justify-between space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-slate-800">{acc.name}</span>
                    <span className="text-[10px] font-semibold text-slate-500 bg-white px-2 py-0.5 rounded-md border border-slate-200">
                      Active
                    </span>
                  </div>
                  <div className="flex items-baseline justify-between">
                    <div>
                      <p className="text-[10px] text-slate-500 uppercase font-semibold">Available</p>
                      <p className="text-sm font-bold text-slate-900">{formatCurrency(acc.balance || 0)}</p>
                    </div>
                    {acc.cardDues !== null && acc.cardDues > 0 && (
                      <div className="text-right">
                        <p className="text-[10px] text-rose-500 uppercase font-semibold">Card dues</p>
                        <p className="text-sm font-bold text-rose-600">{formatCurrency(acc.cardDues)}</p>
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="py-8 px-4 text-center rounded-xl bg-slate-50 border border-dashed border-slate-200 flex flex-col items-center justify-center">
              <div className="w-10 h-10 rounded-full bg-slate-100 flex items-center justify-center text-slate-500 mb-2">
                <Landmark className="w-5 h-5" />
              </div>
              <p className="text-xs font-bold text-slate-700">Not synced yet</p>
              <p className="text-xs max-w-sm mt-0.5" style={{ color: '#657487' }}>
                Add accounts in Pocket Ledger, then sync to view live bank balances and credit card dues here.
              </p>
            </div>
          )}
        </section>

        {/* Visual Charts Grid */}
        <section className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Chart Left: Category Distribution (Donut & Breakdown) */}
          <div className="lg:col-span-6 bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col justify-between">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                  <PieChartIcon className="w-4 h-4 text-indigo-600" />
                  Category Breakdown
                </h2>
                <p className="text-xs text-slate-500">
                  Spending share by expense type across selected dates
                </p>
              </div>
              <div className="flex items-center bg-slate-100 p-0.5 rounded-lg text-xs">
                <button
                  onClick={() => setActiveChartTab('donut')}
                  className={`px-2.5 py-1 rounded-md font-medium transition-all ${
                    activeChartTab === 'donut' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Donut
                </button>
                <button
                  onClick={() => setActiveChartTab('bars')}
                  className={`px-2.5 py-1 rounded-md font-medium transition-all ${
                    activeChartTab === 'bars' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Rank List
                </button>
              </div>
            </div>

            {categoryChartData.length === 0 ? (
              <div className="h-64 flex flex-col items-center justify-center text-slate-400 text-xs">
                No expense data available for selected filters.
              </div>
            ) : activeChartTab === 'donut' && categoryChartData.every(item => item.amount >= 0) ? (
              <div className="grid grid-cols-1 sm:grid-cols-12 gap-5 items-center">
                {/* Donut SVG */}
                <div className="sm:col-span-5 flex items-center justify-center relative py-2">
                  <DonutChartComponent
                    data={categoryChartData}
                    total={stats.totalSpent}
                    categoryColors={categoryColors}
                    formatCurrency={formatCurrency}
                    hoveredSlice={hoveredSlice}
                    setHoveredSlice={setHoveredSlice}
                  />
                </div>

                {/* Donut Legend */}
                <div className="sm:col-span-7 space-y-1.5 max-h-64 overflow-y-auto pr-1 pb-1">
                  {categoryChartData.map((item) => {
                    const pct = stats.totalSpent > 0 ? ((item.amount / stats.totalSpent) * 100).toFixed(1) : 0;
                    const isHovered = hoveredSlice === item.category;
                    const CatIcon = getCategoryIcon(item.category);
                    return (
                      <div
                        key={item.category}
                        onMouseEnter={() => setHoveredSlice(item.category)}
                        onMouseLeave={() => setHoveredSlice(null)}
                        className={`flex items-center justify-between p-1.5 rounded-lg text-xs transition-colors cursor-pointer ${
                          isHovered ? 'bg-slate-100 font-medium' : 'hover:bg-slate-50'
                        }`}
                      >
                        <div className="flex items-center gap-2 min-w-0 pr-2">
                          <span
                            className="w-2.5 h-2.5 rounded-full shrink-0"
                            style={{ backgroundColor: categoryColors[item.category] || '#4F46E5' }}
                          />
                          <CatIcon className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                          <span className="truncate text-slate-700 font-medium">{item.category}</span>
                        </div>
                        <div className="text-right shrink-0">
                          <span className="text-slate-900 font-semibold">{formatCurrency(item.amount)}</span>
                          <span className="text-slate-500 text-[10px] ml-1.5">({pct}%)</span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            ) : (
              <div className="space-y-3 py-1 max-h-64 overflow-y-auto pr-1 pb-1">
                {categoryChartData.map((item) => {
                  const pct = stats.totalSpent > 0 ? (item.amount / stats.totalSpent) * 100 : 0;
                  const CatIcon = getCategoryIcon(item.category);
                  return (
                    <div key={item.category} className="space-y-1">
                      <div className="flex items-center justify-between text-xs">
                        <div className="flex items-center gap-1.5">
                          <CatIcon className="w-3.5 h-3.5 text-slate-500" />
                          <span className="font-semibold text-slate-700">{item.category}</span>
                        </div>
                        <div className="space-x-1.5">
                          <span className="font-bold text-slate-900">{formatCurrency(item.amount)}</span>
                          <span className="text-slate-500 text-[11px]">({pct.toFixed(1)}%)</span>
                        </div>
                      </div>
                      <div className="h-2 w-full bg-slate-100 rounded-full overflow-hidden">
                        <div
                          className="h-full rounded-full transition-all duration-500"
                          style={{
                            width: `${Math.max(0, Math.min(100, pct))}%`,
                            backgroundColor: categoryColors[item.category] || '#4F46E5'
                          }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Chart Right: Income & Spending Timeline (Dual-colored: Green Income, Original Purple Expenses) */}
          <div className="lg:col-span-6 bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col justify-between">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                  <BarChart3 className="w-4 h-4 text-indigo-600" />
                  Income & Spending Timeline
                </h2>
                <p className="text-xs text-slate-500">
                  Chronological cashflow overview (Green = Income, Purple = Spending)
                </p>
              </div>
              <div className="flex items-center gap-2">
                <div className="flex items-center gap-1.5 text-[10px] text-slate-600 font-semibold bg-slate-100 px-2 py-1 rounded-md">
                  <span className="w-2 h-2 rounded-full bg-emerald-500" />
                  <span>Income</span>
                  <span className="w-2 h-2 rounded-full bg-indigo-600 ml-1.5" />
                  <span>Spending</span>
                </div>
              </div>
            </div>

            {dailyTimelineData.length === 0 ? (
              <div className="h-64 flex flex-col items-center justify-center text-slate-400 text-xs">
                No timeline records available for selected filters.
              </div>
            ) : (
              <div className="h-64 w-full">
                <TimelineBarChartComponent
                  data={dailyTimelineData}
                  formatCurrency={formatCurrency}
                />
              </div>
            )}
          </div>

          {/* Chart Bottom: Payment Modes & Device Split */}
          <div className="lg:col-span-12 bg-white p-5 sm:p-6 rounded-2xl border border-slate-200/80 shadow-xs">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-5">
              <div>
                <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                  <CreditCard className="w-4 h-4 text-indigo-600" />
                  Payment Methods & Device Distribution
                </h2>
                <p className="text-xs text-slate-500">
                  Personal spending less personal refunds by payment channel; devices show recorded activity
                </p>
              </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Payment Methods */}
              <div className="lg:col-span-8 space-y-3">
                <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Payment Channels
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {paymentChartData.map((item) => {
                    const pct = stats.totalSpent > 0 ? ((item.amount / stats.totalSpent) * 100).toFixed(1) : '0';
                    const PayIcon = getPaymentIcon(item.mode);
                    return (
                      <div key={item.mode} className="bg-slate-50 border border-slate-200/80 p-3.5 rounded-xl flex items-center justify-between hover:bg-slate-100/70 transition-colors">
                        <div className="flex items-center gap-3 min-w-0 pr-2">
                          <div className="w-9 h-9 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
                            <PayIcon className="w-4 h-4" />
                          </div>
                          <div className="min-w-0">
                            <p className="text-xs font-bold text-slate-800 truncate">{item.mode}</p>
                            <p className="text-[11px] text-slate-500">{item.count} {item.count === 1 ? 'transaction' : 'transactions'}</p>
                          </div>
                        </div>
                        <div className="text-right shrink-0">
                          <p className="text-xs font-bold text-slate-900">{formatCurrency(item.amount)}</p>
                          <p className="text-[10px] font-bold text-indigo-600">{pct}% of net spending</p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Device Channels */}
              <div className="lg:col-span-4 space-y-3 flex flex-col justify-start">
                <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Logged Devices
                </h3>
                <DeviceSplitCard transactions={filteredTransactions} formatCurrency={formatCurrency} />
              </div>
            </div>
          </div>
        </section>

        {/* Lena / Dena Section: To receive / To pay summaries and person rows */}
        <section className="bg-white p-5 sm:p-6 rounded-2xl border border-slate-200/80 shadow-xs">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4 pb-3 border-b border-slate-100">
            <div>
              <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                <Users className="w-4 h-4 text-indigo-600" />
                Lena / Dena
              </h2>
              <p className="text-xs text-slate-500">
                To receive & To pay balance tracker (stays current regardless of date filter)
              </p>
            </div>
            {hasPeopleData && (
              <div className="flex items-center gap-3 text-xs">
                <div className="flex items-center gap-1.5 bg-emerald-50 text-emerald-700 px-2.5 py-1 rounded-lg font-semibold border border-emerald-200/60">
                  <span>To receive:</span>
                  <span>{formatCurrency(peopleList.reduce((sum, p) => sum + p.toReceive, 0))}</span>
                </div>
                <div className="flex items-center gap-1.5 bg-rose-50 text-rose-700 px-2.5 py-1 rounded-lg font-semibold border border-rose-200/60">
                  <span>To pay:</span>
                  <span>{formatCurrency(peopleList.reduce((sum, p) => sum + p.toPay, 0))}</span>
                </div>
              </div>
            )}
          </div>

          {hasPeopleData && peopleList.length > 0 ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {peopleList.map(person => (
                <div key={person.id || person.name} className="p-3.5 rounded-xl border border-slate-200 bg-slate-50/50 flex items-center justify-between">
                  <div className="flex items-center gap-2.5 min-w-0 pr-2">
                    <div className="w-8 h-8 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center font-bold text-xs shrink-0">
                      {person.name.charAt(0).toUpperCase()}
                    </div>
                    <span className="font-semibold text-xs text-slate-800 truncate">{person.name}</span>
                  </div>
                  <div className="text-right shrink-0 space-y-0.5">
                    {person.toReceive > 0 && (
                      <p className="text-xs font-bold text-emerald-600">
                        + {formatCurrency(person.toReceive)} <span className="text-[10px] text-slate-400 font-normal">(Get)</span>
                      </p>
                    )}
                    {person.toPay > 0 && (
                      <p className="text-xs font-bold text-rose-600">
                        - {formatCurrency(person.toPay)} <span className="text-[10px] text-slate-400 font-normal">(Give)</span>
                      </p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="py-8 px-4 text-center rounded-xl bg-slate-50 border border-dashed border-slate-200 flex flex-col items-center justify-center">
              <div className="w-10 h-10 rounded-full bg-slate-100 flex items-center justify-center text-slate-500 mb-2">
                <Users className="w-5 h-5" />
              </div>
              <p className="text-xs font-bold text-slate-700">Not synced yet</p>
              <p className="text-xs max-w-sm mt-0.5" style={{ color: '#657487' }}>
                Add Lena / Dena transactions in Pocket Ledger, then sync to view person dues here.
              </p>
            </div>
          )}
        </section>

        {/* Activity Ledger & Data Management Section */}
        <section className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
          {/* Table Controls Bar */}
          <div className="p-4 border-b border-slate-200/80 flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
                <FileText className="w-4 h-4" />
              </div>
              <div>
                <h2 className="text-sm font-bold text-slate-900">
                  Activity Ledger
                </h2>
                <p className="text-xs text-slate-500">
                  Showing {sortedTransactions.length} of {transactions.length} total entries (Date, Type, Details, Account, Amount)
                </p>
              </div>
            </div>

            {/* Approved Filters: Type / Account / Category / Payment / search */}
            <div className="flex flex-wrap items-center gap-2">
              {/* Search input */}
              <div className="relative min-w-[160px] flex-1 sm:flex-initial">
                <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  placeholder="Search ledger..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full pl-8 pr-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-lg text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-indigo-500 focus:bg-white"
                />
                {searchQuery && (
                  <button
                    onClick={() => setSearchQuery('')}
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                  >
                    <X className="w-3 h-3" />
                  </button>
                )}
              </div>

              {/* Type Filter */}
              <select
                value={selectedTypeFilter}
                onChange={(e) => setSelectedTypeFilter(e.target.value)}
                className="text-xs bg-slate-50 border border-slate-200 rounded-lg px-2 py-1.5 text-slate-700 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              >
                <option value="All">All Types</option>
                <option value="EXPENSE">Expense</option>
                <option value="INCOME">Income</option>
                <option value="REFUND">Refund</option>
                <option value="FRIEND_PAID">Friend Paid</option>
                <option value="TRANSFER">Transfer</option>
                <option value="LEND">Lend</option>
                <option value="BORROW">Borrow</option>
                <option value="REPAYMENT_IN">Repayment In</option>
                <option value="REPAYMENT_OUT">Repayment Out</option>
                <option value="ADJUSTMENT">Adjustment</option>
                <option value="WRITE_OFF_RECEIVABLE">Write-off Receivable</option>
                <option value="FORGIVE_PAYABLE">Forgive Payable</option>
              </select>

              {/* Spending Type / Priority Filter */}
              <select
                value={selectedSpendingTypeFilter}
                onChange={(e) => setSelectedSpendingTypeFilter(e.target.value)}
                className="text-xs bg-slate-50 border border-slate-200 rounded-lg px-2 py-1.5 text-slate-700 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                title="Filter by Spending Type (NEED / WANT)"
              >
                <option value="All">All Priorities</option>
                <option value="NEED">Need</option>
                <option value="WANT">Want</option>
                <option value="NONE">Unspecified</option>
              </select>

              {/* Account Filter */}
              <select
                value={selectedAccountFilter}
                onChange={(e) => setSelectedAccountFilter(e.target.value)}
                className="text-xs bg-slate-50 border border-slate-200 rounded-lg px-2 py-1.5 text-slate-700 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              >
                <option value="All">All Accounts</option>
                {allAccounts.map(acc => (
                  <option key={acc} value={acc}>{acc}</option>
                ))}
              </select>

              {/* Category Filter */}
              <select
                value={selectedCategoryFilter}
                onChange={(e) => setSelectedCategoryFilter(e.target.value)}
                className="text-xs bg-slate-50 border border-slate-200 rounded-lg px-2 py-1.5 text-slate-700 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              >
                <option value="All">All Categories</option>
                {allCategories.map(c => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>

              {/* Payment Filter */}
              <select
                value={selectedPaymentFilter}
                onChange={(e) => setSelectedPaymentFilter(e.target.value)}
                className="text-xs bg-slate-50 border border-slate-200 rounded-lg px-2 py-1.5 text-slate-700 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              >
                <option value="All">All Payment Modes</option>
                {allPaymentModes.map(pm => (
                  <option key={pm} value={pm}>{pm}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Table Container - Activity Ledger columns: Date, Type, Details, Account, Amount */}
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50/80 border-b border-slate-200 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                  <th
                    onClick={() => handleSort('date')}
                    className="py-3 px-4 cursor-pointer hover:text-indigo-600 transition-colors"
                  >
                    <div className="flex items-center gap-1.5">
                      <span>Date</span>
                      <ArrowUpDown className="w-3 h-3 text-slate-400" />
                    </div>
                  </th>
                  <th className="py-3 px-4">
                    <span>Type</span>
                  </th>
                  <th
                    onClick={() => handleSort('remarks')}
                    className="py-3 px-4 cursor-pointer hover:text-indigo-600 transition-colors"
                  >
                    <div className="flex items-center gap-1.5">
                      <span>Details</span>
                      <ArrowUpDown className="w-3 h-3 text-slate-400" />
                    </div>
                  </th>
                  <th className="py-3 px-4">
                    <span>Account</span>
                  </th>
                  <th
                    onClick={() => handleSort('amount')}
                    className="py-3 px-4 cursor-pointer hover:text-indigo-600 transition-colors text-right"
                  >
                    <div className="flex items-center justify-end gap-1.5">
                      <span>Amount</span>
                      <ArrowUpDown className="w-3 h-3 text-slate-400" />
                    </div>
                  </th>
                  <th className="py-3 px-4 text-center">
                    <span>Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs">
                {sortedTransactions.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="py-12 text-center text-slate-400">
                      <div className="flex flex-col items-center justify-center gap-2">
                        <Search className="w-6 h-6 text-slate-300" />
                        <p className="font-semibold text-slate-700">No matching transactions found</p>
                        <p className="text-slate-400 text-xs">Try adjusting your date range or resetting your search filter.</p>
                      </div>
                    </td>
                  </tr>
                ) : (
                  sortedTransactions.map((item) => {
                    const CatIcon = getCategoryIcon(item.category);
                    const PayIcon = getPaymentIcon(item.paymentMode);
                    return (
                      <tr
                        key={item.index_}
                        className="hover:bg-slate-50/80 transition-colors group"
                      >
                        {/* Date */}
                        <td className="py-3 px-4 whitespace-nowrap">
                          <div className="font-semibold text-slate-800">{item.displayDate || item.date || '—'}</div>
                          <div className="text-[11px] text-slate-400 flex items-center gap-1">
                            <Clock className="w-3 h-3" />
                            {item.time || '—'}
                          </div>
                        </td>

                        {/* Type */}
                        <td className="py-3 px-4 whitespace-nowrap">
                          <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-semibold border ${
                            item.type === 'INCOME'
                              ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                              : item.type === 'REFUND'
                                ? 'bg-sky-50 text-sky-700 border-sky-200'
                                : item.type === 'FRIEND_PAID'
                                  ? 'bg-purple-50 text-purple-700 border-purple-200'
                                  : item.type === 'TRANSFER'
                                    ? 'bg-blue-50 text-blue-700 border-blue-200'
                                    : (item.type === 'LEND' || item.type === 'BORROW' || item.type.startsWith('REPAYMENT'))
                                      ? 'bg-amber-50 text-amber-700 border-amber-200'
                                      : 'bg-slate-100 text-slate-700 border-slate-200'
                          }`}>
                            {item.type}
                          </span>
                        </td>

                        {/* Details (Remarks, Spending Type & Category) */}
                        <td className="py-3 px-4 max-w-[260px]">
                          <div className="flex items-center gap-1.5">
                            <span className="font-medium text-slate-800 truncate">
                              {item.remarks || <span className="text-slate-400 italic">No remarks</span>}
                            </span>
                            {item.spendingType && (
                              <span className={`inline-flex items-center px-1.5 py-0.2 rounded text-[10px] font-bold uppercase tracking-wider shrink-0 border ${
                                item.spendingType.toUpperCase() === 'NEED'
                                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                                  : item.spendingType.toUpperCase() === 'WANT'
                                    ? 'bg-amber-50 text-amber-700 border-amber-200'
                                    : 'bg-slate-100 text-slate-700 border-slate-200'
                              }`}>
                                {item.spendingType}
                              </span>
                            )}
                          </div>
                          <div className="flex items-center gap-1.5 mt-0.5">
                            <span
                              className="w-1.5 h-1.5 rounded-full shrink-0"
                              style={{ backgroundColor: categoryColors[item.category] || '#4F46E5' }}
                            />
                            <span className="text-[11px] text-slate-500 font-medium truncate">{item.category}</span>
                          </div>
                        </td>

                        {/* Account */}
                        <td className="py-3 px-4 whitespace-nowrap">
                          <div className="inline-flex items-center gap-1.5 text-slate-700 bg-slate-100 px-2 py-1 rounded-md font-medium text-[11px]">
                            <PayIcon className="w-3 h-3 text-slate-500" />
                            <span>{item.account || item.paymentMode}</span>
                          </div>
                        </td>

                        {/* Amount */}
                        <td className={`py-3 px-4 text-right whitespace-nowrap font-bold ${
                          item.type === 'INCOME' ? 'text-emerald-600' : 'text-slate-900'
                        }`}>
                          {item.type === 'INCOME' ? `+ ${formatCurrency(item.amount)}` : formatCurrency(item.amount)}
                        </td>

                        {/* Actions */}
                        <td className="py-3 px-4 text-center whitespace-nowrap">
                          <div className="inline-flex items-center gap-1 opacity-80 group-hover:opacity-100 transition-opacity">
                            <button
                              onClick={() => setEditingItem(item)}
                              className="p-1.5 rounded-lg text-slate-500 hover:text-indigo-600 hover:bg-indigo-50 transition-colors"
                              title="Edit transaction"
                            >
                              <Edit2 className="w-3.5 h-3.5" />
                            </button>
                            <button
                              onClick={() => setDeletingItem(item)}
                              className="p-1.5 rounded-lg text-slate-500 hover:text-red-600 hover:bg-red-50 transition-colors"
                              title="Delete transaction"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </section>
      </main>

      {/* ADD EXPENSE MODAL */}
      {isAddModalOpen && (
        <ExpenseModal
          title="Add New Entry"
          initialData={{
            date: new Date().toISOString().split('T')[0],
            month: selectedMonth !== 'All' ? selectedMonth : 'September',
            time: '12:00:00',
            category: allCategories[0] || 'Groceries',
            amount: '',
            paymentMode: allAccounts[0] || 'Fampay',
            remarks: '',
            deviceName: 'iPhone',
            spendingType: ''
          }}
          categories={allCategories}
          paymentModes={allAccounts.length > 0 ? allAccounts : allPaymentModes}
          error={editError}
          onSave={handleSaveAdd}
          onClose={() => setIsAddModalOpen(false)}
        />
      )}

      {/* EDIT EXPENSE MODAL */}
      {editingItem && (
        <ExpenseModal
          title="Edit Activity Entry"
          initialData={editingItem}
          categories={allCategories}
          paymentModes={allAccounts.length > 0 ? allAccounts : allPaymentModes}
          error={editError}
          onSave={handleSaveEdit}
          onClose={() => setEditingItem(null)}
        />
      )}

      {/* DELETE CONFIRMATION DIALOG OVERLAY */}
      {deletingItem && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-sm w-full p-6 shadow-xl border border-slate-200 animate-in fade-in zoom-in duration-150">
            <div className="w-10 h-10 rounded-full bg-red-50 text-red-600 flex items-center justify-center mx-auto mb-4">
              <Trash2 className="w-5 h-5" />
            </div>
            <h3 className="text-base font-bold text-slate-900 text-center mb-1">
              Delete Transaction?
            </h3>
            <p className="text-xs text-slate-500 text-center mb-5 leading-relaxed">
              Are you sure you want to remove <span className="font-semibold text-slate-800">{deletingItem.category}</span> ({formatCurrency(deletingItem.amount)})? This will permanently remove the record from your spreadsheet.
            </p>
            <div className="flex items-center gap-3">
              <button
                onClick={() => setDeletingItem(null)}
                className="flex-1 px-4 py-2 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleConfirmDelete}
                className="flex-1 px-4 py-2 text-xs font-semibold text-white bg-red-600 hover:bg-red-700 rounded-xl transition-colors shadow-sm shadow-red-200"
              >
                Yes, Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// -------------------------------------------------------------
// Interactive Donut Chart Component using D3
// -------------------------------------------------------------
function DonutChartComponent({ data, total, categoryColors, formatCurrency, hoveredSlice, setHoveredSlice }) {
  const svgRef = useRef(null);

  useEffect(() => {
    if (!svgRef.current || !data || data.length === 0) return;

    const width = 220;
    const height = 220;
    const margin = 10;
    const radius = Math.min(width, height) / 2 - margin;
    const innerRadius = radius * 0.65;

    const svg = d3.select(svgRef.current);
    svg.selectAll('*').remove();

    const g = svg
      .attr('viewBox', `0 0 ${width} ${height}`)
      .append('g')
      .attr('transform', `translate(${width / 2}, ${height / 2})`);

    const pie = d3.pie()
      .value(d => d.amount)
      .sort(null)
      .padAngle(0.03);

    const arc = d3.arc()
      .innerRadius(innerRadius)
      .outerRadius(radius)
      .cornerRadius(4);

    const hoverArc = d3.arc()
      .innerRadius(innerRadius - 2)
      .outerRadius(radius + 5)
      .cornerRadius(5);

    const arcs = g.selectAll('.arc')
      .data(pie(data))
      .enter()
      .append('g')
      .attr('class', 'arc');

    arcs.append('path')
      .attr('d', d => (hoveredSlice === d.data.category ? hoverArc(d) : arc(d)))
      .attr('fill', d => categoryColors[d.data.category] || '#4F46E5')
      .attr('class', 'transition-all duration-200 cursor-pointer')
      .attr('stroke', '#ffffff')
      .attr('stroke-width', 2)
      .on('mouseenter', (event, d) => {
        setHoveredSlice(d.data.category);
      })
      .on('mouseleave', () => {
        setHoveredSlice(null);
      });

  }, [data, hoveredSlice, categoryColors]);

  const activeItem = data.find(d => d.category === hoveredSlice);

  return (
    <div className="relative flex items-center justify-center">
      <svg ref={svgRef} className="w-[200px] h-[200px]" />
      <div className="absolute inset-0 flex flex-col items-center justify-center text-center pointer-events-none px-4">
        {activeItem ? (
          <>
            <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider truncate max-w-[120px]">
              {activeItem.category}
            </span>
            <span className="text-sm font-extrabold text-slate-900 tracking-tight">
              {formatCurrency(activeItem.amount)}
            </span>
            <span className="text-[10px] text-indigo-600 font-semibold">
              {total > 0 ? ((activeItem.amount / total) * 100).toFixed(1) : 0}%
            </span>
          </>
        ) : (
          <>
            <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">
              Total
            </span>
            <span className="text-base font-extrabold text-slate-900 tracking-tight">
              {formatCurrency(total)}
            </span>
            <span className="text-[10px] text-slate-400 font-medium">
              {data.length} categories
            </span>
          </>
        )}
      </div>
    </div>
  );
}

// -------------------------------------------------------------
// Income & Spending Timeline Bar Chart Component using D3
// Green for Income, Original Indigo/Purple for Spending
// -------------------------------------------------------------
function TimelineBarChartComponent({ data, formatCurrency }) {
  const svgRef = useRef(null);
  const [activeTooltip, setActiveTooltip] = useState(null);

  useEffect(() => {
    if (!svgRef.current || !data || data.length === 0) return;

    const container = svgRef.current.parentElement;
    const width = container.clientWidth || 450;
    const height = 230;
    const margin = { top: 20, right: 15, bottom: 40, left: 55 };

    const svg = d3.select(svgRef.current);
    svg.selectAll('*').remove();

    svg.attr('viewBox', `0 0 ${width} ${height}`);

    const x = d3.scaleBand()
      .domain(data.map(d => d.date))
      .range([margin.left, width - margin.right])
      .padding(0.35);

    const maxVal = d3.max(data, d => Math.max(d.income || 0, d.expense || 0)) || 100;
    const y = d3.scaleLinear()
      .domain([0, maxVal * 1.15])
      .nice()
      .range([height - margin.bottom, margin.top]);

    // Grid lines
    svg.append('g')
      .attr('class', 'grid')
      .attr('transform', `translate(${margin.left},0)`)
      .call(
        d3.axisLeft(y)
          .ticks(4)
          .tickSize(-(width - margin.left - margin.right))
          .tickFormat('')
      )
      .call(g => g.select('.domain').remove())
      .call(g => g.selectAll('.tick line').attr('stroke', '#f1f5f9'));

    // X Axis
    svg.append('g')
      .attr('transform', `translate(0,${height - margin.bottom})`)
      .call(
        d3.axisBottom(x).tickFormat(d => {
          if (!d) return '';
          const parts = d.split('-');
          if (parts.length === 3) {
            return `${parts[2]}/${parts[1]}`;
          }
          return d;
        })
      )
      .call(g => g.select('.domain').attr('stroke', '#cbd5e1'))
      .call(g => g.selectAll('.tick text')
        .attr('font-size', '10px')
        .attr('fill', '#64748b')
        .attr('font-weight', '500')
      );

    // Y Axis
    svg.append('g')
      .attr('transform', `translate(${margin.left},0)`)
      .call(
        d3.axisLeft(y)
          .ticks(4)
          .tickFormat(d => {
            if (d >= 1000) return `${(d / 1000).toFixed(0)}k`;
            return d;
          })
      )
      .call(g => g.select('.domain').remove())
      .call(g => g.selectAll('.tick text')
        .attr('font-size', '10px')
        .attr('fill', '#94a3b8')
      );

    const barWidth = x.bandwidth();

    // Group for each date
    const dateGroups = svg.selectAll('.date-group')
      .data(data)
      .enter()
      .append('g')
      .attr('class', 'date-group cursor-pointer')
      .attr('transform', d => `translate(${x(d.date)},0)`)
      .on('mouseenter', (event, d) => {
        const [mx, my] = d3.pointer(event, svgRef.current);
        setActiveTooltip({
          x: mx,
          y: my,
          data: d
        });
      })
      .on('mouseleave', () => {
        setActiveTooltip(null);
      });

    // Income bars (Emerald Green)
    dateGroups.filter(d => (d.income || 0) > 0)
      .append('rect')
      .attr('x', d => (d.expense || 0) > 0 ? 0 : 0)
      .attr('y', d => y(d.income))
      .attr('width', d => (d.expense || 0) > 0 ? (barWidth / 2 - 1) : barWidth)
      .attr('height', d => Math.max(0, height - margin.bottom - y(d.income)))
      .attr('fill', '#10B981')
      .attr('rx', 3);

    // Expense bars (Purple/Indigo)
    dateGroups.filter(d => (d.expense || 0) > 0)
      .append('rect')
      .attr('x', d => (d.income || 0) > 0 ? (barWidth / 2 + 1) : 0)
      .attr('y', d => y(d.expense))
      .attr('width', d => (d.income || 0) > 0 ? (barWidth / 2 - 1) : barWidth)
      .attr('height', d => Math.max(0, height - margin.bottom - y(d.expense)))
      .attr('fill', '#4F46E5')
      .attr('rx', 3);

  }, [data]);

  return (
    <div className="relative w-full h-full">
      <svg ref={svgRef} className="w-full h-full overflow-visible" />
      {activeTooltip && (
        <div
          className="absolute z-20 pointer-events-none bg-slate-900 text-white p-2.5 rounded-xl shadow-lg text-xs -translate-x-1/2 -translate-y-full mb-2 whitespace-nowrap border border-slate-700"
          style={{
            left: `${activeTooltip.x}px`,
            top: `${activeTooltip.y - 10}px`
          }}
        >
          <div className="font-bold text-slate-100 flex items-center gap-1.5">
            <Calendar className="w-3 h-3 text-indigo-400" />
            {formatDisplayDate(activeTooltip.data.date)}
          </div>
          {activeTooltip.data.income > 0 && (
            <div className="text-emerald-400 font-extrabold text-xs mt-1">
              Income: {formatCurrency(activeTooltip.data.income)}
            </div>
          )}
          <div className="text-indigo-300 font-extrabold text-xs mt-0.5">
            Spending: {formatCurrency(activeTooltip.data.expense)}
          </div>
          <div className="text-slate-400 text-[10px] mt-0.5">
            {activeTooltip.data.count} {activeTooltip.data.count === 1 ? 'entry' : 'entries'}
          </div>
        </div>
      )}
    </div>
  );
}

// -------------------------------------------------------------
// Device Split Subcomponent
// -------------------------------------------------------------
function DeviceSplitCard({ transactions, formatCurrency }) {
  const { deviceList, total } = useMemo(() => {
    const map = {};
    let sum = 0;
    transactions.forEach(t => {
      const dev = t.deviceName && t.deviceName !== '—' ? t.deviceName : 'Unknown';
      if (!map[dev]) {
        map[dev] = { name: dev, amount: 0, count: 0 };
      }
      map[dev].amount += t.amount;
      map[dev].count += 1;
      sum += t.amount;
    });
    const list = Object.values(map).sort((a, b) => b.amount - a.amount);
    return { deviceList: list, total: sum };
  }, [transactions]);

  const colors = ['#4F46E5', '#059669', '#D97706', '#DB2777', '#7C3AED', '#0891B2', '#64748B'];

  if (deviceList.length === 0) {
    return (
      <div className="bg-slate-50 border border-slate-200/70 p-4 rounded-xl text-xs text-center" style={{ color: '#657487' }}>
        No device data recorded
      </div>
    );
  }

  return (
    <div className="bg-slate-50 border border-slate-200/70 p-4 rounded-xl space-y-3">
      <div className="space-y-2 max-h-36 overflow-y-auto pr-1">
        {deviceList.map((dev, idx) => {
          const pct = total > 0 ? ((dev.amount / total) * 100).toFixed(1) : 0;
          const color = colors[idx % colors.length];
          return (
            <div key={dev.name} className="space-y-1">
              <div className="flex items-center justify-between text-xs">
                <div className="flex items-center gap-1.5 min-w-0 pr-2">
                  <Smartphone className="w-3.5 h-3.5 shrink-0" style={{ color }} />
                  <span className="font-semibold text-slate-800 truncate">{dev.name}</span>
                </div>
                <div className="shrink-0 text-right">
                  <span className="font-bold text-slate-900">{formatCurrency(dev.amount)}</span>
                  <span className="text-[11px] text-slate-500 ml-1.5">({pct}%)</span>
                </div>
              </div>
              <div className="h-1.5 w-full bg-slate-200 rounded-full overflow-hidden">
                <div
                  className="h-full rounded-full transition-all duration-500"
                  style={{ width: `${Math.max(0, Math.min(100, pct))}%`, backgroundColor: color }}
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// -------------------------------------------------------------
// Add / Edit Transaction Form Modal
// -------------------------------------------------------------
function ExpenseModal({ title, initialData, categories, paymentModes, onSave, onClose, error }) {
  const [formData, setFormData] = useState({
    date: toIsoDate(initialData.date, initialData.month),
    month: initialData.month || 'September',
    time: initialData.time || '12:00:00',
    category: initialData.category || categories[0] || 'Groceries',
    amount: initialData.amount !== undefined ? initialData.amount : '',
    paymentMode: initialData.paymentMode || paymentModes[0] || 'UPI',
    remarks: initialData.remarks || '',
    deviceName: initialData.deviceName || 'iPhone',
    spendingType: initialData.spendingType || ''
  });

  const categoryPresets = [
    'Groceries',
    'Entertainment',
    'Food & Dining',
    'Transportation',
    'Shopping',
    'Recharge & Subscriptions',
    'Health & Medical',
    'Rent & Bills',
    'Education',
    'Gifts',
    'Travel',
    'Other'
  ];

  const paymentPresets = [
    'UPI',
    'Credit Card',
    'Debit Card',
    'Bank Transfer',
    'Cash'
  ];

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.amount || isNaN(formData.amount) || parseFloat(formData.amount) <= 0) {
      return;
    }
    onSave(formData);
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-slate-200 animate-in fade-in zoom-in duration-150 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between pb-4 border-b border-slate-100 mb-5">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <Sparkles className="w-4 h-4" />
            </div>
            <h3 className="text-base font-bold text-slate-900">{title}</h3>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
          {/* Amount Field (Primary) */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Amount (₹) <span className="text-red-500">*</span>
            </label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm font-bold text-slate-400">₹</span>
              <input
                type="number"
                step="any"
                required
                placeholder="0.00"
                value={formData.amount}
                onChange={(e) => setFormData({ ...formData, amount: e.target.value })}
                className="w-full pl-8 pr-4 py-2.5 text-base font-bold text-slate-900 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:bg-white"
                autoFocus
              />
            </div>
          </div>

          {/* Category Selector */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Category <span className="text-red-500">*</span>
            </label>
            <select
              value={formData.category}
              onChange={(e) => setFormData({ ...formData, category: e.target.value })}
              className="w-full px-3 py-2 text-xs font-medium text-slate-800 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:bg-white"
            >
              {categoryPresets.map(cat => (
                <option key={cat} value={cat}>{cat}</option>
              ))}
            </select>
          </div>

          {/* Spending Type (Priority) Selector */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Priority / Spending Type
            </label>
            <select
              value={formData.spendingType}
              onChange={(e) => setFormData({ ...formData, spendingType: e.target.value })}
              className="w-full px-3 py-2 text-xs font-medium text-slate-800 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:bg-white"
            >
              <option value="">Unspecified</option>
              <option value="NEED">NEED (Essential)</option>
              <option value="WANT">WANT (Discretionary)</option>
            </select>
          </div>

          {/* Date, Month and Time Grid */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="block text-xs font-semibold text-slate-700">
                  Date (DD/MM/YYYY) <span className="text-red-500">*</span>
                </label>
                <span className="text-[10px] font-bold text-indigo-600 bg-indigo-50 px-1.5 py-0.5 rounded">
                  Format: {formatDisplayDate(formData.date)}
                </span>
              </div>
              <input
                type="date"
                required
                value={formData.date}
                onChange={(e) => {
                  const newDate = e.target.value;
                  let newMonth = formData.month;
                  if (newDate) {
                    const parts = newDate.split('-');
                    if (parts.length >= 2) {
                      const mNum = parseInt(parts[1], 10);
                      const monthNames = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
                      if (mNum >= 1 && mNum <= 12) newMonth = monthNames[mNum - 1];
                    }
                  }
                  setFormData({ ...formData, date: newDate, month: newMonth });
                }}
                className="w-full px-3 py-2 text-xs text-slate-800 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:bg-white"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Time
              </label>
              <input
                type="text"
                placeholder="HH:MM:SS"
                value={formData.time}
                onChange={(e) => setFormData({ ...formData, time: e.target.value })}
                className="w-full px-3 py-2 text-xs text-slate-800 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:bg-white"
              />
            </div>
          </div>

          {/* Payment Mode and Device Grid */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Payment Mode
              </label>
              <select
                value={formData.paymentMode}
                onChange={(e) => setFormData({ ...formData, paymentMode: e.target.value })}
                className="w-full px-3 py-2 text-xs font-medium text-slate-800 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:bg-white"
              >
                {paymentPresets.map(pm => (
                  <option key={pm} value={pm}>{pm}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Device Logged
              </label>
              <input
                type="text"
                list="device-options"
                placeholder="e.g. iPhone, Android, Vinkush"
                value={formData.deviceName}
                onChange={(e) => setFormData({ ...formData, deviceName: e.target.value })}
                className="w-full px-3 py-2 text-xs font-medium text-slate-800 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:bg-white"
              />
              <datalist id="device-options">
                <option value="iPhone" />
                <option value="Android" />
              </datalist>
            </div>
          </div>

          {/* Remarks */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Remarks / Purpose
            </label>
            <input
              type="text"
              placeholder="e.g. Monthly groceries, OTT subscription, Movie"
              value={formData.remarks}
              onChange={(e) => setFormData({ ...formData, remarks: e.target.value })}
              className="w-full px-3 py-2 text-xs text-slate-800 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:bg-white"
            />
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-800 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="px-5 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 rounded-xl transition-all shadow-sm shadow-indigo-200"
            >
              Save Record
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}