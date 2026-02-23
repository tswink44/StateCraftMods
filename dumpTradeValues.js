// priority: -100
// Place in: kubejs/startup_scripts/dumpTradeValues.js
// Dumps global.trades to logs/latest.log
// Search for [DumpTrades] in the log file after loading
// Delete this file after you've gotten your dump!

if (global.trades && global.trades.size > 0) {
    let entries = [];
    global.trades.forEach((data, itemId) => {
        let val = data.value || 0;
        entries.push(itemId + '=' + val);
    });
    entries.sort();
    console.info('[DumpTrades] START DUMP (' + entries.length + ' items)');
    for (let i = 0; i < entries.length; i++) {
        console.info('[DumpTrades] ' + entries[i]);
    }
    console.info('[DumpTrades] END DUMP');
} else {
    console.info('[DumpTrades] global.trades was empty or undefined');
}
