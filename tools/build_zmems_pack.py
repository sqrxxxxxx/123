#!/usr/bin/env python3
"""Build Z_Mems assets into the existing 1.17 server pack without replacing prior assets."""
from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
from PIL import Image,ImageDraw,ImageFont
from merge_smplore_pack import read_pack,check_references
import colorsys,math,io,json,hashlib,subprocess,wave,struct,random,tempfile
import verity_model as sphere
R=Path(__file__).resolve().parents[1];base=read_pack(R/'dist/Z_Server_Pack_1.17.zip');data=dict(base)
def enc(v):return json.dumps(v,ensure_ascii=False,separators=(',',':')).encode()
def add(path,content):
 assert path not in data,path;data[path]=enc(content) if isinstance(content,(dict,list)) else content
def png(im):
 b=io.BytesIO();im.save(b,format='PNG');return b.getvalue()
def item(id,model):add('assets/z_mems/items/'+id+'.json',{'model':{'type':'minecraft:model','model':'z_mems:item/'+model}})
# 600 curved facets, one shared geometry and independent generated skins.
sphere.SUBDIVISIONS=10;sphere.ATLAS_SIZE=512;sphere.TILE=16
styles=['yellow','blue','red','pink','rainbow','black','white','sad','cute']
def makeskin(kind):
 def skin(p):
  x,y,z=(sphere.RADIUS*v for v in sphere.unit(p)); n=sphere.unit(p)
  base={'yellow':(255,226,0),'blue':(18,75,245),'red':(246,35,22),'pink':(238,28,190),'black':(25,21,31),'white':(246,245,250),'sad':(255,217,22),'cute':(255,157,216)}.get(kind)
  if kind=='rainbow':base=tuple(round(q*255) for q in colorsys.hsv_to_rgb(((x/14+.5)*.78)%1,.95,1))
  face=(z < -3)
  eye=(((x-1.65)/.43)**2+((y-1.65)/.88)**2<1 or ((x+1.65)/.43)**2+((y-1.65)/.88)**2<1)
  if kind=='pink' and x>0:eye=(abs(y-1.65)<.11 and .9<x<2.35) or (((x+1.65)/.43)**2+((y-1.65)/.88)**2<1)
  if kind in ('sad','cute'):
   eye=(((x-1.65)/.52)**2+((y-1.25)/.7)**2<1 or ((x+1.65)/.52)**2+((y-1.25)/.7)**2<1)
  smile=abs(y-(-2.65+.07*x*x))<.13 and abs(x)<4.2
  if kind=='sad':smile=abs(y-(-2.15-.10*x*x))<.13 and abs(x)<2.4
  if kind=='red':
   mouth=abs(x)<4.0 and (-2.75+.10*x*x)<y<(-.95+.04*x*x)
   teeth=mouth and ((-1.55+.06*x*x)<y<(-1.17+.05*x*x) or (-2.62+.10*x*x)<y<(-2.35+.10*x*x))
   if face and teeth:return (255,255,251,255)
   if face and mouth:return (24,0,4,255)
  if face and (eye or smile):return (250,250,251,255) if kind in ('blue','black') and smile else ((255,255,255,255) if kind=='black' else (25,13,29,255))
  if face and kind=='cute' and (abs(x)>2.7 and abs(x)<4 and -1.0<y<-.25):return (236,83,155,255)
  light=.59+.41*(n[1]+1)/2;return tuple(round(c*light) for c in base)+(255,)
 return skin
for i,name in enumerate(styles):
 sphere.skin=makeskin(name);model,pixels=sphere.build_sphere()
 if i==0:
  model['textures']={'skin':'z_mems:item/orb_yellow','particle':'z_mems:item/orb_yellow'}
  add('assets/z_mems/models/item/orb_base.json',model)
 image=Image.frombytes('RGBA',(512,512),bytes(pixels));add('assets/z_mems/textures/item/orb_'+name+'.png',png(image))
 add('assets/z_mems/models/item/orb_'+name+'.json',{'parent':'z_mems:item/orb_base','textures':{'skin':'z_mems:item/orb_'+name,'particle':'z_mems:item/orb_'+name}});item('orb_'+name,'orb_'+name)
