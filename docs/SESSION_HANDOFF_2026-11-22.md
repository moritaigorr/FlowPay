# FlowPay - checkpoint de implementacao

Atualizado: 23/11/2026

Este arquivo registra onde retomar a jornada ativa do MVP.

## Objetivo

Implementar os estagios restantes do roadmap em incrementos verificados, mantendo as invariantes de financas, tenancy, seguranca e UX.

## Trabalho registrado

- Portao 9.3: revisao de linha (PENDING, REVIEWED, IGNORED), motivo, autor, evento append-only e idempotencia por tenant.
- Linhas ignoradas nao recebem sugestoes nem podem ser conciliadas ate reabrir.
- Desfazer vinculo mantem o movimento de caixa. Estornar esse movimento e uma operacao separada.
- Candidatos podem ser ignorados/restaurados por linha, com motivo e autoria. Isso nao muda o caixa e nao afeta outras linhas.
- Portao 9.4 parcial: resumo da importacao mostra periodo, fuso, asOf, saldo controlado da conta, totais de atividade do extrato/caixa e diferencas de movimentacao.
- O CSV atual nao fornece saldo bancario inicial/final. A tela nao chama diferenca de movimentacao de diferenca entre saldos.
- Codigo e testes associados estao no commit `1a0cc50` (mensagem original: `Update`).
- A correcao de estado de carregamento da tela esta no commit `81c3f8f` (`Fix reconciliation summary loading state`).

## Verificacoes

- Frontend: `npm.cmd run typecheck`, `npm.cmd run lint`, `npm.cmd run build` passaram sem avisos.
- Backend: Maven 3.9.16 offline com JDK 25 compilou com `--release 21`; 35 testes executados, 0 falhas/erros, 11 Testcontainers ignorados.
- A primeira compilacao apontou argumento faltante no DTO quando a importacao nao tem linhas aceitas. Corrigido em `StatementReconciliationSummaryService`; a segunda execucao Maven passou. A correcao sera incluida neste commit.
- O Docker daemon nao foi encontrado. Ha um servico local PostgreSQL 16 aceitando conexoes, mas o psql exige senha e nao havia credenciais locais fornecidas; o Compose do projeto fixa PostgreSQL 18.7. As migrations V12/V13 e os 11 cenarios de integracao ainda nao foram executados contra PostgreSQL.
- `git diff --check` passou; permanecem avisos de conversao CRLF preexistentes.
- Ainda falta revisar a tela no browser, incluindo mobile e acessibilidade.

## Proximos passos

1. Criar um commit para a correcao do resumo sem linhas e esta atualizacao do checkpoint.
2. Quando Docker estiver disponivel, executar os 11 cenarios Testcontainers e validar migration desde banco vazio e desde V11.
3. Revisar a jornada no browser: resumo, ignorar/restaurar candidato, revisar/reabrir linha e desfazer vinculo mantendo o movimento no saldo.
4. Completar o Portao 9.4 decidindo como o CSV/documento vai informar saldo bancario inicial/final; nao inferir esses valores das linhas atuais.
5. Seguir os Portoes 10 e 11 do roadmap. O MVP completo ainda nao esta concluido.

## Estado

A jornada foi retomada em 23/11/2026. O Portao 9.3 esta implementado; o Portao 9.4 esta parcial. Backend unitario passou, mas integracao PostgreSQL/migrations e validacao visual continuam pendentes.