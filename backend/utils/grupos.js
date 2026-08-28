const PREFIJOS_GRUPO = [
  'SMW', 'ATW', 'ATWX', 'IMTW', 'RBM', 'RMBX',
  'SMM', 'ATM', 'ETSM', 'IEDM', 'IERM', 'IMTM'
];

const GRUPO_VALIDO = new RegExp(`^(?:${PREFIJOS_GRUPO.join('|')})[1-5][1-9]$`);

module.exports = { PREFIJOS_GRUPO, GRUPO_VALIDO };
