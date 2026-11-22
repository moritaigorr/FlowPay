---
name: flowpay-e2e
description: Use when creating, running, or debugging browser end-to-end tests for FlowPay user journeys with Playwright.
---

# Validar jornadas web FlowPay

Leia [AGENTS.md](../../../AGENTS.md), [flowpay-ui](../flowpay-ui/SKILL.md), [flowpay-verify](../flowpay-verify/SKILL.md) e o roteiro real de execução do frontend. Consulte a [documentação de skills e CLI do Playwright](https://playwright.dev/agent-cli/skills) apenas para a ferramenta instalada; não presuma que `playwright-cli` e `@playwright/test` são intercambiáveis.

## Preparar execução

- Inspecione `package.json`, lockfile, configuração, fixtures, scripts e ambiente. Use o runner e comandos que já existem; se Playwright não estiver configurado, proponha a menor instalação alinhada ao projeto antes de escrever testes dependentes dela.
- Confirme que API, PostgreSQL e dados são locais/isolados e sintéticos. Nunca execute E2E destrutivo contra produção ou dados reais.
- Crie contexto de autenticação por teste ou fixture explícita; não versione storage state, cookies, senhas ou tokens.

## Escolher jornadas

Priorize fluxo de valor visível com verificação de efeitos reais:

1. Login, criação/seleção de empresa e isolamento entre memberships.
2. Cadastro de contato e conta financeira; criação de título com dinheiro e vencimento formatados corretamente.
3. Registro de pagamento realizado fora do FlowPay; confirme título, movimento e saldo persistido.
4. Importação de CSV, revisão e conciliação com movimento existente; reimportação não duplica caixa.
5. Erros de validação, sessão expirada, CSRF, timeout/retry idempotente e viewport/teclado conforme risco da entrega.

## Escrever testes resilientes

- Prefira locators acessíveis por role/label e asserts sobre resultados observáveis. Evite seletores frágeis por classe, sleeps e dependência da ordem global dos testes.
- Isole dados e empresas, limpe/crie estado de forma determinística e espere condições observáveis com timeout adequado.
- Quando o risco for financeiro, verifique estado final via API/banco de teste além da mensagem na tela; mocks de rede isolados não comprovam persistência.
- Para tenancy, use duas empresas e sessões válidas; tente ler e mutar recurso alheio e confirme ausência de efeitos nos dois lados.
- Em falha, preserve trace, screenshot e logs sanitizados. Não inclua PII, cookies, tokens ou conteúdo real de extrato nos artefatos.

Execute somente o script existente e reporte navegador, ambiente, jornadas, resultado e limites. Se a aplicação ou infraestrutura ainda não existir, registre a dependência e não declare E2E aprovado.
