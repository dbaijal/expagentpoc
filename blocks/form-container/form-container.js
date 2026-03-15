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
function createLabelField(_fieldCell, contentCell) {
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
 * Cell 1 (config): [formid, redirect, thankyou]
 */
function extractFormConfig(rows) {
  const config = {
    action: 'API', formid: '', redirect: '', thankyou: '',
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
      if (!configRow) configRow = row;
    } else {
      remaining.push(row);
    }
  });

  return { config, remaining, configRow };
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
 * With underscore field grouping, each row has up to 3 cells:
 *   Cell 0 (field_*):      type, name, label
 *   Cell 1 (config_*):     type-specific settings
 *   Cell 2 (validation_*): required flag
 *
 * Field types and their cell contents:
 *   config:   field[type,action]     | config[formid,redirect,thankyou]
 *   input:    field[type,name,label] | config[type,placeholder]         | validation[required]
 *   options:  field[type,name,label] | config[display,options,placeholder]
 *             | validation[required]
 *   textarea: field[type,name,label] | config[placeholder]              | validation[required]
 *   hidden:   field[type,name]       | config[value,source]
 *   upload:   field[type,name,label] | config[label]                    | validation[required]
 *   button:   field[type,label]      | config[role]
 *   label:    field[type]            | content[text]
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

  if (configRow) {
    const configEl = document.createElement('div');
    configEl.className = 'form-config-summary';
    const label = document.createElement('span');
    label.className = 'config-label';
    label.textContent = 'Form Config';
    const details = document.createElement('span');
    details.className = 'config-details';
    const parts = [`Action: ${formConfig.action}`];
    if (formConfig.formid) parts.push(`ID: ${formConfig.formid}`);
    details.textContent = parts.join(' | ');
    configEl.append(label, details);
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
        moveInstrumentation(row, fieldEl);
        form.append(fieldEl);
      }
    }
  });

  block.textContent = '';
  block.append(form);

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
