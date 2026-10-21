package com.pocketledger.v2;

public final class ReceiptParserTest {
    private static void equal(long actual,long expected){if(actual!=expected)throw new AssertionError(actual+" != "+expected);}
    private static void text(String actual,String expected){if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args) throws Exception {
        ReceiptParser.Draft d=ReceiptParser.parse("Cafe\nInvoice No 12345\nItem Qty Rate Amount\nPizza 2 300.00 600.00\nPasta 1 400.00 400.00\nSubtotal 1,000.00\nDiscount 10% 100.00\nCGST 2.5% 25.00\nSGST 2.5% 25.00\nTotal Tax 50.00\nGrand Total Rs. 950.00\nUPI Paid 950.00\nThank you\n");
        equal(d.items.size(),2);text(d.items.get(0).name,"Pizza");equal(d.items.get(0).amount,60000);equal(d.tax,5000);equal(d.discount,10000);equal(d.total,95000);
        ReceiptParser.Draft copy=ReceiptParser.Draft.read(d.json());equal(copy.items.size(),2);text(copy.raw,d.raw);
        d.tax=999999;d.taxRate="5";copy=ReceiptParser.Draft.read(d.json());text(copy.taxRate,"5");equal(BillSplit.taxAtRate(copy.taxRate,100000),5000);
        d=ReceiptParser.parse("Burger ₹120.50\nCGST 2.5% 3.01\nSGST 2.5% 3.01\nTotal 126.52");equal(d.tax,602);equal(d.items.get(0).amount,12050);
        d=ReceiptParser.parse("Pizza 100\nGST 5%\nTotal 105");equal(d.tax,0);equal(d.items.size(),1);
        d=ReceiptParser.parse("Invoice No 123\nPhone 9876543210\nDate 17/10/2026\nGrand Total 950\n");equal(d.items.size(),0);equal(d.total,95000);
        d=ReceiptParser.parse("Paneer curry\n2 150.00 300.00\nService charge 30.00\nDiscount -20.00\nAmount payable 310.00");equal(d.items.size(),1);text(d.items.get(0).name,"Paneer curry");equal(d.extra,3000);equal(d.discount,2000);
        d=ReceiptParser.parse("Dish 1,00,000.00\nTotal 1,00,000.00");equal(d.items.get(0).amount,10000000);
        d=ReceiptParser.parse("Tea 19.99\nRound off -0.49\nGrand Total 19.50");equal(d.roundOff,-49);equal(d.total,1950);equal(d.items.size(),1);
        System.out.println("ReceiptParserTest PASS: quantities, tax components, duplicate tax summary, metadata, Indian amounts, wrapped item names and review round-trip");
    }
}
