-- A LANCAR_PEDIDO nasce (M3-3; ADR-011, emenda), e alcança quem já existe — o que a
-- CONFIGURAR_LOJA não fez em 17/10 (ADR-011, emenda de 19/10), e por isso ela vai junto.
--
-- Quem recebe: o fundador, que é o primeiro membro da loja. As três condições juntas:
-- papel ADMINISTRADOR, o primeiro membro criado na loja, e as dez permissões anteriores
-- à E-4 — Membro.fundador dá o conjunto inteiro na criação. O colaborador convidado com
-- as dez não entra, nem o administrador promovido depois: papel não é lista de
-- permissões, e a promoção nunca deu permissão.
--
-- Sem filtro de estado: permissão não é acesso, e SUSPENSO ou REMOVIDO já barram o
-- acesso sozinhos.
--
-- "on conflict do nothing": quem já tem uma das duas — os fundadores criados depois da
-- E-4 já têm a CONFIGURAR_LOJA — fica como está.
insert into membro_permissao (membro_id, permissao)
select m.id, nova.permissao
from membro m
cross join (values ('LANCAR_PEDIDO'), ('CONFIGURAR_LOJA')) as nova (permissao)
where m.papel = 'ADMINISTRADOR'
  and m.criado_em = (select min(o.criado_em)
                     from membro o
                     where o.estabelecimento_id = m.estabelecimento_id)
  and (select count(*)
       from membro_permissao p
       where p.membro_id = m.id
         and p.permissao in ('VER_PRODUTO', 'CRIAR_PRODUTO', 'ALTERAR_PRODUTO', 'DESATIVAR_PRODUTO',
                             'VER_PEDIDO', 'ALTERAR_STATUS', 'VER_VENDAS', 'VER_ENTREGA',
                             'GERENCIAR_EQUIPE', 'GERENCIAR_JORNADA')) = 10
on conflict (membro_id, permissao) do nothing;
