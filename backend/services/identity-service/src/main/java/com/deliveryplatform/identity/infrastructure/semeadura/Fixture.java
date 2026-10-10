package com.deliveryplatform.identity.infrastructure.semeadura;

import java.util.UUID;

/**
 * Os identificadores da fixture de desenvolvimento, num lugar só neste serviço
 * (ADR-059).
 *
 * <p><b>É um acoplamento declarado.</b> O {@code merchant} grava o vínculo deste
 * usuário com a loja da fixture, pelo mesmo {@code USUARIO}, na classe de mesmo
 * nome — os dois concordam sem se falarem. Mudar um sem o outro deixa um usuário
 * que entra e não vê loja nenhuma.
 */
final class Fixture {

    /** O usuário. É o mesmo valor do {@code Fixture.USUARIO} do {@code merchant}. */
    static final UUID USUARIO = UUID.fromString("5eed0000-0000-4000-8000-000000000003");

    /** O telefone de entrada, em E.164. Não é segredo: é o nome de usuário da fixture. */
    static final String TELEFONE = "+5511999990001";

    private Fixture() {
    }
}
