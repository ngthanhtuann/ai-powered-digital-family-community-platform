// Sinh kiểu dữ liệu từ OpenAPI (docs/04-api/openapi/) vào services/. Không viết tay.
import { readdirSync, mkdirSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';

const specDir = path.resolve('..', 'docs', '04-api', 'openapi');
const outDir = path.resolve('services', 'generated');
mkdirSync(outDir, { recursive: true });

const specs = readdirSync(specDir).filter((f) => /\.(ya?ml|json)$/.test(f));
if (specs.length === 0) {
  console.error(`Không tìm thấy file OpenAPI trong ${specDir}`);
  process.exit(1);
}
for (const spec of specs) {
  const out = path.join(outDir, spec.replace(/\.(ya?ml|json)$/, '.ts'));
  console.log(`openapi-typescript ${spec} -> ${path.relative('.', out)}`);
  execFileSync('npx', ['openapi-typescript', path.join(specDir, spec), '-o', out], {
    stdio: 'inherit',
    shell: process.platform === 'win32',
  });
}
