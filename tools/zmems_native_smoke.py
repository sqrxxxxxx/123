#!/usr/bin/env python3
"""Disposable Paper 1.21.11 instance. Never accesses a real server or user world."""
from pathlib import Path
from zipfile import ZipFile
import hashlib,json,os,shutil,subprocess,time,urllib.request
R=Path(__file__).resolve().parents[1];W=R/'z_mems/target/native-server';Q=R/'z_mems/qa';W.mkdir(parents=True,exist_ok=True);Q.mkdir(parents=True,exist_ok=True);results=[]
try:
 req=urllib.request.Request('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest',headers={'User-Agent':'Z_Mems acceptance QA'});meta=json.load(urllib.request.urlopen(req,timeout=45));record=meta['downloads']['server:default']
 req=urllib.request.Request(record['url'],headers={'User-Agent':'Z_Mems acceptance QA'});(W/'paper.jar').write_bytes(urllib.request.urlopen(req,timeout=120).read());sha=hashlib.sha256((W/'paper.jar').read_bytes()).hexdigest();assert sha==record['checksums']['sha256']
 jar=R/'z_mems/target/Z_Mems-1.0.0.jar';deps=[jar]+list((Path.home()/'.m2/repository').rglob('*.jar'));classes=W/'classes';classes.mkdir(exist_ok=True)
 with (Q/'native-compile.log').open('w') as log:r=subprocess.run(['javac','-proc:none','--release','21','-cp',os.pathsep.join(map(str,deps)),'-d',str(classes),str(R/'z_mems/src/smoke/java/dev/zmems/qa/NativeSmoke.java')],stdout=log,stderr=subprocess.STDOUT)
 assert r.returncode==0,'QA compilation failed'
 plugins=W/'plugins';plugins.mkdir(exist_ok=True)
 for source,name in [(jar,'Z_Mems.jar'),(R/'dist/Z_Test-1.0.3.jar','Z_Test.jar'),(R/'dist/Z_Lor-1.1.1.jar','Z_Lor.jar'),(R/'dist/Z_Verity_1.0.2.jar','Z_Verity.jar')]:shutil.copyfile(source,plugins/name)
 with ZipFile(plugins/'Z_Mems_QA.jar','w') as z:
  for f in classes.rglob('*.class'):z.write(f,str(f.relative_to(classes)))
  z.writestr('plugin.yml',"name: Z_Mems_QA\nversion: 1\nmain: dev.zmems.qa.NativeSmoke\napi-version: '1.21.11'\ndepend: [Z_Mems,Z_Test,Z_Lor,Z_Verity]\n")
 for name,text in {'Z_Mems':'scenes:\n  max-active: 3\n  cooldown-seconds: 0\n  duration-seconds: 30\n  sound-volume: 0.8\n  orbital-damage: 0.0\norbs:\n  max-loaded-per-world: 256\n  placement-cooldown-ticks: 4\n','Z_Verity':'resource-pack:\n  enabled: false\n  prompt-on-join: false\n'}.items():(plugins/name).mkdir(exist_ok=True);(plugins/name/'config.yml').write_text(text)
 (W/'eula.txt').write_text('eula=true\n');(W/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25592\nonline-mode=false\nview-distance=5\nsimulation-distance=4\nspawn-protection=0\nmax-players=3\nmax-tick-time=-1\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\n')
 bots=R/'z_mems/target/bots';bots.mkdir(exist_ok=True);shutil.copyfile(R/'tools/zmems_native_bot.js',bots/'bot.js');subprocess.run(['npm','install','--prefix',str(bots),'--no-audit','--no-fund','mineflayer@4.39.0'],check=True)
 for phase in (1,2):
  report=W/'zmems-report.json';report.unlink(missing_ok=True)
  with (Q/f'server-{phase}.log').open('w') as log,(Q/f'bot-{phase}.log').open('w') as botlog:
   server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=W,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT);bot=None;deadline=time.monotonic()+360
   try:
    while time.monotonic()<deadline and server.poll() is None:
     if bot is None and 'Done (' in (Q/f'server-{phase}.log').read_text(errors='replace'):bot=subprocess.Popen(['node','bot.js'],cwd=bots,stdout=botlog,stderr=subprocess.STDOUT)
     if report.exists():break
     time.sleep(1)
   finally:
    if server.poll() is None:
     try:server.communicate(b'stop\n',timeout=40)
     except subprocess.TimeoutExpired:server.kill();server.wait()
    if bot is not None:
     bot.terminate()
     try:bot.wait(timeout=10)
     except subprocess.TimeoutExpired:bot.kill();bot.wait()
  assert report.exists(),f'no report for phase {phase}';data=json.loads(report.read_text());results.append(data);assert data.get('status')=='PASS',f'phase {phase}: {data}'
 (Q/'latest.json').write_text(json.dumps({'status':'PASS','source_commit':os.environ.get('GITHUB_SHA'),'server_build':meta.get('id',meta.get('build')),'server_sha256':sha,'phases':results,'visual_client':False},ensure_ascii=False,indent=2)+'\n')
except Exception as e:
 (Q/'latest.json').write_text(json.dumps({'status':'FAIL','source_commit':os.environ.get('GITHUB_SHA'),'error':str(e),'completed_phases':results,'visual_client':False},ensure_ascii=False,indent=2)+'\n');raise
