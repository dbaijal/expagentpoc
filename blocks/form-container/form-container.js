import { moveInstrumentation } from '../../scripts/scripts.js';

/**
 * Reads text from a cell by child-element index.
 * Handles three AEM delivery patterns:
 *   - Multiple child elements (div/p per value): cell.children[index]
 *   - Single <p> wrapper around block children (wrapTextNodes): cell > p > children[index]
 *   - Single text node (one value, no wrapper): cell.textContent for index 0
 */
function getChild(cell, index) {
  if (!cell) return '';
  const { children } = cell;
  if (children.length === 1
    && children[0].tagName === 'P'
    && children[0].children.length > 0) {
    return children[0].children[index]?.textContent?.trim() || '';
  }
  if (children.length > 0) {
    return children[index]?.textContent?.trim() || '';
  }
  return index === 0 ? cell.textContent.trim() : '';
}

/**
 * Reads rich HTML from a cell by child-element index.
 */
function getChildHTML(cell, index) {
  if (!cell) return '';
  const { children } = cell;
  if (children.length === 1
    && children[0].tagName === 'P'
    && children[0].children.length > 0) {
    return children[0].children[index]?.innerHTML?.trim() || '';
  }
  if (children.length > 0) {
    return children[index]?.innerHTML?.trim() || '';
  }
  return index === 0 ? cell.innerHTML.trim() : '';
}

/**
 * Generates a unique field ID based on field name.
 */
const idCounters = {};
function generateId(name) {
  const base = `form-${name}`.replace(/\s+/g, '-').toLowerCase();
  idCounters[base] = (idCounters[base] || 0) + 1;
  const suffix = idCounters[base] > 1 ? `-${idCounters[base]}` : '';
  return `${base}${suffix}`;
}

/**
 * Creates an input field (text, email, tel, number, date).
 * Cell 0 (field): [type, name, label]
 * Cell 1 (config): [inputType, placeholder]
 * Cell 2 (validation): [required]
 */
function createInputField(fieldCell, configCell, validationCell) {
  const name = getChild(fieldCell, 1);
  const label = getChild(fieldCell, 2);
  const inputType = getChild(configCell, 0) || 'text';
  const placeholder = getChild(configCell, 1);
  const required = getChild(validationCell, 0) === 'true';
  const id = generateId(name);

  const wrapper = document.createElement('div');
  wrapper.className = 'field-wrapper input-wrapper';

  const labelEl = document.createElement('label');
  labelEl.id = `${id}-label`;
  labelEl.setAttribute('for', id);
  labelEl.textContent = label || name;
  if (required) labelEl.dataset.required = 'true';

  const input = document.createElement('input');
  input.type = inputType;
  input.id = id;
  input.name = name;
  if (placeholder) input.placeholder = placeholder;
  if (required) input.required = true;
  input.setAttribute('aria-labelledby', `${id}-label`);

  wrapper.append(labelEl, input);
  return wrapper;
}

/**
 * Creates an options field (select dropdown, radio buttons, or checkboxes).
 * Cell 0 (field): [type, name, label]
 * Cell 1 (config): [optionType, options, placeholder]
 * Cell 2 (validation): [required]
 */
