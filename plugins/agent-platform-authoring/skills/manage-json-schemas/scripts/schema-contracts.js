#!/usr/bin/env node

const fs = require("node:fs");
const path = require("node:path");

const DRAFT = "https://json-schema.org/draft/2020-12/schema";
const CAPS_CASE = /^[A-Z][A-Z0-9_]*$/;
const BUNDLED_PROFILE_PATH = path.join(__dirname, "../references/schema-profile/schema-profile.schema.json");
const FIXED_CONTEXT = Object.freeze({
  fileTree: Object.freeze({
    layout: "HIERARCHY_DIRECTORY",
    directoryCase: "KEBAB_CASE",
    filenameCase: "KEBAB_CASE",
    schemaExtension: ".schema.json",
    rootSchemaName: "MATCH_PARENT_DIRECTORY",
  }),
  hierarchy: Object.freeze({
    requireRootSchema: true,
    requireNestedKey: true,
    requireNestedKeyInRequired: true,
    requireRootUnionReferences: true,
    rejectRepeatedParentPrefixInVariantFilename: true,
    rejectRepeatedParentPrefixInVariantType: true,
  }),
});

function main() {
  const [command, ...args] = process.argv.slice(2);
  try {
    if (!command || command === "help" || command === "--help" || command === "-h") {
      printHelp();
      return;
    }
    if (command === "init") return initSchema(parseArgs(args));
    if (command === "extract") return extractSchema(parseArgs(args));
    if (command === "profile") return checkProfileCommand(parseArgs(args));
    if (command === "policy") return checkPolicyCommand(parseArgs(args));
    if (command === "policy-tree") return checkPolicyTreeCommand(parseArgs(args));
    if (command === "validate") return validateCommand(args);
    fail(`Unknown command: ${command}`);
  } catch (error) {
    fail(error.message);
  }
}

function printHelp() {
  console.log(`Usage:
  schema-contracts.js init --name CAPS_CASE --out path/to/schema.json
  schema-contracts.js extract --sample sample.json --name CAPS_CASE --out path/to/schema.json
  schema-contracts.js profile --schema path/to/schema.json
  schema-contracts.js policy --schema path/to/schema.json
  schema-contracts.js policy-tree --root schemas
  schema-contracts.js validate --schema path/to/schema.json --data sample.json

Policy:
  Schema syntax is constrained by this skill's bundled schema profile.
  Project-local schema policy and profile overrides are not supported.
  Discriminator field is "type".
  Discriminator schemas use enum, not const.
  Discriminator values use CAPS_CASE.
  Schema directories mirror semantic hierarchies.
  Root schemas use <node>/<node>.schema.json.
  Variant schemas use <node>/<variant>.schema.json.
  Schemas include granular examples on objects, properties, arrays, and items.
  Ajv is the validation engine for validate.`);
}

function initSchema(options) {
  const name = required(options, "name");
  const out = required(options, "out");
  assertCapsCase(name, "name");
  const schema = objectSchema(name, {
    type: { type: "string", enum: [name] },
  }, ["type"]);
  assertSchemaProfile(schema, out);
  assertSchemaPolicy(schema);
  writeJson(out, schema);
  console.log(`Wrote ${out}`);
}

function extractSchema(options) {
  const samplePath = required(options, "sample");
  const name = required(options, "name");
  const out = required(options, "out");
  assertCapsCase(name, "name");
  const sample = readJson(samplePath);
  const inferred = inferSchema(sample, ["#"]);
  const schema = {
    $schema: DRAFT,
    $id: normalizeId(out),
    title: name,
    ...inferred,
  };
  if (schema.type === "object") {
    schema.properties = schema.properties || {};
    schema.properties.type = { type: "string", enum: [name] };
    schema.required = Array.from(new Set([...(schema.required || []), "type"])).sort();
    schema.additionalProperties = false;
    addExamples(schema, { ...sample, type: name });
  }
  assertSchemaProfile(schema, out);
  assertSchemaPolicy(schema);
  writeJson(out, schema);
  console.log(`Wrote ${out}`);
}

function checkPolicyCommand(options) {
  const schemaPath = required(options, "schema");
  rejectOptions(options, ["profile", "policy", "policy-schema"]);
  const schema = readJson(schemaPath);
  assertSchemaProfile(schema, schemaPath);
  assertSchemaPolicy(schema);
  console.log(`Policy OK: ${schemaPath}`);
}

