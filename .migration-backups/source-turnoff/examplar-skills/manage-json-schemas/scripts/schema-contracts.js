#!/usr/bin/env node

const fs = require("node:fs");
const path = require("node:path");

const DRAFT = "https://json-schema.org/draft/2020-12/schema";
const CAPS_CASE = /^[A-Z][A-Z0-9_]*$/;

function main() {
  const [command, ...args] = process.argv.slice(2);
  try {
    if (!command || command === "help" || command === "--help" || command === "-h") {
      printHelp();
      return;
    }
    if (command === "init") return initSchema(parseArgs(args));
    if (command === "extract") return extractSchema(parseArgs(args));
    if (command === "policy") return checkPolicyCommand(parseArgs(args));
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
  schema-contracts.js policy --schema path/to/schema.json
  schema-contracts.js validate --schema path/to/schema.json --data sample.json

Policy:
  Discriminator field is "type".
  Discriminator schemas use enum, not const.
  Discriminator values use CAPS_CASE.
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
  assertSchemaPolicy(schema);
  writeJson(out, schema);
  console.log(`Wrote ${out}`);
}

function checkPolicyCommand(options) {
  const schemaPath = required(options, "schema");
  const schema = readJson(schemaPath);
  assertSchemaPolicy(schema);
  console.log(`Policy OK: ${schemaPath}`);
}

function validateCommand(rawArgs) {
  const options = parseArgs(rawArgs);
  const schemaPath = required(options, "schema");
  const dataPath = required(options, "data");
  const schema = readJson(schemaPath);
  assertSchemaPolicy(schema);
  const validate = validatorFor(schema).compile(schema);
  const data = readJson(dataPath);
  if (validate(data)) {
    console.log(`Validation OK: ${dataPath}`);
    return;
  }
  throw new Error(`Validation failed: ${JSON.stringify(validate.errors, null, 2)}`);
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

function fail(message) {
  console.error(message);
  process.exit(1);
}

main();
