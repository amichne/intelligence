import fs from "node:fs";
import crypto from "node:crypto";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const gardenRoot = path.join(repoRoot, "garden");
const gardenManifestDir = path.join(gardenRoot, "manifests");
const gardenSchemaDir = path.join(gardenRoot, "schemas", "intelligence");
const coreSchemaDir = path.join(repoRoot, "schemas", "core");
const adapterSchemaDir = path.join(repoRoot, "schemas", "adapters");
const hookSchemaDir = path.join(repoRoot, "schemas", "hooks");
const options = parseArguments(process.argv.slice(2));
const hydratedRoot = options.hydrated;

const requireFromRepo = createRequire(import.meta.url);
const Ajv2020Module = loadDependency("ajv/dist/2020.js");
const AjvDraft7Module = loadDependency("ajv");
const addFormatsModule = loadDependency("ajv-formats");
const Ajv2020 = Ajv2020Module.default ?? Ajv2020Module;
const AjvDraft7 = AjvDraft7Module.default ?? AjvDraft7Module;
const addFormats = addFormatsModule.default ?? addFormatsModule;

const ajv = new Ajv2020({
  allErrors: true,
  strict: false,
  validateSchema: true
});
addFormats(ajv);

const draft7Ajv = new AjvDraft7({
  allErrors: true,
  strict: false,
  validateSchema: true
});
addFormats(draft7Ajv);

const adapterHookSchemas = [
  { adapter: "claude", schemaId: "claude-hooks.schema.json", validator: ajv },
  { adapter: "codex", schemaId: "https://json.schemastore.org/codex-hooks.json", validator: draft7Ajv },
  { adapter: "github", schemaId: "github-hooks.schema.json", validator: ajv }
];

const validatedJsonFiles = new Set();

for (const schemaPath of listJsonFiles(coreSchemaDir)) {
  ajv.addSchema(readJson(schemaPath));
}

for (const schemaPath of listJsonFiles(gardenSchemaDir)) {
  ajv.addSchema(readJson(schemaPath));
}

for (const schemaPath of [...listJsonFilesRecursive(adapterSchemaDir), ...listJsonFiles(hookSchemaDir)]) {
  const schema = readJson(schemaPath);
  if (schema.$schema === "http://json-schema.org/draft-07/schema#") {
    draft7Ajv.addSchema(schema);
  } else {
    ajv.addSchema(schema);
  }
}

const checks = [
  ["marketplace.schema.json", path.join(repoRoot, "marketplace.json")],
  ...listJsonFiles(path.join(repoRoot, "profiles")).map((file) => ["workflow-profile.schema.json", file]),
  ...listPluginManifests(path.join(repoRoot, "plugins")).map((file) => ["plugin.schema.json", file]),
  ...listCodexPluginManifests(path.join(repoRoot, "plugins")).map((file) => ["codex-plugin.schema.json", file]),
  ...hydratedChecks(hydratedRoot),
  ...listFiles(path.join(repoRoot, "hooks"))
    .filter((file) => file.endsWith(".hook.json"))
    .map((file) => ["hook.schema.json", file]),
  ...listFiles(path.join(repoRoot, "hooks"))
    .filter((file) => file.endsWith(".requirements.json"))
    .map((file) => ["hook-skill-requirements.schema.json", file]),
  ["intelligence-source-roots.schema.json", manifestPath("source-roots.json")],
  ["intelligence-promotions.schema.json", manifestPath("promotions.json")],
  ["intelligence-primitive-audits.schema.json", manifestPath("primitive-audits.json")],
  ["intelligence-source-review-decisions.schema.json", manifestPath("source-review-decisions.json")],
  ["intelligence-digest-review-decisions.schema.json", manifestPath("digest-review-decisions.json")],
  ["intelligence-source-root-decisions.schema.json", manifestPath("source-root-decisions.json")],
  ["intelligence-plugin-coverage.schema.json", manifestPath("plugin-coverage.json")],
  ["intelligence-primitive-decision-coverage.schema.json", manifestPath("primitive-decision-coverage.json")],
  ["intelligence-toolbox-readiness.schema.json", manifestPath("toolbox-readiness.json")],
  ["intelligence-review-completeness.schema.json", manifestPath("review-completeness.json")],
  ["intelligence-source-cleanup-gaps.schema.json", manifestPath("source-cleanup-gaps.json")],
  ["intelligence-source-root-retirement.schema.json", manifestPath("source-root-retirement.json")],
  ["intelligence-source-turnoff-readiness.schema.json", manifestPath("source-turnoff-readiness.json")],
  ["intelligence-runtime-activation-plan.schema.json", manifestPath("runtime-activation-plan.json")],
  ["intelligence-runtime-activation-preflight.schema.json", manifestPath("runtime-activation-preflight.json")],
  ["intelligence-runtime-activation-approvals.schema.json", manifestPath("runtime-activation-approvals.json")],
  ["intelligence-runtime-trim-execution.schema.json", manifestPath("runtime-trim-execution.json")],
  ["intelligence-runtime-links.schema.json", manifestPath("runtime-links.json")],
  ["intelligence-cleanup-ledger.schema.json", manifestPath("cleanup-ledger.json")],
  ["intelligence-discovered-primitives.schema.json", manifestPath("discovered-primitives.json")],
  ["intelligence-consolidation-report.schema.json", manifestPath("consolidation-report.json")]
];

const adapterHookChecks = listAdapterHookChecks();

let failures = 0;
for (const [schemaId, filePath] of checks) {
  failures += validateDataFile(ajv, schemaId, filePath);
}

for (const [validator, schemaId, filePath] of adapterHookChecks) {
  failures += validateDataFile(validator, schemaId, filePath);
}

failures += validateNodeDependencyManifests();

if (options.portable) {
  console.log("SKIP host-local manifest reference checks (--portable)");
} else {
  for (const manifest of listJsonFiles(gardenManifestDir)) {
    const data = readJson(manifest);
    failures += validateManifestReferences(manifest, data);
  }

  failures += validatePromotionAuditCoverage();
  failures += validateFirstPartyCollisionPolicy();
}

failures += validateSchemaDocuments();
failures += validateJsonCoverage();

if (failures > 0) {
  process.exitCode = 1;
}

function validateDataFile(validator, schemaId, filePath) {
  const validate = validator.getSchema(schemaId);
  if (!validate) {
    throw new Error(`Schema was not registered: ${schemaId}`);
  }

  const data = readJson(filePath);
  if (validate(data)) {
    markValidated(filePath);
    console.log(`OK ${path.relative(repoRoot, filePath)}`);
    return validateLocalReferences(filePath, data);
  }

  console.error(`FAIL ${path.relative(repoRoot, filePath)}`);
  console.error(JSON.stringify(validate.errors, null, 2));
  return 1;
}

function parseArguments(args) {
  let hydrated = null;
  let portable = false;
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--portable") {
      portable = true;
      continue;
    }
    if (arg === "--hydrated") {
      const value = args[index + 1];
      if (!value) {
        printUsageAndExit();
        process.exit(2);
      }
      hydrated = path.resolve(repoRoot, value);
      index += 1;
      continue;
    }
    console.error(`unknown argument: ${arg}`);
    printUsageAndExit();
  }
  return { hydrated, portable };
}

function printUsageAndExit() {
  console.error("usage: node scripts/validate-manifests.mjs [--portable] [--hydrated <dir>]");
  process.exit(2);
}

function hydratedChecks(directory) {
  if (!directory) {
    return [];
  }
  return [
    ["codex-marketplace.schema.json", path.join(directory, "codex", "marketplace.json")],
    ["codex-marketplace-lock.schema.json", path.join(directory, "codex", "marketplace-lock.json")],
    ["github-marketplace.schema.json", path.join(directory, "github-copilot", "marketplace.json")],
    ...listCodexPluginManifests(path.join(directory, "codex", "plugins")).map((file) => [
      "codex-plugin.schema.json",
      file
    ])
  ];
}

function listAdapterHookChecks() {
  return adapterHookSchemas.flatMap(({ adapter, schemaId, validator }) =>
    listJsonFiles(path.join(repoRoot, "hooks", adapter)).map((file) => [
      validator,
      schemaId,
      file
    ])
  );
}

function manifestPath(name) {
  return path.join(gardenManifestDir, name);
}

function listPluginManifests(directory) {
  if (!fs.existsSync(directory)) {
    return [];
  }
  return fs.readdirSync(directory, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => path.join(directory, entry.name, "plugin.json"))
    .filter((file) => fs.existsSync(file))
    .sort();
}

function listCodexPluginManifests(directory) {
  if (!fs.existsSync(directory)) {
    return [];
  }
  return fs.readdirSync(directory, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => path.join(directory, entry.name, ".codex-plugin", "plugin.json"))
    .filter((file) => fs.existsSync(file))
    .sort();
}

function listFiles(directory) {
  if (!fs.existsSync(directory)) {
    return [];
  }
  return fs.readdirSync(directory)
    .sort()
    .map((entry) => path.join(directory, entry))
    .filter((file) => fs.statSync(file).isFile());
}

function listJsonFiles(directory) {
  if (!fs.existsSync(directory)) {
    return [];
  }
  return fs.readdirSync(directory)
    .filter((entry) => entry.endsWith(".json"))
    .sort()
    .map((entry) => path.join(directory, entry));
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function validateNodeDependencyManifests() {
  const packagePath = path.join(repoRoot, "package.json");
  const lockPath = path.join(repoRoot, "package-lock.json");
  let failures = 0;

  if (!fs.existsSync(packagePath)) {
    console.error("FAIL package.json: missing Node dependency manifest");
    failures += 1;
  } else {
    const packageJson = readJson(packagePath);
    markValidated(packagePath);
    console.log("OK package.json");
    failures += requireJsonValue(packagePath, "name", packageJson.name, "intelligence");
    failures += requireJsonValue(packagePath, "private", packageJson.private, true);
    failures += requireJsonValue(
      packagePath,
      "scripts.validate:manifests",
      packageJson.scripts?.["validate:manifests"],
      "node scripts/validate-manifests.mjs"
    );
    failures += requireStringField(packagePath, "devDependencies.ajv", packageJson.devDependencies?.ajv);
    failures += requireStringField(packagePath, "devDependencies.ajv-formats", packageJson.devDependencies?.["ajv-formats"]);
  }

  if (!fs.existsSync(lockPath)) {
    console.error("FAIL package-lock.json: missing npm lockfile");
    failures += 1;
  } else {
    const lock = readJson(lockPath);
    markValidated(lockPath);
    console.log("OK package-lock.json");
    failures += requireJsonValue(lockPath, "name", lock.name, "intelligence");
    failures += requireJsonValue(lockPath, "lockfileVersion", lock.lockfileVersion, 3);
    failures += requireJsonValue(lockPath, "requires", lock.requires, true);
    failures += requireStringField(lockPath, "packages.\"\".devDependencies.ajv", lock.packages?.[""]?.devDependencies?.ajv);
    failures += requireStringField(
      lockPath,
      "packages.\"\".devDependencies.ajv-formats",
      lock.packages?.[""]?.devDependencies?.["ajv-formats"]
    );
    failures += requireStringField(lockPath, "packages.node_modules/ajv.integrity", lock.packages?.["node_modules/ajv"]?.integrity);
    failures += requireStringField(
      lockPath,
      "packages.node_modules/ajv-formats.integrity",
      lock.packages?.["node_modules/ajv-formats"]?.integrity
    );
  }

  return failures;
}

function requireJsonValue(filePath, field, actual, expected) {
  if (actual === expected) {
    return 0;
  }
  console.error(
    `FAIL ${path.relative(repoRoot, filePath)}: ${field} must be ${JSON.stringify(expected)}, found ${JSON.stringify(actual)}`
  );
  return 1;
}

function requireStringField(filePath, field, value) {
  if (typeof value === "string" && value.length > 0) {
    return 0;
  }
  console.error(`FAIL ${path.relative(repoRoot, filePath)}: ${field} must be a non-empty string`);
  return 1;
}

function loadDependency(specifier) {
  try {
    return requireFromRepo(specifier);
  } catch (error) {
    if (error?.code === "MODULE_NOT_FOUND") {
      console.error(`Missing Node validation dependency: ${specifier}. Run npm ci from the repository root.`);
      process.exit(1);
    }
    throw error;
  }
}

function validateLocalReferences(filePath, data) {
  let failures = 0;
  if (data.type === "MARKETPLACE") {
    for (const entry of data.plugins ?? []) {
      failures += requireLocalPath(filePath, entry.plugin?.source?.path, `plugin ${entry.name}`);
    }
  }
  if (data.type === "MARKETPLACE" || data.type === "PLUGIN") {
    for (const collectionName of ["skills", "agents", "instructions", "hooks"]) {
      for (const primitive of asArray(data[collectionName])) {
        failures += validatePrimitiveReference(filePath, primitive);
      }
    }
  }
  if (isPrimitiveReference(data)) {
    failures += validatePrimitiveReference(filePath, data);
  }
  if (data.type === "HOOK_SKILL_REQUIREMENTS") {
    for (const skill of data.skills ?? []) {
      failures += requireLocalPath(filePath, skill.skillPath, `required skill ${skill.id}`);
    }
  }
  if (data.type === "WORKFLOW_PROFILE") {
    failures += validateWorkflowProfileReferences(filePath, data);
  }
  return failures;
}

function validateWorkflowProfileReferences(filePath, data) {
  const marketplace = readJson(path.join(repoRoot, "marketplace.json"));
  const runtimeLinks = readJson(manifestPath("runtime-links.json"));
  const pluginNames = new Set((marketplace.plugins ?? []).map((entry) => entry.name));
  const hookNames = new Set((marketplace.hooks ?? []).map((entry) => entry.name));
  const runtimeLinkNames = new Set((runtimeLinks.entries ?? []).map((entry) => entry.name));
  let failures = 0;

  for (const pluginName of data.plugins ?? []) {
    if (!pluginNames.has(pluginName)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: workflow profile references unknown plugin ${pluginName}`
      );
      failures += 1;
    }
  }

  for (const hook of data.hooks ?? []) {
    if (!hookNames.has(hook.name)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: workflow profile references unknown hook ${hook.name}`
      );
      failures += 1;
      continue;
    }
    failures += requireLocalPath(
      filePath,
      `hooks/${hook.adapter}/${hook.name}.hooks.json`,
      `workflow profile hook adapter ${hook.name}@${hook.adapter}`
    );
  }

  for (const runtimeLinkName of data.runtimeLinks ?? []) {
    if (!runtimeLinkNames.has(runtimeLinkName)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: workflow profile references unknown runtime link ${runtimeLinkName}`
      );
      failures += 1;
    }
  }

  return failures;
}

function validateManifestReferences(filePath, data) {
  if (filePath.endsWith(path.join("manifests", "promotions.json"))) {
    return validatePromotionManifestReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "runtime-links.json"))) {
    return validateRuntimeLinkReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "primitive-audits.json"))) {
    return validatePrimitiveAuditReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "source-review-decisions.json"))) {
    return validateSourceReviewDecisionReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "digest-review-decisions.json"))) {
    return validateDigestReviewDecisionReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "source-root-decisions.json"))) {
    return validateSourceRootDecisionReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "cleanup-ledger.json"))) {
    return validateCleanupLedgerReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "plugin-coverage.json"))) {
    return validatePluginCoverageReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "primitive-decision-coverage.json"))) {
    return validatePrimitiveDecisionCoverageReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "review-completeness.json"))) {
    return validateReviewCompletenessReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "source-root-retirement.json"))) {
    return validateSourceRootRetirementReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "source-turnoff-readiness.json"))) {
    return validateSourceTurnoffReadinessReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "runtime-activation-plan.json"))) {
    return validateRuntimeActivationPlanReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "runtime-activation-preflight.json"))) {
    return validateRuntimeActivationPreflightReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "runtime-activation-approvals.json"))) {
    return validateRuntimeActivationApprovalReferences(filePath, data);
  }
  if (filePath.endsWith(path.join("manifests", "runtime-trim-execution.json"))) {
    return validateRuntimeTrimExecutionReferences(filePath, data);
  }
  return 0;
}

function validatePromotionManifestReferences(filePath, data) {
  let failures = 0;
  for (const entry of data.entries ?? []) {
    failures += requireLocalPath(filePath, entry.canonicalPath, `promotion ${entry.name} canonicalPath`);
    if (entry.sourceResolvedPath && !fs.existsSync(entry.sourceResolvedPath)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: missing promotion sourceResolvedPath for ${entry.name}: ${entry.sourceResolvedPath}`
      );
      failures += 1;
    }
    for (const source of entry.supportingSources ?? []) {
      if (source.sourceResolvedPath && !fs.existsSync(source.sourceResolvedPath)) {
        console.error(
          `FAIL ${path.relative(repoRoot, filePath)}: missing promotion supporting sourceResolvedPath for ${entry.name}: ${source.sourceResolvedPath}`
        );
        failures += 1;
      }
    }
    failures += validateFirstPartyPromotion(filePath, entry);
  }
  return failures;
}