function checkProfileCommand(options) {
  const schemaPath = required(options, "schema");
  rejectOptions(options, ["profile", "policy", "policy-schema"]);
  const schema = readJson(schemaPath);
  assertSchemaProfile(schema, schemaPath);
  console.log(`Profile OK: ${schemaPath}`);
}

function checkPolicyTreeCommand(options) {
  rejectOptions(options, ["profile", "policy", "policy-schema"]);
  const context = FIXED_CONTEXT;
  const root = path.resolve(required(options, "root"));
  const files = walkSchemaFiles(root);
  if (files.length === 0) throw new Error(`No .schema.json files found under ${root}`);
  const errors = [];
  const hierarchies = new Map();

  for (const schemaPath of files) {
    let schema;
    try {
      schema = readJson(schemaPath);
      assertSchemaProfile(schema, schemaPath);
      assertSchemaPolicy(schema);
    } catch (error) {
      errors.push(`${schemaPath}: ${error.message}`);
      continue;
    }
    const relativePath = normalizePath(path.relative(root, schemaPath));
    const parts = relativePath.split("/");
    if (context.fileTree.layout === "HIERARCHY_DIRECTORY" && parts.length !== 2) {
      errors.push(`${relativePath}: schema files must live at schemas/<node>/<type>.schema.json`);
      continue;
    }
    const [parentDir, filename] = parts;
    const schemaName = filename.replace(/\.schema\.json$/, "");
    if (!filename.endsWith(context.fileTree.schemaExtension)) {
      errors.push(`${relativePath}: schema filename must end with ${context.fileTree.schemaExtension}`);
      continue;
    }
    if (context.fileTree.directoryCase === "KEBAB_CASE" && !isKebabCase(parentDir)) {
      errors.push(`${relativePath}: hierarchy directory must be kebab-case`);
    }
    if (context.fileTree.filenameCase === "KEBAB_CASE" && !isKebabCase(schemaName)) {
      errors.push(`${relativePath}: schema filename must be kebab-case`);
    }

    const hierarchy = ensureHierarchy(hierarchies, parentDir);
    const typeEnums = discriminatorEnums(schema);
    const isRootSchema = schemaName === parentDir;
    const expectedParentType = kebabToCaps(parentDir);
    const expectedSchemaType = kebabToCaps(schemaName);

    if (isRootSchema) {
      hierarchy.rootSchema = { schemaPath, schema, relativePath };
      if (!typeEnums.includes(expectedParentType)) {
        errors.push(`${relativePath}: root schema discriminator should be ${expectedParentType}`);
      }
    } else {
      hierarchy.variants.push({ schemaPath, schema, relativePath, schemaName });
      if (context.hierarchy.rejectRepeatedParentPrefixInVariantFilename && schemaName.startsWith(`${parentDir}-`)) {
        errors.push(`${relativePath}: child schema filename repeats parent prefix; use ${schemaName.slice(parentDir.length + 1)}.schema.json`);
      }
      if (typeEnums.length > 0 && !typeEnums.includes(expectedSchemaType)) {
        errors.push(`${relativePath}: variant discriminator should include ${expectedSchemaType}`);
      }
      for (const value of typeEnums) {
        if (context.hierarchy.rejectRepeatedParentPrefixInVariantType && value.startsWith(`${expectedParentType}_`)) {
          errors.push(`${relativePath}: variant discriminator ${value} repeats parent prefix; use parent type ${expectedParentType} plus nested ${camelCaseFromCaps(expectedParentType)}.type`);
        }
      }
    }
  }

  for (const [parentDir, hierarchy] of hierarchies) {
    if (hierarchy.variants.length === 0) continue;
    const expectedParentType = kebabToCaps(parentDir);
    const nestedKey = camelCaseFromCaps(expectedParentType);
    if (!hierarchy.rootSchema && context.hierarchy.requireRootSchema) {
      errors.push(`${parentDir}: missing root schema ${parentDir}/${parentDir}.schema.json`);
      continue;
    }
    if (!hierarchy.rootSchema) continue;
    const schema = hierarchy.rootSchema.schema;
    if (context.hierarchy.requireNestedKey && (!schema.properties || !schema.properties[nestedKey])) {
      errors.push(`${hierarchy.rootSchema.relativePath}: missing nested hierarchy key "${nestedKey}"`);
    }
    if (context.hierarchy.requireNestedKeyInRequired && (!Array.isArray(schema.required) || !schema.required.includes(nestedKey))) {
      errors.push(`${hierarchy.rootSchema.relativePath}: required must include nested hierarchy key "${nestedKey}"`);
    }
    const nested = schema.properties && schema.properties[nestedKey];
    const refs = collectRefs(nested);
    if (context.hierarchy.requireRootUnionReferences) {
      if (refs.length === 0) {
        errors.push(`${hierarchy.rootSchema.relativePath}: nested "${nestedKey}" union should reference child variant schemas`);
      }
      for (const variant of hierarchy.variants) {
        const expectedRef = `${variant.schemaName}.schema.json`;
        if (!refs.some((ref) => ref === expectedRef || ref.endsWith(`/${expectedRef}`))) {
          errors.push(`${hierarchy.rootSchema.relativePath}: nested "${nestedKey}" union should reference ${expectedRef}`);
        }
      }
    }
  }

  if (errors.length > 0) throw new Error(`Schema tree policy failed:\n${errors.map((error) => `- ${error}`).join("\n")}`);
  console.log(`Schema tree policy OK: ${root} (${files.length} files)`);
}

