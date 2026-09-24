package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.ConviteRepositorio;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.application.usecase.AceitarConviteService;
import com.deliveryplatform.merchant.application.usecase.GerenciarEquipeService;
import com.deliveryplatform.merchant.domain.exception.ConviteInvalido;
import com.deliveryplatform.merchant.domain.model.Convite;
import com.deliveryplatform.merchant.domain.model.EstadoDoMembro;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Papel;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.merchant.domain.model.Telefone;
import com.deliveryplatform.merchant.infrastructure.outbox.OutboxJpaEntity;
import com.deliveryplatform.merchant.infrastructure.outbox.OutboxSpringDataRepository;
import com.deliveryplatform.merchant.support.EquipeDeTeste;
import com.deliveryplatform.merchant.support.Infraestrutura;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A invariante 7, provada no ponto em que ela pode falhar.
 *
 * <p>Não é um teste de "o outbox grava". É um teste de que <b>o fato e o evento
 * ou valem os dois ou não vale nenhum</b>: cada operação de escrita da equipe
 * deixa exatamente uma linha, com o estado resultante dentro, e uma operação
 * recusada não deixa linha nenhuma.
 *
 * <p>O relay fica desligado. Aqui só interessa o que foi gravado; quem publica
 * tem o seu próprio teste, com broker de verdade.
 */
@SpringBootTest
@TestPropertySource(properties = "delivery.outbox.habilitado=false")
class OutboxIT extends Infraestrutura {

    @Autowired GerenciarEquipeService equipes;
    @Autowired AceitarConviteService aceites;
    @Autowired MembroRepositorio membros;
    @Autowired ConviteRepositorio convites;
    @Autowired EstabelecimentoRepositorio lojas;
    @Autowired OutboxSpringDataRepository outbox;
    @Autowired ObjectMapper json;