function validateRuntimeLinkReferences(filePath, data) {
  let failures = 0;
  for (const entry of data.entries ?? []) {
    failures += requireLocalPath(filePath, entry.sourcePath, `runtime link ${entry.name} sourcePath`);
    const findings = entry.reviewFindings ?? [];
    const blockingFindings = findings.filter((finding) => finding.status === "BLOCKING");
    if (entry.status === "REVIEW_REQUIRED" && blockingFindings.length === 0) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: runtime link ${entry.name} REVIEW_REQUIRED needs at least one BLOCKING review finding`
      );
      failures += 1;
    }
    if ((entry.status === "PLANNED" || entry.status === "READY") && blockingFindings.length > 0) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: runtime link ${entry.name} cannot be ${entry.status} while BLOCKING review findings remain`
      );
      failures += 1;
    }
  }
  return failures;
}

function validatePrimitiveAuditReferences(filePath, data) {
  let failures = 0;
  for (const entry of data.entries ?? []) {
    failures += requireLocalPath(
      filePath,
      entry.primitive?.path,
      `primitive audit ${entry.primitive?.name ?? "unknown"} canonical path`
    );
    for (const source of entry.sourceCandidates ?? []) {
      if (source.resolvedPath && !fs.existsSync(source.resolvedPath)) {
        console.error(
          `FAIL ${path.relative(repoRoot, filePath)}: missing audit source resolvedPath for ${entry.primitive?.name}: ${source.resolvedPath}`
        );
        failures += 1;
      }
    }
  }
  return failures;
}

function validateSourceReviewDecisionReferences(filePath, data) {
  const reportPath = manifestPath("consolidation-report.json");
  if (!fs.existsSync(reportPath)) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: missing consolidation report for source review decisions`
    );
    return 1;
  }

  const report = readJson(reportPath);
  const queueByKey = new Map();
  for (const item of report.nameReviewQueue ?? []) {
    queueByKey.set(sourceReviewKey(item.type, item.name), item);
  }

  const seen = new Set();
  let failures = 0;

  for (const entry of data.entries ?? []) {
    const key = sourceReviewKey(entry.target?.primitiveType, entry.target?.name);
    if (seen.has(key)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: duplicate source-review decision for ${entry.target?.primitiveType} ${entry.target?.name}`
      );
      failures += 1;
    }
    seen.add(key);

    const queueItem = queueByKey.get(key);
    if (!queueItem) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: source-review decision has no generated name-review item for ${entry.target?.primitiveType} ${entry.target?.name}`
      );
      failures += 1;
      continue;
    }

    failures += validateSourceReviewQueueEvidence(filePath, entry, queueItem);
    failures += validateSourceReviewStatus(filePath, entry);
    failures += validateSourceReviewCoverage(filePath, entry);
  }

  for (const item of report.nameReviewQueue ?? []) {
    const key = sourceReviewKey(item.type, item.name);
    if (!seen.has(key)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: missing source-review decision for generated name-review item ${item.type} ${item.name}`
      );
      failures += 1;
    }
  }

  return failures;
}

function validateDigestReviewDecisionReferences(filePath, data) {
  const reportPath = manifestPath("consolidation-report.json");
  if (!fs.existsSync(reportPath)) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: missing consolidation report for digest review decisions`
    );
    return 1;
  }

  const report = readJson(reportPath);
  const queueByDigest = new Map();
  for (const item of report.digestReviewQueue ?? []) {
    queueByDigest.set(item.sha256, item);
  }

  const seen = new Set();
  let failures = 0;

  for (const entry of data.entries ?? []) {
    const digest = entry.target?.sha256;
    if (seen.has(digest)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: duplicate digest-review decision for ${digest}`
      );
      failures += 1;
    }
    seen.add(digest);

    const queueItem = queueByDigest.get(digest);
    if (!queueItem) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: digest-review decision has no generated digest-review item for ${digest}`
      );
      failures += 1;
      continue;
    }

    failures += validateDigestReviewQueueEvidence(filePath, entry, queueItem);
    failures += validateDigestReviewStatus(filePath, entry);
    failures += validateDigestReviewCoverage(filePath, entry);
  }

  for (const item of report.digestReviewQueue ?? []) {
    if (!seen.has(item.sha256)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: missing digest-review decision for generated digest-review item ${item.sha256}`
      );
      failures += 1;
    }
  }

  return failures;
}

function validateSourceRootDecisionReferences(filePath, data) {
  const sourceRoots = readJson(manifestPath("source-roots.json"));
  const discovered = readJson(manifestPath("discovered-primitives.json"));
  const roots = new Set((sourceRoots.scanRoots ?? []).map((root) => root.name));
  const entriesByRoot = new Map();
  for (const entry of discovered.entries ?? []) {
    const bucket = entriesByRoot.get(entry.sourceRoot) ?? [];
    bucket.push(entry);
    entriesByRoot.set(entry.sourceRoot, bucket);
  }
  const seen = new Set();
  let failures = 0;

  for (const entry of data.entries ?? []) {
    if (seen.has(entry.sourceRoot)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: duplicate source-root decision for ${entry.sourceRoot}`
      );
      failures += 1;
    }
    seen.add(entry.sourceRoot);

    if (!roots.has(entry.sourceRoot)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: source-root decision references unknown root ${entry.sourceRoot}`
      );
      failures += 1;
      continue;
    }

    const discoveredEntries = entriesByRoot.get(entry.sourceRoot) ?? [];
    failures += compareNumber(
      filePath,
      `source-root decision ${entry.sourceRoot} observedEntryCount`,
      discoveredEntries.length,
      entry.observedEntryCount
    );
    const expectedStatus = entry.decision === "REVIEW_REQUIRED" ? "NEEDS_REVIEW" : "DECIDED";
    if (entry.status !== expectedStatus) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: source-root decision ${entry.sourceRoot} status is stale; expected ${expectedStatus}, found ${entry.status}`
      );
      failures += 1;
    }

    const primitiveKeys = new Set(discoveredEntries.map((item) =>
      `${item.type}\u0000${item.name}\u0000${item.path}\u0000${item.sha256}`
    ));
    for (const evidence of entry.primitiveEvidence ?? []) {
      const key = `${evidence.primitiveType}\u0000${evidence.name}\u0000${evidence.path}\u0000${evidence.sha256}`;
      if (!primitiveKeys.has(key)) {
        console.error(
          `FAIL ${path.relative(repoRoot, filePath)}: source-root decision ${entry.sourceRoot} evidence is stale for ${evidence.primitiveType} ${evidence.name}`
        );
        failures += 1;
      }
    }
    if (entry.coverageMode === "LISTED_PRIMITIVES" && (entry.primitiveEvidence ?? []).length !== discoveredEntries.length) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: source-root decision ${entry.sourceRoot} primitiveEvidence count is stale; expected ${discoveredEntries.length}, found ${(entry.primitiveEvidence ?? []).length}`
      );
      failures += 1;
    }
    if (entry.coverageMode === "ALL_DISCOVERED_ENTRIES" && (entry.primitiveEvidence ?? []).length === 0 && discoveredEntries.length > 0) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: source-root decision ${entry.sourceRoot} should include at least one evidence sample for ALL_DISCOVERED_ENTRIES`
      );
      failures += 1;
    }
  }

  return failures;
}

function validateDigestReviewQueueEvidence(filePath, entry, queueItem) {
  const evidence = entry.queueEvidence ?? {};
  const digest = entry.target?.sha256;
  let failures = 0;

  if (evidence.queueType !== "DIGEST_REVIEW") {
    console.error(`FAIL ${path.relative(repoRoot, filePath)}: ${digest} must reference DIGEST_REVIEW evidence`);
    failures += 1;
  }
  if (evidence.generatedAction !== queueItem.action) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: ${digest} generatedAction is stale; expected ${queueItem.action}, found ${evidence.generatedAction}`
    );
    failures += 1;
  }
  if (evidence.priority !== queueItem.priority) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: ${digest} priority is stale; expected ${queueItem.priority}, found ${evidence.priority}`
    );
    failures += 1;
  }
  if (evidence.entryCount !== queueItem.entries?.length) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: ${digest} entryCount is stale; expected ${queueItem.entries?.length}, found ${evidence.entryCount}`
    );
    failures += 1;
  }

  failures += compareStringSet(filePath, digest, "buckets", queueItem.buckets ?? [], evidence.buckets ?? []);
  failures += compareStringSet(
    filePath,
    digest,
    "sourceRoots",
    sortedUnique((queueItem.entries ?? []).map((item) => item.sourceRoot)),
    evidence.sourceRoots ?? []
  );
  failures += compareStringSet(
    filePath,
    digest,
    "candidateKeys",
    sortedUnique((queueItem.entries ?? []).map((item) => sourceReviewKey(item.type, item.name).replace("\u0000", ":"))),
    evidence.candidateKeys ?? []
  );

  return failures;
}

function validateDigestReviewStatus(filePath, entry) {
  const expectedStatus = entry.decision === "REVIEW_REQUIRED" ? "NEEDS_REVIEW" : "DECIDED";
  if (entry.status === expectedStatus) {
    return 0;
  }
  console.error(
    `FAIL ${path.relative(repoRoot, filePath)}: digest ${entry.target?.sha256} decision ${entry.decision} must have status ${expectedStatus}`
  );
  return 1;
}