function validateCommand(rawArgs) {
  const options = parseArgs(rawArgs);
  const schemaPath = required(options, "schema");
  const dataPath = required(options, "data");
  rejectOptions(options, ["profile", "policy", "policy-schema"]);
  const schema = readJson(schemaPath);
  assertSchemaProfile(schema, schemaPath);
  assertSchemaPolicy(schema);
  const validator = validatorFor(schema);
  addRelatedSchemas(validator, schemaPath);
  const validate = validator.compile(schema);
  const data = readJson(dataPath);
  if (validate(data)) {
    console.log(`Validation OK: ${dataPath}`);
    return;
  }
  throw new Error(`Validation failed: ${JSON.stringify(validate.errors, null, 2)}`);
}

function assertSchemaProfile(schema, schemaPath) {
  const profile = readBundledProfile();
  const allowedKeywords = allowedProfileKeywords(profile);
  const allowedSchemaUris = allowedProfileSchemaUris(profile);
  const errors = [];
  if (!schema || typeof schema !== "object" || Array.isArray(schema)) {
    throw new Error("Schema profile failed:\n- #: schema document must be an object");
  }
  if (!allowedSchemaUris.includes(schema.$schema)) {
    errors.push(`#: $schema must be one of ${allowedSchemaUris.join(", ")}`);
  }
  if (typeof schema.$id !== "string" || schema.$id.length === 0) {
    errors.push("#: $id must be a non-empty string");
  }
  if (typeof schema.title !== "string" || schema.title.length === 0) {
    errors.push("#: title must be a non-empty string");
  }
  if (schema.type !== "object") {
    errors.push("#: top-level schema type must be object");
  }
  visitProfileNode(schema, "#", allowedKeywords, errors);
  if (errors.length > 0) {
    throw new Error(`Schema profile failed:\n${errors.map((error) => `- ${schemaPath}${error}`).join("\n")}`);
  }
}

function readBundledProfile() {
  if (!fs.existsSync(BUNDLED_PROFILE_PATH)) {
    throw new Error(`Missing bundled schema profile: ${BUNDLED_PROFILE_PATH}`);
  }
  return readJson(BUNDLED_PROFILE_PATH);
}

function allowedProfileKeywords(profile) {
  const properties = profile && profile.$defs && profile.$defs.schemaNode && profile.$defs.schemaNode.properties;
  if (!properties || typeof properties !== "object") {
    throw new Error(`Bundled schema profile is missing $defs.schemaNode.properties: ${BUNDLED_PROFILE_PATH}`);
  }
  return new Set(Object.keys(properties));
}

function allowedProfileSchemaUris(profile) {
  const values = profile && profile.properties && profile.properties.$schema && profile.properties.$schema.enum;
  if (!Array.isArray(values) || values.length === 0 || !values.every((value) => typeof value === "string")) {
    throw new Error(`Bundled schema profile is missing properties.$schema.enum: ${BUNDLED_PROFILE_PATH}`);
  }
  return values;
}