# Shared pixel palette and simple block-model parts: custom geometry never overwrites vanilla entities.
palette=['#16121a','#38222b','#f1b5c9','#d48099','#f9dce6','#ffffff','#e42d38','#141213','#f5c500','#d99c00','#26a435','#18712e','#353b47','#b9c1c9','#794029','#72d7ef','#f4ab14','#794bd7','#242bff','#c8af66']
im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
for i,c in enumerate(palette):d.rectangle(((i%16),i//16,(i%16),i//16),fill=c)
add('assets/z_mems/textures/item/palette.png',png(im))
def cube(a,b,c):
 x=c%16;y=c//16;face={'texture':'#p','uv':[x,y,x+1,y+1]};return {'from':a,'to':b,'faces':{k:face for k in ['north','south','east','west','up','down']}}
def geometry(name,boxes):
 model={'ambientocclusion':False,'textures':{'p':'z_mems:item/palette','particle':'z_mems:item/palette'},'elements':[cube(*v) for v in boxes], 'display':{'fixed':{'scale':[1,1,1]},'gui':{'rotation':[15,205,0],'scale':[.8,.8,.8]}}}
 add('assets/z_mems/models/item/'+name+'.json',model);item(name,name)
# Complete recognisable bodies rather than recolouring ordinary Minecraft mobs.
geometry('pig', [([4,1,5],[6.5,5,11],2),([9.5,1,5],[12,5,11],2),([4,5,5],[12,10,11],2),([1,6,6],[4,9,10],2),([12,6,6],[15,9,10],2),([4,10,4],[12,15,12],2),([5,11,3.5],[11,13,4.2],3),([5,13,3.3],[6,14,4.2],5),([10,13,3.3],[11,14,4.2],5),([4,15,4],[12,16,12],7),([4,15.6,4],[12,16,12],6),([4,0,5],[7,1,11],7),([9,0,5],[12,1,11],7)])
geometry('banana',[([5,2,5],[11,13,11],8),([6,13,6],[10,16,10],8),([6,0,5],[8,2,10],6),([9,0,5],[11,2,10],6),([3,7,6],[5,10,9],5),([11,7,6],[13,10,9],5),([6,10,4.7],[7,11.5,5],7),([9,10,4.7],[10,11.5,5],7),([6,8,4.7],[10,8.5,5],1)])
geometry('watermelon',[([3,4,3],[13,13,13],10),([3.1,4,5],[13,13,6],11),([3.1,4,9],[13,13,10],11),([4,1,5],[7,4,10],10),([9,1,5],[12,4,10],10),([4,0,5],[7,1,10],6),([9,0,5],[12,1,10],6),([5,10,2.7],[6,11,3],7),([10,10,2.7],[11,11,3],7),([5,7,2.7],[11,8,3],6)])
geometry('cow_boss',[([4,0,5],[7,6,11],0),([9,0,5],[12,6,11],0),([3,6,4],[13,12,12],1),([1,6,5],[3,12,11],0),([13,6,5],[15,12,11],0),([3,12,4],[13,16,12],1),([4,13,3.7],[6,14,4],6),([10,13,3.7],[12,14,4],6),([6,12,3],[10,13,4],19),([1,14,5],[3,16,7],13),([13,14,5],[15,16,7],13)])
geometry('cosmic_cat',[([4,4,5],[12,11,12],7),([4,10,4],[12,15,11],7),([4,15,5],[6,16,7],7),([10,15,5],[12,16,7],7),([5,12,3.7],[7,13.5,4],8),([9,12,3.7],[11,13.5,4],8),([12,5,9],[15,7,11],7),([4,2,6],[6,4,10],7),([10,2,6],[12,4,10],7)])
geometry('white_cat',[([5,2,5],[11,10,11],5),([4,9,4],[12,15,12],5),([4,15,5],[6,16,7],5),([10,15,5],[12,16,7],5),([5,12,3.6],[6.5,13.5,4],7),([9.5,12,3.6],[11,13.5,4],7),([7.5,10.5,3.5],[8.5,11,4],2),([4,4,5],[5,8,10],5),([11,4,5],[12,8,10],5)])
geometry('skull',[([3,6,3],[13,15,13],13),([5,3,5],[11,6,11],13),([4,10,2.7],[7,13,3],7),([9,10,2.7],[12,13,3],7),([5,5,4.7],[6,6,5],7),([8,5,4.7],[9,6,5],7),([10,5,4.7],[11,6,5],7)])
geometry('muscle',[([3,4,5],[13,9,11],8),([1,5,5],[4,10,11],8),([12,5,5],[15,10,11],8),([2,9,4],[14,11,12],9),([4,10,4],[12,15,12],8),([5,12,3.6],[6,13,4],7),([10,12,3.6],[11,13,4],7),([6,10.6,3.6],[10,11.1,4],7),([6,3,4.7],[10,4,5],9)])
geometry('orbital_device',[([2,6,5],[14,10,11],0),([1,7,6],[4,9,10],13),([10,7,4],[14,9,12],6),([5,4,6],[8,7,10],1),([7,7,4.8],[10,9,5],15)])
geometry('crane',[([6,0,6],[10,12,10],8),([4,11,6],[15,13,10],8),([12,7,7],[13,11,9],7),([11,6,6],[14,7,10],13)])
scene_ids=['cow_rift','city_5g','meow_jump','verity_sugar','orbital','bell_party','apocalypse','pig_dance','smiley_show','cosmic_cat','skull_cat','city_cranes','fruit_festival']
font=ImageFont.load_default()
for i,name in enumerate(scene_ids):
 icon=Image.new('RGBA',(32,32));d=ImageDraw.Draw(icon);color=palette[(i*3+4)%len(palette)];d.rounded_rectangle((2,2,29,29),4,fill='#242033',outline=color,width=2);d.text((6,9),str(i+1),font=font,fill='white',stroke_width=0);add('assets/z_mems/textures/item/token_'+name+'.png',png(icon));add('assets/z_mems/models/item/token_'+name+'.json',{'parent':'minecraft:item/generated','textures':{'layer0':'z_mems:item/token_'+name}});item('token_'+name,'token_'+name)
# Short original generated cues. No speech/music is extracted from the supplied clips.
sounds={}
with tempfile.TemporaryDirectory() as tmp:
 for name in ['spark','portal','warning','beam','party','meow','chime']:
  rate=22050;sec={'spark':.6,'portal':2.3,'warning':.45,'beam':2.8,'party':4,'meow':.6,'chime':.8}[name];rng=random.Random(name)
  with wave.open(tmp+'/a.wav','wb') as w:
   w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate);buf=bytearray()
   for n in range(int(rate*sec)):
    t=n/rate;env=min(1,t*15)*min(1,(sec-t)*5)
    f={'spark':(420+1400*t),'portal':(65+110*t),'warning':660,'beam':(70+45*math.sin(t*6)),'party':(260*2**([0,4,7,12][int(t*4)%4]/12)),'meow':(520+160*math.sin(11*t)),'chime':(800*2**(-t/3))}[name]
    value=.42*math.sin(2*math.pi*f*t)+(.13*rng.uniform(-1,1) if name in ('spark','beam','portal') else 0)
    buf.extend(struct.pack('<h',int(32767*env*max(-1,min(1,value)))))
   w.writeframes(buf)
  subprocess.run(['ffmpeg','-v','error','-y','-i',tmp+'/a.wav','-c:a','libvorbis','-q:a','3',tmp+'/a.ogg'],check=True)
  add('assets/z_mems/sounds/'+name+'.ogg',Path(tmp+'/a.ogg').read_bytes());sounds[name]={'sounds':[{'name':'z_mems:'+name,'stream':False}]}
add('assets/z_mems/sounds.json',sounds)
meta=json.loads(data['pack.mcmeta']);meta['pack']['description']='Z_Server_Pack 1.18 • Z_Mems';data['pack.mcmeta']=enc(meta)
for n,b in base.items():assert n=='pack.mcmeta' or data[n]==b,n
check=check_references(data)
out=R/'dist/Z_Server_Pack_1.18.zip'
with ZipFile(out,'w',ZIP_DEFLATED) as z:
 for name,content in sorted(data.items()):info=ZipInfo(name,(2026,1,1,0,0,0));info.compress_type=ZIP_DEFLATED;z.writestr(info,content)
digest=hashlib.sha1(out.read_bytes()).hexdigest();out.with_suffix('.zip.sha1').write_text(digest+'\n')
(R/'z_mems/qa/pack.json').write_text(json.dumps({'base':'Z_Server_Pack_1.17.zip','old_files_preserved':len(base)-1,'added_files':len(data)-len(base),'sha1':digest,'references':check},indent=2))
print(out.name,'sha1',digest,'base files preserved',len(base)-1,'validated',check)