function validateDigestReviewCoverage(filePath, entry) {
  let failures = 0;
  if (entry.decision === "COVERED_BY_CANONICAL" && !entry.canonicalCoverage?.length) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: digest ${entry.target?.sha256} decision COVERED_BY_CANONICAL requires canonicalCoverage`
    );
    failures += 1;
  }
  for (const coverage of entry.canonicalCoverage ?? []) {
    failures += requireLocalPath(
      filePath,
      coverage.path,
      `digest-review ${entry.target?.sha256} canonicalCoverage`
    );
  }
  return failures;
}

function validateCleanupLedgerReferences(filePath, data) {
  const sourceReviewPath = manifestPath("source-review-decisions.json");
  const digestReviewPath = manifestPath("digest-review-decisions.json");
  const discoveredPath = manifestPath("discovered-primitives.json");
  const reportPath = manifestPath("consolidation-report.json");
  const sourceReviews = fs.existsSync(sourceReviewPath)
    ? readJson(sourceReviewPath)
    : { entries: [] };
  const digestReviews = fs.existsSync(digestReviewPath)
    ? readJson(digestReviewPath)
    : { entries: [] };
  const discovered = fs.existsSync(discoveredPath)
    ? readJson(discoveredPath)
    : { entries: [] };
  const report = fs.existsSync(reportPath)
    ? readJson(reportPath)
    : { digestReviewQueue: [] };
  const sourceDecisionByKey = new Map(
    (sourceReviews.entries ?? []).map((entry) => [
      sourceReviewKey(entry.target?.primitiveType, entry.target?.name),
      entry
    ])
  );
  const digestDecisionBySha = new Map(
    (digestReviews.entries ?? []).map((entry) => [entry.target?.sha256, entry])
  );
  const digestQueueBySha = new Map(
    (report.digestReviewQueue ?? []).map((entry) => [entry.sha256, entry])
  );
  const discoveredBySourcePath = new Map(
    (discovered.entries ?? []).map((entry) => [
      `${entry.sourceRoot ?? ""}\u0000${entry.path ?? ""}`,
      entry
    ])
  );
  const seen = new Set();
  let failures = 0;

  for (const entry of data.entries ?? []) {
    const key = `${entry.sourceRoot ?? ""}\u0000${entry.sourcePath ?? ""}\u0000${entry.decision ?? ""}`;
    if (seen.has(key)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: duplicate cleanup entry for ${entry.sourceRoot} ${entry.sourcePath} ${entry.decision}`
      );
      failures += 1;
    }
    seen.add(key);

    if (entry.canonicalPath) {
      failures += requireLocalPath(
        filePath,
        entry.canonicalPath,
        `cleanup ${entry.sourceRoot} ${entry.sourcePath} canonicalPath`
      );
    }

    if (entry.decision === "REPLACE_WITH_SYMLINK" || entry.decision === "DELETE_ORIGINAL") {
      failures += validateCleanupReviewEvidence(
        filePath,
        entry,
        digestDecisionBySha,
        digestQueueBySha,
        sourceDecisionByKey,
        discoveredBySourcePath
      );
    }

    if (entry.decision === "REPLACE_WITH_SYMLINK" && entry.targetPath !== entry.canonicalPath) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} REPLACE_WITH_SYMLINK targetPath must match canonicalPath`
      );
      failures += 1;
    }
  }

  return failures;
}

function validateCleanupReviewEvidence(
  filePath,
  entry,
  digestDecisionBySha,
  digestQueueBySha,
  sourceDecisionByKey,
  discoveredBySourcePath
) {
  const evidence = entry.reviewEvidence ?? {};
  if (evidence.type === "SOURCE_REVIEW_EVIDENCE") {
    return validateSourceCleanupReviewEvidence(filePath, entry, sourceDecisionByKey, discoveredBySourcePath);
  }
  if (evidence.type !== "DIGEST_REVIEW_EVIDENCE") {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} has unsupported reviewEvidence type ${evidence.type}`
    );
    return 1;
  }
  return validateDigestCleanupReviewEvidence(filePath, entry, digestDecisionBySha, digestQueueBySha);
}

function validateDigestCleanupReviewEvidence(filePath, entry, digestDecisionBySha, digestQueueBySha) {
  const evidence = entry.reviewEvidence ?? {};
  const digest = evidence.targetSha256;
  let failures = 0;

  const decision = digestDecisionBySha.get(digest);
  if (!decision) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} reviewEvidence has no digest decision for ${digest}`
    );
    return 1;
  }
  if (decision.decision !== evidence.decision) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} reviewEvidence decision is stale; expected ${decision.decision}, found ${evidence.decision}`
    );
    failures += 1;
  }
  if (decision.decision !== "COVERED_BY_CANONICAL") {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} cannot use digest decision ${decision.decision} as cleanup authority`
    );
    failures += 1;
  }

  const coveragePaths = new Set((decision.canonicalCoverage ?? []).map((coverage) => coverage.path));
  if (!coveragePaths.has(entry.canonicalPath)) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} canonicalPath ${entry.canonicalPath} is not listed in digest decision ${digest}`
    );
    failures += 1;
  }

  const queueItem = digestQueueBySha.get(digest);
  if (!queueItem) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} has no generated digest-review queue item for ${digest}`
    );
    failures += 1;
  } else if (!(queueItem.entries ?? []).some((item) =>
    item.sourceRoot === entry.sourceRoot && item.path === entry.sourcePath
  )) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} is not part of digest-review group ${digest}`
    );
    failures += 1;
  }

  return failures;
}

function validateSourceCleanupReviewEvidence(filePath, entry, sourceDecisionByKey, discoveredBySourcePath) {
  const evidence = entry.reviewEvidence ?? {};
  const key = sourceReviewKey(evidence.targetPrimitiveType, evidence.targetName);
  const decision = sourceDecisionByKey.get(key);
  let failures = 0;

  if (!decision) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} reviewEvidence has no source-review decision for ${evidence.targetPrimitiveType} ${evidence.targetName}`
    );
    return 1;
  }
  if (decision.decision !== evidence.decision) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} reviewEvidence decision is stale; expected ${decision.decision}, found ${evidence.decision}`
    );
    failures += 1;
  }
  if (decision.decision !== "COVERED_BY_CANONICAL") {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} cannot use source-review decision ${decision.decision} as cleanup authority`
    );
    failures += 1;
  }

  const coveragePaths = new Set((decision.canonicalCoverage ?? []).map((coverage) => coverage.path));
  if (!coveragePaths.has(entry.canonicalPath)) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} canonicalPath ${entry.canonicalPath} is not listed in source-review decision ${evidence.targetPrimitiveType} ${evidence.targetName}`
    );
    failures += 1;
  }

  const sourcePrimitive = discoveredBySourcePath.get(`${entry.sourceRoot ?? ""}\u0000${entry.sourcePath ?? ""}`);
  if (entry.status === "EXECUTED" && entry.decision === "REPLACE_WITH_SYMLINK") {
    failures += validateExecutedReplacementEvidence(filePath, entry, evidence, sourcePrimitive);
    return failures;
  }

  if (!(decision.queueEvidence?.sourceRoots ?? []).includes(entry.sourceRoot)) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} sourceRoot is not part of source-review group ${evidence.targetPrimitiveType} ${evidence.targetName}`
    );
    failures += 1;
  }
  if (!(decision.queueEvidence?.sourceDigests ?? []).includes(evidence.sourceSha256)) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} sourceSha256 is not part of source-review group ${evidence.targetPrimitiveType} ${evidence.targetName}`
    );
    failures += 1;
  }

  if (!sourcePrimitive) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} has no discovered primitive entry`
    );
    failures += 1;
  } else {
    failures += compareCleanupEvidenceField(
      filePath,
      entry,
      "targetPrimitiveType",
      sourcePrimitive.type,
      evidence.targetPrimitiveType
    );
    failures += compareCleanupEvidenceField(
      filePath,
      entry,
      "targetName",
      sourcePrimitive.name,
      evidence.targetName
    );
    failures += compareCleanupEvidenceField(
      filePath,
      entry,
      "sourceSha256",
      sourcePrimitive.sha256,
      evidence.sourceSha256
    );
  }

  return failures;
}

function validateExecutedReplacementEvidence(filePath, entry, evidence, sourcePrimitive) {
  let failures = 0;
  const observedPath = resolveRuntimePath(entry.observedPath);
  const canonicalPath = resolveRuntimePath(entry.canonicalPath);
  const backupPath = path.join(repoRoot, ".migration-backups", "source-turnoff", entry.sourceRoot, entry.sourcePath);

  try {
    if (!fs.lstatSync(observedPath).isSymbolicLink()) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: executed cleanup ${entry.sourceRoot} ${entry.sourcePath} observedPath is not a symlink`
      );
      failures += 1;
    } else if (fs.realpathSync.native(observedPath) !== fs.realpathSync.native(canonicalPath)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: executed cleanup ${entry.sourceRoot} ${entry.sourcePath} observedPath does not resolve to canonicalPath`
      );
      failures += 1;
    }
  } catch (error) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: executed cleanup ${entry.sourceRoot} ${entry.sourcePath} cannot verify observed symlink: ${error.message}`
    );
    failures += 1;
  }

  if (!fs.existsSync(backupPath)) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: executed cleanup ${entry.sourceRoot} ${entry.sourcePath} missing backup path ${path.relative(repoRoot, backupPath)}`
    );
    failures += 1;
  } else {
    const expectedSha = evidence.sourceSha256 ?? evidence.targetSha256;
    const actualSha = digestPath(backupPath);
    if (expectedSha !== actualSha) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: executed cleanup ${entry.sourceRoot} ${entry.sourcePath} backup digest is stale; expected ${expectedSha}, found ${actualSha}`
      );
      failures += 1;
    }
  }

  if (sourcePrimitive) {
    failures += compareCleanupEvidenceField(
      filePath,
      entry,
      "targetPrimitiveType",
      sourcePrimitive.type,
      evidence.targetPrimitiveType
    );
    failures += compareCleanupEvidenceField(
      filePath,
      entry,
      "targetName",
      sourcePrimitive.name,
      evidence.targetName
    );
  }

  return failures;
}

function compareCleanupEvidenceField(filePath, entry, field, expected, actual) {
  if (expected === actual) {
    return 0;
  }
  console.error(
    `FAIL ${path.relative(repoRoot, filePath)}: cleanup ${entry.sourceRoot} ${entry.sourcePath} reviewEvidence ${field} is stale; expected ${expected}, found ${actual}`
  );
  return 1;
}

