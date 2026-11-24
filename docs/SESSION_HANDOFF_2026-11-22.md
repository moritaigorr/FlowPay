# FlowPay - checkpoint de implementacao

Atualizado: 24/11/2026

## Continuação registrada em 23/11/2026

- Etapa 5.4: substituído `window.prompt` do cancelamento de título por formulário inline acessível, com motivo obrigatório, limite de 500 caracteres, foco no campo e retorno do foco ao cabeçalho ao fechar/enviar. O texto informa que cancelar não cria movimento de caixa. Rascunho é limpo em troca de sessão/logout.
- A jornada financeira E2E agora cria e cancela um título e confirma a persistência do motivo. `npm.cmd run lint` e `npm.cmd run build` passaram. A jornada completa com API/PostgreSQL não foi executada novamente nesta sessão.
- Próxima fatia: filtros de títulos por estado, pessoa e período (roadmap 5.4), começando por conferir contrato existente e definir o menor filtro vertical útil. Não ampliar para outros domínios antes de verificar esse contrato.

Este arquivo registra onde retomar a jornada ativa do MVP.

## Objetivo

Implementar os estagios restantes do roadmap em incrementos pequenos, mantendo as invariantes de financas, tenancy, seguranca e UX. O MVP ainda nao esta concluido.

## Continuação registrada em 24/11/2026

- Implementada filtragem de títulos por estado, contato e intervalo de vencimento na API e na interface. O estado em aberto/vencido considera a data de referência `America/Sao_Paulo`; troca de usuário limpa todos os filtros. O teste de integração PostgreSQL cobre estados aberto, vencido, parcial, quitado e cancelado, contato, intervalo e período inválido; há efeitos de baixa/cancelamento em banco Testcontainers. Ainda não executado localmente. `npm.cmd run lint` e `npm.cmd run build` passaram. A suíte PostgreSQL não foi executada: Maven/Docker não estão disponíveis e o JDK 25 apontado no ambiente não existe no caminho informado (PATH resolve Java 8).
- O Actions run #6 (`78836f7`) aprovou o job de backend e falhou no `tsc` do frontend por `setTitlesQuery(')` malformado no botão Limpar. Corrigido localmente; lint/build passam, mas não houve novo run porque não foi feito push. O Playwright demo verifica parâmetros de status/contato/período, limpeza e mensagem vazia contextual; ainda não foi executado no browser nesta sessão. O teste Testcontainers agora percorre todos os estados derivados (aberto, vencido, parcial, quitado, cancelado) e fixa o vencimento na data empresarial para não falhar perto da virada UTC.
- Próxima ação: executar a jornada Playwright e observar novo CI quando o commit local for sincronizado. O teste Testcontainers cobre a consulta, mas execução local aguarda JDK 21, Maven e Docker disponíveis.

## Continuação registrada em 24/11/2026

- Segurança da identidade: cadastro por `POST /api/auth/register` negado por padrão (`flowpay.identity.registration.enabled=false`); `application-local.yml` habilita o fluxo local. Foi adicionado teste unitário para rejeitar o cadastro antes de persistência/autenticação quando desabilitado, além de teste MockMvc de sucesso no perfil local. Esses testes aguardam execução. Login continua disponível nos demais perfis. Rate limiting ainda não existe; não expor registro publicamente.
- A SPA consulta `GET /api/auth/config` e oculta o cadastro quando desabilitado ou quando a configuração falha, mantendo login visível. O E2E demo espera o cadastro no perfil local. Frontend: lint/build e descoberta Playwright passaram; jornada de browser ainda não executada. O teste unitário afirma default false e o MockMvc local afirma true, mas os testes de backend aguardam execução (Maven/JDK 21/Docker).

## Estado do roadmap

- Atualizacao de 23/11: DemoSeedPostgresIntegrationTest parte de PostgreSQL 16 descartavel vazio, confere registros, membership OWNER e hash, e reexecuta sem duplicacao. Tambem valida login com CSRF e leituras de contatos/dashboard pela sessao demo.
- Isolamento confirmado pela API: outra identidade, com membership separado, sessão e CSRF válidos, não lê nem altera contato demo e vê saldo controlado zero. Em 23/11, Chrome local confirmou demo-login e jornada financeira completa (2/2 E2E) em PostgreSQL descartável, com replay de CSV sem duplicar caixa e saldo final esperado de R$ 1.150,00. O workflow GitHub Actions ainda precisa de execução observada; revisão visual da conciliação e auditoria manual de acessibilidade seguem pendentes.

