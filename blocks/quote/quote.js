export default async function decorate(block) {
  const rows = [...block.children];
  const [quotation, attribution] = rows.map((c) => c.firstElementChild);
  const countries = rows[2]?.textContent.trim();
  const blockquote = document.createElement('blockquote');
  // decorate quotation
  quotation.className = 'quote-quotation';
  blockquote.append(quotation);
  // decoration attribution
  if (attribution) {
    attribution.className = 'quote-attribution';
    blockquote.append(attribution);
    const ems = attribution.querySelectorAll('em');
    ems.forEach((em) => {
      const cite = document.createElement('cite');
      cite.innerHTML = em.innerHTML;
      em.replaceWith(cite);
    });
  }
  // decorate countries
  if (countries) {
    const countriesEl = document.createElement('p');
    countriesEl.className = 'quote-countries';
    countriesEl.textContent = countries;
    blockquote.append(countriesEl);
  }
  block.innerHTML = '';
  block.append(blockquote);
}