function validatePluginCoverageReferences(filePath, data) {
  let failures = 0;
  const seen = new Set();

  for (const entry of data.entries ?? []) {
    const primitive = entry.primitive ?? {};
    const key = primitiveKey(primitive.primitiveType, primitive.name);
    if (seen.has(key)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: duplicate plugin coverage entry for ${primitive.primitiveType} ${primitive.name}`
      );
      failures += 1;
    }
    seen.add(key);

    for (const canonicalPath of entry.canonicalPaths ?? []) {
      failures += requireLocalPath(
        filePath,
        canonicalPath,
        `plugin coverage ${primitive.primitiveType} ${primitive.name} canonicalPath`
      );
    }
    for (const reference of entry.pluginReferences ?? []) {
      failures += requireLocalPath(
        filePath,
        reference.primitivePath,
        `plugin coverage ${primitive.primitiveType} ${primitive.name} plugin reference`
      );
    }
    for (const reference of entry.marketplaceReferences ?? []) {
      failures += requireLocalPath(
        filePath,
        reference.pluginPath,
        `plugin coverage ${primitive.primitiveType} ${primitive.name} marketplace reference`
      );
    }

    if (entry.coverageStatus === "PLUGIN_COMPOSED" && !entry.pluginReferences?.length) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: ${primitive.primitiveType} ${primitive.name} is PLUGIN_COMPOSED without pluginReferences`
      );
      failures += 1;
    }
    if (entry.coverageStatus === "MARKETPLACE_EXPOSED") {
      if (primitive.primitiveType !== "PLUGIN") {
        console.error(
          `FAIL ${path.relative(repoRoot, filePath)}: ${primitive.primitiveType} ${primitive.name} cannot use MARKETPLACE_EXPOSED coverage`
        );
        failures += 1;
      }
      if (!entry.marketplaceReferences?.length) {
        console.error(
          `FAIL ${path.relative(repoRoot, filePath)}: ${primitive.primitiveType} ${primitive.name} is MARKETPLACE_EXPOSED without marketplaceReferences`
        );
        failures += 1;
      }
    }
    if (entry.coverageStatus === "SCOPED_INSTRUCTION") {
      if (primitive.primitiveType !== "INSTRUCTION") {
        console.error(
          `FAIL ${path.relative(repoRoot, filePath)}: ${primitive.primitiveType} ${primitive.name} cannot use SCOPED_INSTRUCTION coverage`
        );
        failures += 1;
      }
      if (!(entry.canonicalPaths ?? []).some((canonicalPath) => canonicalPath.endsWith("AGENTS.md"))) {
        console.error(
          `FAIL ${path.relative(repoRoot, filePath)}: ${primitive.primitiveType} ${primitive.name} SCOPED_INSTRUCTION needs an AGENTS.md path`
        );
        failures += 1;
      }
    }
  }

  const standalone = (data.entries ?? []).filter((entry) => entry.coverageStatus === "STANDALONE_ONLY").length;
  if (data.summary?.allCanonicalRouted !== (standalone === 0)) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: allCanonicalRouted is stale; expected ${standalone === 0}`
    );
    failures += 1;
  }

  return failures;
}

function validatePrimitiveDecisionCoverageReferences(filePath, data) {
  const discovered = readJson(manifestPath("discovered-primitives.json"));
  const consolidation = readJson(manifestPath("consolidation-report.json"));
  const sourceReview = readJson(manifestPath("source-review-decisions.json"));
  const digestReview = readJson(manifestPath("digest-review-decisions.json"));
  const sourceRootDecisions = readJson(manifestPath("source-root-decisions.json"));
  const sourceReviewKeys = primitiveDecisionSourceReviewKeys(consolidation, sourceReview);
  const digestReviewShas = new Set((digestReview.entries ?? []).map((entry) => entry.target?.sha256));
  const sourceRootDecisionRoots = new Set((sourceRootDecisions.entries ?? []).map((entry) => entry.sourceRoot));
  const expectedByKey = new Map();
  let failures = 0;

  for (const item of discovered.entries ?? []) {
    const key = primitiveDecisionEntryKey(item.sourceRoot, item.type, item.name, item.path, item.sha256);
    expectedByKey.set(key, primitiveDecisionCoverageState(item, sourceReviewKeys, digestReviewShas, sourceRootDecisionRoots));
  }

  const seen = new Set();
  for (const entry of data.entries ?? []) {
    const key = primitiveDecisionEntryKey(
      entry.sourceRoot,
      entry.primitive?.primitiveType,
      entry.primitive?.name,
      entry.path,
      entry.sha256
    );
    if (seen.has(key)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: duplicate primitive decision coverage entry ${entry.sourceRoot} ${entry.primitive?.primitiveType} ${entry.primitive?.name} ${entry.path}`
      );
      failures += 1;
    }
    seen.add(key);
    const expectedState = expectedByKey.get(key);
    if (!expectedState) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: primitive decision coverage entry has no inventory entry ${entry.sourceRoot} ${entry.primitive?.primitiveType} ${entry.primitive?.name} ${entry.path}`
      );
      failures += 1;
      continue;
    }
    if (entry.coverageState !== expectedState) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: primitive decision coverage ${entry.sourceRoot} ${entry.primitive?.primitiveType} ${entry.primitive?.name} state is stale; expected ${expectedState}, found ${entry.coverageState}`
      );
      failures += 1;
    }
  }

  for (const key of expectedByKey.keys()) {
    if (!seen.has(key)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: missing primitive decision coverage entry for ${key.replaceAll("\u0000", " ")}`
      );
      failures += 1;
    }
  }

  const entries = data.entries ?? [];
  failures += compareNumber(filePath, "primitive decision coverage totalEntries", entries.length, data.summary?.totalEntries);
  failures += compareNumber(filePath, "primitive decision coverage canonicalOwner", countCoverageState(entries, "CANONICAL_OWNER"), data.summary?.canonicalOwner);
  failures += compareNumber(filePath, "primitive decision coverage canonicalRuntimeAlias", countCoverageState(entries, "CANONICAL_RUNTIME_ALIAS"), data.summary?.canonicalRuntimeAlias);
  failures += compareNumber(filePath, "primitive decision coverage sourceReviewDecision", countCoverageState(entries, "SOURCE_REVIEW_DECISION"), data.summary?.sourceReviewDecision);
  failures += compareNumber(filePath, "primitive decision coverage digestReviewDecision", countCoverageState(entries, "DIGEST_REVIEW_DECISION"), data.summary?.digestReviewDecision);
  failures += compareNumber(filePath, "primitive decision coverage sourceRootDecision", countCoverageState(entries, "SOURCE_ROOT_DECISION"), data.summary?.sourceRootDecision);
  failures += compareNumber(filePath, "primitive decision coverage unreviewedSingleton", countCoverageState(entries, "UNREVIEWED_SINGLETON"), data.summary?.unreviewedSingleton);
  if (data.summary?.allEntriesCovered !== (countCoverageState(entries, "UNREVIEWED_SINGLETON") === 0)) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: primitive decision coverage allEntriesCovered is stale`
    );
    failures += 1;
  }

  return failures;
}

function primitiveDecisionSourceReviewKeys(consolidation, sourceReview) {
  const decided = new Set((sourceReview.entries ?? [])
    .filter((entry) => entry.status === "DECIDED")
    .map((entry) => primitiveKey(entry.target?.primitiveType, entry.target?.name)));
  const result = new Set();
  for (const queueItem of consolidation.nameReviewQueue ?? []) {
    if (!decided.has(primitiveKey(queueItem.type, queueItem.name))) {
      continue;
    }
    for (const entry of queueItem.entries ?? []) {
      result.add(primitiveDecisionEntryKey(entry.sourceRoot, entry.type, entry.name, entry.path, entry.sha256));
    }
  }
  return result;
}

function primitiveDecisionCoverageState(item, sourceReviewKeys, digestReviewShas, sourceRootDecisionRoots) {
  const key = primitiveDecisionEntryKey(item.sourceRoot, item.type, item.name, item.path, item.sha256);
  if (item.sourceRootRole === "canonical-candidate") {
    return "CANONICAL_OWNER";
  }
  if (isCanonicalRuntimeAlias(item)) {
    return "CANONICAL_RUNTIME_ALIAS";
  }
  if (sourceReviewKeys.has(key)) {
    return "SOURCE_REVIEW_DECISION";
  }
  if (digestReviewShas.has(item.sha256)) {
    return "DIGEST_REVIEW_DECISION";
  }
  if (sourceRootDecisionRoots.has(item.sourceRoot)) {
    return "SOURCE_ROOT_DECISION";
  }
  return "UNREVIEWED_SINGLETON";
}

function isCanonicalRuntimeAlias(item) {
  if (item.sourceRootRole !== "runtime-source" || !item.resolvedPath) {
    return false;
  }
  const relative = path.relative(repoRoot, item.resolvedPath);
  return relative && !relative.startsWith("..") && !path.isAbsolute(relative);
}

function primitiveDecisionEntryKey(sourceRoot, primitiveType, name, entryPath, sha256) {
  return `${sourceRoot}\u0000${primitiveType}\u0000${name}\u0000${entryPath}\u0000${sha256}`;
}

function countCoverageState(entries, state) {
  return entries.filter((entry) => entry.coverageState === state).length;
}

function validateReviewCompletenessReferences(filePath, data) {
  const pluginCoverage = readJson(manifestPath("plugin-coverage.json"));
  const audits = readJson(manifestPath("primitive-audits.json"));
  const promotions = readJson(manifestPath("promotions.json"));
  const auditKeys = new Set((audits.entries ?? []).map((entry) =>
    primitiveKey(entry.primitive?.primitiveType, entry.primitive?.name)
  ));
  const promotionKeys = new Set((promotions.entries ?? []).map((entry) =>
    primitiveKey(entry.type, entry.name)
  ));
  const coverageKeys = new Set((pluginCoverage.entries ?? []).map((entry) =>
    primitiveKey(entry.primitive?.primitiveType, entry.primitive?.name)
  ));
  const completenessKeys = new Set();
  let failures = 0;

  for (const entry of data.entries ?? []) {
    const primitive = entry.primitive ?? {};
    const key = primitiveKey(primitive.primitiveType, primitive.name);
    if (completenessKeys.has(key)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: duplicate review completeness entry for ${primitive.primitiveType} ${primitive.name}`
      );
      failures += 1;
    }
    completenessKeys.add(key);
    if (!coverageKeys.has(key)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: review completeness entry has no plugin coverage entry for ${primitive.primitiveType} ${primitive.name}`
      );
      failures += 1;
    }
    const expectedAuditState = auditKeys.has(key) ? "AUDITED" : "NEEDS_AUDIT";
    if (entry.auditState !== expectedAuditState) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: ${primitive.primitiveType} ${primitive.name} auditState is stale; expected ${expectedAuditState}, found ${entry.auditState}`
      );
      failures += 1;
    }
    const expectedPromotionState = promotionKeys.has(key) ? "PROMOTED" : "NATIVE_CANONICAL";
    if (entry.promotionState !== expectedPromotionState) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: ${primitive.primitiveType} ${primitive.name} promotionState is stale; expected ${expectedPromotionState}, found ${entry.promotionState}`
      );
      failures += 1;
    }
    for (const canonicalPath of entry.canonicalPaths ?? []) {
      failures += requireLocalPath(
        filePath,
        canonicalPath,
        `review completeness ${primitive.primitiveType} ${primitive.name} canonicalPath`
      );
    }
  }

  for (const key of coverageKeys) {
    if (!completenessKeys.has(key)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: missing review completeness entry for ${key.replace("\u0000", " ")}`
      );
      failures += 1;
    }
  }

  const audited = (data.entries ?? []).filter((entry) => entry.auditState === "AUDITED").length;
  const needsAudit = (data.entries ?? []).filter((entry) => entry.auditState === "NEEDS_AUDIT").length;
  const promoted = (data.entries ?? []).filter((entry) => entry.promotionState === "PROMOTED").length;
  const nativeCanonical = (data.entries ?? []).filter((entry) => entry.promotionState === "NATIVE_CANONICAL").length;
  failures += compareNumber(filePath, "review completeness totalCanonical", data.entries?.length ?? 0, data.summary?.totalCanonical);
  failures += compareNumber(filePath, "review completeness audited", audited, data.summary?.audited);
  failures += compareNumber(filePath, "review completeness needsAudit", needsAudit, data.summary?.needsAudit);
  failures += compareNumber(filePath, "review completeness promoted", promoted, data.summary?.promoted);
  failures += compareNumber(filePath, "review completeness nativeCanonical", nativeCanonical, data.summary?.nativeCanonical);
  if (data.summary?.allCanonicalAudited !== (needsAudit === 0)) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: allCanonicalAudited is stale; expected ${needsAudit === 0}, found ${data.summary?.allCanonicalAudited}`
    );
    failures += 1;
  }

  return failures;
}