- Portao 9.3 implementado: revisao de linha (PENDING, REVIEWED, IGNORED), motivo, autor, evento append-only e idempotencia por tenant. Linhas ignoradas nao recebem sugestoes nem podem ser conciliadas ate reabrir.
- Desfazer vinculo mantem o movimento de caixa. Estornar o movimento e uma operacao separada. Ignorar/restaurar candidato nao altera caixa nem afeta outras linhas.
- Portão 9.4: revisão visual desktop/mobile concluída para a jornada coberta. Corrigidas tabelas roláveis sem foco, espaçamento/pluralização e apresentação dos saldos informados. E2E agora cobre CSV com duas linhas aceitas, uma duplicada, uma rejeitada, abertura R$ 1.000, fechamento R$ 1.150, diferenças de saldo/roll-forward R$ 0, vínculo sem duplicidade e replay. Chrome + PostgreSQL descartável: suíte completa 2/2 passou.
- Portao 10.2 backend verificado: seed, reexecucao idempotente, login/CSRF e isolamento de leitura/alteracao por outro tenant. A apresentacao visual ainda aguarda revisao mais ampla.
- Portao 10.3 parcial: fluxo demo em desktop/mobile, scans axe e alternador/envio do login via Tab/Enter passaram; gestao de foco tambem esta validada por build/lint/typecheck. Auditoria manual de teclado nos outros fluxos, contraste, zoom e leitor de tela continua pendente.
- Seed implementa empresa, login `demo@flowpay.local`, dois contatos, conta com saldo inicial ficticio, categorias e titulos em aberto. Nao apaga registros nem cria baixas/importacoes. Protecao adicional: so executa com perfil `local` e URL PostgreSQL loopback para o banco `flowpay`.
- Pendencias restantes do demo: revisar a apresentacao visual completa; o acesso HTTP e isolamento entre tenants estao cobertos.
- Portão 10.1: jornada Playwright opt-in validada localmente em Chrome com tenant e PostgreSQL descartáveis. Além do fluxo financeiro, verifica rejeição de data inválida, duplicata em arquivo, saldo de abertura/fechamento, roll-forward, saldo R$ 1.150,00, conciliação sem duplicar caixa e replay. Suíte completa 2/2 passou.
- Nota operacional: a primeira tentativa de E2E reutilizou uma Vite já aberta e criou o tenant sintético `E2E fe5ed8ed-9753-4f96-a192-e2a8e8fda94b` na instância local preexistente; não foi removido, pois a aplicação não oferece exclusão segura de tenant. As execuções aprovadas depois usaram API, Vite e PostgreSQL descartáveis em portas exclusivas.
- Portão 10.1 permanece parcial até observar o CI. Portões 10.3 e 11 seguem parciais pela auditoria manual de acessibilidade e operação; revisão visual dos estados E2E da conciliação está concluída.
- Portao 11.2: revisão parcial em `FLOWPAY_SECURITY_REVIEW.md`; testes MockMvc de multipart CSRF, acesso a conta de outro tenant e limite de 10 MB passaram no `mvn verify` local. Sem declaração de conformidade ASVS.

## Commits recentes

- `5b652c0` Verify statement balances and rejected CSV rows
- `1e5e205` Improve reconciliation tables on mobile
- `0c57a5f` Record local E2E isolation incident
- `743e6d5` Verify isolated FlowPay financial E2E journey
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
2. Observar a suíte no GitHub Actions e conferir no browser estados de saldo incompatível e importação somente com linhas rejeitadas.
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

## Retomada de 23/11/2026 - Importacao rejeitada e saldos divergentes

- Estendido `frontend/e2e/financial-settlement.spec.ts` para importar um segundo CSV com uma unica linha rejeitada e validar historico, motivo de rejeicao, ausencia de acoes de conciliacao, saldo controlado preservado em R$ 1.150, diferenca de fechamento de R$ 150 e roll-forward de R$ 300.
- A jornada confirma que arquivo sem linhas aceitas nao altera caixa; a conciliacao mostra zero linhas validas e reporta a diferenca entre atividade registrada e extrato.
- Corrigida a inicializacao CSRF da SPA: GETs concorrentes compartilham a renovacao em andamento, a renovacao inicial fica dentro do tratamento de erro e login/cadastro aguardam CSRF fresco. Isso evitou a falha 403 observada intermitentemente no E2E com duas workers.
- Verificacoes: frontend lint/build passaram; Playwright Chrome com PostgreSQL Testcontainers descartavel passou 2/2 com duas workers. GitHub Actions ainda nao foi observado.
- Ambiente E2E usou Vite 5174, API 8081 e PostgreSQL 16 descartavel. Nao usar API preexistente em 8080 nem reutilizar o tenant sintetico antigo descrito anteriormente.
- Proximo passo: acompanhar um run do CI no GitHub Actions; depois completar a revisao manual de acessibilidade/operacao e seguir para os proximos itens do roadmap MVP.

## Retomada de 23/11/2026 - Diagnostico do GitHub Actions

