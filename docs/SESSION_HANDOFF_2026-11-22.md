# FlowPay - checkpoint de implementacao

Atualizado: 23/11/2026

Este arquivo registra onde retomar a jornada ativa do MVP.

## Objetivo

Implementar os estagios restantes do roadmap em incrementos pequenos, mantendo as invariantes de financas, tenancy, seguranca e UX. O MVP ainda nao esta concluido.

## Estado do roadmap

- Atualizacao de 23/11: DemoSeedPostgresIntegrationTest parte de PostgreSQL 16 descartavel vazio, confere registros, membership OWNER e hash, e reexecuta sem duplicacao. Tambem valida login com CSRF e leituras de contatos/dashboard pela sessao demo.
- Isolamento confirmado pela API: outra identidade, com membership separado, sessao e CSRF validos, nao le nem altera contato demo e ve saldo controlado zero. Revisao visual e jornada E2E completa seguem pendentes.

- Portao 9.3 implementado: revisao de linha (PENDING, REVIEWED, IGNORED), motivo, autor, evento append-only e idempotencia por tenant. Linhas ignoradas nao recebem sugestoes nem podem ser conciliadas ate reabrir.
- Desfazer vinculo mantem o movimento de caixa. Estornar o movimento e uma operacao separada. Ignorar/restaurar candidato nao altera caixa nem afeta outras linhas.
- Portao 9.4 parcial: formulario, migration V14 e calculos foram validados por 12 integracoes em PostgreSQL 16.16 via Testcontainers. Resumo separa atividade, saldo final do extrato vs caixa controlado e roll-forward; revisao no browser pendente.
- Portao 10.2 backend verificado: seed, reexecucao idempotente, login/CSRF e isolamento de leitura/alteracao por outro tenant. A apresentacao visual ainda aguarda revisao mais ampla.
- Portao 10.3 parcial: gestao de foco foi implementada e verificada por typecheck/lint/build; Playwright navegou o demo em desktop e mobile sem overflow horizontal. Auditoria de teclado, contraste, zoom e leitor de tela segue pendente.
- Seed implementa empresa, login `demo@flowpay.local`, dois contatos, conta com saldo inicial ficticio, categorias e titulos em aberto. Nao apaga registros nem cria baixas/importacoes. Protecao adicional: so executa com perfil `local` e URL PostgreSQL loopback para o banco `flowpay`.
- Pendencias restantes do demo: revisar a apresentacao visual completa; o acesso HTTP e isolamento entre tenants estao cobertos.
- Portoes 10.1 e 10.3 seguem parciais: a jornada E2E cobre login, dashboard, contatos, conta e titulos, mas ainda nao baixa titulos nem importa/concilia CSV. Portao 11 ainda pede auditoria e operacao.

## Commits recentes

- `cb379b2` Document verified reconciliation balance flow
- `eb508aa` Validate statement balances and tenant line access
- `686b938` Record reconciliation verification status
- `74094db` Test statement balance import rules
- `3bbbcd9` Refresh FlowPay reconciliation docs
- `def2abf` Add statement balance reconciliation
- `f015023` Document current FlowPay feature coverage
- `9b663ac` Add isolated deterministic demo data seed
- `b27abb1` Label the isolated demo workspace
- `26c7a90` Restrict demo seed to local database
- `a6fd931` Restore focus after contact form closes
- `5193171` Return focus after financial title form closes
- `95931d8` Return focus from primary workspace forms
- `a75b325` Restore focus across settlement and reconciliation
- Trabalho anterior: `1a0cc50`, `81c3f8f`, `5c6c9a5` e `6a2c90a`.

## Verificacoes e limites

- Backend (23/11): `mvn --batch-mode --no-transfer-progress -f backend/pom.xml verify` passou com JDK 25/Maven 3.9.16. Foram 42 testes sem falhas nem skips, incluindo 15 testes Testcontainers em PostgreSQL 16.16. V14, calculos, replay, caixa, login/CSRF e isolamento de leitura/alteracao foram exercitados; seed aplicado em schema vazio e reexecutado sem duplicacao.
- Frontend: `npm.cmd run typecheck`, `npm.cmd run lint -- --deny-warnings` e `npm.cmd run build` passaram apos a gestao de foco dos fluxos principais.
- Frontend E2E (23/11): `npm.cmd run test:e2e -- --reporter=line` passou em Chrome com login demo, dashboard, contatos, conta e titulos. Viewport 1440x1000 e mobile 390x844; scrollWidth mobile ficou dentro da viewport. Senha vem de FLOWPAY_E2E_DEMO_PASSWORD, fora do repositorio.
- Docker Desktop foi iniciado para os testes; Testcontainers criou e encerrou containers descartaveis. O banco PostgreSQL local permanente nao foi alterado. Compose fixa PostgreSQL 18.7; integracoes locais usaram 16.16.
- O perfil `local,demo` foi exercitado com banco descartavel; autenticacao HTTP e acesso cruzado por outro tenant agora estao cobertos por integracao MockMvc.
- Revisao browser limitada a Chrome desktop 1440x1000 e mobile 390x844; auditoria de acessibilidade e outros fluxos visuais permanecem pendentes.

## Proximos passos

1. Ampliar a jornada Playwright com baixa realizada fora do FlowPay, importacao/reconciliacao CSV e verificacao do caixa/resultados persistidos em ambiente E2E isolado.
2. Revisar no browser a jornada 9.4: ausencia/presenca de saldos, replay e exibicao dos tres tipos de diferenca.
3. Continuar Portoes 10 e 11 com uma jornada E2E completa, acessibilidade e seguranca/operacao proporcionais ao uso demo.

## Arquivos de referencia

- [Roadmap](FLOWPAY_IMPLEMENTATION_ROADMAP.md)
- [README e comandos locais](../README.md)
- [Instrucoes de desenvolvimento](../AGENTS.md)

## Retomada de 23/11/2026 - Portao 9.4

- A confirmacao do CSV aceita saldos opcionais de abertura/fechamento com datas. Migration V14 persiste metadados. O resumo separa atividade, saldo final do extrato vs caixa controlado e roll-forward.
- Decisao: valores de fim do dia; fechamento posterior a abertura; linhas aceitas apos a abertura e ate o fechamento; saldo negativo permitido. Reenvio do checksum preserva a primeira importacao.
- Frontend: typecheck, lint sem avisos e build passaram.
- Backend (23/11): `mvn --batch-mode --no-transfer-progress -f backend/pom.xml verify` passou com JDK 25/Maven 3.9.16. Foram 42 testes sem falhas nem skips, incluindo 15 testes Testcontainers em PostgreSQL 16.16. V14, calculos, replay, caixa, login/CSRF e isolamento de leitura/alteracao foram exercitados; seed aplicado em schema vazio e reexecutado sem duplicacao.
- V14, replay de metadados, calculos com saldos positivo/negativo e isolamento foram verificados. O Portao 9.4 segue parcial ate revisar a jornada no browser; Portoes 10 e 11 ainda pedem E2E e acessibilidade.
- Previa isolada ativa: Vite em `http://127.0.0.1:5173`, API em `http://127.0.0.1:8080`, banco PostgreSQL descartavel em tmpfs. O seed demo foi carregado. O helper visual nao conectou (pipe nativo indisponivel); inspecao manual continua pendente e os dados desaparecem quando o container parar.
- Proximo passo: inspecionar a previa, concluir verificacao visual/teclado e avancar a jornada demo/E2E do Portao 10.