function validateSourceRootRetirementReferences(filePath, data) {
  const sourceRoots = readJson(manifestPath("source-roots.json"));
  const discovered = readJson(manifestPath("discovered-primitives.json"));
  const sourceReview = readJson(manifestPath("source-review-decisions.json"));
  const digestReview = readJson(manifestPath("digest-review-decisions.json"));
  const sourceRootDecisions = readJson(manifestPath("source-root-decisions.json"));
  const cleanupLedger = readJson(manifestPath("cleanup-ledger.json"));
  const approvals = readJson(manifestPath("runtime-activation-approvals.json"));
  const discoveredByRoot = countEntriesByRoot(discovered.entries ?? []);
  const sourceDecisions = decisionCountsByRoot(sourceReview);
  const digestDecisions = decisionCountsByRoot(digestReview);
  const rootDecisions = rootDecisionCountsByRoot(sourceRootDecisions);
  const cleanup = cleanupCountsByRoot(cleanupLedger);
  const approval = approvalCountsByRoot(sourceRoots, cleanupLedger, approvals);
  const rootsByName = new Map((sourceRoots.scanRoots ?? []).map((root) => [root.name, root]));
  const seen = new Set();
  const entries = data.roots ?? [];
  let failures = 0;

  failures += compareNumber(filePath, "source root retirement totalRoots", entries.length, data.summary?.totalRoots);
  failures += compareNumber(
    filePath,
    "source root retirement totalObservedEntries",
    entries.reduce((sum, entry) => sum + (entry.observedEntries ?? 0), 0),
    data.summary?.totalObservedEntries
  );
  failures += compareSourceRootStateCount(filePath, data, "canonicalOwners", "CANONICAL_OWNER");
  failures += compareSourceRootStateCount(filePath, data, "partialReplacementReady", "PARTIAL_REPLACEMENT_READY");
  failures += compareSourceRootStateCount(filePath, data, "runtimeDependencyMapped", "RUNTIME_DEPENDENCY_MAPPED");
  failures += compareSourceRootStateCount(filePath, data, "runtimeReviewRequired", "RUNTIME_REVIEW_REQUIRED");
  failures += compareSourceRootStateCount(filePath, data, "coveredNoReplacementPlan", "COVERED_NO_REPLACEMENT_PLAN");
  failures += compareSourceRootStateCount(filePath, data, "retainExternalOwners", "RETAIN_EXTERNAL_OWNER");
  failures += compareSourceRootStateCount(filePath, data, "mixedRetainAndCovered", "MIXED_RETAIN_AND_COVERED");
  failures += compareSourceRootStateCount(filePath, data, "cleanupRecorded", "CLEANUP_RECORDED");
  failures += compareSourceRootStateCount(filePath, data, "emptySourceRoots", "EMPTY_SOURCE_ROOT");
  failures += compareSourceRootStateCount(filePath, data, "noActionRecorded", "NO_ACTION_RECORDED");

  for (const entry of entries) {
    if (seen.has(entry.sourceRoot)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: duplicate source root retirement entry ${entry.sourceRoot}`
      );
      failures += 1;
    }
    seen.add(entry.sourceRoot);

    const root = rootsByName.get(entry.sourceRoot);
    if (!root) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: source root retirement entry has no source root ${entry.sourceRoot}`
      );
      failures += 1;
      continue;
    }

    failures += compareNumber(filePath, `source root ${entry.sourceRoot} observedEntries`, discoveredByRoot.get(entry.sourceRoot) ?? 0, entry.observedEntries);
    failures += comparePacketString(filePath, `source root ${entry.sourceRoot}`, "sourcePath", root.path, entry.sourcePath);
    failures += comparePacketString(filePath, `source root ${entry.sourceRoot}`, "role", root.role, entry.role);

    const expectedSource = sourceDecisions.get(entry.sourceRoot) ?? new Map();
    const expectedDigest = digestDecisions.get(entry.sourceRoot) ?? new Map();
    const expectedRoot = rootDecisions.get(entry.sourceRoot) ?? new Map();
    const expectedCleanup = cleanup.get(entry.sourceRoot) ?? new Map();
    const expectedApproval = approval.get(entry.sourceRoot) ?? new Map();
    const decision = entry.decisionSummary ?? {};
    const cleanupSummary = entry.cleanupSummary ?? {};
    const approvalSummary = entry.approvalSummary ?? {};

    failures += compareNumber(filePath, `source root ${entry.sourceRoot} sourceCoveredByCanonical`, expectedSource.get("COVERED_BY_CANONICAL") ?? 0, decision.sourceCoveredByCanonical);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} sourceRetainedExternal`, expectedSource.get("RETAIN_EXTERNAL") ?? 0, decision.sourceRetainedExternal);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} sourceKeepCanonical`, expectedSource.get("KEEP_CANONICAL") ?? 0, decision.sourceKeepCanonical);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} digestCoveredByCanonical`, expectedDigest.get("COVERED_BY_CANONICAL") ?? 0, decision.digestCoveredByCanonical);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} digestRetainedExternal`, expectedDigest.get("RETAIN_EXTERNAL") ?? 0, decision.digestRetainedExternal);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} rootRetainedExternal`, expectedRoot.get("RETAIN_EXTERNAL_OWNER") ?? 0, decision.rootRetainedExternal);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} rootPromoteCandidates`, expectedRoot.get("PROMOTE_CANDIDATES") ?? 0, decision.rootPromoteCandidates);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} rootReviewRequired`, expectedRoot.get("REVIEW_REQUIRED") ?? 0, decision.rootReviewRequired);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} cleanup proposed`, expectedCleanup.get("PROPOSED") ?? 0, cleanupSummary.proposed);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} cleanup approved`, expectedCleanup.get("APPROVED") ?? 0, cleanupSummary.approved);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} cleanup executed`, expectedCleanup.get("EXECUTED") ?? 0, cleanupSummary.executed);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} approval readyForApproval`, expectedApproval.get("READY_FOR_APPROVAL") ?? 0, approvalSummary.readyForApproval);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} approval readyForManualImport`, expectedApproval.get("READY_FOR_MANUAL_IMPORT") ?? 0, approvalSummary.readyForManualImport);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} approval reviewRequired`, expectedApproval.get("REVIEW_REQUIRED") ?? 0, approvalSummary.reviewRequired);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} approval dependencyMapped`, expectedApproval.get("DEPENDENCY_MAPPED") ?? 0, approvalSummary.dependencyMapped);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} approval blocked`, expectedApproval.get("BLOCKED") ?? 0, approvalSummary.blocked);
    failures += compareNumber(filePath, `source root ${entry.sourceRoot} approval alreadyActive`, expectedApproval.get("ALREADY_ACTIVE") ?? 0, approvalSummary.alreadyActive);
    failures += comparePacketString(
      filePath,
      `source root ${entry.sourceRoot}`,
      "retirementState",
      sourceRootRetirementState(root, entry.observedEntries, decision, cleanupSummary, approvalSummary),
      entry.retirementState
    );
  }

  for (const root of sourceRoots.scanRoots ?? []) {
    if (!seen.has(root.name)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: missing source root retirement entry for ${root.name}`
      );
      failures += 1;
    }
  }

  return failures;
}

function compareSourceRootStateCount(filePath, data, summaryField, state) {
  return compareNumber(
    filePath,
    `source root retirement ${summaryField}`,
    (data.roots ?? []).filter((entry) => entry.retirementState === state).length,
    data.summary?.[summaryField]
  );
}

function countEntriesByRoot(entries) {
  const result = new Map();
  for (const entry of entries) {
    result.set(entry.sourceRoot, (result.get(entry.sourceRoot) ?? 0) + 1);
  }
  return result;
}

function decisionCountsByRoot(manifest) {
  const result = new Map();
  for (const entry of manifest.entries ?? []) {
    for (const sourceRoot of entry.queueEvidence?.sourceRoots ?? []) {
      const counts = result.get(sourceRoot) ?? new Map();
      counts.set(entry.decision, (counts.get(entry.decision) ?? 0) + 1);
      result.set(sourceRoot, counts);
    }
  }
  return result;
}

function rootDecisionCountsByRoot(manifest) {
  const result = new Map();
  for (const entry of manifest.entries ?? []) {
    const counts = result.get(entry.sourceRoot) ?? new Map();
    counts.set(entry.decision, (counts.get(entry.decision) ?? 0) + 1);
    result.set(entry.sourceRoot, counts);
  }
  return result;
}

function cleanupCountsByRoot(manifest) {
  const result = new Map();
  for (const entry of manifest.entries ?? []) {
    const counts = result.get(entry.sourceRoot) ?? new Map();
    counts.set(entry.status, (counts.get(entry.status) ?? 0) + 1);
    result.set(entry.sourceRoot, counts);
  }
  return result;
}

function approvalCountsByRoot(sourceRoots, cleanupLedger, approvals) {
  const cleanupRootByOperation = new Map();
  for (const entry of cleanupLedger.entries ?? []) {
    if (entry.decision !== "REPLACE_WITH_SYMLINK") {
      continue;
    }
    cleanupRootByOperation.set(slug(`replace-${entry.sourceRoot}-${entry.sourcePath}`), entry.sourceRoot);
  }
  const roots = (sourceRoots.scanRoots ?? []).map((root) => ({
    name: root.name,
    path: expandRepoPath(root.path)
  }));
  const result = new Map();
  for (const packet of approvals.packets ?? []) {
    const sourceRoot = cleanupRootByOperation.get(packet.name) ?? matchingSourceRoot(packet, roots);
    if (!sourceRoot) {
      continue;
    }
    const counts = result.get(sourceRoot) ?? new Map();
    counts.set(packet.approvalState, (counts.get(packet.approvalState) ?? 0) + 1);
    if (packet.approvalState === "REVIEW_REQUIRED" && (packet.dependencyPackets ?? []).length > 0) {
      counts.set("DEPENDENCY_MAPPED", (counts.get("DEPENDENCY_MAPPED") ?? 0) + 1);
    }
    result.set(sourceRoot, counts);
  }
  return result;
}

function matchingSourceRoot(packet, roots) {
  for (const pathValue of [packet.targetPath, packet.sourcePath]) {
    if (!pathValue || pathValue.startsWith("codex://")) {
      continue;
    }
    const candidate = path.resolve(pathValue.replace(/^~(?=$|\/)/, process.env.HOME ?? ""));
    for (const root of roots) {
      if (candidate === root.path || isPathInside(candidate, root.path)) {
        return root.name;
      }
    }
  }
  return null;
}

function expandRepoPath(pathValue) {
  const expanded = pathValue.replace(/^~(?=$|\/)/, process.env.HOME ?? "");
  if (path.isAbsolute(expanded)) {
    return path.resolve(expanded);
  }
  return path.resolve(repoRoot, expanded);
}

function isPathInside(candidate, root) {
  const relative = path.relative(root, candidate);
  return relative !== "" && !relative.startsWith("..") && !path.isAbsolute(relative);
}

function sourceRootRetirementState(root, observed, decision, cleanup, approval) {
  if (root.role === "canonical-candidate" && root.name === "intelligence") {
    return "CANONICAL_OWNER";
  }
  if (decision.rootReviewRequired > 0) {
    return "RUNTIME_REVIEW_REQUIRED";
  }
  if (approval.blocked > 0) {
    return "RUNTIME_REVIEW_REQUIRED";
  }
  if (approval.reviewRequired > 0) {
    return approval.dependencyMapped >= approval.reviewRequired
      ? "RUNTIME_DEPENDENCY_MAPPED"
      : "RUNTIME_REVIEW_REQUIRED";
  }
  if (approval.readyForApproval > 0 || cleanup.proposed > 0) {
    return "PARTIAL_REPLACEMENT_READY";
  }
  if (observed === 0 && cleanup.executed > 0) {
    return "CLEANUP_RECORDED";
  }
  if (observed === 0) {
    return "EMPTY_SOURCE_ROOT";
  }
  const covered = decision.sourceCoveredByCanonical +
    decision.digestCoveredByCanonical +
    decision.rootPromoteCandidates;
  const retained = decision.sourceRetainedExternal +
    decision.digestRetainedExternal +
    decision.rootRetainedExternal;
  if (covered > 0 && retained > 0) {
    return "MIXED_RETAIN_AND_COVERED";
  }
  if (covered > 0) {
    return "COVERED_NO_REPLACEMENT_PLAN";
  }
  if (retained > 0) {
    return "RETAIN_EXTERNAL_OWNER";
  }
  if (cleanup.executed > 0) {
    return "CLEANUP_RECORDED";
  }
  return "NO_ACTION_RECORDED";
}

