---
name: flowpay-finance
description: Use when implementing or changing FlowPay financial accounts, receivables, payables, settlements, cash transactions, or their persistence and APIs.
---

# Implementar domínio financeiro

Leia [AGENTS.md](../../../AGENTS.md) e [System Design, seções 5–9](../../../docs/FLOWPAY_SYSTEM_DESIGN.md). Preserve a fonte única de caixa e demonstre as invariantes no banco.

## Desenhar o caso de uso

Identifique tenant autorizado, papel exigido, título, conta, movimento e alocações envolvidos. Diferencie vencimento (`LocalDate`) e instante ocorrido (`Instant` UTC). Use centavos inteiros, BRL, limites explícitos e direção separada. Ao cruzar Java BIGINT e TypeScript, defina representação/limites de transporte que não percam precisão; não converta arbitrariamente para `number` fora de seu intervalo inteiro seguro.

Defina contrato público entre módulos sem importar suas entidades ou repositories. Valide recursos associados no mesmo tenant; use também FKs compostas com UNIQUE correspondente. Não aceite `tenant_id` do request como prova de autorização.

## Gravar com consistência

Para registrar baixa, dentro da transação:

1. Resolva idempotência escopada por `(tenant_id, operation, key)`, incluindo disputa pela mesma chave. Defina resultado da repetição e do reuso com payload diferente; os documentos não fixam esse contrato.
2. Bloqueie o título antes de ler/recalcular o saldo pendente. Revalide autorização, estado e valor; baixa excessiva falha integralmente.
3. Crie ou use a `CashTransaction` prevista pelo caso de uso e registre a alocação. Soma de baixas válidas ≤ título; soma de alocações ativas ≤ movimento. Para alocar em movimento existente sob concorrência, proteja também esse limite na transação.
4. Grave mudança, auditoria e outbox pertinente no mesmo commit. Uma falha reverte os efeitos associados.

Quando várias entidades precisarem de lock, estabeleça uma ordem determinística para reduzir deadlocks. Defina limites de espera e retries apenas para falhas transitórias reconhecidas, revalidando a operação inteira. Não mantenha locks durante chamadas externas. A chave idempotente precisa representar a mesma intenção: defina identificação do payload, comportamento de requisição ainda em curso e recuperação após crash; uma resposta em memória não comprova idempotência persistente.

Movimentos não alocados continuam reconhecidos no caixa como não classificados/pendentes. Reversão preserva fatos confirmados, exige motivo e registra efeito explícito; não elimina o histórico. Eventos assíncronos podem ser repetidos: consumidores deduplicam efeitos por evento.

## Exemplo de aceite

Título de `10000` centavos, duas requisições independentes de `7000`: uma confirma; a outra encontra `3000` pendentes e falha sem novos efeitos. Resultado: baixa de `7000`, pendência de `3000` e um único efeito de caixa. Repetir a requisição vencedora com a mesma chave não gera outro movimento.

## Verificação e entrega

Cubra limite monetário, baixa parcial/excessiva, tenant alheio, repetição idempotente, disputa concorrente e rollback conforme a alteração. Locks e constraints exigem PostgreSQL/Testcontainers, com transações independentes e barreira de sincronização. Documente contrato, migration e resultados executados; se a aplicação ainda não existe, declare essa dependência.
