# Forms Rule Editor — EDS Solution Design

## 1. Overview

This document describes the approach for migrating the AEM 6.4 Show/Hide Rule Editor functionality to AEM Edge Delivery Services (EDS) with Universal Editor (xWalk). The rule engine enables conditional field visibility, required state, disabled state, and value setting based on dynamic conditions evaluated at runtime.

---

## 2. Current State (AEM 6.4)

### Components
- **ShowHideRuleFilter** — Server-side Sling filter that injects CSS classes for initial field visibility
- **RuleEditorComponent** — Dialog-based visual rule builder for authors

### Capabilities

| Capability | Details |
|-----------|---------|
| **Condition Operators** | 9 operators: `equal`, `notEqual`, `lessThan`, `lessThanEqualTo`, `greaterThan`, `greaterThanEqualTo`, `startsWith`, `endsWith`, `contains` |
| **Logical Operators** | `ALL` (AND) — all conditions must be true / `ANY` (OR) — any condition must be true |
| **Execution** | Dual — server-side CSS class injection for initial state + client-side JavaScript for dynamic changes |
| **Security** | AES encryption of rules before delivery to frontend |
| **Storage** | Rules stored as child nodes under each field component in JCR |
| **Rule Builder** | Visual drag-and-drop rule editor in the authoring dialog |

### Current Flow (AEM 6.4)
```
Author opens field dialog
  → Opens Rule Editor tab
  → Builds rules visually (IF field X equals "value" THEN show field Y)
  → Rules saved as JCR child nodes
  → Rules encrypted via AES before frontend delivery
  → Server-side: Sling filter reads rules, injects CSS classes on initial render
  → Client-side: JavaScript evaluates rules on field change events
```

---

## 3. EDS Approach

### Architecture Decision

A **custom rule engine** will be developed within the `form-container` block. Rules will be authored in a **dedicated rules spreadsheet per form**, and the form block JS fetches the rules JSON at runtime and evaluates conditions client-side.

### Why Rules Spreadsheet (Not Inline on Fields)

| Approach | Pros | Cons | Verdict |
|----------|------|------|---------|
| **Inline on each field** (UE property like `show:country=US`) | Simple for 1-2 rules | Cannot express AND/OR logic, no multi-condition support, clutters field properties panel | Not suitable for 9 operators + AND/OR |
| **Dedicated rules spreadsheet per form** | Full operator support, AND/OR logic, easy to read/audit, no dialog clutter, authors manage rules in familiar tabular format | Separate artifact to maintain | **Recommended** |

### Key Design Decisions
1. **Client-side only execution** — No server-side component needed; rules evaluate immediately on page load
2. **No encryption needed** — Rules live in a spreadsheet (not embedded in page markup), so no security exposure
3. **All 9 operators preserved** — Full parity with AEM 6.4
4. **AND/OR logic preserved** — Grouped by Rule ID with `all`/`any` logic column
5. **Custom blocks (xWalk)** — Forms are built using custom blocks in Universal Editor, not spreadsheet-based forms

---

## 4. Rules Spreadsheet Structure

Each form references its own rules spreadsheet. Example path: `/content/forms/rules/webinar-form`

### Spreadsheet Columns

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| **Rule ID** | String | Yes | Groups multiple conditions into one rule (e.g., `rule-1`) |
| **Target Field** | String | Yes | The field name that will be affected (e.g., `stateDropdown`) |
| **Action** | String | Yes | What happens when conditions are met: `show`, `hide`, `required`, `disable`, `setValue` |
| **Logic** | String | Yes | How to combine conditions: `all` (AND) or `any` (OR) |
| **Condition Field** | String | Yes | The field whose value is being checked (e.g., `country`) |
| **Operator** | String | Yes | Comparison operator (see Section 5) |
| **Value** | String | Yes | The value to compare against |

### Example Rules Sheet

| Rule ID | Target Field | Action | Logic | Condition Field | Operator | Value |
|---------|-------------|--------|-------|----------------|----------|-------|
| rule-1 | stateDropdown | show | all | country | equal | US |
| rule-1 | stateDropdown | show | all | formType | notEqual | simple |
| rule-2 | phoneExtension | show | any | country | equal | US |
| rule-2 | phoneExtension | show | any | country | equal | CA |
| rule-3 | discountField | show | all | quantity | greaterThanEqualTo | 10 |
| rule-4 | otherInput | show | all | reason | contains | other |
| rule-5 | prefixField | show | all | fullName | startsWith | Dr |
| rule-6 | emailOptIn | disable | all | country | equal | DE |
| rule-7 | budgetRange | required | all | requestType | equal | quote |
| rule-8 | companySize | setValue | all | orgType | equal | enterprise |

### How Logic Grouping Works