function slug(value) {
  return value.toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function validateSourceTurnoffReadinessReferences(filePath, data) {
  const pluginCoverage = readJson(manifestPath("plugin-coverage.json"));
  const reviewCompleteness = readJson(manifestPath("review-completeness.json"));
  const sourceReview = readJson(manifestPath("source-review-decisions.json"));
  const digestReview = readJson(manifestPath("digest-review-decisions.json"));
  const cleanupLedger = readJson(manifestPath("cleanup-ledger.json"));
  const runtimeLinks = readJson(manifestPath("runtime-links.json"));
  const summary = data.summary ?? {};
  let failures = 0;

  failures += compareNumber(filePath, "source turnoff allCanonicalRouted", pluginCoverage.summary?.allCanonicalRouted ? 1 : 0, summary.allCanonicalRouted ? 1 : 0);
  failures += compareNumber(filePath, "source turnoff reviewCompletenessOpen", reviewCompleteness.summary?.needsAudit, summary.reviewCompletenessOpen);
  failures += compareNumber(filePath, "source turnoff sourceReviewOpen", countStatusNot(sourceReview, "DECIDED"), summary.sourceReviewOpen);
  failures += compareNumber(filePath, "source turnoff digestReviewOpen", countStatusNot(digestReview, "DECIDED"), summary.digestReviewOpen);
  failures += compareNumber(filePath, "source turnoff proposedReplacements", countStatus(cleanupLedger, "PROPOSED"), summary.proposedReplacements);
  failures += compareNumber(filePath, "source turnoff approvedReplacements", countStatus(cleanupLedger, "APPROVED"), summary.approvedReplacements);
  failures += compareNumber(filePath, "source turnoff executedCleanupEntries", countStatus(cleanupLedger, "EXECUTED"), summary.executedCleanupEntries);
  failures += compareNumber(
    filePath,
    "source turnoff retainedExternalGroups",
    countDecision(sourceReview, "RETAIN_EXTERNAL") + countDecision(digestReview, "RETAIN_EXTERNAL"),
    summary.retainedExternalGroups
  );
  failures += compareNumber(
    filePath,
    "source turnoff runtimeLinksRequiringApproval",
    countInactiveRuntimeLinksRequiringApproval(runtimeLinks),
    summary.runtimeLinksRequiringApproval
  );
  const expectedReadinessStatus = sourceTurnoffReadinessStatus(
    pluginCoverage.summary?.allCanonicalRouted,
    summary.reviewCompletenessOpen,
    summary.sourceReviewOpen,
    summary.digestReviewOpen,
    summary.proposedReplacements,
    summary.approvedReplacements
  );
  if (summary.readinessStatus !== expectedReadinessStatus) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: readinessStatus is stale; expected ${expectedReadinessStatus}, found ${summary.readinessStatus}`
    );
    failures += 1;
  }

  const expectedProposals = (cleanupLedger.entries ?? [])
    .filter((entry) => entry.status === "PROPOSED")
    .map((entry) => `${entry.sourceRoot}\u0000${entry.sourcePath}\u0000${entry.canonicalPath}\u0000${cleanupReviewEvidenceSha256(entry.reviewEvidence)}`)
    .sort();
  const actualProposals = (data.proposedReplacements ?? [])
    .map((entry) => `${entry.sourceRoot}\u0000${entry.sourcePath}\u0000${entry.canonicalPath}\u0000${entry.reviewEvidenceSha256}`)
    .sort();
  if (
    expectedProposals.length !== actualProposals.length ||
    !expectedProposals.every((value, index) => value === actualProposals[index])
  ) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: proposedReplacements are stale`
    );
    failures += 1;
  }
  for (const proposal of data.proposedReplacements ?? []) {
    failures += requireLocalPath(
      filePath,
      proposal.canonicalPath,
      `source turnoff proposal ${proposal.sourceRoot} ${proposal.sourcePath} canonicalPath`
    );
  }

  const expectedCanMutate = summary.readinessStatus === "APPROVED_FOR_EXECUTION" &&
    summary.runtimeLinksRequiringApproval === 0;
  if (summary.canMutateRuntimeWithoutApproval !== expectedCanMutate) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: canMutateRuntimeWithoutApproval is stale; expected ${expectedCanMutate}, found ${summary.canMutateRuntimeWithoutApproval}`
    );
    failures += 1;
  }

  return failures;
}

function cleanupReviewEvidenceSha256(evidence) {
  if (evidence?.type === "SOURCE_REVIEW_EVIDENCE") {
    return evidence.sourceSha256;
  }
  return evidence?.targetSha256;
}

function countInactiveRuntimeLinksRequiringApproval(runtimeLinks) {
  return (runtimeLinks.entries ?? [])
    .filter((entry) => entry.requiresApproval && !runtimeLinkAlreadyActive(entry))
    .length;
}

function runtimeLinkAlreadyActive(entry) {
  if (entry.strategy === "MARKETPLACE_IMPORT") {
    return codexMarketplaceAlreadyConfigured(entry);
  }
  const sourcePath = resolveRuntimeManifestPath(entry.sourcePath);
  const targetPath = resolveRuntimeManifestPath(entry.targetPath);
  if (entry.strategy === "SYMLINK_CHILDREN") {
    return runtimeChildrenAlreadyActive(entry, sourcePath, targetPath);
  }
  try {
    return fs.lstatSync(targetPath).isSymbolicLink() &&
      fs.realpathSync.native(targetPath) === fs.realpathSync.native(sourcePath);
  } catch {
    return false;
  }
}

function codexMarketplaceAlreadyConfigured(entry) {
  const sourcePath = resolveRuntimeManifestPath(entry.sourcePath);
  const marketplaceRoot = path.dirname(sourcePath);
  const configPath = path.join(process.env.CODEX_HOME || path.join(os.homedir(), ".codex"), "config.toml");
  if (!fs.existsSync(configPath)) {
    return false;
  }
  const configText = fs.readFileSync(configPath, "utf8");
  return configText.includes(`source = "${marketplaceRoot}"`);
}

function runtimeChildrenAlreadyActive(entry, sourcePath, targetPath) {
  if (!fs.existsSync(sourcePath) || !fs.statSync(sourcePath).isDirectory()) {
    return false;
  }
  if (!fs.existsSync(targetPath) || !fs.statSync(targetPath).isDirectory()) {
    return false;
  }
  const children = runtimeChildSources(entry, sourcePath);
  if (children.length === 0) {
    return false;
  }
  for (const sourceChild of children) {
    const targetChild = path.join(targetPath, path.basename(sourceChild));
    try {
      if (!fs.lstatSync(targetChild).isSymbolicLink()) {
        return false;
      }
      if (fs.realpathSync.native(targetChild) !== fs.realpathSync.native(sourceChild)) {
        return false;
      }
    } catch {
      return false;
    }
  }
  return true;
}

function runtimeChildSources(entry, sourcePath) {
  const primitiveTypes = new Set(entry.primitiveTypes ?? []);
  return fs.readdirSync(sourcePath)
    .sort()
    .map((child) => path.join(sourcePath, child))
    .filter((child) => isRuntimeChildSource(child, primitiveTypes));
}

function isRuntimeChildSource(child, primitiveTypes) {
  const name = path.basename(child);
  if (name === "AGENTS.md") {
    return false;
  }
  if (primitiveTypes.size === 1 && primitiveTypes.has("SKILL")) {
    return fs.statSync(child).isDirectory() && fs.existsSync(path.join(child, "SKILL.md"));
  }
  if (primitiveTypes.size === 1 && primitiveTypes.has("HOOK")) {
    return fs.statSync(child).isFile() && name.endsWith(".hooks.json");
  }
  if (primitiveTypes.size === 1 && primitiveTypes.has("AGENT")) {
    return fs.statSync(child).isDirectory() || name.endsWith(".agent.md");
  }
  return true;
}

function resolveRuntimeManifestPath(value) {
  if (value?.startsWith("codex://")) {
    return value;
  }
  let expanded = value;
  if (value === "~") {
    expanded = os.homedir();
  } else if (value?.startsWith("~/")) {
    expanded = path.join(os.homedir(), value.slice(2));
  }
  if (path.isAbsolute(expanded)) {
    return expanded;
  }
  return path.resolve(repoRoot, expanded);
}

function validateRuntimeActivationPlanReferences(filePath, data) {
  let failures = 0;
  const summary = data.summary ?? {};
  const operations = data.operations ?? [];
  const operationsByName = new Map(operations.map((operation) => [operation.name, operation]));
  failures += compareNumber(filePath, "runtime activation totalOperations", operations.length, summary.totalOperations);
  failures += compareNumber(
    filePath,
    "runtime activation waitingApproval",
    operations.filter((operation) => operation.status === "WAITING_APPROVAL").length,
    summary.waitingApproval
  );
  failures += compareNumber(
    filePath,
    "runtime activation waitingReview",
    operations.filter((operation) => operation.status === "WAITING_REVIEW").length,
    summary.waitingReview
  );
  failures += compareNumber(
    filePath,
    "runtime activation readyForManualImport",
    operations.filter((operation) => operation.status === "READY_FOR_MANUAL_IMPORT").length,
    summary.readyForManualImport
  );

  const readiness = readJson(manifestPath("source-turnoff-readiness.json"));
  if (summary.runtimeMutationAllowed !== readiness.summary?.canMutateRuntimeWithoutApproval) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: runtimeMutationAllowed is stale; expected ${readiness.summary?.canMutateRuntimeWithoutApproval}, found ${summary.runtimeMutationAllowed}`
    );
    failures += 1;
  }

  const runtimeLinks = readJson(manifestPath("runtime-links.json"));
  for (const entry of runtimeLinks.entries ?? []) {
    const expected = expectedRuntimeLinkOperation(entry);
    const operation = operationsByName.get(expected.name);
    if (!operation) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: missing runtime link activation operation ${expected.name}`
      );
      failures += 1;
      continue;
    }
    failures += compareRuntimeActivationField(filePath, expected.name, "operationType", expected.operationType, operation.operationType);
    failures += compareRuntimeActivationField(filePath, expected.name, "status", expected.status, operation.status);
    failures += compareRuntimeActivationField(filePath, expected.name, "sourcePath", expected.sourcePath, operation.sourcePath);
    failures += compareRuntimeActivationField(filePath, expected.name, "targetPath", expected.targetPath, operation.targetPath);
    failures += compareRuntimeActivationField(filePath, expected.name, "requiresApproval", expected.requiresApproval, operation.requiresApproval);
    failures += compareRuntimeActivationField(filePath, expected.name, "runtimeLinkStrategy", expected.runtimeLinkStrategy, operation.runtimeLinkStrategy);
    failures += compareRuntimeActivationField(filePath, expected.name, "collisionPolicy", expected.collisionPolicy, operation.collisionPolicy);
    failures += compareRuntimeActivationField(filePath, expected.name, "evidence.manifest", "garden/manifests/runtime-links.json", operation.evidence?.manifest);
    failures += compareRuntimeActivationField(filePath, expected.name, "evidence.status", entry.status, operation.evidence?.status);
  }

  for (const operation of operations) {
    if (!operation.sourcePath?.startsWith("codex://")) {
      if (!fs.existsSync(operation.sourcePath)) {
        console.error(
          `FAIL ${path.relative(repoRoot, filePath)}: runtime activation operation ${operation.name} missing sourcePath: ${operation.sourcePath}`
        );
        failures += 1;
      }
    }
    if (operation.operationType === "REPLACE_SOURCE_WITH_SYMLINK") {
      if (!operation.backupPath) {
        console.error(
          `FAIL ${path.relative(repoRoot, filePath)}: runtime activation operation ${operation.name} requires backupPath`
        );
        failures += 1;
      }
      if (!operation.requiresApproval || operation.status !== "WAITING_APPROVAL") {
        console.error(
          `FAIL ${path.relative(repoRoot, filePath)}: runtime activation replacement ${operation.name} must wait for approval`
        );
        failures += 1;
      }
    }
    if (operation.operationType === "ACTIVATE_RUNTIME_LINK") {
      if (!operation.runtimeLinkStrategy || !operation.collisionPolicy) {
        console.error(
          `FAIL ${path.relative(repoRoot, filePath)}: runtime link activation ${operation.name} must include runtimeLinkStrategy and collisionPolicy`
        );
        failures += 1;
      }
    }
  }

  return failures;
}

function expectedRuntimeLinkOperation(entry) {
  const operationType = entry.strategy === "MARKETPLACE_IMPORT"
    ? "IMPORT_MARKETPLACE"
    : "ACTIVATE_RUNTIME_LINK";
  return {
    name: slug(entry.name),
    operationType,
    status: runtimeLinkPlanStatus(entry),
    sourcePath: resolveRuntimeLinkSourcePath(entry.sourcePath),
    targetPath: resolveRuntimeLinkTargetPath(entry.targetPath),
    requiresApproval: entry.requiresApproval,
    runtimeLinkStrategy: entry.strategy,
    collisionPolicy: entry.collisionPolicy
  };
}

function runtimeLinkPlanStatus(entry) {
  if (entry.strategy === "MARKETPLACE_IMPORT" && entry.status === "READY") {
    return "READY_FOR_MANUAL_IMPORT";
  }
  if (entry.status === "PLANNED") {
    return "WAITING_APPROVAL";
  }
  return "WAITING_REVIEW";
}

function resolveRuntimeLinkSourcePath(value) {
  if (value.startsWith("codex://")) {
    return value;
  }
  return resolveRuntimePath(value);
}

function resolveRuntimeLinkTargetPath(value) {
  if (value.startsWith("codex://")) {
    return value;
  }
  return resolveRuntimePath(value);
}

function resolveRuntimePath(value) {
  if (value === "~") {
    return os.homedir();
  }
  if (value.startsWith("~/")) {
    return path.resolve(os.homedir(), value.slice(2));
  }
  if (path.isAbsolute(value)) {
    return path.resolve(value);
  }
  return path.resolve(repoRoot, value);
}

function compareRuntimeActivationField(filePath, operationName, field, expected, actual) {
  if (expected === actual) {
    return 0;
  }
  console.error(
    `FAIL ${path.relative(repoRoot, filePath)}: runtime activation operation ${operationName} ${field} is stale; expected ${expected}, found ${actual}`
  );
  return 1;
}

function validateRuntimeActivationPreflightReferences(filePath, data) {
  let failures = 0;
  const entries = data.entries ?? [];
  const summary = data.summary ?? {};
  failures += compareNumber(filePath, "runtime activation preflight totalEntries", entries.length, summary.totalEntries);
  failures += compareNumber(
    filePath,
    "runtime activation preflight alreadyActive",
    entries.filter((entry) => entry.status === "ALREADY_ACTIVE").length,
    summary.alreadyActive
  );
  failures += compareNumber(
    filePath,
    "runtime activation preflight blocked",
    entries.filter((entry) => entry.status === "BLOCKED").length,
    summary.blocked
  );
  failures += compareNumber(
    filePath,
    "runtime activation preflight readyForApproval",
    entries.filter((entry) => entry.status === "READY_FOR_APPROVAL").length,
    summary.readyForApproval
  );
  failures += compareNumber(
    filePath,
    "runtime activation preflight reviewRequired",
    entries.filter((entry) => entry.status === "REVIEW_REQUIRED").length,
    summary.reviewRequired
  );
  if (summary.runtimeMutationAllowed !== false) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: runtime activation preflight must not allow runtime mutation`
    );
    failures += 1;
  }

  const plan = readJson(manifestPath("runtime-activation-plan.json"));
  const planOperations = new Set((plan.operations ?? []).map((operation) => operation.name));
  const preflightOperations = new Set(entries.map((entry) => entry.operationName));
  for (const operationName of planOperations) {
    if (!preflightOperations.has(operationName)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: missing preflight entry for activation operation ${operationName}`
      );
      failures += 1;
    }
  }
  for (const operationName of preflightOperations) {
    if (!planOperations.has(operationName)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: preflight entry has no activation operation ${operationName}`
      );
      failures += 1;
    }
  }

  return failures;
}

