import { Navigate, Route, Routes } from 'react-router';
import { EntrarPage } from './paginas/EntrarPage';
import { CadastrarPage } from './paginas/CadastrarPage';
import { CadastroDeLojaPage } from './paginas/CadastroDeLojaPage';
import { ConvitePage } from './paginas/ConvitePage';
import { PainelPage } from './paginas/PainelPage';
import { InicioDoPainel } from './painel/InicioDoPainel';
import {
  ListaDaSecao,
  EquipeDaLoja,
  ConfiguracaoDaLojaDoPainel,
  HorarioAntigo,
  LojaDoPainel,
  NovoProdutoDaLoja,
  ProdutoDaSecao,
  SecaoInicial,
} from './painel/LojaDoPainel';
import { ExigeSessao } from './rotas/ExigeSessao';

/**
 * As rotas, e a de `*` não é descuido: sem ela, um caminho errado renderiza
 * página em branco sem erro nenhum no console, que é o defeito mais difícil de
 * diagnosticar num roteador.
 */
export function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/painel" replace />} />
      <Route path="/entrar" element={<EntrarPage />} />
      <Route path="/cadastrar" element={<CadastrarPage />} />
      {/* ADR-070 §4: pública, fora do ExigeSessao — quem abre o link pode não ter conta. */}
      <Route path="/convite/:token" element={<ConvitePage />} />
      {/* ADR-061: a loja escolhida mora na URL, e cada tela do painel tem endereço. */}
      <Route
        path="/painel"
        element={
          <ExigeSessao>
            <PainelPage />
          </ExigeSessao>
        }
      >
        <Route index element={<InicioDoPainel />} />
        <Route path="lojas/nova" element={<CadastroDeLojaPage />} />
        <Route path="lojas/:estabelecimentoId" element={<LojaDoPainel />}>
          <Route index element={<SecaoInicial />} />
          <Route path="produtos" element={<ListaDaSecao secao="cardapio" />} />
          {/* Segmento fixo ganha do :produtoId no ranking do roteador. ADR-067 §3. */}
          <Route path="produtos/novo" element={<NovoProdutoDaLoja />} />
          <Route path="produtos/:produtoId" element={<ProdutoDaSecao secao="cardapio" />} />
          <Route path="disponibilidade" element={<ListaDaSecao secao="disponibilidade" />} />
          <Route path="equipe" element={<EquipeDaLoja />} />
          <Route path="configuracao" element={<ConfiguracaoDaLojaDoPainel />} />
          {/* O endereço da W-H continua valendo: leva à configuração, onde o horário mora. */}
          <Route path="horario" element={<HorarioAntigo />} />
          <Route
            path="disponibilidade/:produtoId"
            element={<ProdutoDaSecao secao="disponibilidade" />}
          />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/painel" replace />} />
    </Routes>
  );
}
