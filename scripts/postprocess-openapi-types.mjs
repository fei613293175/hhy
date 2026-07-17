#!/usr/bin/env node
import { readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const targets = [
  'packages/api-client/src/client.generated.ts',
  'packages/api-client/src/admin.generated.ts',
]

const recursiveMember = `        JsonValue: components["schemas"]["JsonScalar"] | components["schemas"]["JsonValue"][] | {
            [key: string]: components["schemas"]["JsonValue"];
        };`
const replacement = '        JsonValue: HhyJsonValue;'
const typeAlias = 'export type HhyJsonValue = string | number | boolean | null | HhyJsonValue[] | { [key: string]: HhyJsonValue };\n\n'

for (const relative of targets) {
  const path = resolve(relative)
  let source = await readFile(path, 'utf8')
  const occurrences = source.split(recursiveMember).length - 1
  if (occurrences !== 1) {
    throw new Error(`${relative}: expected one recursive JsonValue member, got ${occurrences}`)
  }
  source = source.replace(recursiveMember, replacement)
  if (!source.includes(typeAlias)) {
    const marker = 'export interface paths {'
    if (!source.includes(marker)) {
      throw new Error(`${relative}: paths interface marker missing`)
    }
    source = source.replace(marker, typeAlias + marker)
  }
  await writeFile(path, source, 'utf8')
}

console.log(`postprocessed recursive JsonValue in ${targets.length} OpenAPI type files`)
