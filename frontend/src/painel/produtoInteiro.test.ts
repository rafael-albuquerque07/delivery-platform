import { describe, expect, it } from 'vitest';

import { acrescimoLegivel, regraDoGrupo } from './produtoInteiro';

describe('regraDoGrupo', () => {
  it('sai do min e do max, e de mais nada', () => {
    expect(regraDoGrupo(1, 1)).toBe('Escolha 1');
    expect(regraDoGrupo(1, 2)).toBe('Escolha de 1 a 2');
    expect(regraDoGrupo(0, 2)).toBe('Opcional, até 2');
  });
});

describe('acrescimoLegivel', () => {
  it('positivo e negativo com o sinal do Intl', () => {
    expect(acrescimoLegivel(8)).toMatch(/^\+R\$\s8,00$/);
    expect(acrescimoLegivel(-2)).toMatch(/^-R\$\s2,00$/);
  });

  it('o zero não vira texto nenhum', () => {
    expect(acrescimoLegivel(0)).toBeNull();
  });

  it('sem valor no contrato, também nada — nunca "R$ 0,00" inventado', () => {
    expect(acrescimoLegivel(undefined)).toBeNull();
  });
});
