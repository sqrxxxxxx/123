const mineflayer=require('mineflayer');
const bot=mineflayer.createBot({host:'127.0.0.1',port:25592,username:'MemsTester',version:'1.21.11',auth:'offline'});
bot.on('spawn',()=>console.log('MemsTester joined'));
bot.on('error',e=>console.error('BOT ERROR',e));bot.on('kicked',e=>console.error('BOT KICKED',JSON.stringify(e)));