function visitProfileNode(schema, pointer, allowedKeywords, errors) {
  if (!schema || typeof schema !== "object" || Array.isArray(schema)) {
    errors.push(`${pointer}: schema node must be an object`);
    return;
  }
  for (const key of Object.keys(schema)) {
    if (!allowedKeywords.has(key)) errors.push(`${pointer}: unsupported schema keyword "${key}"`);
  }
  if (Object.prototype.hasOwnProperty.call(schema, "type") && typeof schema.type !== "string") {
    errors.push(`${pointer}/type: type must be a string`);
  }
  if (schema.type === "object" && schema.additionalProperties !== false) {
    errors.push(`${pointer}: object schemas must set additionalProperties to false`);
  }
  if (schema.type === "object") {
    if (!schema.properties || typeof schema.properties !== "object" || Array.isArray(schema.properties)) {
      errors.push(`${pointer}: object schemas must define properties.type`);
    } else if (!schema.properties.type || typeof schema.properties.type !== "object" || Array.isArray(schema.properties.type)) {
      errors.push(`${pointer}/properties: object schemas must define discriminator property "type"`);
    } else {
      const typeSchema = schema.properties.type;
      if (typeSchema.type !== "string") {
        errors.push(`${pointer}/properties/type/type: discriminator property schema type must be string`);
      }
      if (!Array.isArray(typeSchema.enum) || typeSchema.enum.length === 0) {
        errors.push(`${pointer}/properties/type/enum: discriminator property must define a non-empty enum`);
      }
    }
    if (!Array.isArray(schema.required) || !schema.required.includes("type")) {
      errors.push(`${pointer}/required: object schemas must require discriminator property "type"`);
    }
  }
  if ((schema.oneOf || schema.anyOf) && !hasTypeDiscriminator(schema)) {
    errors.push(`${pointer}/discriminator: union schemas must declare discriminator.propertyName "type"`);
  }
  if (Object.prototype.hasOwnProperty.call(schema, "discriminator") && !hasTypeDiscriminator(schema)) {
    errors.push(`${pointer}/discriminator: discriminator must be { "propertyName": "type" }`);
  }
  if (schema.required && (!Array.isArray(schema.required) || !schema.required.every((value) => typeof value === "string"))) {
    errors.push(`${pointer}/required: required must be an array of strings`);
  }
  if (schema.examples && !Array.isArray(schema.examples)) {
    errors.push(`${pointer}/examples: examples must be an array`);
  }
  if (schema.enum && (!Array.isArray(schema.enum) || schema.enum.length === 0)) {
    errors.push(`${pointer}/enum: enum must be a non-empty array`);
  }
  for (const key of ["$id", "$schema", "$ref", "title", "description", "format", "pattern"]) {
    if (Object.prototype.hasOwnProperty.call(schema, key) && typeof schema[key] !== "string") {
      errors.push(`${pointer}/${key}: ${key} must be a string`);
    }
  }
  for (const key of ["minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems"]) {
    if (Object.prototype.hasOwnProperty.call(schema, key) && typeof schema[key] !== "number") {
      errors.push(`${pointer}/${key}: ${key} must be a number`);
    }
  }
  for (const key of ["uniqueItems", "additionalProperties", "additionalItems"]) {
    if (Object.prototype.hasOwnProperty.call(schema, key) && typeof schema[key] !== "boolean") {
      errors.push(`${pointer}/${key}: ${key} must be a boolean`);
    }
  }
  for (const key of ["oneOf", "anyOf", "allOf"]) {
    if (!Object.prototype.hasOwnProperty.call(schema, key)) continue;
    if (!Array.isArray(schema[key]) || schema[key].length === 0) {
      errors.push(`${pointer}/${key}: ${key} must be a non-empty array`);
      continue;
    }
    schema[key].forEach((branch, index) => visitProfileNode(branch, `${pointer}/${key}/${index}`, allowedKeywords, errors));
  }
  if (schema.properties) {
    if (typeof schema.properties !== "object" || Array.isArray(schema.properties)) {
      errors.push(`${pointer}/properties: properties must be an object`);
    } else {
      for (const [key, value] of Object.entries(schema.properties)) {
        visitProfileNode(value, `${pointer}/properties/${key}`, allowedKeywords, errors);
      }
    }
  }
  if (schema.$defs) visitSchemaMap(schema.$defs, `${pointer}/$defs`, allowedKeywords, errors);
  if (schema.definitions) visitSchemaMap(schema.definitions, `${pointer}/definitions`, allowedKeywords, errors);
  if (schema.items) visitProfileNode(schema.items, `${pointer}/items`, allowedKeywords, errors);
}