**AND (all):** Same Rule ID + logic `all` — ALL conditions must be true.
```
rule-1: stateDropdown shows ONLY when:
  country = "US"  AND  formType ≠ "simple"
```

**OR (any):** Same Rule ID + logic `any` — ANY condition must be true.
```
rule-2: phoneExtension shows when:
  country = "US"  OR  country = "CA"
```

---

## 5. Supported Operators

All 9 operators from AEM 6.4 are fully preserved.

| Operator | Description | Example | JS Evaluation |
|----------|------------|---------|--------------|
| `equal` | Exact match | country = "US" | `value === conditionValue` |
| `notEqual` | Not equal | formType ≠ "simple" | `value !== conditionValue` |
| `lessThan` | Less than (numeric) | quantity < 10 | `Number(value) < Number(conditionValue)` |
| `lessThanEqualTo` | Less than or equal | quantity ≤ 10 | `Number(value) <= Number(conditionValue)` |
| `greaterThan` | Greater than (numeric) | quantity > 5 | `Number(value) > Number(conditionValue)` |
| `greaterThanEqualTo` | Greater than or equal | quantity ≥ 10 | `Number(value) >= Number(conditionValue)` |
| `startsWith` | String starts with | name starts with "Dr" | `value.startsWith(conditionValue)` |
| `endsWith` | String ends with | email ends with ".edu" | `value.endsWith(conditionValue)` |
| `contains` | String contains | reason contains "other" | `value.includes(conditionValue)` |

---

## 6. Supported Actions

| Action | What It Does | Implementation |
|--------|-------------|----------------|
| `show` | Shows the target field when conditions are met, hides when not met | Toggle `display: none` + `aria-hidden` attribute |
| `hide` | Hides the target field when conditions are met (inverse of show) | Toggle `display: none` + `aria-hidden` attribute |
| `required` | Makes field required when conditions are met, optional when not met | Toggle `required` attribute + visual asterisk indicator |
| `disable` | Disables field when conditions are met, enables when not met | Toggle `disabled` attribute |
| `setValue` | Sets field value when conditions are met | Programmatically set `field.value` |

---

## 7. Authoring Experience

### Step 1: Create the Form

Author creates the form using the form-container block in Universal Editor. Fields are added with their types, labels, and basic properties.

### Step 2: Create the Rules Spreadsheet

Author creates a rules spreadsheet for the form. Example: `/content/forms/rules/webinar-form`

The spreadsheet uses the tabular structure defined in Section 4.

### Step 3: Link Rules Sheet to Form

Author sets the rules sheet path in the form-container properties panel in Universal Editor:

```
+---------------------------------------+
| Form Container Properties             |
+---------------------------------------+
| Action Type:    [Eloqua          v]   |
| Eloqua Form:   [MSD_SEMI_XBL_...]    |
| Thank You Page: [/events/.../ty]      |
| Rules Sheet:    [/content/forms/...]  |  <-- path to rules spreadsheet
| GCMS Form ID:   [601170]             |
+---------------------------------------+
```

### Component Model Definition

```json
{
  "component": "aem-content",
  "name": "rulesSheet",
  "label": "Conditional Rules Sheet",
  "valueType": "string",
  "description": "Path to the rules spreadsheet for conditional field visibility and behavior"
}
```

The `aem-content` picker allows authors to browse and select the rules spreadsheet from AEM content.

---

## 8. Runtime Flow

```
Page loads
  |
  v
Form-container block decorates all fields
  |
  v
Reads "rulesSheet" property from block configuration
  |
  v
Fetches rules JSON (GET /content/forms/rules/webinar-form.json)
  |
  v
Parses rules into structured rule map:
  {
    "stateDropdown": [{
      action: "show",
      logic: "all",
      conditions: [
        { field: "country", operator: "equal", value: "US" },
        { field: "formType", operator: "notEqual", value: "simple" }
      ]
    }],
    "phoneExtension": [{
      action: "show",
      logic: "any",
      conditions: [
        { field: "country", operator: "equal", value: "US" },
        { field: "country", operator: "equal", value: "CA" }
      ]
    }]
  }
  |
  v
Evaluate ALL rules on initial load
  -> Apply initial visibility/required/disabled state
  -> Fields with unmet conditions are hidden before paint (no flash)
  |
  v
Build dependency map (which fields trigger which rules):
  {
    "country":   ["stateDropdown", "phoneExtension", "emailOptIn"],
    "formType":  ["stateDropdown"],
    "quantity":  ["discountField"],
    "reason":    ["otherInput"],
    "requestType": ["budgetRange"]
  }
  |
  v
Attach 'change' + 'input' event listeners on dependency fields ONLY
(not on all fields — optimized for performance)
  |
  v
User changes a field (e.g., selects country = "US")
  |
  v
Event listener fires -> looks up dependency map
  -> Finds affected targets: ["stateDropdown", "phoneExtension", "emailOptIn"]
  |
  v
Re-evaluates ONLY the rules for those target fields
  |
  v
Applies actions:
  - stateDropdown: show (if all conditions met)
  - phoneExtension: show (if any condition met)
  - emailOptIn: disable/enable based on condition
```