- Consulta read-only ao Actions encontrou o run CI #5, SHA remoto `def2abfb87df4956bb074d20a79b6bdc8383d346` (run ID `45257072993`). Frontend checks passou; Backend build falhou em `FinancialTitlePostgresIntegrationTest`.
- Causa raiz nos logs: `webEnvironment=NONE` deixa de registrar `AuthenticationConfiguration`, requerido pelo bean de autenticação da aplicação; os 11 erros são a falha do mesmo contexto repetida pelo limite do Spring.
- O checkout local já corrige essa classe com `webEnvironment=MOCK` no commit `eb508aa`; `backend/target/surefire-reports` registra 12 testes PostgreSQL desse teste, 0 falhas/erros/skips. O SHA remoto do run não contém essa correção. A execução do GitHub nao valida o checkout atual.
- A tentativa de `mvn verify` nesta retomada não foi iniciada: `mvn` não está no PATH, e este checkout não tem Maven Wrapper. O relatório local preexistente comprova somente aquela classe de integração, não um verify completo nesta sessão.
- Próximo passo: observar um run da revisão atual quando os commits locais forem sincronizados com o remoto. Não foi feito push.

## Retomada de 23/11/2026 - Backup e ensaio de restauracao local

- Iniciado o Portao 11.4 com `scripts/backup-postgres.ps1` e `scripts/restore-postgres.ps1`, limitado ao servico Compose local `db` e banco `flowpay`.
- Backup usa formato custom do PostgreSQL, valida o arquivo e grava em `backups/`, agora ignorado pelo Git. Restore exige alvo novo `flowpay_restore_*`, verifica que nao existe, valida o archive, restaura sem owner/ACL e confere o historico Flyway; nao sobrescreve nem remove banco algum.
- README explica uso local e limita explicitamente o procedimento: sem politica de retencao, criptografia, RPO/RTO de producao ou backup de object storage.
- Verificacoes: parser PowerShell aprovou ambos scripts; guards de nome e arquivo foram exercitados; mock de CLI validou sequencia backup/restore, contagem de migrations e recusa de alvo existente. Ainda falta ensaio real com Docker/PostgreSQL: CLI Docker nao esta no PATH nesta sessao.
- Proximo passo: executar o ensaio com Docker Compose em banco local descartavel e comparar as migrations restauradas; manter politicas de backup de producao em aberto ate definir requisitos operacionais.

## Retomada de 24/11/2026 - Transferencias internas

- Decisao de implementacao para o MVP: registrar transferencias entre contas proprias como uma saida e uma entrada pareadas. FlowPay documenta a operacao feita fora do produto; nao movimenta o dinheiro.
- Migration V15, endpoint idempotente e formulario de transferencia foram adicionados. O banco vincula tenant, valor, data e descricao das pernas; o servico exige contas distintas e ativas e bloqueia-as em ordem estavel.
- Saldo por conta reflete as pernas; caixa consolidado fica igual. Dashboard e categorias mensais excluem transferencias das entradas/saidas operacionais; o historico indica as contas envolvidas.
- Incluida cobertura PostgreSQL MockMvc para criacao, replay, conflito de chave, mesma conta, saldos por conta e consolidados e totais operacionais. A jornada E2E agora cria outra conta e transfere R$ 25,00.
- Verificacoes nesta retomada: npm run lint, npm run build e listagem Playwright passaram. javac --release 21 compilou os tres arquivos Java alterados e o teste de integracao usando o classpath Maven em cache. A execucao PostgreSQL e a jornada browser aguardam Docker/Maven e servicos locais; Docker e Maven nao estao no PATH desta sessao.
- Proximo passo: executar mvn verify e a jornada Playwright completa em PostgreSQL descartavel quando Docker/Maven estiverem disponiveis; entao avancar para os itens de qualidade/operacao e decidir separadamente a politica de ajuste manual de saldo.

## Retomada de 24/11/2026 - Permissoes por papel

- Formalizada a decisao MVP: OWNER e MANAGER podem escrever dados operacionais; READER tem acesso somente de leitura. Administracao de membros permanece fora do recorte enquanto nao existe convite/gestao de membership.
- Criada politica central TenantRole.canManageBusinessData e aplicada a contatos, contas, categorias, titulos, movimentos, importacao e conciliacao. Isso alinha as escritas antes OWNER-only com a capacidade de MANAGER ja existente em importacao e conciliacao.
- Cobertura adicionada para a matriz de papel e para sessao MockMvc: manager cria contato, conta, categoria, movimento e titulo; reader le contato/movimento e recebe 403 nas escritas.
- Verificacao: javac --release 21 compilou a politica, os servicos alterados e os testes; npm run lint/build e descoberta Playwright passaram. O MockMvc/Testcontainers ainda nao rodou: Maven e Docker nao estao no PATH desta sessao. Proximo passo: executar mvn verify e as jornadas browser em PostgreSQL descartavel quando Docker/Maven estiverem disponiveis.
