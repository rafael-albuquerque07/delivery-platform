// Sem redis-conventions: nada no order usa Redis, e nada escrito jamais disse por
// quê. Os cinco usos que a arquitetura v1.0 atribuía ao Redis morreram cada um
// numa decisão própria. ADR-021, emenda de 26/09/2026.
plugins {
    id("delivery.jpa-conventions")
    id("delivery.messaging-conventions")
}