function hasTypeDiscriminator(schema) {
  if (!schema || typeof schema !== "object" || Array.isArray(schema)) return false;
  const discriminator = schema.discriminator;
  if (!discriminator || typeof discriminator !== "object" || Array.isArray(discriminator)) return false;
  const keys = Object.keys(discriminator);
  return keys.length === 1 && discriminator.propertyName === "type";
}

function visitSchemaMap(value, pointer, allowedKeywords, errors) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    errors.push(`${pointer}: must be an object`);
    return;
  }
  for (const [key, child] of Object.entries(value)) {
    visitProfileNode(child, `${pointer}/${key}`, allowedKeywords, errors);
  }
}

function inferSchema(value, pointer) {
  if (value === null) return { type: "null", examples: [null] };
  if (Array.isArray(value)) {
    return {
      type: "array",
      items: value.length === 0 ? {} : mergeSchemas(value.map((item, index) => inferSchema(item, pointer.concat(String(index))))),
      additionalItems: false,
      examples: [value],
    };
  }
  if (typeof value === "object") {
    const entries = Object.entries(value);
    const properties = {};
    for (const [key, child] of entries) {
      properties[key] = inferSchema(child, pointer.concat(key));
    }
    const schema = {
      type: "object",
      properties,
      required: entries.map(([key]) => key).sort(),
      additionalProperties: false,
      examples: [value],
    };
    if (Object.prototype.hasOwnProperty.call(value, "type")) {
      if (typeof value.type !== "string") {
        throw new Error(`${pointer.join("/")}/type must be a string discriminator`);
      }
      assertCapsCase(value.type, `${pointer.join("/")}/type`);
      schema.properties.type = { type: "string", enum: [value.type] };
    }
    return schema;
  }
  if (typeof value === "string") {
    return value.length === 0
      ? { type: "string", examples: [value] }
      : { type: "string", minLength: 1, examples: [value] };
  }
  if (typeof value === "boolean") return { type: "boolean", examples: [value] };
  if (Number.isInteger(value)) return { type: "integer", examples: [value] };
  if (typeof value === "number") return { type: "number", examples: [value] };
  throw new Error(`Unsupported JSON value at ${pointer.join("/")}`);
}

function mergeSchemas(schemas) {
  const [first, ...rest] = schemas;
  return rest.reduce(mergeTwoSchemas, first);
}

function mergeTwoSchemas(left, right) {
  if (left.type !== right.type) return { anyOf: uniqueSchemas([left, right]) };
  if (left.type === "object") {
    const keys = Array.from(new Set([...Object.keys(left.properties || {}), ...Object.keys(right.properties || {})])).sort();
    const properties = {};
    for (const key of keys) {
      if (left.properties && right.properties && left.properties[key] && right.properties[key]) {
        properties[key] = mergeTwoSchemas(left.properties[key], right.properties[key]);
      } else {
        properties[key] = (left.properties && left.properties[key]) || (right.properties && right.properties[key]);
      }
    }
    const required = (left.required || []).filter((key) => (right.required || []).includes(key)).sort();
    return { type: "object", properties, required, additionalProperties: false };
  }
  if (left.type === "array") {
    return { type: "array", items: mergeTwoSchemas(left.items || {}, right.items || {}), additionalItems: false };
  }
  return JSON.stringify(left) === JSON.stringify(right) ? left : { anyOf: uniqueSchemas([left, right]) };
}

