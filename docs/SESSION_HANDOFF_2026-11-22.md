# FlowPay — checkpoint de implementação

Data: 22/11/2026

Este checkpoint registra o estado da jornada pausada para que possa ser retomada daqui.

## Objetivo ativo

Implementar os estágios restantes do roadmap MVP em incrementos pequenos e verificados, preservando as invariantes de finanças, tenancy, segurança e experiência documentadas.

## Avanços feitos nesta sessão

- Portão 9.3: revisão de linha de extrato (`PENDING`, `REVIEWED`, `IGNORED`), motivo, autor, histórico append-only e idempotência por tenant.
- Linhas ignoradas não recebem sugestões nem podem ser conciliadas até serem reabertas.
- Desfazer vínculo registra motivo, autor e idempotência e preserva o movimento de caixa. Estorno do movimento continua sendo uma operação separada.
- Candidatos individuais podem ser ignorados/restaurados por linha, com motivo e autoria persistidos. A supressão não altera o movimento nem o oculta de outras linhas; há limite de 100 candidatos ignorados por linha.
- Portão 9.4 parcial: resumo da importação mostra período e `asOf`, saldo controlado específico da conta, atividade de entradas/saídas do extrato e do caixa, diferença líquida de movimentação e vínculos ativos.
- A interface explica que o CSV atual contém movimentos, mas não saldo bancário inicial/final. A diferença apresentada é de movimentação, não uma diferença entre saldos.
- Roadmap atualizado para documentar esse limite do contrato CSV e a necessidade de definir como fornecer saldo inicial/final antes de calcular diferença de saldos.
- Adicionados testes unitários de autorização/validação e casos Testcontainers para revisão, supressão/restauração, desfazer vínculo e resumo por conta.

## Arquivos principais deste incremento

- `backend/src/main/resources/db/migration/V12__reconciliation_review_and_unlink.sql`
- `backend/src/main/resources/db/migration/V13__reconciliation_candidate_suppressions.sql`
- `backend/src/main/java/com/flowpay/statements/application/StatementLineReviewService.java`
- `backend/src/main/java/com/flowpay/statements/application/StatementReconciliationSummaryService.java`
- `backend/src/main/java/com/flowpay/reconciliation/application/ReconciliationCandidateIgnoreService.java`
- `backend/src/main/java/com/flowpay/reconciliation/application/ReconciliationMatchService.java`
- `backend/src/main/java/com/flowpay/reconciliation/application/ReconciliationSuggestionService.java`
- `frontend/src/statements/StatementImportPreview.tsx`
- `docs/FLOWPAY_IMPLEMENTATION_ROADMAP.md`
- `backend/src/test/java/com/flowpay/finance/application/FinancialTitlePostgresIntegrationTest.java`

Há também alterações preexistentes de incrementos anteriores no worktree. Não as reverta; inspecione `git status --short` antes de editar ou integrar.

## Verificações e limites

- Frontend: `npm.cmd run typecheck` passou.
- Frontend: `npm.cmd run lint` terminou com aviso `react(set-state-in-effect)` em `frontend/src/statements/StatementImportPreview.tsx`, linha 136, por atualizar `reconciliationSummary` dentro do efeito de carregamento. Corrigir ao retomar.
- Frontend: `npm.cmd run build` passou.
- `git diff --check` passou, com avisos de conversão de fim de linha preexistentes.
- Backend e Testcontainers não foram executados. O ambiente disponível tem somente Java 8; Maven, JDK 21, Docker e PostgreSQL local não foram encontrados. Por isso V12/V13 e o código Java ainda não têm validação de execução nesta máquina.
- Não foi feita revisão visual da tela no browser após este incremento.

## Próximos passos ao retomar

1. Corrigir o aviso de lint movendo o reset do resumo para o evento que seleciona importação ou evitando `setState` síncrono no efeito; manter loading/error/result corretos e impedir resposta obsoleta.
2. Revisar Java, SQL e os testes adicionados; executar backend quando Maven + JDK 21 e Docker/PostgreSQL estiverem disponíveis. Validar migração de banco vazio e atualização desde V11.
3. Testar a jornada no browser: selecionar importação, ver resumo; ignorar/restaurar candidato; revisar/reabrir linha; desfazer vínculo e confirmar visualmente que o movimento continua no saldo.
4. Continuar o Portão 9.4: decidir/documentar um contrato explícito para saldo inicial/final de extrato. Não inferir saldo bancário a partir das linhas CSV atuais.
5. Depois seguir Portões 10 e 11 do `docs/FLOWPAY_IMPLEMENTATION_ROADMAP.md` (jornada demonstrável, demo, acessibilidade, validação abrangente, segurança, observabilidade, backup e deploy), consultando o checkout antes de declarar qualquer item pronto.

## Estado da jornada

O objetivo completo continua em andamento e pausado a pedido do responsável. O Portão 9.3 foi implementado; o 9.4 está parcial e sem verificação de backend. Não declarar o MVP concluído.