function validateRuntimeActivationApprovalReferences(filePath, data) {
  let failures = 0;
  const readiness = readJson(manifestPath("source-turnoff-readiness.json"));
  const plan = readJson(manifestPath("runtime-activation-plan.json"));
  const preflight = readJson(manifestPath("runtime-activation-preflight.json"));
  const planByName = new Map((plan.operations ?? []).map((operation) => [operation.name, operation]));
  const preflightByName = new Map((preflight.entries ?? []).map((entry) => [entry.operationName, entry]));
  const replacementDependencies = runtimeActivationReplacementDependencyMap(plan);
  const packets = data.packets ?? [];
  const packetByName = new Map();

  failures += compareNumber(filePath, "runtime activation approvals totalPackets", packets.length, data.summary?.totalPackets);
  failures += compareNumber(
    filePath,
    "runtime activation approvals readyForApproval",
    packets.filter((packet) => packet.approvalState === "READY_FOR_APPROVAL").length,
    data.summary?.readyForApproval
  );
  failures += compareNumber(
    filePath,
    "runtime activation approvals readyForManualImport",
    packets.filter((packet) => packet.approvalState === "READY_FOR_MANUAL_IMPORT").length,
    data.summary?.readyForManualImport
  );
  failures += compareNumber(
    filePath,
    "runtime activation approvals reviewRequired",
    packets.filter((packet) => packet.approvalState === "REVIEW_REQUIRED").length,
    data.summary?.reviewRequired
  );
  failures += compareNumber(
    filePath,
    "runtime activation approvals blocked",
    packets.filter((packet) => packet.approvalState === "BLOCKED").length,
    data.summary?.blocked
  );
  failures += compareNumber(
    filePath,
    "runtime activation approvals alreadyActive",
    packets.filter((packet) => packet.approvalState === "ALREADY_ACTIVE").length,
    data.summary?.alreadyActive
  );
  if (data.summary?.sourceTurnoffStatus !== readiness.summary?.readinessStatus) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: runtime activation approvals sourceTurnoffStatus is stale; expected ${readiness.summary?.readinessStatus}, found ${data.summary?.sourceTurnoffStatus}`
    );
    failures += 1;
  }
  if (data.summary?.runtimeMutationAllowed !== readiness.summary?.canMutateRuntimeWithoutApproval) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: runtime activation approvals runtimeMutationAllowed is stale; expected ${readiness.summary?.canMutateRuntimeWithoutApproval}, found ${data.summary?.runtimeMutationAllowed}`
    );
    failures += 1;
  }
  if (data.summary?.approvalRequired !== packets.some((packet) => packet.requiresApproval)) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: runtime activation approvals approvalRequired is stale`
    );
    failures += 1;
  }

  for (const packet of packets) {
    if (packetByName.has(packet.name)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: duplicate runtime activation approval packet ${packet.name}`
      );
      failures += 1;
    }
    packetByName.set(packet.name, packet);

    const operation = planByName.get(packet.name);
    if (!operation) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: approval packet has no runtime activation operation ${packet.name}`
      );
      failures += 1;
      continue;
    }
    const preflightEntry = preflightByName.get(packet.name);
    if (!preflightEntry) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: approval packet has no preflight entry ${packet.name}`
      );
      failures += 1;
      continue;
    }

    failures += comparePacketString(filePath, packet.name, "operationType", operation.operationType, packet.operationType);
    failures += comparePacketString(filePath, packet.name, "planStatus", operation.status, packet.planStatus);
    failures += comparePacketString(filePath, packet.name, "preflightStatus", preflightEntry.status, packet.preflightStatus);
    failures += comparePacketString(filePath, packet.name, "approvalState", runtimeActivationApprovalState(operation, preflightEntry), packet.approvalState);
    failures += comparePacketString(filePath, packet.name, "riskLevel", runtimeActivationApprovalRisk(operation), packet.riskLevel);
    failures += comparePacketString(filePath, packet.name, "sourcePath", operation.sourcePath, packet.sourcePath);
    failures += comparePacketString(filePath, packet.name, "targetPath", operation.targetPath, packet.targetPath);
    failures += comparePacketString(filePath, packet.name, "sourceKind", preflightEntry.source?.kind, packet.sourceKind);
    failures += comparePacketString(filePath, packet.name, "targetKind", preflightEntry.target?.kind, packet.targetKind);
    if (operation.backupPath) {
      failures += comparePacketString(filePath, packet.name, "backupPath", operation.backupPath, packet.backupPath);
    }
    if (preflightEntry.backup?.kind) {
      failures += comparePacketString(filePath, packet.name, "backupKind", preflightEntry.backup.kind, packet.backupKind);
    }
    if (operation.runtimeLinkStrategy) {
      failures += comparePacketString(filePath, packet.name, "runtimeLinkStrategy", operation.runtimeLinkStrategy, packet.runtimeLinkStrategy);
    }
    if (operation.collisionPolicy) {
      failures += comparePacketString(filePath, packet.name, "collisionPolicy", operation.collisionPolicy, packet.collisionPolicy);
    }
    if (packet.evidence?.manifest !== operation.evidence?.manifest) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: approval packet ${packet.name} evidence manifest is stale; expected ${operation.evidence?.manifest}, found ${packet.evidence?.manifest}`
      );
      failures += 1;
    }
    failures += compareStringSet(
      filePath,
      `approval packet ${packet.name}`,
      "preflightReasons",
      preflightEntry.reasons ?? [],
      packet.preflightReasons ?? []
    );
    failures += compareStringSet(
      filePath,
      `approval packet ${packet.name}`,
      "stepPreviews",
      (operation.steps ?? []).map((step) => `${step.description}\u0000${step.commandPreview}`),
      (packet.stepPreviews ?? []).map((step) => `${step.description}\u0000${step.commandPreview}`)
    );
    failures += compareStringSet(
      filePath,
      `approval packet ${packet.name}`,
      "childCollisions",
      (preflightEntry.childCollisions ?? []).map(childCollisionKey),
      (packet.childCollisions ?? []).map(childCollisionKey)
    );
    failures += compareStringSet(
      filePath,
      `approval packet ${packet.name}`,
      "dependencyPackets",
      runtimeActivationDependencyPackets(preflightEntry.childCollisions ?? [], replacementDependencies).map(dependencyPacketKey),
      (packet.dependencyPackets ?? []).map(dependencyPacketKey)
    );
  }

  for (const operation of plan.operations ?? []) {
    if (!packetByName.has(operation.name)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: missing approval packet for runtime activation operation ${operation.name}`
      );
      failures += 1;
    }
  }

  return failures;
}

function validateRuntimeTrimExecutionReferences(filePath, data) {
  let failures = 0;
  const entries = data.entries ?? [];
  const summary = data.summary ?? {};
  failures += compareNumber(filePath, "runtime trim execution selectedPackets", entries.length, summary.selectedPackets);
  failures += compareNumber(
    filePath,
    "runtime trim execution dryRunEntries",
    entries.filter((entry) => entry.actionState === "DRY_RUN").length,
    summary.dryRunEntries
  );
  failures += compareNumber(
    filePath,
    "runtime trim execution appliedEntries",
    entries.filter((entry) => entry.actionState === "APPLIED").length,
    summary.appliedEntries
  );
  failures += compareNumber(
    filePath,
    "runtime trim execution skippedEntries",
    entries.filter((entry) => entry.actionState === "SKIPPED").length,
    summary.skippedEntries
  );
  failures += compareNumber(
    filePath,
    "runtime trim execution failedEntries",
    entries.filter((entry) => entry.actionState === "FAILED").length,
    summary.failedEntries
  );
  if (summary.mode === "APPLY" && summary.runtimeMutationAttempted !== true) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: runtime trim execution APPLY mode must set runtimeMutationAttempted`
    );
    failures += 1;
  }
  if (summary.mode === "DRY_RUN" && summary.runtimeMutationAttempted !== false) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: runtime trim execution DRY_RUN mode must not set runtimeMutationAttempted`
    );
    failures += 1;
  }
  for (const entry of entries) {
    if (entry.operationType === "REPLACE_SOURCE_WITH_SYMLINK" && !entry.backupPath) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: runtime trim execution ${entry.packetName} replacement entry requires backupPath`
      );
      failures += 1;
    }
    if (entry.operationType === "ACTIVATE_RUNTIME_LINK" && !entry.runtimeLinkStrategy) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: runtime trim execution ${entry.packetName} runtime link entry requires runtimeLinkStrategy`
      );
      failures += 1;
    }
  }
  return failures;
}

function childCollisionKey(collision) {
  return `${collision.name}\u0000${collision.sourcePath}\u0000${collision.targetPath}\u0000${collision.targetKind}`;
}

function dependencyPacketKey(dependency) {
  return `${dependency.name}\u0000${dependency.targetPath}\u0000${dependency.reason}`;
}

function runtimeActivationReplacementDependencyMap(plan) {
  const result = new Map();
  for (const operation of plan.operations ?? []) {
    if (operation.operationType !== "REPLACE_SOURCE_WITH_SYMLINK") {
      continue;
    }
    const dependency = {
      name: operation.name,
      targetPath: operation.targetPath,
      reason: "Approve and execute this replacement before retrying the child-link activation that collides with the same target path."
    };
    for (const key of runtimeTargetPathKeys(operation.targetPath)) {
      result.set(key, dependency);
    }
  }
  return result;
}

function runtimeActivationDependencyPackets(childCollisions, replacementDependencies) {
  const result = [];
  const seen = new Set();
  for (const collision of childCollisions) {
    for (const key of runtimeTargetPathKeys(collision.targetPath)) {
      const dependency = replacementDependencies.get(key);
      if (!dependency || seen.has(dependency.name)) {
        continue;
      }
      result.push(dependency);
      seen.add(dependency.name);
      break;
    }
  }
  return result.sort((left, right) => left.name.localeCompare(right.name));
}

function runtimeTargetPathKeys(value) {
  const keys = [value];
  try {
    if (fs.existsSync(value)) {
      keys.push(fs.realpathSync.native(value));
    }
  } catch {
    // Keep the raw path as the stable key when the filesystem cannot resolve it.
  }
  keys.push(path.resolve(value));
  return sortedUnique(keys);
}

function runtimeActivationApprovalState(operation, preflightEntry) {
  if (preflightEntry.status === "BLOCKED") {
    return "BLOCKED";
  }
  if (preflightEntry.status === "ALREADY_ACTIVE") {
    return "ALREADY_ACTIVE";
  }
  if (operation.operationType === "IMPORT_MARKETPLACE" && preflightEntry.status === "READY_FOR_APPROVAL") {
    return "READY_FOR_MANUAL_IMPORT";
  }
  if (preflightEntry.status === "READY_FOR_APPROVAL") {
    return "READY_FOR_APPROVAL";
  }
  return "REVIEW_REQUIRED";
}

function runtimeActivationApprovalRisk(operation) {
  if (operation.operationType === "REPLACE_SOURCE_WITH_SYMLINK") {
    return "HIGH";
  }
  if (operation.operationType === "ACTIVATE_RUNTIME_LINK") {
    return "MEDIUM";
  }
  return "LOW";
}

function comparePacketString(filePath, packetName, field, expected, actual) {
  if (expected === actual) {
    return 0;
  }
  console.error(
    `FAIL ${path.relative(repoRoot, filePath)}: approval packet ${packetName} ${field} is stale; expected ${expected}, found ${actual}`
  );
  return 1;
}

function sourceTurnoffReadinessStatus(allCanonicalRouted, reviewCompletenessOpen, sourceReviewOpen, digestReviewOpen, proposed, approved) {
  if (!allCanonicalRouted || reviewCompletenessOpen > 0 || sourceReviewOpen > 0 || digestReviewOpen > 0) {
    return "NOT_READY";
  }
  if (approved > 0 && proposed === 0) {
    return "APPROVED_FOR_EXECUTION";
  }
  return "REVIEW_READY";
}

function countStatus(manifest, status) {
  return (manifest.entries ?? []).filter((entry) => entry.status === status).length;
}

function countStatusNot(manifest, status) {
  return (manifest.entries ?? []).filter((entry) => entry.status !== status).length;
}

function countDecision(manifest, decision) {
  return (manifest.entries ?? []).filter((entry) => entry.decision === decision).length;
}

function compareNumber(filePath, label, expected, actual) {
  if (expected === actual) {
    return 0;
  }
  console.error(
    `FAIL ${path.relative(repoRoot, filePath)}: ${label} is stale; expected ${expected}, found ${actual}`
  );
  return 1;
}

