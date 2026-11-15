"""Split animal VectorDrawables into ordered, articulated layers.

Only existing paths are moved. Preserve their original ancestor groups, clips,
gradients, strokes and paint order. Private art stays under res-private.
Run again after importing shop artwork. --check verifies recipes and rest pose.
"""
from pathlib import Path
import copy, json, sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', A[1:-1])
ET.register_namespace('aapt', 'http://schemas.android.com/aapt')

def build(check=False):
    catalog = json.loads((ROOT/'app/src/main/assets/shop.json').read_text(encoding='utf-8'))
    recipes = json.loads((ROOT/'tools/animal_motion_parts.json').read_text(encoding='utf-8'))
    result = {}
    for animal in catalog['animals']:
        aid=animal['id']
        if aid not in recipes: continue
        directory=ROOT/'app/src/main'/('res-private' if animal['private'] else 'res')/'drawable'
        source=directory/(animal['res']+'.xml')
        if not source.exists():
            assert animal['private'],source
            continue
        tree=ET.parse(source).getroot()
        paths=list(tree.iter('path'))
        numbers={id(p):i for i,p in enumerate(paths)}
        shadow_source=directory/(animal['res']+'_shadow.xml')
        shadows=set()
        if shadow_source.exists():
            shadow_paths={p.get(A+'pathData') for p in ET.parse(shadow_source).getroot().iter('path')}
            shadows={i for i,p in enumerate(paths) if p.get(A+'pathData') in shadow_paths}
        assigned={}
        for motion in recipes[aid]:
            assert motion['kind'] in ('sway','twitch','flutter','flap','blink')
            assert motion['period']>0
            for i in motion['paths']:
                assert 0<=i<len(paths),(aid,motion['name'],i)
                assert i not in assigned and i not in shadows,(aid,i)
                assigned[i]=motion['name']
        runs=[]
        for i in range(len(paths)):
            if i in shadows: continue
            group=assigned.get(i,'')
            if runs and runs[-1][0]==group: runs[-1][1].append(i)
            else:runs.append((group,[i]))
        assert [i for _,ids in runs for i in ids]==[i for i in range(len(paths)) if i not in shadows]
        layers=[]
        for index,(group,ids) in enumerate(runs):
            keep=set(ids)
            def prune(node):
                if node.tag=='path':return copy.deepcopy(node) if numbers[id(node)] in keep else None
                if node.tag not in ('vector','group'):return copy.deepcopy(node)
                out=ET.Element(node.tag,node.attrib)
                for child in node:
                    kept=prune(child)
                    if kept is not None:out.append(kept)
                if node.tag=='group' and not list(out.iter('path')):return None
                return out
            layer=prune(tree)
            assert [ET.tostring(p) for p in layer.iter('path')]==[ET.tostring(paths[i]) for i in ids]
            res=f'shop_motion_{aid}_{index}'
            ET.indent(layer)
            content=ET.tostring(layer,encoding='unicode')+'\n'
            dest=directory/(res+'.xml')
            if check:assert dest.read_text(encoding='utf-8')==content,dest
            else:dest.write_text(content,encoding='utf-8')
            layers.append(dict(res=res,motion=group))
        motions=[{k:v for k,v in m.items() if k!='paths'} for m in recipes[aid]]
        result[aid]=dict(layers=layers,motions=motions)
    assert set(result)==set(recipes) or all(a['private'] for a in catalog['animals'] if a['id'] in set(recipes)-set(result))
    path=ROOT/'app/src/main/assets/animal_motion.json'
    content=json.dumps(result,ensure_ascii=False,indent=1)+'\n'
    if check:assert path.read_text(encoding='utf-8')==content
    else:path.write_text(content,encoding='utf-8')
    print(f'{len(result)} animals, {sum(len(v["motions"]) for v in result.values())} motions, {sum(len(v["layers"]) for v in result.values())} ordered layers verified')

if __name__=='__main__':build('--check' in sys.argv)
