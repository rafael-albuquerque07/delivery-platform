package com.deliveryplatform.merchant.api.error;

import com.deliveryplatform.merchant.application.exception.AcessoNegado;
import com.deliveryplatform.merchant.application.exception.CadastroDeLojaRecusado;
import com.deliveryplatform.merchant.application.exception.ConviteRecusado;
import com.deliveryplatform.merchant.application.exception.ConviteSemAutoridade;
import com.deliveryplatform.merchant.application.exception.AreasRecusadas;
import com.deliveryplatform.merchant.application.exception.HorarioRecusado;
import com.deliveryplatform.merchant.application.exception.OperacaoRecusada;
import com.deliveryplatform.merchant.domain.exception.ConviteInvalido;
import com.deliveryplatform.merchant.domain.exception.JaPertenceAEquipe;
import com.deliveryplatform.merchant.application.exception.SemExpedientePorHorario;
import org.springframework.http.HttpStatus;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Dois tratadores: {@code AcessoNegado} → 403 e {@code SemExpedientePorHorario}
 * → 409 (G-C1). Esta frase dizia "um tratador só" desde a C-B, e a G-C1 pôs o
 * segundo sem emendá-la.
 *
 * <p><b>O contrato não sai daqui.</b> Cada rota declara as próprias recusas em
 * {@code @ApiResponses}, e o {@code ContratoDeErrosIT} prova as duas direções
 * (ADR-053). {@code @ResponseStatus} nestes métodos não mudaria nada: o springdoc
 * o ignora com {@code override-with-generic-response: false}, e em execução quem
 * manda é o status do {@code ProblemDetail}.
 *
 * <p>Este serviço tem sete exceções de domínio escritas — escalada, A2, A3,
 * convite inválido, já pertence à equipe. <b>Nenhuma delas é alcançável por
 * HTTP ainda</b>, porque as quatro rotas que existem são leituras. Escrever os
 * tratadores agora seria escrever sete blocos que nenhuma requisição executa —
 * a peça-que-nunca-rodou em forma de {@code @ExceptionHandler}, e ainda por
 * cima a mais perigosa: um tratador errado só aparece no dia em que a exceção
 * finalmente acontece, que é o pior dia para descobrir.
 *
 * <p>Cada um nasce com a rota que o alcança.
 */
@RestControllerAdvice
public class TratadorDeErros {