function createOptionsField(fieldCell, configCell, validationCell) {
  const name = getChild(fieldCell, 1);
  const label = getChild(fieldCell, 2);
  const optionType = getChild(configCell, 0) || 'select';
  const optionsStr = getChild(configCell, 1);
  const placeholder = getChild(configCell, 2);
  const required = getChild(validationCell, 0) === 'true';
  const id = generateId(name);
  const options = optionsStr ? optionsStr.split(',').map((o) => o.trim()) : [];

  const wrapper = document.createElement('div');
  wrapper.className = `field-wrapper ${optionType}-wrapper`;

  const labelEl = document.createElement('label');
  labelEl.id = `${id}-label`;
  labelEl.textContent = label || name;
  if (required) labelEl.dataset.required = 'true';

  if (optionType === 'select') {
    labelEl.setAttribute('for', id);
    const select = document.createElement('select');
    select.id = id;
    select.name = name;
    if (required) select.required = true;

    if (placeholder) {
      const ph = document.createElement('option');
      ph.value = '';
      ph.textContent = placeholder;
      ph.disabled = true;
      ph.selected = true;
      select.append(ph);
    }

    options.forEach((opt) => {
      const option = document.createElement('option');
      option.value = opt;
      option.textContent = opt;
      select.append(option);
    });

    wrapper.append(labelEl, select);
  } else {
    wrapper.append(labelEl);
    const groupDiv = document.createElement('div');
    groupDiv.className = `${optionType}-group`;
    groupDiv.setAttribute('role', optionType === 'radio' ? 'radiogroup' : 'group');
    groupDiv.setAttribute('aria-labelledby', `${id}-label`);

    options.forEach((opt, i) => {
      const optId = `${id}-${i}`;
      const optWrapper = document.createElement('div');
      optWrapper.className = 'selection-wrapper';

      const input = document.createElement('input');
      input.type = optionType;
      input.id = optId;
      input.name = name;
      input.value = opt;
      if (required && optionType === 'radio' && i === 0) input.required = true;

      const optLabel = document.createElement('label');
      optLabel.setAttribute('for', optId);
      optLabel.textContent = opt;

      optWrapper.append(input, optLabel);
      groupDiv.append(optWrapper);
    });

    wrapper.append(groupDiv);
  }

  return wrapper;
}

/**
 * Creates a textarea field.
 * Cell 0 (field): [type, name, label]
 * Cell 1 (config): [placeholder]
 * Cell 2 (validation): [required]
 */
function createTextareaField(fieldCell, configCell, validationCell) {
  const name = getChild(fieldCell, 1);
  const label = getChild(fieldCell, 2);
  const placeholder = getChild(configCell, 0);
  const required = getChild(validationCell, 0) === 'true';
  const id = generateId(name);

  const wrapper = document.createElement('div');
  wrapper.className = 'field-wrapper textarea-wrapper';

  const labelEl = document.createElement('label');
  labelEl.id = `${id}-label`;
  labelEl.setAttribute('for', id);
  labelEl.textContent = label || name;
  if (required) labelEl.dataset.required = 'true';

  const textarea = document.createElement('textarea');
  textarea.id = id;
  textarea.name = name;
  if (placeholder) textarea.placeholder = placeholder;
  if (required) textarea.required = true;
  textarea.setAttribute('aria-labelledby', `${id}-label`);

  wrapper.append(labelEl, textarea);
  return wrapper;
}

/**
 * Creates a hidden field.
 * Cell 0 (field): [type, name]
 * Cell 1 (config): [value, source]
 */
