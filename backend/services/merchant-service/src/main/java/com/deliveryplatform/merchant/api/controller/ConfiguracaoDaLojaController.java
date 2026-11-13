package com.deliveryplatform.merchant.api.controller;

import com.deliveryplatform.merchant.api.dto.AreasDeEntregaRequest;
import com.deliveryplatform.merchant.api.dto.AreasDeEntregaResponse;
import com.deliveryplatform.merchant.application.port.in.ConsultarAreasDeEntrega;
import com.deliveryplatform.merchant.application.port.in.DefinirAreasDeEntrega;
import com.deliveryplatform.merchant.application.port.in.DefinirAreasDeEntrega.AreaPedida;
import com.deliveryplatform.merchant.api.dto.DescontoDeRetiradaRequest;
import com.deliveryplatform.merchant.api.dto.DescontoDeRetiradaResponse;
import com.deliveryplatform.merchant.api.dto.FaixaDeHorarioRequest;
import com.deliveryplatform.merchant.api.dto.HorarioRequest;
import com.deliveryplatform.merchant.api.dto.HorarioResponse;
import com.deliveryplatform.merchant.api.dto.PedidoMinimoRequest;
import com.deliveryplatform.merchant.api.dto.PedidoMinimoResponse;
import com.deliveryplatform.merchant.api.seguranca.SujeitoDoToken;
import com.deliveryplatform.merchant.application.port.in.ConsultarHorario;
import com.deliveryplatform.merchant.application.port.in.ConsultarOperacao;
import com.deliveryplatform.merchant.application.port.in.DefinirDescontoDeRetirada;
import com.deliveryplatform.merchant.application.port.in.DefinirHorario;
import com.deliveryplatform.merchant.application.port.in.DefinirPedidoMinimo;
import com.deliveryplatform.merchant.application.port.in.DefinirHorario.FaixaPedida;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A configuração da loja, sob {@code /settings} — o prefixo que o gateway já roteia para
 * o {@code merchant} ({@code merchant-settings}, ADR-012). O horário (ADR-073), o pedido
 * mínimo e o desconto de retirada (ADR-080) e as áreas de entrega (ADR-081), cada um
 * sub-recurso irmão, lido e escrito, sob a mesma {@code CONFIGURAR_LOJA}.
 *
 * <p>O {@code estabelecimentoId} da URL <b>autoriza</b>: o caso de uso o confronta com o
 * vínculo do {@code sub} antes de buscar a loja (invariante 9).
 */
@RestController
@RequestMapping("/api/v1/merchants/{estabelecimentoId}/settings")
public class ConfiguracaoDaLojaController {

    private final DefinirHorario horarios;
    private final ConsultarHorario leitura;
    private final ConsultarOperacao operacoes;
    private final DefinirPedidoMinimo minimos;
    private final DefinirDescontoDeRetirada descontos;
    private final ConsultarAreasDeEntrega areasLidas;
    private final DefinirAreasDeEntrega areasEscritas;

    public ConfiguracaoDaLojaController(DefinirHorario horarios, ConsultarHorario leitura,
                                        ConsultarOperacao operacoes, DefinirPedidoMinimo minimos,
                                        DefinirDescontoDeRetirada descontos,
                                        ConsultarAreasDeEntrega areasLidas,
                                        DefinirAreasDeEntrega areasEscritas) {
        this.horarios = horarios;
        this.leitura = leitura;
        this.operacoes = operacoes;
        this.minimos = minimos;
        this.descontos = descontos;
        this.areasLidas = areasLidas;
        this.areasEscritas = areasEscritas;
    }

