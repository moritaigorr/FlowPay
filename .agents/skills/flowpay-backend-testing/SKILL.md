---
name: flowpay-backend-testing
description: Use when creating, changing, or diagnosing Java and Spring Boot tests for FlowPay domain rules, persistence, APIs, security, or concurrency.
---

# Testar o backend FlowPay

Leia [AGENTS.md](../../../AGENTS.md), a skill [flowpay-verify](../flowpay-verify/SKILL.md) e o teste/comando existente mais próximo. Use a [referência oficial de testes do Spring Boot](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html) para escolher entre testes de unidade, slices e contexto completo, respeitando as versões efetivamente instaladas.

## Escolher o nível

- **Domínio:** regras puras de valores, estados, limites, datas e transições; não carregue o contexto Spring sem necessidade.
- **Persistência:** Flyway, constraints, repositories, consultas tenant-scoped e locks no PostgreSQL real via Testcontainers, quando configurado.
- **API/segurança:** contrato JSON, validação, status de erro acordados, sessão, membership, autorização e CSRF.
- **Concorrência:** transações independentes, barreiras determinísticas, espera limitada e conferência dos efeitos persistidos após commit.
- **Contrato/UI/E2E:** use o nível apropriado para provar serialização, integração SPA e jornada visível; mocks não comprovam efeito financeiro no banco.

## Casos financeiros e de tenancy

- Teste valores positivos em centavos e limites; baixa parcial, total e excessiva; repetição da mesma chave; conflito de chave com outro payload conforme contrato; rollback; reversão com motivo; dois pedidos disputando o mesmo saldo; alocação concorrente no mesmo movimento.
- Verifique soma de baixas válidas ≤ valor do título e soma de alocações ativas ≤ valor do movimento.
- Confirme que operações financeiras relacionadas persistem atomicamente e que retry após resultado ambíguo não duplica efeitos.
- Use dois tenants e identidade/CSRF válidos ao comprovar isolamento. Verifique leitura e mutação de IDs de outra empresa e, quando relevante, rejeição da FK composta.
- Importe, associe e reimporte extrato; confirme que essas ações não aumentam caixa depois da baixa já registrada.

## Qualidade do teste

- Prefira fixtures pequenas, nomes que expressem regra e asserts sobre estado final persistido.
- Não use sleeps como sincronização de concorrência; use latches/barreiras e timeouts para que deadlocks falhem com clareza.
- Não torne testes verdes por desabilitar constraints, usar banco diferente do projeto ou mockar a regra sob teste.
- Leia comandos e configuração atuais antes de executar. Use somente bancos isolados e dados fictícios; não use credenciais de produção.

Execute os comandos existentes relevantes e reporte resultado, cobertura e limitações. Se os testes ou o backend ainda não existem, não invente comandos nem declare aprovação.
