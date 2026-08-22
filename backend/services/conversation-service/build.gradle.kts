// Canal, conversa e interpretação num serviço só — docs/dominio/conversa.md.
// Documental porque a conversa é uma árvore de mensagens lida inteira, com
// forma que muda conforme o canal.
//
// Sem redis-conventions: o estado da conversa vive no MongoDB. Se um dia
// entrar cache ou limitador de taxa, acrescente aqui — e não antes.
plugins {
    id("delivery.mongo-conventions")
    id("delivery.messaging-conventions")
}
