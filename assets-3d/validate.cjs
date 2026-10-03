const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const validator = require('gltf-validator');

async function run() {
  const root = path.resolve(__dirname, '..');
  const assetPath = 'models/blackbird-v1.glb';
  const bytes = fs.readFileSync(path.join(root, 'app/src/main/assets', assetPath));
  const report = await validator.validateBytes(new Uint8Array(bytes), { uri: assetPath, maxIssues: 100, writeTimestamp: false });
  fs.writeFileSync(path.join(root, 'artifacts/f14-gltf-validation.json'), JSON.stringify(report, null, 2) + '\n');
  if (report.issues.numErrors || report.issues.numWarnings) throw new Error('Khronos validation did not pass cleanly');
  const manifestPath = path.join(root, 'app/src/main/assets/asset-manifest.json');
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  const sha256 = crypto.createHash('sha256').update(bytes).digest('hex');
  const entry = {
    path: assetPath, scientificName: 'Turdus merula', version: 'blackbird-v1',
    author: 'Faunavia procedural asset generator', source: 'Project source: assets-3d/blackbird/create.py',
    license: 'Project-original geometry and materials; repository terms apply; no third-party asset',
    modifications: 'Original procedural low-poly study, two illustrative animation clips', attribution: 'Faunavia',
    review: 'Pending human anatomy, visual and distribution-terms review; pending physical-device performance',
    sha256, bytes: bytes.length, units: 'metres', upAxis: 'Y', pivot: 'ground',
    validator: validator.version(), validationErrors: report.issues.numErrors,
    validationWarnings: report.issues.numWarnings, contentDate: '2026-10-03'
  };
  if (process.argv.includes('--check')) {
    const recorded = manifest.assets.find(a => a.path === assetPath);
    if (!recorded || recorded.sha256 !== sha256 || recorded.bytes !== bytes.length || !recorded.license || !recorded.author) {
      throw new Error('Asset manifest does not match the validated GLB');
    }
  } else {
    manifest.assets = [...manifest.assets.filter(a => a.path !== assetPath), entry];
    fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
  }
  if (bytes.length > 20 * 1024 * 1024) throw new Error('GLB exceeds mobile budget');
  console.log(JSON.stringify({ sha256, bytes: bytes.length, errors: report.issues.numErrors, warnings: report.issues.numWarnings }));
}
run().catch(error => { console.error(error); process.exitCode = 1; });