    /**
     * O horário semanal como está gravado — a irmã de leitura do {@code PUT} (ADR-073, emenda
     * de 17/10/2026). <b>Loja que nunca abre por horário responde 200 com o mapa vazio</b>, e
     * não 404: vazio é um horário, e não a ausência de um.
     */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "O horário gravado; mapa vazio quando a loja não abre por horário"),
            @ApiResponse(responseCode = "400", description = "Identificador da loja malformado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem CONFIGURAR_LOJA ou loja inexistente — a mesma recusa", content = @Content)
    })
    @GetMapping(path = "/horario", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lê o horário semanal da loja",
            description = "Exige CONFIGURAR_LOJA. Mapa vazio quando a loja não abre por horário.")
    public HorarioResponse lerHorario(
            @PathVariable UUID estabelecimentoId, @AuthenticationPrincipal Jwt token) {
        return HorarioResponse.de(leitura.daLoja(estabelecimentoId, SujeitoDoToken.de(token)));
    }

    /**
     * Substitui o horário semanal inteiro. <b>Não publica evento</b>: a abertura do
     * expediente é da varredura (ADR-073 §3), que nota a loja na passada seguinte.
     */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "O horário como ficou gravado"),
            @ApiResponse(responseCode = "400", description = "Corpo malformado, dia que não é dia da semana, identificador da loja malformado, ou faixa que o domínio recusa — início igual ao fim, ou a mesma faixa duas vezes no mesmo dia", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem CONFIGURAR_LOJA ou loja inexistente — a mesma recusa", content = @Content)
    })
    @PutMapping(path = "/horario", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Define o horário semanal da loja",
            description = "Exige CONFIGURAR_LOJA. Substitui o horário inteiro; mapa vazio "
                    + "volta a loja a \"nunca abre por horário\".")
    public HorarioResponse definirHorario(
            @PathVariable UUID estabelecimentoId,
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody HorarioRequest pedido) {

        Map<DayOfWeek, List<FaixaPedida>> horario = new EnumMap<>(DayOfWeek.class);
        pedido.horarioDeFuncionamento().forEach((dia, faixas) -> horario.put(dia,
                faixas.stream().map(ConfiguracaoDaLojaController::paraComando).toList()));

        return HorarioResponse.de(
                horarios.definir(estabelecimentoId, SujeitoDoToken.de(token), horario));
    }

    /** Os pedidos mínimos como estão gravados — uma entrada por modalidade aceita (ADR-080). */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Os mínimos gravados, zero inclusive"),
            @ApiResponse(responseCode = "400", description = "Identificador da loja malformado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem CONFIGURAR_LOJA ou loja inexistente — a mesma recusa", content = @Content)
    })
    @GetMapping(path = "/pedido-minimo", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lê os pedidos mínimos da loja, por modalidade",
            description = "Exige CONFIGURAR_LOJA. Zero é \"sem mínimo\".")
    public PedidoMinimoResponse lerPedidoMinimo(
            @PathVariable UUID estabelecimentoId, @AuthenticationPrincipal Jwt token) {
        return PedidoMinimoResponse.de(operacoes.daLoja(estabelecimentoId, SujeitoDoToken.de(token)));
    }

    /**
     * Substitui os pedidos mínimos inteiros (ADR-028, ADR-080). O mínimo é sobre o valor dos
     * itens; quem o cobra é a guarda de T01, no {@code order}. Não publica evento.
     */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Os mínimos como ficaram gravados"),
            @ApiResponse(responseCode = "400", description = "Corpo malformado, modalidade desconhecida, identificador da loja malformado, ou mínimos que o domínio recusa — modalidade aceita sem entrada, entrada para modalidade que a loja não aceita, ou valor negativo (M17)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem CONFIGURAR_LOJA ou loja inexistente — a mesma recusa", content = @Content)
    })
    @PutMapping(path = "/pedido-minimo", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Define os pedidos mínimos da loja, por modalidade",
            description = "Exige CONFIGURAR_LOJA. Substitui todos; uma entrada por modalidade "
                    + "aceita. Zero vale como \"sem mínimo\".")
    public PedidoMinimoResponse definirPedidoMinimo(
            @PathVariable UUID estabelecimentoId,
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody PedidoMinimoRequest pedido) {
        return PedidoMinimoResponse.de(minimos.definir(
                estabelecimentoId, SujeitoDoToken.de(token), pedido.pedidoMinimoPorModalidade()));
    }

    /** O desconto de retirada como está gravado (ADR-080). */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "O desconto gravado; zero quando não há"),
            @ApiResponse(responseCode = "400", description = "Identificador da loja malformado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem CONFIGURAR_LOJA ou loja inexistente — a mesma recusa", content = @Content)
    })
    @GetMapping(path = "/desconto-retirada", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lê o desconto de retirada da loja",
            description = "Exige CONFIGURAR_LOJA. Zero é sem desconto.")
    public DescontoDeRetiradaResponse lerDescontoDeRetirada(
            @PathVariable UUID estabelecimentoId, @AuthenticationPrincipal Jwt token) {
        return DescontoDeRetiradaResponse.de(
                operacoes.daLoja(estabelecimentoId, SujeitoDoToken.de(token)));
    }

    /** Troca o desconto de retirada (ADR-024, ADR-080). Não publica evento. */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "O desconto como ficou gravado"),
            @ApiResponse(responseCode = "400", description = "Corpo malformado, identificador da loja malformado, ou desconto negativo (M15)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem CONFIGURAR_LOJA ou loja inexistente — a mesma recusa", content = @Content)
    })
    @PutMapping(path = "/desconto-retirada", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Define o desconto de retirada da loja",
            description = "Exige CONFIGURAR_LOJA. Valor fixo, para a loja inteira; zero é sem desconto.")
    public DescontoDeRetiradaResponse definirDescontoDeRetirada(
            @PathVariable UUID estabelecimentoId,
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody DescontoDeRetiradaRequest pedido) {
        return DescontoDeRetiradaResponse.de(descontos.definir(
                estabelecimentoId, SujeitoDoToken.de(token), pedido.descontoDeRetirada()));
    }

    /** As áreas de entrega como estão gravadas, ativas e desativadas (ADR-081). */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "As áreas gravadas; lista vazia quando a loja não entrega em lugar nenhum"),
            @ApiResponse(responseCode = "400", description = "Identificador da loja malformado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem CONFIGURAR_LOJA ou loja inexistente — a mesma recusa", content = @Content)
    })
    @GetMapping(path = "/areas-entrega", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lê as áreas de entrega da loja",
            description = "Exige CONFIGURAR_LOJA. Lista vazia quando a loja não entrega em lugar nenhum.")
    public AreasDeEntregaResponse lerAreasDeEntrega(
            @PathVariable UUID estabelecimentoId, @AuthenticationPrincipal Jwt token) {
        return AreasDeEntregaResponse.de(areasLidas.daLoja(estabelecimentoId, SujeitoDoToken.de(token)));
    }

    /**
     * Substitui as áreas de entrega inteiras (ADR-020, ADR-081). Ninguém guarda o identificador de
     * uma área — o pedido congela nome e taxa por valor —, e por isso a lista se troca inteira.
     * Não publica evento.
     */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "As áreas como ficaram gravadas"),
            @ApiResponse(responseCode = "400", description = "Corpo malformado, identificador da loja malformado, ou áreas que o domínio recusa — CEP inválido ou faixa invertida, taxa negativa, faixa repetida na mesma área, duas áreas com o mesmo nome (M9), ou faixas que se cruzam entre áreas ativas (M10)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem CONFIGURAR_LOJA ou loja inexistente — a mesma recusa", content = @Content)
    })
    @PutMapping(path = "/areas-entrega", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Define as áreas de entrega da loja",
            description = "Exige CONFIGURAR_LOJA. Substitui todas; lista vazia volta a loja a não "
                    + "entregar em lugar nenhum. Taxa zero é entrega grátis, e bairro sem área não é.")
    public AreasDeEntregaResponse definirAreasDeEntrega(
            @PathVariable UUID estabelecimentoId,
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody AreasDeEntregaRequest pedido) {
        List<AreaPedida> areas = pedido.areasDeEntrega().stream()
                .map(area -> new AreaPedida(area.nome(), area.taxa(), area.ativa(),
                        area.faixasDeCep().stream()
                                .map(f -> new DefinirAreasDeEntrega.FaixaPedida(f.inicio(), f.fim()))
                                .toList()))
                .toList();
        return AreasDeEntregaResponse.de(
                areasEscritas.definir(estabelecimentoId, SujeitoDoToken.de(token), areas));
    }

    private static FaixaPedida paraComando(FaixaDeHorarioRequest faixa) {
        return new FaixaPedida(faixa.inicio(), faixa.fim());
    }
}
