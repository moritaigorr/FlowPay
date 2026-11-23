# FlowPay - checkpoint de implementacao

Atualizado: 23/11/2026

Este arquivo registra onde retomar a jornada ativa do MVP.

## Objetivo

Implementar os estagios restantes do roadmap em incrementos pequenos, mantendo as invariantes de financas, tenancy, seguranca e UX. O MVP ainda nao esta concluido.

## Estado do roadmap

- Atualizacao de 23/11: DemoSeedPostgresIntegrationTest parte de PostgreSQL 16 descartavel vazio, confere registros, membership OWNER e hash, e reexecuta sem duplicacao. Tambem valida login com CSRF e leituras de contatos/dashboard pela sessao demo.
- Isolamento confirmado pela API: outra identidade, com membership separado, sessão e CSRF válidos, não lê nem altera contato demo e vê saldo controlado zero. Em 23/11, Chrome local confirmou demo-login e jornada financeira completa (2/2 E2E) em PostgreSQL descartável, com replay de CSV sem duplicar caixa e saldo final esperado de R$ 1.150,00. O workflow GitHub Actions ainda precisa de execução observada; revisão visual da conciliação e auditoria manual de acessibilidade seguem pendentes.

- Portao 9.3 implementado: revisao de linha (PENDING, REVIEWED, IGNORED), motivo, autor, evento append-only e idempotencia por tenant. Linhas ignoradas nao recebem sugestoes nem podem ser conciliadas ate reabrir.
- Desfazer vinculo mantem o movimento de caixa. Estornar o movimento e uma operacao separada. Ignorar/restaurar candidato nao altera caixa nem afeta outras linhas.
- Portão 9.4: revisão visual do resumo e das linhas salvas passou em Chrome desktop/mobile. Corrigidos espaçamento e pluralização dos indicadores e navegação de teclado para tabelas roláveis. Axe passou no estado conciliado em 390 px e desktop; documento sem overflow horizontal em 390 px.
- Portao 10.2 backend verificado: seed, reexecucao idempotente, login/CSRF e isolamento de leitura/alteracao por outro tenant. A apresentacao visual ainda aguarda revisao mais ampla.
- Portao 10.3 parcial: fluxo demo em desktop/mobile, scans axe e alternador/envio do login via Tab/Enter passaram; gestao de foco tambem esta validada por build/lint/typecheck. Auditoria manual de teclado nos outros fluxos, contraste, zoom e leitor de tela continua pendente.
- Seed implementa empresa, login `demo@flowpay.local`, dois contatos, conta com saldo inicial ficticio, categorias e titulos em aberto. Nao apaga registros nem cria baixas/importacoes. Protecao adicional: so executa com perfil `local` e URL PostgreSQL loopback para o banco `flowpay`.
- Pendencias restantes do demo: revisar a apresentacao visual completa; o acesso HTTP e isolamento entre tenants estao cobertos.
- Portão 10.1: jornada Playwright opt-in validada localmente em Chrome com tenant e PostgreSQL descartáveis: login demo e cenário financeiro passaram (2/2). Cobertura inclui tenant sintético, contato cliente/fornecedor, conta, categorias, recebimento parcial, pagamento externo, CSV conciliado com movimento existente, replay e relatório mensal. Confirma saldo final de R$ 1.150,00 (abertura R$ 1.000,00 + entrada R$ 200,00 - saída R$ 50,00), diferença líquida do extrato e vínculos sem duplicidade.
- Nota operacional: a primeira tentativa de E2E reutilizou uma Vite já aberta e criou o tenant sintético `E2E fe5ed8ed-9753-4f96-a192-e2a8e8fda94b` na instância local preexistente; não foi removido, pois a aplicação não oferece exclusão segura de tenant. As execuções aprovadas depois usaram API, Vite e PostgreSQL descartáveis em portas exclusivas.
- Portão 10.1 permanece parcial até observar o CI. Portões 10.3 e 11 seguem parciais pela auditoria manual de acessibilidade e operação; a revisão visual da conciliação foi concluída para os estados cobertos pelo fluxo E2E.
- Portao 11.2: revisão parcial em `FLOWPAY_SECURITY_REVIEW.md`; testes MockMvc de multipart CSRF, acesso a conta de outro tenant e limite de 10 MB passaram no `mvn verify` local. Sem declaração de conformidade ASVS.

## Commits recentes

- `468492a` Measure statement import duration and outcomes
- `901d1af` Add privacy-safe request correlation logging
- `1698ab7` Extend accessibility coverage to key finance screens
- `f90d055` Expand financial demo journey coverage
- `9b12ecb` Assert reconciliation summary in financial E2E
- `9ae34c5` Run demo Playwright journey in CI
- `7c904a1` Add isolated financial settlement E2E
- `be95e27` Verify statement import screen accessibly
- `63fefd0` Cover statement reconciliation in E2E
- `753ef81` Make reconciliation E2E assertions resilient
- `79f5b73` Add negative upload security coverage
- 3006df0 Cover keyboard login in browser journey
- `1ea50d6` Document automated accessibility coverage
- `1eedaa5` Add automated demo accessibility scans
- `839ec25` Document verified demo browser journey
- `5f895e9` Add FlowPay demo browser smoke journey
- `0febdb0` Record demo tenant isolation verification
- `aee5211` Verify demo login and tenant isolation
- `7ec19fa` Record demo seed integration verification
- `35d96d2` Test demo seed idempotency
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

