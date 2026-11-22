---
name: flowpay-verify
description: Use when validating a FlowPay delivery or reviewing changes affecting financial integrity, tenant isolation, imports, reconciliation, workers, or critical web journeys.
---

# Verificar uma entrega FlowPay

Leia [AGENTS.md](../../../AGENTS.md) e as seções das fontes relacionadas à mudança. A evidência precisa demonstrar comportamento e efeitos persistidos, não apenas chamadas de mocks.

## Escolher a verificação

Inspecione diff, manifests, wrappers, migrations e testes existentes. Execute comandos reais do projeto. Se ainda houver apenas documentos, verifique links, estrutura e consistência; informe que a aplicação não existe, sem inventar execução de JUnit ou Playwright.

| Área alterada | Evidência relevante |
|---|---|
| Baixa/caixa | Parcial, excessiva, repetição, rollback, reversão e saldo resultante |
| Concorrência | Duas transações independentes disputando título/chave/match no PostgreSQL, com barreira determinística |
| Tenancy | Leitura e mutação com ID alheio; FK composta rejeita associação indevida no banco |
| Autenticação | Sessão/membership/papel; mutação sem CSRF rejeitada e fluxo com token válido funciona |
| CSV/conciliação | Prévia inválida, reimportação, revisão de possíveis duplicatas e match sem novo caixa |
| Outbox/jobs | Reinício/retry preserva estado e deduplica efeitos após commit |
| UI | Carregamento/vazio/erro, teclado, foco, contraste e desktop/mobile |
| Migration/CI | Banco vazio e atualização quando houver versão anterior; comandos corretos e falhas visíveis |

Selecione casos pelo risco afetado. Testcontainers/PostgreSQL comprovam locks e constraints; testes unitários não substituem essa evidência. Testes concorrentes devem aguardar os resultados, verificar commits e saldos finais e evitar sleeps. Falhas que impedem rodar um teste não equivalem a aprovação.

Antes de executar comandos, identifique ambiente e efeitos: testes de integração devem usar banco isolado e dados fictícios, não credenciais de produção. Verifique se a sincronização provoca concorrência real sem impedir que a transação que ganhou o lock termine; use timeouts para que um deadlock do teste não trave a suíte. Inclua o caso de resposta perdida após commit e o reenvio idempotente quando a mudança afetar essas garantias.

Para testar autorização de tenant, envie sessão e CSRF válidos: rejeição por falta de CSRF não demonstra isolamento. Teste também persistência direta quando precisar comprovar a FK, e confira ausência de efeitos financeiros confirmados nos dois tenants. `403` versus `404` depende do contrato definido; não fixe uma resposta supostamente exigida pelos documentos.

## Jornada de referência

Empresa A cadastra contato → título de R$ 500 → baixa manual → importa CSV → vincula linha ao movimento → reimporta → consulta relatório. Depois da baixa, importar/vincular/reimportar não muda novamente o caixa. Empresa B não acessa nem associa esses recursos. Sandbox, se existir, não interfere nesses saldos.

## Relatar

Em revisão, entregue achados por gravidade com arquivo/linha quando houver, gatilho, efeito e evidência. Distinga defeito reproduzido de hipótese e não modifique código sem pedido de implementação. Ao validar, informe comandos, resultados, cobertura e limitações. Havendo testes falhos, identifique-os mesmo que pareçam anteriores à mudança. Não acrescente testes que apenas comparem o texto dos documentos com ele mesmo.
