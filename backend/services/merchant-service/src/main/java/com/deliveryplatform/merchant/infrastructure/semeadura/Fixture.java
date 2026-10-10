package com.deliveryplatform.merchant.infrastructure.semeadura;

import java.util.UUID;

/**
 * Os identificadores da fixture de desenvolvimento, num lugar só neste serviço
 * (ADR-059).
 *
 * <p><b>É um acoplamento declarado.</b> O {@code catalog} tem a mesma loja na
 * classe de mesmo nome, e os dois concordam sem se falarem — é a folga de
 * arquitetura que a ADR-059 escreve para não ser descoberta como surpresa. Mudar
 * um sem o outro deixa o produto semeado numa loja que não existe, e a abertura
 * desta loja sem nada para reativar.
 */
final class Fixture {

    /** A loja. É o mesmo valor do {@code Fixture.LOJA} do {@code catalog}. */
    static final UUID LOJA = UUID.fromString("5eed0000-0000-4000-8000-000000000001");

    /** O dono do vínculo. É o mesmo valor do {@code Fixture.USUARIO} do {@code identity}. */
    static final UUID USUARIO = UUID.fromString("5eed0000-0000-4000-8000-000000000003");

    private Fixture() {
    }
}
