package com.deliveryplatform.catalog.infrastructure.semeadura;

import java.util.UUID;

/**
 * Os identificadores da fixture de desenvolvimento, num lugar só neste serviço
 * (ADR-059).
 *
 * <p><b>É um acoplamento declarado.</b> O {@code merchant} tem a mesma loja na
 * classe de mesmo nome, e os dois concordam sem se falarem — é a folga de
 * arquitetura que a ADR-059 escreve para não ser descoberta como surpresa. Mudar
 * um sem o outro deixa o produto semeado numa loja que não existe.
 */
final class Fixture {

    /** A loja. É o mesmo valor do {@code Fixture.LOJA} do {@code merchant}. */
    static final UUID LOJA = UUID.fromString("5eed0000-0000-4000-8000-000000000001");

    /**
     * A categoria. Solta de propósito: {@code Categoria} não tem repositório nem
     * documento, e nada no domínio ou na leitura exige que ela exista gravada.
     */
    static final UUID CATEGORIA = UUID.fromString("5eed0000-0000-4000-8000-000000000002");

    private Fixture() {
    }
}