    /**
     * 403 para os três casos de M7, com o mesmo corpo.
     *
     * <p><b>403 e não 404</b>, mesmo quando a loja não existe: um 404 aqui
     * diria que aquele identificador não é uma loja, e a diferença entre as duas
     * respostas é um scanner de estabelecimentos escrito em códigos de status.
     */
    @ExceptionHandler(AcessoNegado.class)
    public ProblemDetail acessoNegado(AcessoNegado excecao) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "sem acesso a este estabelecimento");
    }

    /**
     * 409 para a loja que não abre por horário — ADR-049 §5.
     *
     * <p>Nasce com a rota que o alcança, o {@code expediente-corrente}. É
     * <i>estado do mundo</i>, não erro do chamador: a loja existe, e o que não
     * existe é um expediente para carimbar.
     */
    @ExceptionHandler(SemExpedientePorHorario.class)
    public ProblemDetail semExpedientePorHorario(SemExpedientePorHorario excecao) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "a loja não abre por horário");
    }

    /**
     * 400 para a loja que o agregado recusa (ADR-060). Nasce de um ponto só — a
     * construção no {@code CriarEstabelecimentoService} —, e por isso é tipo próprio e
     * não {@code IllegalArgumentException}: um tratador desse tipo transformaria em 400
     * todo defeito que o lançasse.
     *
     * <p><b>O {@code detail} é escrito aqui, por motivo — nunca
     * {@code excecao.getMessage()}</b> (W-E). A tela o mostra como veio (ADR-055), e a
     * mensagem do agregado carrega código de invariante — <i>"(M12)"</i> — e às vezes o
     * valor digitado. Ela fica na causa, para o log. É o que o tratador do telefone do
     * {@code identity} já fazia, com a razão escrita no javadoc dele.
     */
    @ExceptionHandler(CadastroDeLojaRecusado.class)
    public ProblemDetail cadastroDeLojaRecusado(CadastroDeLojaRecusado excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, fraseDe(excecao.motivo()));
    }

    /**
     * 400 para o convite que o agregado recusa (ADR-069). A frase é daqui, por motivo,
     * e não diz quais permissões faltam: o nome do enum é técnico, e a tela que oferece
     * as caixas é a mesma que sabe quais o autor tem.
     */
    @ExceptionHandler(ConviteRecusado.class)
    public ProblemDetail conviteRecusado(ConviteRecusado excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, fraseDe(excecao.motivo()));
    }

    /**
     * 400 para o horário que o domínio recusa (ADR-073 §4) — as duas recusas que existem,
     * e só elas. A frase é daqui, por motivo; a mensagem do domínio, com a faixa como ela
     * chegou, fica na causa.
     */
    @ExceptionHandler(HorarioRecusado.class)
    public ProblemDetail horarioRecusado(HorarioRecusado excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, fraseDe(excecao.motivo()));
    }

    /**
     * 400 para o pedido mínimo ou o desconto que o domínio recusa (ADR-080) — M17 e M15. A
     * frase é daqui, por motivo; a mensagem do domínio, com os valores como chegaram, fica na
     * causa.
     */
    /**
     * 400 para as áreas de entrega que o domínio recusa (ADR-081) — CEP, E3 e faixa repetida, M9
     * e M10. A frase é daqui, por motivo; a mensagem do domínio, com o nome e a faixa como
     * chegaram, fica na causa.
     */
    @ExceptionHandler(AreasRecusadas.class)
    public ProblemDetail areasRecusadas(AreasRecusadas excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, fraseDe(excecao.motivo()));
    }

    @ExceptionHandler(OperacaoRecusada.class)
    public ProblemDetail operacaoRecusada(OperacaoRecusada excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, fraseDe(excecao.motivo()));
    }

    /**
     * 404 para o convite que não vale: inexistente, expirado, aceito ou cancelado — a
     * mesma frase nos quatro (ADR-069 §3). Distinguir contaria a quem tenta tokens no escuro
     * quais já existiram. A frase diz o que fazer, e não por quê.
     */
    @ExceptionHandler(ConviteInvalido.class)
    public ProblemDetail conviteInvalido(ConviteInvalido excecao) {
        return semToken(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "este convite não vale mais; peça outro a quem te convidou"));
    }

    /**
     * 409: o token conferiu, mas quem convidou não pode mais conceder o que ofereceu (A2 no
     * aceite, ADR-069 §5). Não conta nada a quem adivinha — só chega aqui quem tem o token.
     */
    @ExceptionHandler(ConviteSemAutoridade.class)
    public ProblemDetail conviteSemAutoridade(ConviteSemAutoridade excecao) {
        return semToken(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "quem te convidou não pode mais conceder este acesso; peça um novo convite"));
    }

    /** 409: quem aceita já está na equipe. A única resposta que evita pedir outro convite. */
    @ExceptionHandler(JaPertenceAEquipe.class)
    public ProblemDetail jaPertenceAEquipe(JaPertenceAEquipe excecao) {
        return semToken(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "você já faz parte da equipe desta loja"));
    }

    /**
     * O {@code instance} fixo, e não o caminho do pedido. Sem isto o Spring o preenche com a
     * URI — e a URI do convite <b>contém o token</b>: toda recusa o ecoaria no corpo, um lugar
     * a mais onde a credencial aparece (ADR-069 §1). Medido na E-2, ao escrever o teste.
     */
    private static ProblemDetail semToken(ProblemDetail problema) {
        problema.setInstance(URI.create("/api/v1/convites"));
        return problema;
    }

    static String fraseDe(ConviteRecusado.Motivo motivo) {
        return switch (motivo) {
            case TELEFONE -> "telefone inválido";
            case PERMISSAO_QUE_NAO_TEM -> "você só pode oferecer permissões que você mesmo tem";
        };
    }

    static String fraseDe(CadastroDeLojaRecusado.Motivo motivo) {
        return switch (motivo) {
            case DOCUMENTO -> "documento inválido: informe um CPF ou um CNPJ";
            case TELEFONE -> "telefone inválido";
            case FUSO_HORARIO -> "fuso horário inválido";
            case TEXTO_OBRIGATORIO -> "nome, endereço e bairro são obrigatórios";
            case MODALIDADE_SEM_FORMA_DE_PAGAR ->
                    "escolha ao menos uma modalidade, e uma forma de pagar para cada uma";
            case FUNDO_DE_TROCO_NEGATIVO -> "o fundo máximo de troco não pode ser negativo";
        };
    }

    static String fraseDe(HorarioRecusado.Motivo motivo) {
        return switch (motivo) {
            case INICIO_IGUAL_AO_FIM -> "uma faixa de horário não pode terminar na mesma hora em que começa";
            case FAIXA_REPETIDA -> "a mesma faixa de horário aparece duas vezes no mesmo dia";
        };
    }

    static String fraseDe(AreasRecusadas.Motivo motivo) {
        return switch (motivo) {
            case CEP_INVALIDO -> "um CEP precisa ter oito dígitos, e a faixa começa antes de terminar";
            case AREA_INVALIDA -> "a taxa de uma área não pode ser negativa, e a mesma faixa de CEP "
                    + "não se repete na mesma área";
            case AREAS_EM_CONFLITO -> "duas áreas não podem ter o mesmo nome, e as faixas de CEP de "
                    + "áreas ativas não podem se cruzar";
        };
    }

    static String fraseDe(OperacaoRecusada.Motivo motivo) {
        return switch (motivo) {
            case PEDIDO_MINIMO -> "informe um pedido mínimo para cada modalidade que a loja aceita, "
                    + "e só para elas — zero ou mais, e zero é sem mínimo";
            case DESCONTO_NEGATIVO -> "o desconto de retirada não pode ser negativo";
        };
    }
}