function uniqueSchemas(schemas) {
  const seen = new Set();
  return schemas.filter((schema) => {
    const key = JSON.stringify(schema);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function objectSchema(name, properties, requiredFields) {
  const schema = {
    $schema: DRAFT,
    $id: `${name.toLowerCase().replaceAll("_", "-")}.schema.json`,
    title: name,
    type: "object",
    properties,
    required: requiredFields,
    additionalProperties: false,
  };
  addExamples(schema, exampleForSchema(schema));
  return schema;
}

function assertSchemaPolicy(schema) {
  const errors = [];
  visitSchema(schema, "#", errors);
  if (errors.length > 0) throw new Error(`Schema policy failed:\n${errors.map((error) => `- ${error}`).join("\n")}`);
}

function visitSchema(schema, pointer, errors) {
  if (!schema || typeof schema !== "object" || Array.isArray(schema)) return;
  if (shouldHaveExamples(schema) && !Array.isArray(schema.examples)) {
    errors.push(`${pointer}: add granular examples for this schema node`);
  }
  if (schema.type === "object" && schema.properties && schema.properties.type) {
    const typeSchema = schema.properties.type;
    if (!Array.isArray(schema.required) || !schema.required.includes("type")) {
      errors.push(`${pointer}: object with discriminator property must require "type"`);
    }
    if (Object.prototype.hasOwnProperty.call(typeSchema, "const")) {
      errors.push(`${pointer}/properties/type: use enum, not const`);
    }
    if (!Array.isArray(typeSchema.enum) || typeSchema.enum.length === 0) {
      errors.push(`${pointer}/properties/type: discriminator must define a non-empty enum`);
    } else {
      for (const value of typeSchema.enum) {
        if (typeof value !== "string" || !CAPS_CASE.test(value)) {
          errors.push(`${pointer}/properties/type: discriminator value must be CAPS_CASE: ${String(value)}`);
        }
      }
    }
  }
  for (const key of ["oneOf", "anyOf"]) {
    if (Array.isArray(schema[key])) {
      schema[key].forEach((branch, index) => visitSchema(branch, `${pointer}/${key}/${index}`, errors));
    }
  }
  if (schema.allOf) schema.allOf.forEach((branch, index) => visitSchema(branch, `${pointer}/allOf/${index}`, errors));
  if (schema.$defs) {
    for (const [key, value] of Object.entries(schema.$defs)) visitSchema(value, `${pointer}/$defs/${key}`, errors);
  }
  if (schema.definitions) {
    for (const [key, value] of Object.entries(schema.definitions)) visitSchema(value, `${pointer}/definitions/${key}`, errors);
  }
  if (schema.properties) {
    for (const [key, value] of Object.entries(schema.properties)) visitSchema(value, `${pointer}/properties/${key}`, errors);
  }
  if (schema.items && typeof schema.items === "object") visitSchema(schema.items, `${pointer}/items`, errors);
}

function shouldHaveExamples(schema) {
  if (schema.$ref) return false;
  if (schema.oneOf || schema.anyOf || schema.allOf) return true;
  return Boolean(schema.type || schema.enum);
}

function walkSchemaFiles(root) {
  const results = [];
  function walk(current) {
    if (!fs.existsSync(current)) throw new Error(`Missing root: ${root}`);
    const stat = fs.statSync(current);
    if (stat.isDirectory()) {
      for (const entry of fs.readdirSync(current).sort()) walk(path.join(current, entry));
      return;
    }
    if (stat.isFile() && current.endsWith(".schema.json")) results.push(current);
  }
  walk(root);
  return results.sort();
}

function addRelatedSchemas(validator, schemaPath) {
  const root = schemaRootFor(schemaPath);
  if (!root || !fs.existsSync(root)) return;
  for (const relatedPath of walkSchemaFiles(root)) {
    if (path.resolve(relatedPath) === path.resolve(schemaPath)) continue;
    validator.addSchema(readJson(relatedPath));
  }
}

function schemaRootFor(schemaPath) {
  const parts = path.resolve(schemaPath).split(path.sep);
  const schemaIndex = parts.lastIndexOf("schemas");
  if (schemaIndex === -1) return path.dirname(schemaPath);
  return parts.slice(0, schemaIndex + 1).join(path.sep) || path.sep;
}

function ensureHierarchy(hierarchies, parentDir) {
  if (!hierarchies.has(parentDir)) hierarchies.set(parentDir, { rootSchema: null, variants: [] });
  return hierarchies.get(parentDir);
}

function discriminatorEnums(schema) {
  if (!schema || typeof schema !== "object" || Array.isArray(schema)) return [];
  if (schema.type === "object" && schema.properties && schema.properties.type) {
    const typeSchema = schema.properties.type;
    if (Array.isArray(typeSchema.enum)) return typeSchema.enum.filter((value) => typeof value === "string");
  }
  return [];
}

function collectRefs(schema) {
  const refs = [];
  function visit(value) {
    if (!value || typeof value !== "object") return;
    if (Array.isArray(value)) {
      value.forEach(visit);
      return;
    }
    if (typeof value.$ref === "string") refs.push(value.$ref);
    for (const child of Object.values(value)) visit(child);
  }
  visit(schema);
  return refs;
}

function isKebabCase(value) {
  return /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value);
}

function kebabToCaps(value) {
  return value.toUpperCase().replaceAll("-", "_");
}

function camelCaseFromCaps(value) {
  return value
    .toLowerCase()
    .split("_")
    .map((part, index) => index === 0 ? part : `${part.charAt(0).toUpperCase()}${part.slice(1)}`)
    .join("");
}

function addExamples(schema, example) {
  if (example === undefined) return schema;
  schema.examples = [example];
  if (schema.type === "object" && schema.properties && example && typeof example === "object" && !Array.isArray(example)) {
    for (const [key, childSchema] of Object.entries(schema.properties)) {
      if (childSchema && typeof childSchema === "object" && !Array.isArray(childSchema)) {
        addExamples(childSchema, Object.prototype.hasOwnProperty.call(example, key) ? example[key] : exampleForSchema(childSchema));
      }
    }
  }
  if (schema.type === "array" && schema.items && typeof schema.items === "object" && Array.isArray(example) && example.length > 0) {
    addExamples(schema.items, example[0]);
  }
  return schema;
}

function exampleForSchema(schema) {
  if (!schema || typeof schema !== "object") return undefined;
  if (Array.isArray(schema.enum) && schema.enum.length > 0) return schema.enum[0];
  if (schema.type === "string") return schema.pattern ? "EXAMPLE" : "example";
  if (schema.type === "integer") return Math.max(schema.minimum || 0, 1);
  if (schema.type === "number") return Math.max(schema.minimum || 0, 1);
  if (schema.type === "boolean") return true;
  if (schema.type === "null") return null;
  if (schema.type === "array") {
    const item = exampleForSchema(schema.items || {});
    return item === undefined ? [] : [item];
  }
  if (schema.type === "object") {
    const example = {};
    for (const key of schema.required || Object.keys(schema.properties || {})) {
      if (schema.properties && schema.properties[key]) {
        example[key] = exampleForSchema(schema.properties[key]);
      }
    }
    return example;
  }
  return undefined;
}

function parseArgs(args) {
  const parsed = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (!arg.startsWith("--")) throw new Error(`Unexpected argument: ${arg}`);
    const key = arg.slice(2);
    const value = args[index + 1];
    if (!value || value.startsWith("--")) throw new Error(`Missing value for --${key}`);
    parsed[key] = value;
    index += 1;
  }
  return parsed;
}

function required(options, key) {
  if (!options[key]) throw new Error(`Missing --${key}`);
  return options[key];
}

function rejectOptions(options, keys) {
  for (const key of keys) {
    if (Object.prototype.hasOwnProperty.call(options, key)) {
      throw new Error(`--${key} is not supported; this skill uses its bundled schema profile and fixed policy`);
    }
  }
}

function assertCapsCase(value, label) {
  if (!CAPS_CASE.test(value)) throw new Error(`${label} must be CAPS_CASE`);
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function validatorFor(schema) {
  const uri = String(schema.$schema || "");
  const options = {
    allErrors: true,
    strict: false,
    validateSchema: true,
  };
  const addFormats = loadModule("ajv-formats");
  if (uri.includes("draft-04")) {
    const AjvDraft04 = loadModule("ajv-draft-04");
    return addFormats(new AjvDraft04(options));
  }
  if (uri.includes("2020-12")) {
    const Ajv2020 = loadModule("ajv/dist/2020");
    return addFormats(new Ajv2020(options));
  }
  const Ajv = loadModule("ajv");
  return addFormats(new Ajv(options));
}

function loadModule(name) {
  try {
    return require(require.resolve(name, { paths: [process.cwd(), __dirname] }));
  } catch (error) {
    throw new Error(`Missing ${name}. Install repo schema tooling with npm install.`);
  }
}

function writeJson(filePath, value) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`);
}

function normalizeId(filePath) {
  return filePath.split(path.sep).join("/");
}

function normalizePath(filePath) {
  return filePath.split(path.sep).join("/");
}

function fail(message) {
  console.error(message);
  process.exit(1);
}

main();
