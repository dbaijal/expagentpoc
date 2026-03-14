import { moveInstrumentation } from '../../scripts/scripts.js';

/**
 * Reads text content from a block cell, trimming whitespace.
 * @param {Element} cell - The cell element
 * @returns {string} The trimmed text content
 */
function getCellText(cell) {
  return cell ? cell.textContent.trim() : '';
}

/**
 * Reads rich HTML content from a block cell.
 * @param {Element} cell - The cell element
 * @returns {string} The inner HTML
 */
function getCellHTML(cell) {
  return cell ? cell.innerHTML.trim() : '';
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
 * Model columns: fieldType | name | label | inputType | placeholder | required
 */
function createInputField(cols) {
  const name = getCellText(cols[1]);
  const label = getCellText(cols[2]);
  const inputType = getCellText(cols[3]) || 'text';
  const placeholder = getCellText(cols[4]);
  const required = getCellText(cols[5]) === 'true';
  const id = generateId(name);

  const wrapper = document.createElement('div');
  wrapper.className = 'field-wrapper input-wrapper';

  const labelEl = document.createElement('label');
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
  labelEl.id = `${id}-label`;

  wrapper.append(labelEl, input);
  return wrapper;
}

/**
 * Creates an options field (select dropdown, radio buttons, or checkboxes).
 * Model columns: fieldType | name | label | optionType | options | placeholder | required
 */
function createOptionsField(cols) {
  const name = getCellText(cols[1]);
  const label = getCellText(cols[2]);
  const optionType = getCellText(cols[3]) || 'select';
  const optionsStr = getCellText(cols[4]);
  const placeholder = getCellText(cols[5]);
  const required = getCellText(cols[6]) === 'true';
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
    // radio or checkbox
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
 * Model columns: fieldType | name | label | placeholder | required
 */
function createTextareaField(cols) {
  const name = getCellText(cols[1]);
  const label = getCellText(cols[2]);
  const placeholder = getCellText(cols[3]);
  const required = getCellText(cols[4]) === 'true';
  const id = generateId(name);

  const wrapper = document.createElement('div');
  wrapper.className = 'field-wrapper textarea-wrapper';

  const labelEl = document.createElement('label');
  labelEl.setAttribute('for', id);
  labelEl.id = `${id}-label`;
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
 * Model columns: fieldType | name | value | valueSource
 */
function createHiddenField(cols) {
  const name = getCellText(cols[1]);
  const value = getCellText(cols[2]);
  const valueSource = getCellText(cols[3]) || 'static';

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
 * Model columns: fieldType | name | label | buttonText | required
 */
function createUploadField(cols) {
  const name = getCellText(cols[1]);
  const label = getCellText(cols[2]);
  const buttonText = getCellText(cols[3]) || 'Choose File';
  const required = getCellText(cols[4]) === 'true';
  const id = generateId(name);

  const wrapper = document.createElement('div');
  wrapper.className = 'field-wrapper upload-wrapper';

  const labelEl = document.createElement('label');
  labelEl.setAttribute('for', id);
  labelEl.id = `${id}-label`;
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
 * Model columns: fieldType | label | buttonType
 */
function createButtonField(cols) {
  const label = getCellText(cols[1]) || 'Submit';
  const buttonType = getCellText(cols[2]) || 'submit';

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
 * Model columns: fieldType | text (richtext)
 */
function createLabelField(cols) {
  const html = getCellHTML(cols[1]);

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
 * Handles form submission.
 */
async function handleSubmit(form, block) {
  if (form.getAttribute('data-submitting') === 'true') return;

  const submit = form.querySelector('button[type="submit"]');
  try {
    form.setAttribute('data-submitting', 'true');
    if (submit) submit.disabled = true;

    const payload = generatePayload(form);
    const actionType = block.dataset.actionType || 'API';
    const formId = block.dataset.formId || '';

    const response = await fetch(form.action, {
      method: 'POST',
      body: JSON.stringify({ data: payload, actionType, formId }),
      headers: { 'Content-Type': 'application/json' },
    });

    if (response.ok) {
      const { redirectUrl } = block.dataset;
      const { thankYouMessage } = block.dataset;

      if (redirectUrl) {
        window.location.href = redirectUrl;
      } else if (thankYouMessage) {
        const msg = document.createElement('div');
        msg.className = 'form-success';
        msg.textContent = thankYouMessage;
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
 * Block structure (each row = a field component):
 *   Row columns vary by fieldType (first column):
 *   - input:    fieldType | name | label | inputType | placeholder | required
 *   - options:  fieldType | name | label | optionType | options | placeholder | required
 *   - textarea: fieldType | name | label | placeholder | required
 *   - hidden:   fieldType | name | value | valueSource
 *   - upload:   fieldType | name | label | buttonText | required
 *   - button:   fieldType | label | buttonType
 *   - label:    fieldType | text (richtext)
 */
export default function decorate(block) {
  const form = document.createElement('form');
  form.className = 'form-container-form';
  form.noValidate = false;

  // Set form action from block data attributes (set by UE properties panel)
  const actionUrl = block.dataset.actionUrl || '#';
  form.action = actionUrl;
  form.method = 'POST';

  [...block.children].forEach((row) => {
    const cols = [...row.children];
    const fieldType = getCellText(cols[0]).toLowerCase();
    const creator = FIELD_CREATORS[fieldType];

    if (creator) {
      const fieldEl = creator(cols);
      if (fieldEl) {
        moveInstrumentation(row, fieldEl);
        form.append(fieldEl);
      }
    }
  });

  block.textContent = '';
  block.append(form);

  // Handle form submission
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    if (form.checkValidity()) {
      handleSubmit(form, block);
    } else {
      const firstInvalid = form.querySelector(':invalid:not(fieldset)');
      if (firstInvalid) {
        firstInvalid.focus();
        firstInvalid.scrollIntoView({ behavior: 'smooth' });
      }
    }
  });
}
