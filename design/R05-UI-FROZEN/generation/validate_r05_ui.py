#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import argparse
import csv
import hashlib
import json
import sys
from PIL import Image

SCRIPT=Path(__file__).resolve()
DEFAULT_PACKAGE=SCRIPT.parents[3]
TARGET={'SCR-ID-001','SCR-ID-002','SCR-ID-003','SCR-ID-004','H5-012','ADM-ID-001','ADM-ID-002'}

def sha256(path: Path) -> str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''):
            h.update(block)
    return h.hexdigest()

def read_csv(path: Path):
    with path.open(encoding='utf-8-sig',newline='') as f:
        return list(csv.DictReader(f))

def main() -> int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--package',type=Path,default=DEFAULT_PACKAGE)
    ap.add_argument('--json-out',type=Path)
    args=ap.parse_args()
    package=args.package.expanduser().resolve()
    frozen=package/'EDITABLE_OVERLAY/R05-UI-FROZEN'
    errors=[]
    metrics={}
    def require(cond,code,message,details=None):
        if not cond:
            errors.append({'code':code,'message':message,'details':details})

    manifest_path=frozen/'VISUAL_MANIFEST.json'
    require(manifest_path.is_file(),'MANIFEST_MISSING',str(manifest_path))
    if not manifest_path.is_file():
        report={'status':'FAIL','errors':errors,'metrics':metrics}
    else:
        manifest=json.loads(manifest_path.read_text('utf-8'))
        pages=manifest.get('pages',[])
        require(manifest.get('status')=='FROZEN_VISUAL_SPEC','STATUS','visual status')
        require({p.get('page_id') for p in pages}==TARGET,'PAGE_IDS','target page IDs')
        image_count=0
        for page in pages:
            spec=frozen/page['spec']
            require(spec.is_file() and sha256(spec)==page['spec_sha256'],'SPEC_HASH',page['spec'])
            for item in page.get('images',[]):
                image_count+=1
                p=frozen/item['file']
                require(p.is_file(),'IMAGE_MISSING',item['file'])
                if p.is_file():
                    require(sha256(p)==item['sha256'],'IMAGE_HASH',item['file'])
                    require(list(Image.open(p).size)==item['size'],'IMAGE_SIZE',item['file'])
            ov=page['overview']; p=frozen/ov['file']
            require(p.is_file() and sha256(p)==ov['sha256'],'OVERVIEW_HASH',ov['file'])
            if p.is_file(): require(list(Image.open(p).size)==ov['size'],'OVERVIEW_SIZE',ov['file'])
        require(image_count==39,'IMAGE_COUNT','37 business states + 2 responsive states',image_count)
        require((frozen/'images/HHY_R05_IDENTITY_VISUAL_MASTER_OVERVIEW.png').is_file(),'MASTER_OVERVIEW','master overview')
        acceptance=read_csv(package/'EDITABLE_OVERLAY/catalogs/ui_visual_acceptance.csv')
        require({r['页面ID'] for r in acceptance}==TARGET,'ACCEPTANCE_IDS','visual acceptance page IDs')
        require(all(r['验收状态']=='VISUAL_SPEC_FROZEN' for r in acceptance),'ACCEPTANCE_STATUS','visual acceptance status')
        page_catalog=read_csv(package/'EDITABLE_OVERLAY/catalogs/ui_page_specifications.csv')
        target_rows=[r for r in page_catalog if r['页面ID'] in TARGET]
        require(len(target_rows)==7,'PAGE_CATALOG_COUNT','page catalog target rows')
        require(all('R05-UI-FROZEN/specs/' in r['UI参考'] and 'OVERVIEW.png' in r['UI参考'] for r in target_rows),'PAGE_REFS','exact page visual references')
        metrics={'pages':len(pages),'state_and_responsive_images':image_count,'page_overviews':len(pages),'master_overviews':1}
        report={'status':'PASS' if not errors else 'FAIL','metrics':metrics,'errors':errors}
    out=args.json_out or package/'EDITABLE_OVERLAY/R05-UI-FROZEN/generation/validation-latest.json'
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))
    return 0 if report['status']=='PASS' else 1

if __name__=='__main__':
    raise SystemExit(main())
