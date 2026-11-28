#!/usr/bin/env python3
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
from Xlib import X,display
import hashlib,json,os,shutil,subprocess,time,urllib.request
from lor_vanilla_client import prepare,launch,GAME
R=Path(__file__).resolve().parents[1];W=R/'z_mems/target/visual-server';Q=R/'z_mems/visual-qa';W.mkdir(parents=True,exist_ok=True);Q.mkdir(parents=True,exist_ok=True)
try:
 prepare();resource=GAME/'resourcepacks';resource.mkdir(exist_ok=True);shutil.copyfile(R/'dist/Z_Server_Pack_1.18.zip',resource/'zserver.zip')
 with (GAME/'options.txt').open('a') as f:f.write('resourcePacks:["file/zserver.zip"]\n')
 req=urllib.request.Request('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest',headers={'User-Agent':'Z_Mems visual QA'});meta=json.load(urllib.request.urlopen(req,timeout=45));record=meta['downloads']['server:default'];req=urllib.request.Request(record['url'],headers={'User-Agent':'Z_Mems visual QA'});(W/'paper.jar').write_bytes(urllib.request.urlopen(req,timeout=120).read());assert hashlib.sha256((W/'paper.jar').read_bytes()).hexdigest()==record['checksums']['sha256']
 jar=R/'z_mems/target/Z_Mems-1.0.0.jar';deps=[jar]+list((Path.home()/'.m2/repository').rglob('*.jar'));classes=W/'classes';classes.mkdir(exist_ok=True)
 with (Q/'compile.log').open('w') as log:proc=subprocess.run(['javac','-proc:none','--release','21','-cp',os.pathsep.join(map(str,deps)),'-d',str(classes),str(R/'z_mems/src/visual/java/dev/zmems/visual/VisualProbe.java')],stdout=log,stderr=subprocess.STDOUT)
 assert proc.returncode==0,'visual QA did not compile'
 plugins=W/'plugins';plugins.mkdir(exist_ok=True);shutil.copyfile(jar,plugins/'Z_Mems.jar')
 with ZipFile(plugins/'Z_Mems_Visual.jar','w') as z:
  for c in classes.rglob('*.class'):z.write(c,str(c.relative_to(classes)))
  z.writestr('plugin.yml',"name: Z_Mems_Visual\nversion: 1\nmain: dev.zmems.visual.VisualProbe\napi-version: '1.21.11'\ndepend: [Z_Mems]\n")
 (plugins/'Z_Mems').mkdir(exist_ok=True);(plugins/'Z_Mems/config.yml').write_text('scenes:\n  max-active: 3\n  cooldown-seconds: 0\n  duration-seconds: 30\n  sound-volume: 0.8\n  orbital-damage: 0.0\norbs:\n  max-loaded-per-world: 256\n')
 (W/'eula.txt').write_text('eula=true\n');(W/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25585\nonline-mode=false\nview-distance=6\nsimulation-distance=4\nspawn-protection=0\nallow-flight=true\nmax-tick-time=-1\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\n')
 with (Q/'server.log').open('w') as log:
  server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=W,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT);client=None;captured=[]
  try:
   deadline=time.monotonic()+360
   while time.monotonic()<deadline and server.poll() is None:
    if client is None and 'Done (' in (Q/'server.log').read_text(errors='replace'):client=launch()
    if (W/'visual-error.txt').exists():raise RuntimeError((W/'visual-error.txt').read_text())
    for name in ['smile','pig','cow']:
     if name not in captured and (W/f'capture-{name}.txt').exists():
      conn=display.Display(os.environ['DISPLAY']);screen=conn.screen();raw=screen.root.get_image(0,0,screen.width_in_pixels,screen.height_in_pixels,X.ZPixmap,0xffffffff)
      Image.frombytes('RGB',(screen.width_in_pixels,screen.height_in_pixels),raw.data,'raw','BGRX').save(Q/(name+'.png'));captured.append(name);conn.close()
    if len(captured)==3:break
    time.sleep(.3)
   assert len(captured)==3,'missing screenshots: '+str(captured)
  finally:
   if client is not None:client.terminate();client.wait(timeout=20)
   if server.poll() is None:
    try:server.communicate(b'stop\n',timeout=40)
    except subprocess.TimeoutExpired:server.kill();server.wait()
 if (GAME/'logs/latest.log').exists():shutil.copyfile(GAME/'logs/latest.log',Q/'client.log')
 (Q/'result.json').write_text(json.dumps({'status':'CAPTURED','source_commit':os.environ.get('GITHUB_SHA'),'client':'Minecraft Java 1.21.11','pack':'Z_Server_Pack_1.18.zip','screenshots':captured},indent=2))
except Exception as e:
 (Q/'result.json').write_text(json.dumps({'status':'FAIL','source_commit':os.environ.get('GITHUB_SHA'),'error':str(e)},indent=2));raise