function createHiddenField(fieldCell, configCell) {
  const name = getChild(fieldCell, 1);
  const value = getChild(configCell, 0);
  const valueSource = getChild(configCell, 1) || 'static';

  const input = document.createElement('input');
  input.type = 'hidden';
  input.name = name;

  switch (valueSource) {
    case 'query': {
      const params = new URLSearchParams(window.location.search);
      input.value = params.get(value) || '';
      break;
    }
    case 'cookie': {
      const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${value}=([^;]*)`));
      input.value = match ? decodeURIComponent(match[1]) : '';
      break;
    }
    case 'timestamp':
      input.value = new Date().toISOString();
      break;
    default:
      input.value = value;
  }

  return input;
}

/**
 * Creates a file upload field.
 * Cell 0 (field): [type, name, label]
 * Cell 1 (config): [buttonText]
 * Cell 2 (validation): [required]
 */
function createUploadField(fieldCell, configCell, validationCell) {
  const name = getChild(fieldCell, 1);
  const label = getChild(fieldCell, 2);
  const buttonText = getChild(configCell, 0) || 'Choose File';
  const required = getChild(validationCell, 0) === 'true';
  const id = generateId(name);

  const wrapper = document.createElement('div');
  wrapper.className = 'field-wrapper upload-wrapper';

  const labelEl = document.createElement('label');
  labelEl.id = `${id}-label`;
  labelEl.setAttribute('for', id);
  labelEl.textContent = label || name;
  if (required) labelEl.dataset.required = 'true';

  const fileInput = document.createElement('input');
  fileInput.type = 'file';
  fileInput.id = id;
  fileInput.name = name;
  if (required) fileInput.required = true;

  const btn = document.createElement('span');
  btn.className = 'upload-btn';
  btn.textContent = buttonText;

  const fileNameDisplay = document.createElement('span');
  fileNameDisplay.className = 'upload-filename';
  fileNameDisplay.textContent = 'No file chosen';

  btn.addEventListener('click', () => fileInput.click());
  fileInput.addEventListener('change', () => {
    fileNameDisplay.textContent = fileInput.files.length
      ? fileInput.files[0].name
      : 'No file chosen';
  });

  wrapper.append(labelEl, btn, fileInput, fileNameDisplay);
  return wrapper;
}

/**
 * Creates a form button (submit or reset).
 * Cell 0 (field): [type, label]
 * Cell 1 (config): [buttonType]
 */
function createButtonField(fieldCell, configCell) {
  const label = getChild(fieldCell, 1) || 'Submit';
  const buttonType = getChild(configCell, 0) || 'submit';

  const wrapper = document.createElement('div');
  wrapper.className = 'field-wrapper button-wrapper';

  const button = document.createElement('button');
  button.type = buttonType;
  button.className = 'button';
  button.textContent = label;

  wrapper.append(button);
  return wrapper;
}

/**
 * Creates a label / rich text element.
 * Cell 0 (field): [type]
 * Cell 1 (content): [text as richtext]
 */
function createLabelField(fieldCell, contentCell) {
  const html = getChildHTML(contentCell, 0);

  const wrapper = document.createElement('div');
  wrapper.className = 'field-wrapper label-wrapper';
  wrapper.innerHTML = html;

  return wrapper;
}

/** Maps fieldType to its creator function. */
const FIELD_CREATORS = {
  input: createInputField,
  options: createOptionsField,
  textarea: createTextareaField,
  hidden: createHiddenField,
  upload: createUploadField,
  button: createButtonField,
  label: createLabelField,
};

/**
 * Valid column span values in the 12-column grid.
 */
const VALID_SPANS = new Set(['3', '4', '6', '8', '12']);

/**
 * Reads wizard step and layout span from the trailing cells of a row.
 *
 * Supports two formats:
 *   New (both cells):  [..., wizard_step, layout_span]
 *   Old (step only):   [..., wizard_step]
 *
 * Disambiguation for overlapping values ("3", "4"):
 *   - If the cell before the last holds a wizard value ("-" or "1"-"5"),
 *     the last cell is layout_span and the previous is wizard_step.
 *   - Otherwise the last cell is a wizard_step (old format).
 */
function getFieldMeta(cells) {
  const meta = { step: '', span: '' };
  if (cells.length < 3) return meta;

  const lastVal = getChild(cells[cells.length - 1], 0);
  const isStep = /^[1-5]$/.test(lastVal);
  const isSpan = VALID_SPANS.has(lastVal);

  if (isSpan && !isStep) {
    // Unambiguous span (6, 8, 12) — new format
    meta.span = lastVal;
    if (cells.length >= 4) {
      const sv = getChild(cells[cells.length - 2], 0);
      if (/^[1-5]$/.test(sv)) meta.step = sv;
    }
  } else if (isStep && !isSpan) {
    // Unambiguous step (1, 2, 5) — old or new format
    meta.step = lastVal;
  } else if (isStep && isSpan) {
    // Ambiguous (3, 4): check cell before to decide
    if (cells.length >= 4) {
      const prevVal = getChild(cells[cells.length - 2], 0);
      if (/^[1-5]$/.test(prevVal) || prevVal === '-') {
        // Previous cell is a wizard value → last is span
        meta.span = lastVal;
        if (/^[1-5]$/.test(prevVal)) meta.step = prevVal;
      } else {
        // Previous cell is not a wizard value → old format step
        meta.step = lastVal;
      }
    } else {
      meta.step = lastVal;
    }
  }

  return meta;
}

/**
 * Collects form payload from all fields.
 */
function generatePayload(form) {
  const payload = {};
  [...form.elements].forEach((field) => {
    if (field.name && field.type !== 'submit' && !field.disabled) {
      if (field.type === 'radio') {
        if (field.checked) payload[field.name] = field.value;
      } else if (field.type === 'checkbox') {
        if (field.checked) {
          payload[field.name] = payload[field.name]
            ? `${payload[field.name]},${field.value}`
            : field.value;
        }
      } else if (field.type === 'file') {
        if (field.files.length) payload[field.name] = field.files[0].name;
      } else {
        payload[field.name] = field.value;
      }
    }
  });
  return payload;
}

/**
 * Reads form-level config from a "config" type child row.
 * Cell 0 (field): [type, action]
 * Cell 1 (config): [formid, redirect, thankyou, steptitles]
 */
function extractFormConfig(rows) {
  const config = {
    action: 'API', formid: '', redirect: '', thankyou: '', steptitles: '',
  };
  const remaining = [];
  let configRow = null;

  rows.forEach((row) => {
    const cells = [...row.children];
    const fieldCell = cells[0];
    const fieldType = getChild(fieldCell, 0).toLowerCase();

    if (fieldType === 'config') {
      config.action = getChild(fieldCell, 1) || config.action;
      const configCell = cells[1];
      config.formid = getChild(configCell, 0) || config.formid;
      config.redirect = getChild(configCell, 1) || config.redirect;
      config.thankyou = getChild(configCell, 2) || config.thankyou;
      config.steptitles = getChild(configCell, 3) || config.steptitles;
      if (!configRow) configRow = row;
    } else {
      remaining.push(row);
    }
  });

  return { config, remaining, configRow };
}

/**
 * Checks whether the form has any fields with data-step attributes.
 */
function isMultiStep(form) {
  return form.querySelectorAll('[data-step]').length > 0;
}

/**
 * Groups form fields by their data-step attribute into fieldset panels.
 * Returns array of { title, element } for each step.
 */
function groupFieldsByStep(form, stepTitles) {
  const titles = stepTitles
    ? stepTitles.split(',').map((t) => t.trim())
    : [];
  const stepMap = new Map();

  // Collect fields by step number
  [...form.children].forEach((child) => {
    const stepNum = child.dataset?.step;
    if (stepNum) {
      if (!stepMap.has(stepNum)) stepMap.set(stepNum, []);
      stepMap.get(stepNum).push(child);
    }
  });

  // Sort step numbers and create fieldsets
  const sortedKeys = [...stepMap.keys()].sort((a, b) => parseInt(a, 10) - parseInt(b, 10));
  const steps = [];

  sortedKeys.forEach((key, i) => {
    const stepIndex = parseInt(key, 10) - 1;
    const title = titles[stepIndex] || `Step ${key}`;

    const fieldset = document.createElement('fieldset');
    fieldset.className = 'form-step';
    fieldset.dataset.step = key;
    if (i > 0) fieldset.hidden = true;

    const legend = document.createElement('legend');
    legend.className = 'form-step-title';
    legend.textContent = `${i + 1} of ${sortedKeys.length}: ${title}`;
    fieldset.append(legend);

    stepMap.get(key).forEach((f) => fieldset.append(f));
    form.append(fieldset);

    steps.push({ title, element: fieldset });
  });

  return steps;
}

/**
 * Builds a progress bar showing step indicators.
 */
function buildProgressBar(steps) {
  const bar = document.createElement('nav');
  bar.className = 'form-progress';
  bar.setAttribute('aria-label', 'Form steps');

  steps.forEach((step, i) => {
    if (i > 0) {
      const connector = document.createElement('span');
      connector.className = 'form-progress-connector';
      bar.append(connector);
    }

    const dot = document.createElement('span');
    dot.className = 'form-progress-step';
    dot.dataset.step = i + 1;
    dot.setAttribute('aria-label', step.title);
    dot.textContent = i + 1;
    if (i === 0) dot.classList.add('active', 'current');
    bar.append(dot);
  });

  return bar;
}

/**
 * Builds Back / Next / Submit navigation buttons.
 */
function buildStepNavigation(steps) {
  const nav = document.createElement('div');
  nav.className = 'form-step-nav';

  const backBtn = document.createElement('button');
  backBtn.type = 'button';
  backBtn.className = 'button form-back';
  backBtn.textContent = 'Back';
  backBtn.hidden = true;

  const nextBtn = document.createElement('button');
  nextBtn.type = 'button';
  nextBtn.className = 'button form-next';
  nextBtn.textContent = 'Next';

  const submitBtn = document.createElement('button');
  submitBtn.type = 'submit';
  submitBtn.className = 'button form-submit';
  submitBtn.textContent = 'Submit';
  submitBtn.hidden = steps.length > 1;

  nav.append(backBtn, nextBtn, submitBtn);
  return nav;
}

/**
 * Validates all visible required fields in the given step fieldset.
 * Returns true if all pass, false and focuses the first invalid field otherwise.
 */
function validateStep(stepElement) {
  const fields = stepElement.querySelectorAll('input, select, textarea');
  let firstInvalid = null;
  fields.forEach((field) => {
    if (!field.checkValidity() && !firstInvalid) {
      firstInvalid = field;
    }
  });
  if (firstInvalid) {
    firstInvalid.reportValidity();
    firstInvalid.focus();
    return false;
  }
  return true;
}

/**
 * Navigates to a specific step, updating visibility, progress bar, and buttons.
 */
function navigateToStep(form, steps, target) {
  steps.forEach((step, i) => {
    step.element.hidden = (i + 1) !== target;
  });

  // Update progress bar
  form.querySelectorAll('.form-progress-step').forEach((dot) => {
    const stepNum = parseInt(dot.dataset.step, 10);
    dot.classList.toggle('active', stepNum <= target);
    dot.classList.toggle('current', stepNum === target);
  });
  form.querySelectorAll('.form-progress-connector').forEach((conn, i) => {
    conn.classList.toggle('completed', (i + 1) < target);
  });

  // Update nav buttons
  const backBtn = form.querySelector('.form-back');
  const nextBtn = form.querySelector('.form-next');
  const submitBtn = form.querySelector('.form-submit');
  if (backBtn) backBtn.hidden = target === 1;
  if (nextBtn) nextBtn.hidden = target === steps.length;
  if (submitBtn) submitBtn.hidden = target !== steps.length;

  // Scroll to top of form
  form.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

/**
 * Sets up the multi-step wizard: groups fields by data-step, adds progress bar and nav.
 */
function setupWizard(form, stepTitles) {
  const steps = groupFieldsByStep(form, stepTitles);
  if (steps.length < 2) return;

  let currentStep = 1;

  // Remove any authored submit buttons (wizard adds its own)
  form.querySelectorAll('.button-wrapper').forEach((bw) => {
    const btn = bw.querySelector('button[type="submit"]');
    if (btn) bw.remove();
  });

  // Build and insert progress bar at the top (after config anchor)
  const progressBar = buildProgressBar(steps);
  const configAnchor = form.querySelector('.form-config-anchor');
  if (configAnchor) {
    configAnchor.after(progressBar);
  } else {
    form.prepend(progressBar);
  }

  // Build and append navigation
  const nav = buildStepNavigation(steps);
  form.append(nav);

  // Next button handler
  nav.querySelector('.form-next').addEventListener('click', () => {
    if (validateStep(steps[currentStep - 1].element)) {
      currentStep += 1;
      navigateToStep(form, steps, currentStep);
    }
  });

  // Back button handler
  nav.querySelector('.form-back').addEventListener('click', () => {
    if (currentStep > 1) {
      currentStep -= 1;
      navigateToStep(form, steps, currentStep);
    }
  });

  // Progress dot click handlers
  progressBar.querySelectorAll('.form-progress-step').forEach((dot) => {
    dot.addEventListener('click', () => {
      const target = parseInt(dot.dataset.step, 10);
      if (target < currentStep) {
        currentStep = target;
        navigateToStep(form, steps, currentStep);
      }
    });
  });

  form.classList.add('form-wizard');
}

/**
 * Handles form submission.
 */
async function handleSubmit(form, formConfig) {
  if (form.getAttribute('data-submitting') === 'true') return;

  const submit = form.querySelector('button[type="submit"]');
  try {
    form.setAttribute('data-submitting', 'true');
    if (submit) submit.disabled = true;

    const payload = generatePayload(form);

    const response = await fetch(form.action, {
      method: 'POST',
      body: JSON.stringify({
        data: payload,
        action: formConfig.action,
        formId: formConfig.formid,
      }),
      headers: { 'Content-Type': 'application/json' },
    });

    if (response.ok) {
      if (formConfig.redirect) {
        window.location.href = formConfig.redirect;
      } else if (formConfig.thankyou) {
        const msg = document.createElement('div');
        msg.className = 'form-success';
        msg.textContent = formConfig.thankyou;
        form.replaceWith(msg);
      }
    } else {
      throw new Error(await response.text());
    }
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error('Form submission error:', e);
  } finally {
    form.setAttribute('data-submitting', 'false');
    if (submit) submit.disabled = false;
  }
}

/**
 * Form Container block — renders a form from authored child components.
 *
 * With underscore field grouping, each row has up to 5 cells:
 *   Cell 0 (field_*):      type, name, label
 *   Cell 1 (config_*):     type-specific settings
 *   Cell 2 (validation_*): required flag
 *   Cell 3 (wizard_*):     step number (default "-")
 *   Cell 4 (layout_*):     column span (default "12")
 *
 * Both wizard_step and layout_span use non-empty defaults so
 * their cells are always rendered, giving reliable positions.
 *
 * Field types and their cell contents:
 *   config:   field[type,action]
 *             | config[formid,redirect,thankyou,steptitles]
 *   input:    field[type,name,label] | config[type,placeholder]
 *             | validation[required] | wizard[step] | layout[span]
 *   options:  field[type,name,label]
 *             | config[display,options,placeholder]
 *             | validation[required] | wizard[step] | layout[span]
 *   textarea: field[type,name,label] | config[placeholder]
 *             | validation[required] | wizard[step] | layout[span]
 *   hidden:   field[type,name] | config[value,source]
 *             | wizard[step] | layout[span]
 *   upload:   field[type,name,label] | config[label]
 *             | validation[required] | wizard[step] | layout[span]
 *   button:   field[type,label] | config[role]
 *   label:    field[type] | content[text]
 *             | wizard[step] | layout[span]
 */
export default function decorate(block) {
  const { config: formConfig, remaining, configRow } = extractFormConfig(
    [...block.children],
  );

  const form = document.createElement('form');
  form.className = 'form-container-form';
  form.noValidate = false;
  form.action = '#';
  form.method = 'POST';

  form.dataset.action = formConfig.action;
  if (formConfig.formid) form.dataset.formid = formConfig.formid;
  if (formConfig.redirect) form.dataset.redirect = formConfig.redirect;
  if (formConfig.thankyou) form.dataset.thankyou = formConfig.thankyou;

  if (configRow) {
    const configEl = document.createElement('div');
    configEl.className = 'form-config-anchor';
    moveInstrumentation(configRow, configEl);
    form.append(configEl);
  }

  remaining.forEach((row) => {
    const cells = [...row.children];
    const fieldCell = cells[0];
    const configCell = cells[1];
    const validationCell = cells[2];

    const fieldType = getChild(fieldCell, 0).toLowerCase();
    const creator = FIELD_CREATORS[fieldType];

    if (creator) {
      const fieldEl = creator(fieldCell, configCell, validationCell);
      if (fieldEl) {
        const meta = getFieldMeta(cells);
        if (meta.step) fieldEl.dataset.step = meta.step;
        if (meta.span && meta.span !== '12') {
          fieldEl.style.setProperty('--field-span', meta.span);
        }
        moveInstrumentation(row, fieldEl);
        form.append(fieldEl);
      }
    }
  });

  block.textContent = '';
  block.append(form);

  // If any fields have step assignments, set up multi-step wizard
  if (isMultiStep(form)) {
    setupWizard(form, formConfig.steptitles);
  }

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    if (form.checkValidity()) {
      handleSubmit(form, formConfig);
    } else {
      const firstInvalid = form.querySelector(':invalid:not(fieldset)');
      if (firstInvalid) {
        firstInvalid.focus();
        firstInvalid.scrollIntoView({ behavior: 'smooth' });
      }
    }
  });
}
