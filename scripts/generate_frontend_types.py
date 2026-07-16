from pathlib import Path
import json
import yaml

root = Path(__file__).resolve().parents[1]
data = yaml.safe_load((root / 'database/enum_registry.yaml').read_text(encoding='utf-8'))
lines = ['// Generated from database/enum_registry.yaml. Do not edit.', '']
for item in data['enums']:
    name = ''.join(part.title() for part in item['code'].lower().split('_'))
    values = ' | '.join(json.dumps(value, ensure_ascii=False) for value in item['values'])
    array = json.dumps(item['values'], ensure_ascii=False)
    lines.append(f'export type {name} = {values};')
    lines.append(f'export const {name}Values = {array} as const;')
    lines.append('')
out = root / 'packages/domain-types/src/index.ts'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text('\n'.join(lines) + '\n', encoding='utf-8')