    private UUID loja;
    private UUID marli;
    private UUID bia;

    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @BeforeEach
    void umaPizzariaComDuasPessoas() {
        outbox.deleteAll();
        loja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        marli = membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora())).getUsuarioId();
        bia = membros.salvar(Membro.colaborador(
                UUID.randomUUID(), loja, EquipeDeTeste.DA_BIA, agora())).getUsuarioId();
    }

    private OutboxJpaEntity aUnicaLinha() {
        List<OutboxJpaEntity> linhas = outbox.findAll();
        assertThat(linhas)
                .as("uma operação, um evento — nem zero nem dois")
                .hasSize(1);
        return linhas.getFirst();
    }

    /** A linha guarda o envelope inteiro, que é o que sai para a fila. */
    private JsonNode envelopeDaUnicaLinha() {
        try {
            return json.readTree(aUnicaLinha().getPayload());
        } catch (Exception e) {
            throw new IllegalStateException("a linha do outbox não é JSON válido", e);
        }
    }

    private JsonNode payloadDaUnicaLinha() {
        return envelopeDaUnicaLinha().get("payload");
    }

    // ── uma linha por operação ──────────────────────────────────────────────

    @Test
    void suspender_deixa_um_evento_com_o_estado_novo() {
        equipes.suspender(loja, marli, bia);

        JsonNode payload = payloadDaUnicaLinha();
        assertThat(payload.get("usuarioId").asString()).isEqualTo(bia.toString());
        assertThat(payload.get("estabelecimentoId").asString()).isEqualTo(loja.toString());
        assertThat(payload.get("estado").asString())
                .as("o payload é o estado DEPOIS da mudança, não o que mudou")
                .isEqualTo(EstadoDoMembro.SUSPENSO.name());
    }

    @Test
    void reativar_deixa_um_evento() {
        equipes.suspender(loja, marli, bia);
        outbox.deleteAll();

        equipes.reativar(loja, marli, bia);

        assertThat(payloadDaUnicaLinha().get("estado").asString())
                .isEqualTo(EstadoDoMembro.ATIVO.name());
    }

    @Test
    void promover_deixa_um_evento_com_o_papel_novo() {
        equipes.promover(loja, marli, bia);

        assertThat(payloadDaUnicaLinha().get("papel").asString())
                .isEqualTo(Papel.ADMINISTRADOR.name());
    }

    @Test
    void rebaixar_deixa_um_evento_com_o_papel_novo() {
        equipes.promover(loja, marli, bia);
        outbox.deleteAll();

        equipes.rebaixar(loja, marli, bia);

        assertThat(payloadDaUnicaLinha().get("papel").asString())
                .isEqualTo(Papel.COLABORADOR.name());
    }

    @Test
    void remover_deixa_um_evento() {
        equipes.remover(loja, marli, bia);

        assertThat(payloadDaUnicaLinha().get("estado").asString())
                .isEqualTo(EstadoDoMembro.REMOVIDO.name());
    }

    @Test
    void sair_deixa_um_evento_do_proprio_vinculo() {
        equipes.sair(loja, bia);

        JsonNode payload = payloadDaUnicaLinha();
        assertThat(payload.get("usuarioId").asString()).isEqualTo(bia.toString());
        assertThat(payload.get("estado").asString()).isEqualTo(EstadoDoMembro.REMOVIDO.name());
    }

    @Test
    void alterar_permissoes_deixa_um_evento_com_a_lista_nova() {
        // A sétima operação administrativa. Não constava do rascunho da rodada, e
        // é a que mais importa emitir: uma revogação que não sai deixa o
        // consumidor autorizando com a lista velha.
        equipes.alterarPermissoes(loja, marli, bia, EnumSet.of(Permissao.VER_PEDIDO));

        assertThat(payloadDaUnicaLinha().get("permissoes"))
                .extracting(JsonNode::asString)
                .containsExactly(Permissao.VER_PEDIDO.name());
    }

    @Test
    void aceitar_convite_deixa_um_evento_do_vinculo_novo() {
        UUID quemConvidou = membros.buscarPorUsuarioELoja(marli, loja).orElseThrow().getId();
        Convite convite = convites.salvar(Convite.novo(
                loja, Telefone.de("11955554444"), EquipeDeTeste.DO_JUNIOR, quemConvidou, agora()));
        UUID junior = UUID.randomUUID();

        aceites.aceitar(convite.getToken(), junior);

        JsonNode payload = payloadDaUnicaLinha();
        assertThat(payload.get("usuarioId").asString()).isEqualTo(junior.toString());
        assertThat(payload.get("papel").asString()).isEqualTo(Papel.COLABORADOR.name());
        assertThat(payload.get("estado").asString()).isEqualTo(EstadoDoMembro.ATIVO.name());
    }

    @Test
    void token_desconhecido_e_recusado_como_convite_invalido_e_nao_deixa_evento() {
        assertThatThrownBy(() -> aceites.aceitar("nao-e-token-de-ninguem", UUID.randomUUID()))
                .as("a mesma recusa de token expirado, aceito ou cancelado — uma exceção "
                        + "diferente diria a quem adivinha se o token existe")
                .isInstanceOf(ConviteInvalido.class);

        assertThat(outbox.findAll()).isEmpty();
    }

    // ── o que a invariante 7 realmente pede ─────────────────────────────────

    @Test
    void operacao_recusada_nao_deixa_evento() {
        // A Bia não é administradora: a A1 recusa. Se o evento tivesse sido
        // gravado antes da regra rodar, ou fora da transação, ele sobreviveria
        // aqui — e algum serviço aplicaria uma suspensão que nunca aconteceu.
        assertThatThrownBy(() -> equipes.suspender(loja, bia, marli))
                .isInstanceOf(RuntimeException.class);

        assertThat(outbox.findAll())
                .as("o fato foi recusado; o evento não pode ter sobrado")
                .isEmpty();
    }

    @Test
    void o_ultimo_administrador_nao_sai_e_nao_deixa_evento() {
        equipes.remover(loja, marli, bia);
        outbox.deleteAll();

        assertThatThrownBy(() -> equipes.sair(loja, marli))
                .isInstanceOf(RuntimeException.class);

        assertThat(outbox.findAll()).isEmpty();
    }

    // ── o payload, como contrato ────────────────────────────────────────────

    @Test
    void o_payload_nao_traz_nome_nem_telefone_de_ninguem() {
        equipes.suspender(loja, marli, bia);

        assertThat(aUnicaLinha().getPayload())
                .as("nome e telefone são dado do identity-service, e a ADR-001 proíbe "
                        + "este serviço de importá-lo — um payload de fila é o jeito mais "
                        + "silencioso de furar essa regra")
                .doesNotContain("Marli")
                .doesNotContain("+55");
    }

    @Test
    void o_payload_traz_a_lista_inteira_de_permissoes() {
        equipes.suspender(loja, marli, bia);

        JsonNode permissoes = payloadDaUnicaLinha().get("permissoes");
        assertThat(permissoes.isArray()).isTrue();
        assertThat(permissoes)
                .as("ausência é negação: quem aplica substitui a lista, não soma a ela — "
                        + "tratar como incremento transforma revogação em concessão permanente")
                .isNotEmpty();
    }

    @Test
    void cada_linha_tem_eventId_igual_a_chave_primaria() {
        equipes.suspender(loja, marli, bia);

        OutboxJpaEntity linha = aUnicaLinha();
        assertThat(envelopeDaUnicaLinha().get("eventId").asString())
                .as("o eventId nasce uma vez, junto com a linha — é isso que permite "
                        + "reenviar sem que o consumidor veja um evento novo")
                .isEqualTo(linha.getId().toString());
    }

    @Test
    void o_envelope_e_o_esquema_comum_do_repositorio() {
        equipes.suspender(loja, marli, bia);

        JsonNode envelope = envelopeDaUnicaLinha();
        assertThat(envelope.propertyNames())
                .as("contracts/events/_envelope-v1.json, com additionalProperties: false")
                .containsExactlyInAnyOrder(
                        "eventId", "eventType", "eventVersion", "occurredAt", "correlationId", "payload");
        assertThat(envelope.get("eventType").asString())
                .as("a versão vive em eventVersion, nunca dentro do eventType")
                .isEqualTo("VinculoAlterado");
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("payload").propertyNames())
                .as("o instante está no envelope; no payload seriam dois campos obrigados a concordar")
                .containsExactlyInAnyOrder(
                        "estabelecimentoId", "usuarioId", "membroId", "papel", "estado", "permissoes");
    }

    @Test
    void a_linha_nasce_pendente_e_com_a_chave_de_rota_do_contrato() {
        equipes.suspender(loja, marli, bia);

        OutboxJpaEntity linha = aUnicaLinha();
        assertThat(linha.getPublicadoEm()).isNull();
        assertThat(linha.getTentativas()).isZero();
        assertThat(linha.getTipo()).isEqualTo("VinculoAlterado");
        assertThat(linha.getVersao()).isEqualTo((short) 1);
        assertThat(linha.getAgregado()).isEqualTo("membro");
        assertThat(linha.getChaveDeRota()).isEqualTo("merchant.vinculo.alterado.v1");
    }

    @Test
    void o_agregadoId_e_o_membro_e_nao_o_estabelecimento() {
        equipes.suspender(loja, marli, bia);

        Membro vinculo = membros.buscarPorUsuarioELoja(bia, loja).orElseThrow();
        assertThat(aUnicaLinha().getAgregadoId())
                .as("o fato aconteceu num vínculo; é o vínculo que se rastreia no outbox")
                .isEqualTo(vinculo.getId())
                .isNotEqualTo(loja);
    }
}