function validateSourceReviewQueueEvidence(filePath, entry, queueItem) {
  const evidence = entry.queueEvidence ?? {};
  const key = sourceReviewKey(entry.target?.primitiveType, entry.target?.name);
  let failures = 0;

  if (evidence.queueType !== "NAME_REVIEW") {
    console.error(`FAIL ${path.relative(repoRoot, filePath)}: ${key} must reference NAME_REVIEW evidence`);
    failures += 1;
  }
  if (evidence.generatedAction !== queueItem.action) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: ${key} generatedAction is stale; expected ${queueItem.action}, found ${evidence.generatedAction}`
    );
    failures += 1;
  }
  if (evidence.priority !== queueItem.priority) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: ${key} priority is stale; expected ${queueItem.priority}, found ${evidence.priority}`
    );
    failures += 1;
  }
  if (evidence.entryCount !== queueItem.entries?.length) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: ${key} entryCount is stale; expected ${queueItem.entries?.length}, found ${evidence.entryCount}`
    );
    failures += 1;
  }

  failures += compareStringSet(filePath, key, "buckets", queueItem.buckets ?? [], evidence.buckets ?? []);
  failures += compareStringSet(
    filePath,
    key,
    "sourceRoots",
    sortedUnique((queueItem.entries ?? []).map((item) => item.sourceRoot)),
    evidence.sourceRoots ?? []
  );
  failures += compareStringSet(
    filePath,
    key,
    "sourceDigests",
    sortedUnique((queueItem.entries ?? []).map((item) => item.sha256)),
    evidence.sourceDigests ?? []
  );

  return failures;
}

function validateSourceReviewStatus(filePath, entry) {
  const needsReview = new Set(["DEFER", "REVIEW_REQUIRED", "REWRITE_FIRST"]);
  const expectedStatus = needsReview.has(entry.decision) ? "NEEDS_REVIEW" : "DECIDED";
  if (entry.status === expectedStatus) {
    return 0;
  }
  console.error(
    `FAIL ${path.relative(repoRoot, filePath)}: ${entry.target?.primitiveType} ${entry.target?.name} decision ${entry.decision} must have status ${expectedStatus}`
  );
  return 1;
}

function validateSourceReviewCoverage(filePath, entry) {
  let failures = 0;
  const coverageRequired = new Set(["COVERED_BY_CANONICAL", "KEEP_CANONICAL"]);
  if (coverageRequired.has(entry.decision) && !entry.canonicalCoverage?.length) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: ${entry.target?.primitiveType} ${entry.target?.name} decision ${entry.decision} requires canonicalCoverage`
    );
    failures += 1;
  }
  for (const coverage of entry.canonicalCoverage ?? []) {
    failures += requireLocalPath(
      filePath,
      coverage.path,
      `source-review ${entry.target?.primitiveType} ${entry.target?.name} canonicalCoverage`
    );
  }
  return failures;
}

function sourceReviewKey(type, name) {
  return `${type ?? ""}\u0000${name ?? ""}`;
}

function digestPath(filePath) {
  const hasher = crypto.createHash("sha256");
  const stat = fs.statSync(filePath);
  if (stat.isDirectory()) {
    for (const child of listDigestFiles(filePath)) {
      const relative = path.relative(filePath, child).split(path.sep).join(path.posix.sep);
      hasher.update(relative);
      hasher.update("\0");
      hasher.update(fs.readFileSync(child));
      hasher.update("\0");
    }
  } else {
    hasher.update(fs.readFileSync(filePath));
  }
  return `sha256:${hasher.digest("hex")}`;
}

function listDigestFiles(root) {
  const results = [];
  const skippedDirectories = new Set([".git", ".gradle", ".idea", "__pycache__", "build", "dist", "node_modules", "target"]);

  function visit(current) {
    const stat = fs.statSync(current);
    if (stat.isDirectory()) {
      if (skippedDirectories.has(path.basename(current))) {
        return;
      }
      for (const entry of fs.readdirSync(current).sort()) {
        visit(path.join(current, entry));
      }
      return;
    }
    if (stat.isFile() && !current.endsWith(".pyc") && !current.endsWith(".class")) {
      results.push(current);
    }
  }

  visit(root);
  return results.sort();
}

function compareStringSet(filePath, key, label, expectedValues, actualValues) {
  const expected = sortedUnique(expectedValues);
  const actual = sortedUnique(actualValues);
  if (expected.length === actual.length && expected.every((value, index) => value === actual[index])) {
    return 0;
  }
  console.error(
    `FAIL ${path.relative(repoRoot, filePath)}: ${key} ${label} are stale; expected [${expected.join(", ")}], found [${actual.join(", ")}]`
  );
  return 1;
}

function sortedUnique(values) {
  return [...new Set(values)].sort();
}

function validateFirstPartyPromotion(filePath, entry) {
  if (!hasFirstPartySource(entry)) {
    return 0;
  }
  const handling = entry.firstPartyHandling;
  if (!handling) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: promotion ${entry.name} uses first-party source material without firstPartyHandling`
    );
    return 1;
  }
  let failures = 0;
  if (handling.policy !== "RENAMED_AND_REWRITTEN" && handling.policy !== "INTENTIONAL_LOCAL_REPLACEMENT") {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: promotion ${entry.name} has unsupported firstPartyHandling.policy: ${handling.policy}`
    );
    failures += 1;
  }
  if (handling.policy === "RENAMED_AND_REWRITTEN" && !Array.isArray(handling.sourceNames)) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: promotion ${entry.name} must list firstPartyHandling.sourceNames when renamed and rewritten`
    );
    failures += 1;
  }
  if (handling.policy === "RENAMED_AND_REWRITTEN" && handling.sourceNames?.includes(entry.name)) {
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: promotion ${entry.name} still uses a first-party source name`
    );
    failures += 1;
  }
  return failures;
}

function validatePromotionAuditCoverage() {
  const promotionsPath = manifestPath("promotions.json");
  const auditsPath = manifestPath("primitive-audits.json");
  if (!fs.existsSync(promotionsPath) || !fs.existsSync(auditsPath)) {
    return 0;
  }

  const promotions = readJson(promotionsPath);
  const audits = readJson(auditsPath);
  const auditKeys = new Set();
  let failures = 0;

  for (const audit of audits.entries ?? []) {
    const primitive = audit.primitive ?? {};
    const key = primitiveKey(primitive.primitiveType, primitive.name);
    if (auditKeys.has(key)) {
      console.error(
        `FAIL garden/manifests/primitive-audits.json: duplicate audit entry for ${primitive.primitiveType} ${primitive.name}`
      );
      failures += 1;
    }
    auditKeys.add(key);
  }

  for (const promotion of promotions.entries ?? []) {
    const key = primitiveKey(promotion.type, promotion.name);
    if (!auditKeys.has(key)) {
      console.error(
        `FAIL garden/manifests/primitive-audits.json: missing audit entry for promoted ${promotion.type} ${promotion.name}`
      );
      failures += 1;
    }
  }

  return failures;
}

function primitiveKey(type, name) {
  return `${type ?? ""}\u0000${name ?? ""}`;
}

function hasFirstPartySource(entry) {
  return firstPartySourceLike(entry) || (entry.supportingSources ?? []).some(firstPartySourceLike);
}

function firstPartySourceLike(source) {
  const sourceRoot = source?.sourceRoot ?? "";
  const sourcePath = source?.sourcePath ?? "";
  const sourceResolvedPath = source?.sourceResolvedPath ?? "";
  return sourceRoot === "claude-plugins" ||
    sourcePath.includes(".claude/plugins") ||
    sourceResolvedPath.includes("/.claude/plugins/") ||
    sourcePath.startsWith("../apollo/skills/.system/") ||
    sourcePath.startsWith("~/.agents/skills/.system/") ||
    sourcePath.startsWith("~/.codex/skills/.system/") ||
    sourceResolvedPath.includes("/.agents/skills/.system/") ||
    sourceResolvedPath.includes("/.codex/skills/.system/") ||
    sourceResolvedPath.includes("/apollo/skills/.system/");
}

function validateFirstPartyCollisionPolicy() {
  const inventoryPath = manifestPath("discovered-primitives.json");
  if (!fs.existsSync(inventoryPath)) {
    return 0;
  }
  const inventory = readJson(inventoryPath);
  const nameGroups = new Map();
  const digestGroups = new Map();
  for (const entry of inventory.entries ?? []) {
    appendGroup(nameGroups, `${entry.type}\u0000${entry.name}`, entry);
    appendGroup(digestGroups, entry.sha256, entry);
  }

  let failures = 0;
  for (const entries of nameGroups.values()) {
    const canonical = entries.filter((entry) => entry.sourceRootRole === "canonical-candidate");
    if (canonical.length === 0 || !entries.some(inventoryFirstPartySourceLike)) {
      continue;
    }
    const [{ type, name }] = entries;
    console.error(
      `FAIL garden/manifests/discovered-primitives.json: canonical ${type} ${name} collides with first-party installed/system source name`
    );
    failures += 1;
  }
  for (const entries of digestGroups.values()) {
    const canonical = entries.filter((entry) => entry.sourceRootRole === "canonical-candidate");
    const firstParty = entries.filter(inventoryFirstPartySourceLike);
    if (canonical.length === 0 || firstParty.length === 0) {
      continue;
    }
    console.error(
      `FAIL garden/manifests/discovered-primitives.json: canonical primitive content digest matches first-party installed/system source: ${entries[0].sha256}`
    );
    failures += 1;
  }
  return failures;
}

function appendGroup(groups, key, entry) {
  if (!groups.has(key)) {
    groups.set(key, []);
  }
  groups.get(key).push(entry);
}

function inventoryFirstPartySourceLike(entry) {
  const sourceRoot = entry?.sourceRoot ?? "";
  const sourceRootRole = entry?.sourceRootRole ?? "";
  const entryPath = entry?.path ?? "";
  return (sourceRoot === "claude-plugins" && (entryPath.startsWith("cache/") || entryPath.startsWith("marketplaces/"))) ||
    sourceRoot === "claude-plugins" ||
    (sourceRootRole === "runtime-source" && entryPath.startsWith(".system/")) ||
    (sourceRoot === "apollo-skills" && entryPath.startsWith(".system/")) ||
    (sourceRoot === "global-codex-skills" && entryPath.startsWith(".system/")) ||
    (sourceRoot === "global-agent-skills" && entryPath.startsWith(".system/"));
}

function validatePrimitiveReference(filePath, primitive) {
  let failures = requirePrimitivePath(filePath, primitive);
  for (const dependency of primitive?.dependsOn ?? []) {
    failures += requirePrimitivePath(filePath, dependency);
  }
  return failures;
}

function requirePrimitivePath(filePath, primitive) {
  if (primitive?.source?.type !== "LOCAL_SOURCE") {
    return 0;
  }
  return requireLocalPath(filePath, primitive.path, `${primitive.type} ${primitive.name}`);
}

function isPrimitiveReference(data) {
  return ["SKILL", "AGENT", "INSTRUCTION", "HOOK"].includes(data?.type);
}

function requireLocalPath(filePath, localPath, label) {
  if (!localPath) {
    return 0;
  }
  const target = path.resolve(repoRoot, localPath);
  if (fs.existsSync(target)) {
    return 0;
  }
  console.error(`FAIL ${path.relative(repoRoot, filePath)}: missing ${label} path: ${localPath}`);
  return 1;
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function validateSchemaDocuments() {
  let failures = 0;
  const profileSchemaPath = path.join(
    repoRoot,
    "skills",
    "manage-json-schemas",
    "references",
    "schema-profile",
    "schema-profile.schema.json"
  );
  const profileSchema = readJson(profileSchemaPath);
  const validateProfile = ajv.compile(profileSchema);

  for (const schemaPath of listJsonFilesRecursive(repoRoot).filter((file) => file.endsWith(".schema.json"))) {
    const schema = readJson(schemaPath);
    let ok = true;
    if (schema.$schema === "../../schema-profile/schema-profile.schema.json") {
      ok = validateProfile(schema);
      if (!ok) {
        console.error(`FAIL ${path.relative(repoRoot, schemaPath)}`);
        console.error(JSON.stringify(validateProfile.errors, null, 2));
      }
    } else {
      const validator = schema.$schema === "http://json-schema.org/draft-07/schema#" ? draft7Ajv : ajv;
      ok = validator.validateSchema(schema);
      if (!ok) {
        console.error(`FAIL ${path.relative(repoRoot, schemaPath)}`);
        console.error(JSON.stringify(validator.errors, null, 2));
      }
    }

    if (ok) {
      markValidated(schemaPath);
      console.log(`OK ${path.relative(repoRoot, schemaPath)}`);
    } else {
      failures += 1;
    }
  }
  return failures;
}

function validateJsonCoverage() {
  let failures = 0;
  for (const filePath of listJsonFilesRecursive(repoRoot)) {
    if (validatedJsonFiles.has(normalizePath(filePath))) {
      continue;
    }
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: JSON file is not covered by a schema validation path`
    );
    failures += 1;
  }
  return failures;
}

function listJsonFilesRecursive(directory) {
  const results = [];
  const skippedDirectories = new Set([".git", ".idea", ".agent-turn", ".migration-backups", "dist", "node_modules"]);

  function visit(current) {
    if (!fs.existsSync(current)) {
      return;
    }
    const stat = fs.lstatSync(current);
    if (stat.isSymbolicLink()) {
      return;
    }
    if (stat.isDirectory()) {
      const name = path.basename(current);
      if (skippedDirectories.has(name)) {
        return;
      }
      for (const entry of fs.readdirSync(current).sort()) {
        visit(path.join(current, entry));
      }
      return;
    }
    if (stat.isFile() && current.endsWith(".json")) {
      results.push(current);
    }
  }

  visit(directory);
  return results.sort();
}

function markValidated(filePath) {
  validatedJsonFiles.add(normalizePath(filePath));
}

function normalizePath(filePath) {
  return path.resolve(filePath);
}
