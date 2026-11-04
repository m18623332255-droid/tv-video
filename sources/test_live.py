import re,glob,subprocess,concurrent.futures as cf,json
ch={}
for f in glob.glob('raw/*.m3u'):
    name=None;attrs=''
    for l in open(f,errors='ignore'):
        l=l.strip()
        if l.startswith('#EXTINF'):
            attrs=l;name=l.rsplit(',',1)[-1].strip()
        elif l.startswith('http') and name:
            ch.setdefault(l,(name,attrs,f));name=None
def t(u):
    try:
        r=subprocess.run(['curl','-sL','-m','8','-r','0-2048','-o','/dev/null','-w','%{http_code}',u],capture_output=True,text=True,timeout=10)
        return r.stdout.strip() in('200','206')
    except: return False
ok={}
with cf.ThreadPoolExecutor(64) as ex:
    for u,r in zip(ch,ex.map(t,ch)):
        if r: ok[u]=ch[u]
def grp(n):
    if re.search('CCTV|CGTN|央视',n,re.I):return '央视'
    if '卫视' in n:return '卫视'
    if re.search('[省市县]|广东|北京|上海|新闻|都市|公共|生活|影视|经济|综合',n):return '地方'
    return '其他'
seen=set();out=['#EXTM3U'];st={}
for u,(n,a,f) in sorted(ok.items(),key=lambda x:(grp(x[1][0]),x[1][0])):
    g=grp(n);st[g]=st.get(g,0)+1
    out+= [f'#EXTINF:-1 group-title="{g}",{n}',u]
open('live.m3u','w').write('\n'.join(out)+'\n')
print(len(ch),len(ok),st,len({v[0] for v in ok.values()}))
for f in glob.glob('raw/*.m3u'):print(f,sum(1 for v in ok.values() if v[2]==f))