---

## 9. Implementation Reference

### Rule Engine Core (form-container.js)

```javascript
/**
 * Fetches and parses rules from the rules spreadsheet
 * @param {string} rulesPath - Path to the rules spreadsheet
 * @returns {Object} Parsed rule map
 */
async function loadRules(rulesPath) {
  if (!rulesPath) return {};
  const resp = await fetch(`${rulesPath}.json`);
  if (!resp.ok) return {};
  const { data } = await resp.json();
  return parseRules(data);
}

/**
 * Parses flat spreadsheet rows into grouped rule map
 * Groups by Rule ID, then by Target Field
 */
function parseRules(rows) {
  const ruleMap = {};

  // Group rows by Rule ID
  const grouped = {};
  rows.forEach((row) => {
    const id = row['Rule ID'];
    if (!grouped[id]) grouped[id] = [];
    grouped[id].push(row);
  });

  // Build structured rules per target field
  Object.values(grouped).forEach((ruleRows) => {
    const target = ruleRows[0]['Target Field'];
    const action = ruleRows[0]['Action'];
    const logic = ruleRows[0]['Logic']; // 'all' or 'any'

    const conditions = ruleRows.map((r) => ({
      field: r['Condition Field'],
      operator: r['Operator'],
      value: r['Value'],
    }));

    if (!ruleMap[target]) ruleMap[target] = [];
    ruleMap[target].push({ action, logic, conditions });
  });

  return ruleMap;
}

/**
 * Evaluates a single condition against current form values
 */
function evaluateCondition(condition, formValues) {
  const fieldValue = formValues[condition.field] || '';
  const condValue = condition.value;

  switch (condition.operator) {
    case 'equal': return fieldValue === condValue;
    case 'notEqual': return fieldValue !== condValue;
    case 'lessThan': return Number(fieldValue) < Number(condValue);
    case 'lessThanEqualTo': return Number(fieldValue) <= Number(condValue);
    case 'greaterThan': return Number(fieldValue) > Number(condValue);
    case 'greaterThanEqualTo': return Number(fieldValue) >= Number(condValue);
    case 'startsWith': return fieldValue.startsWith(condValue);
    case 'endsWith': return fieldValue.endsWith(condValue);
    case 'contains': return fieldValue.includes(condValue);
    default: return false;
  }
}

/**
 * Evaluates a rule (with AND/OR logic) against current form values
 */
function evaluateRule(rule, formValues) {
  if (rule.logic === 'any') {
    return rule.conditions.some((c) => evaluateCondition(c, formValues));
  }
  // Default: 'all' (AND)
  return rule.conditions.every((c) => evaluateCondition(c, formValues));
}

/**
 * Applies the action to the target field
 */
function applyAction(targetField, action, conditionMet) {
  const wrapper = targetField.closest('.field-wrapper');
  if (!wrapper) return;

  switch (action) {
    case 'show':
      wrapper.style.display = conditionMet ? '' : 'none';
      wrapper.setAttribute('aria-hidden', !conditionMet);
      if (!conditionMet) targetField.removeAttribute('required');
      break;
    case 'hide':
      wrapper.style.display = conditionMet ? 'none' : '';
      wrapper.setAttribute('aria-hidden', conditionMet);
      if (conditionMet) targetField.removeAttribute('required');
      break;
    case 'required':
      if (conditionMet) {
        targetField.setAttribute('required', '');
      } else {
        targetField.removeAttribute('required');
      }
      break;
    case 'disable':
      targetField.disabled = conditionMet;
      break;
    case 'setValue':
      if (conditionMet) {
        targetField.value = rule.setValue || '';
        targetField.dispatchEvent(new Event('change'));
      }
      break;
    default:
      break;
  }
}

/**
 * Builds a dependency map: which source fields affect which targets
 */
function buildDependencyMap(ruleMap) {
  const depMap = {};
  Object.entries(ruleMap).forEach(([target, rules]) => {
    rules.forEach((rule) => {
      rule.conditions.forEach((cond) => {
        if (!depMap[cond.field]) depMap[cond.field] = new Set();
        depMap[cond.field].add(target);
      });
    });
  });
  // Convert Sets to Arrays
  Object.keys(depMap).forEach((key) => {
    depMap[key] = [...depMap[key]];
  });
  return depMap;
}

/**
 * Gets all current form values as a key-value map
 */
function getFormValues(form) {
  const values = {};
  const fields = form.querySelectorAll('input, select, textarea');
  fields.forEach((field) => {
    if (field.name) {
      if (field.type === 'checkbox') {
        values[field.name] = field.checked ? field.value : '';
      } else if (field.type === 'radio') {
        if (field.checked) values[field.name] = field.value;
      } else {
        values[field.name] = field.value;
      }
    }
  });
  return values;
}

/**
 * Main initialization: load rules, evaluate, attach listeners
 */
async function initRuleEngine(form, rulesPath) {
  const ruleMap = await loadRules(rulesPath);
  if (!Object.keys(ruleMap).length) return;

  const depMap = buildDependencyMap(ruleMap);

  // Evaluate all rules on initial load (before paint)
  function evaluateAll() {
    const values = getFormValues(form);
    Object.entries(ruleMap).forEach(([targetName, rules]) => {
      const targetField = form.querySelector(`[name="${targetName}"]`);
      if (!targetField) return;
      rules.forEach((rule) => {
        const result = evaluateRule(rule, values);
        applyAction(targetField, rule.action, result);
      });
    });
  }

  // Initial evaluation
  evaluateAll();

  // Attach listeners only on fields that other rules depend on
  Object.keys(depMap).forEach((sourceFieldName) => {
    const sourceField = form.querySelector(`[name="${sourceFieldName}"]`);
    if (!sourceField) return;

    const handler = () => {
      const values = getFormValues(form);
      const targets = depMap[sourceFieldName];
      targets.forEach((targetName) => {
        const targetField = form.querySelector(`[name="${targetName}"]`);
        if (!targetField) return;
        const rules = ruleMap[targetName];
        rules.forEach((rule) => {
          const result = evaluateRule(rule, values);
          applyAction(targetField, rule.action, result);
        });
      });
    };

    sourceField.addEventListener('change', handler);
    sourceField.addEventListener('input', handler);
  });
}
```

