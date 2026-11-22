---
name: flowpay-postgres
description: Use when changing FlowPay PostgreSQL schemas, Flyway migrations, queries, constraints, indexes, locking, or tenant data access.
---

# Trabalhar com PostgreSQL no FlowPay

Leia [AGENTS.md](../../../AGENTS.md) e as seções pertinentes do [System Design](../../../docs/FLOWPAY_SYSTEM_DESIGN.md). Use a documentação [Postgres Best Practices da Supabase](https://github.com/supabase/agent-skills/tree/main/skills/supabase-postgres-best-practices) como referência técnica complementar; adapte exemplos à stack Spring Boot, PostgreSQL e Flyway do FlowPay.

## Antes de alterar

Inspecione migrations aplicadas, entidades, consultas, índices, convenções e versão real do PostgreSQL. Descubra o caminho de leitura/escrita e o tenant autenticado que limita os dados. Não presuma Supabase, políticas RLS, extensões ou serviços ainda ausentes.

## Schema e migrations

- Crie migrations Flyway novas e imutáveis depois de aplicadas. Corrija mudanças anteriores com migration adicional.
- Dados empresariais carregam `tenant_id`. Relacionamentos entre recursos da empresa devem usar FK composta `(tenant_id, resource_id)` e UNIQUE correspondente para impedir referências cruzadas no próprio banco.
- Valores monetários são centavos positivos em `BIGINT`, com direção separada, BRL e limites/checks explícitos. Nunca use `FLOAT`/`DOUBLE` para dinheiro.
- Defina `NOT NULL`, `CHECK`, `UNIQUE` e chaves estrangeiras para invariantes que o banco possa impor. Evite duplicar estado derivável sem razão e política de atualização.
- Para unicidade parcial, idempotência, checksum e match ativo, modele a constraint de acordo com o escopo tenant/operação definido pelo domínio.
- Índices devem corresponder a filtros, joins, ordenação e cardinalidade reais; evite índices especulativos e índices duplicados.

## Consultas e concorrência

- Toda leitura/escrita empresarial filtra pelo tenant derivado da sessão e membership; nunca confie em `tenant_id` enviado pelo cliente.
- Use parâmetros vinculados, paginação limitada e projeções adequadas. Evite N+1 e carregamento de coleções sem limite.
- Para baixas e alocações concorrentes, siga o contrato do domínio: bloqueie o título antes de recalcular o saldo, proteja o limite do movimento e estabeleça ordem estável de locks.
- Mantenha transações curtas. Não faça chamadas externas sob lock. Use isolamento, lock e retry apenas com justificativa e evidência.
- `StatementLine` e sua importação não alteram caixa; só `CashTransaction` altera saldo controlado. Conciliação liga uma linha a um movimento ou cria um movimento conforme contrato, sem duplicar efeito.

## Verificação

Use o PostgreSQL/Testcontainers do projeto para constraints, migrations, locks, idempotência e isolamento de tenant. Teste banco vazio e atualização quando houver migration anterior. Use `EXPLAIN (ANALYZE, BUFFERS)` somente em dados seguros e representativos; não execute mutações de produção durante análise.

Relate migration, invariantes garantidas pelo banco, testes executados e limites. Se ainda não houver código ou infraestrutura, descreva a proposta e não declare validação executada.