- Backend (23/11): `mvn --batch-mode --no-transfer-progress -f backend/pom.xml verify` passou com Maven 3.9.16/JDK 25 (compilação `--release 21`): 46 testes, 0 falhas, 0 erros, 0 skips; 17 integrações Testcontainers em PostgreSQL 16. Migration V14, seed, cálculos/replay de saldos, caixa, CSRF, isolamento, filtros de correlação e métricas de criação/replay foram exercitados; JAR Spring Boot empacotado.
- Frontend: `npm.cmd run typecheck`, `npm.cmd run lint -- --deny-warnings` e `npm.cmd run build` passaram apos a gestao de foco dos fluxos principais.
- Frontend E2E (23/11): login demo com Tab/Enter, dashboard, contatos, conta e titulos passaram em Chrome; viewport 1440x1000 e mobile 390x844 sem overflow horizontal. Axe/WCAG 2.1 A/AA automatizado passou em login, dashboard e contatos apos remover aria-label redundante do link da marca. Resultado automatizado nao atesta conformidade; teclado nos demais fluxos, contraste manual e leitor de tela pendentes.
- Tela inicial de extratos revisada em Chrome 1440x1000 e 390x844. E2E autenticado confirma heading/labels, texto que informa que a previa nao altera caixa, axe WCAG 2.1 A/AA sem violacoes e largura de documento <=390. Revisao visual nao cobre resumo de conciliacao nem linhas/matches salvos.
- CI E2E configurado: PostgreSQL 18.7 de servico, API local/demo, senha efemera e Chromium. Ainda falta observar um run real no GitHub Actions.
- Docker Desktop foi iniciado para os testes; Testcontainers criou e encerrou containers descartaveis. O banco PostgreSQL local permanente nao foi alterado. Compose fixa PostgreSQL 18.7; integracoes locais usaram 16.16.
- O perfil `local,demo` foi exercitado com banco descartavel; autenticacao HTTP e acesso cruzado por outro tenant agora estao cobertos por integracao MockMvc.
- Revisao browser limitada a Chrome desktop 1440x1000 e mobile 390x844; auditoria de acessibilidade e outros fluxos visuais permanecem pendentes.

## Proximos passos

1. Observar o workflow e a jornada Playwright com escrita no GitHub Actions; a mesma jornada passou localmente em Chrome e PostgreSQL descartável.
2. Completar o browser QA com linhas rejeitadas/repetidas e saldos opcionais; o estado conciliado agora foi revisado em Chrome desktop/mobile.
3. Completar Portoes 10 e 11 com auditoria manual de teclado/zoom/contraste/leitor de tela, abuso de login/cadastro, retenção de extratos e verificações de operação proporcionais ao demo.

## Arquivos de referencia

- [Roadmap](FLOWPAY_IMPLEMENTATION_ROADMAP.md)
- [README e comandos locais](../README.md)
- [Instrucoes de desenvolvimento](../AGENTS.md)

## Retomada de 23/11/2026 - Portao 9.4

- A confirmacao do CSV aceita saldos opcionais de abertura/fechamento com datas. Migration V14 persiste metadados. O resumo separa atividade, saldo final do extrato vs caixa controlado e roll-forward.
- Decisao: valores de fim do dia; fechamento posterior a abertura; linhas aceitas apos a abertura e ate o fechamento; saldo negativo permitido. Reenvio do checksum preserva a primeira importacao.
- Frontend: typecheck, lint sem avisos e build passaram.
- Backend (23/11): `mvn --batch-mode --no-transfer-progress -f backend/pom.xml verify` passou com Maven 3.9.16/JDK 25 (compilação `--release 21`): 46 testes, 0 falhas, 0 erros, 0 skips; 17 integrações Testcontainers em PostgreSQL 16. Migration V14, seed, cálculos/replay de saldos, caixa, CSRF, isolamento, filtros de correlação e métricas de criação/replay foram exercitados; JAR Spring Boot empacotado.
- V14, replay de metadados, calculos com saldos positivo/negativo e isolamento foram verificados. O Portao 9.4 segue parcial ate revisar a jornada no browser; Portoes 10 e 11 ainda pedem E2E e acessibilidade.
- Previa isolada estava ativa no checkpoint: Vite em `http://127.0.0.1:5173`, API em `http://127.0.0.1:8080` e banco descartavel em tmpfs. Chrome via Playwright agora confirmou login, dashboard, contatos, conta e titulos em desktop/mobile; o helper visual nativo continua indisponivel.
- Proximo passo: executar/observar a jornada financeira Playwright no PostgreSQL descartável do CI; ela agora inclui acessibilidade axe para pagamento, linhas/resumo de conciliação e relatório mensal. Depois revisar visualmente conciliação e completar teclado/zoom/contraste/leitor de tela. Backend `mvn verify` local já passou (46/46; JDK 25/Maven 3.9.16).