### Integration in form-container.js decorate()

```javascript
export default async function decorate(block) {
  const form = document.createElement('form');

  // ... existing field decoration logic ...

  // Initialize rule engine
  const rulesSheet = block.dataset.rulesSheet
    || block.querySelector('[data-rules-sheet]')?.dataset.rulesSheet;
  if (rulesSheet) {
    await initRuleEngine(form, rulesSheet);
  }

  block.textContent = '';
  block.append(form);
}
```

---

## 10. Comparison: AEM 6.4 vs EDS

| Aspect | AEM 6.4 | EDS | Impact |
|--------|---------|-----|--------|
| **Rule authoring** | Visual drag-drop rule editor in dialog | Tabular spreadsheet authoring | Authors use spreadsheet — simpler, easier to audit and bulk-edit |
| **Execution model** | Server-side CSS class injection + Client-side JS | Client-side only | No functional impact — rules evaluate on page load before paint |
| **Encryption** | AES encryption of rules before frontend delivery | Not needed | Rules in spreadsheet are not embedded in page markup |
| **Storage** | JCR child nodes per field | Dedicated spreadsheet per form | Easier to manage, version, and review |
| **Operators** | 9 operators | All 9 operators preserved | Full parity |
| **Logical operators** | ALL (AND) / ANY (OR) | ALL / ANY preserved | Full parity |
| **Actions** | Show / Hide | Show / Hide / Required / Disable / SetValue | Enhanced capability |
| **Performance** | Full DOM scan on each change | Dependency map — only affected fields re-evaluated | Improved performance |

---

## 11. Limitations

| Limitation | Detail | Mitigation |
|-----------|--------|------------|
| **Client-side only** | No server-side CSS injection for initial field state | Rules evaluate on `DOMContentLoaded` before first paint via `requestAnimationFrame`. Fields with unmet conditions are hidden immediately. Imperceptible difference from server-side approach. |
| **No visual rule builder** | AEM 6.4 has a drag-drop rule editor UI | Spreadsheet is tabular and easy to learn. For forms with many rules, a spreadsheet is actually easier to read and audit than a visual builder. |
| **Rules in separate artifact** | Not inline with the field definition | Keeps field authoring clean. Rules sheet provides a single view of ALL conditional logic for a form — better for debugging and handoff. |
| **No server-side validation of rules** | Hidden fields could be manipulated via browser dev tools | Server-side submission endpoint should independently validate required fields regardless of client-side visibility rules. Same best practice as any web form. |

---

## 12. Migration Checklist

- [ ] Create rule engine functions in `form-container.js`
- [ ] Add `rulesSheet` property to form-container component model
- [ ] Create rules spreadsheet template for authors
- [ ] Migrate existing rules from JCR child nodes to spreadsheet format
- [ ] Test all 9 operators with sample form
- [ ] Test AND/OR logic combinations
- [ ] Test initial load visibility (no flash of hidden fields)
- [ ] Test dynamic field changes (show/hide/required/disable)
- [ ] Validate with screen readers (aria-hidden correctness)
- [ ] Performance test with large forms (50+ fields, 30+ rules)
