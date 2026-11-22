# FlowPay — plano de implementação

**Status:** plano proposto, baseado nos documentos de produto e arquitetura disponíveis.  
**Objetivo:** decompor FlowPay em entregas pequenas, demonstráveis e com critérios de aceite.  
**Regra de uso:** avançar por portões; reavaliar o plano ao descobrir o código existente. Não executar todas as etapas de uma vez.

## Como ler este plano

Cada etapa lista resultado, trabalho, dependências e critério de conclusão. “MVP” significa o fluxo manual de gestão financeira definido nos documentos; não significa integração bancária nem movimentação de dinheiro. As escolhas que os documentos não especificam estão marcadas como **decisão de implementação** para serem confirmadas no desenho da etapa correspondente.

Sequência geral:

```text
0. Descoberta e decisões
        ↓
1. Repositório e ambiente
        ↓
2. Esqueleto executável ──→ 3. Design system base
        ↓                               ↓
4. Persistência e tenancy → 5. Identidade → 6. Empresas e memberships
                                             ↓
                          7. Contatos → 8. Contas financeiras
                                             ↓
                          9. Categorias → 10. Recebíveis e pagáveis
                                             ↓
                          11. Movimentos e baixas → 12. Dashboard
                                             ↓
                          13. Importação CSV → 14. Conciliação
                                             ↓
                          15. Relatórios → 16. Jornada MVP
                                             ↓
                          17. Segurança e operação → 18. Demonstração
                                             ↓
                          19+. Recursos posteriores, cada um como projeto separado
```

O design system pode ser desenvolvido em paralelo ao esqueleto, pois não altera contratos financeiros. Os demais paralelismos só devem acontecer após definir interfaces e arquivos sob responsabilidade de cada pessoa/agente.

## Portão 0 — alinhar escopo e decisões

### 0.1 Confirmar o estado do repositório

- **Ações:** verificar branch, alterações locais, arquivos gerados, árvore atual, histórico, documentação e presença de PRD, código, manifests, testes e workflows.
- **Aceite:** inventário registra o que existe e o que está apenas planejado; alterações do usuário permanecem intactas.
- **Dependências:** nenhuma.

### 0.2 Resolver divergências e escolhas do MVP

Registrar decisão curta ou ADR para:

- O que a primeira demonstração precisa executar: autenticar, escolher empresa, cadastrar contato/conta, criar título, registrar baixa e consultar saldo.
- Ordem e nível de entrega da gestão multiusuário: proprietário e leitor são exemplos, mas nomes e permissões exatos não estão definidos.
- Como uma baixa se associa a movimento(s), como movimento não alocado entra no saldo, e se transferências entre contas fazem parte do MVP.
- Idempotência/outbox no núcleo financeiro: o Master chama esses itens de infraestrutura avançada; System Design exige idempotência e outbox para operações críticas. Decisão recomendada: usar chave idempotente e transação local nas mutações financeiras desde o primeiro endpoint; introduzir worker/outbox só quando surgir efeito assíncrono confiável.
- Escopo da importação de CSV no MVP: limites de arquivo, formatos de data, colunas suportadas, política de duplicidade entre arquivos, retenção e storage privado. Os números de 10 MB/50 mil linhas e 90 dias são hipóteses, não compromissos.
- Contratos API: prefixo/versionamento, paginação, filtro, formatos de dinheiro/datas, erro público, códigos HTTP, CSRF e resposta a repetição com chave idempotente.
- Decisão de implementação para exclusão/cancelamento de cadastros, retenção/auditoria e fuso horário empresarial.
- Gerenciador e versão do Node, estratégia de lockfile e compatibilidade de versões de dependências. Java 21 e Maven já foram escolhidos.
- Idioma dos documentos novos: Master prefere inglês para documentação pública; a comunicação e UI inicial são pt-BR. Definir idioma do README e do OpenAPI.

- **Aceite:** decisões têm responsável, consequência e estado (definida/pendente); escopo do MVP exclui funções posteriores.

### 0.3 Criar critérios gerais de produto e engenharia

- Definir demonstração útil: gestor cria dados e consegue responder quanto tem registrado, quanto falta receber/pagar e o que está vencido.
- Definir metas mensuráveis após existir implementação: cobertura das jornadas prioritárias, tempo de importação, p95 de endpoints ou performance de listagem. Não inventar metas de escala antes de medir.
- Definir o conjunto mínimo de verificação que bloqueia uma entrega: unitários, integração Postgres, API, UI e E2E conforme o risco.
- **Aceite:** uma pessoa que não conhece a conversa consegue verificar “pronto” a partir dos critérios escritos.

## Portão 1 — tornar o repositório reproduzível

### 1.1 Convenções de repositório

- Organizar `docs/`, `backend/`, `frontend/`, `.github/workflows/` e infraestrutura local somente quando os projetos forem criados.
- Definir `.gitignore` para IntelliJ, Node, builds, uploads locais, logs, ambiente e segredos. Nunca versionar `.env` com valores reais; usar `.env.example` sem segredo.
- Definir formatação Java e TypeScript, convenção de commits opcional, política de branch e padrão de nome de migration.
- **Aceite:** clone limpo não pede arquivos ignorados para funcionar; não há segredo ou dado financeiro pessoal no diff.

### 1.2 Criar backend mínimo

- Maven, Java 21 e Spring Boot numa versão compatível e suportada, Spring Web, Validation, Data JPA, Security e driver PostgreSQL/Flyway conforme a fatia habilitar cada camada.
- Criar pacote raiz `com.flowpay` e módulos iniciais sem criar código vazio para fases futuras.
- Adicionar endpoint de saúde que não exponha configuração nem dados sensíveis.
- **Aceite:** `./mvnw verify` funciona em clone limpo com Java 21, se Maven Wrapper for escolhido; nenhuma funcionalidade empresarial é declarada pronta.

### 1.3 Criar frontend mínimo

- Vite, React e TypeScript; escolher gerenciador de pacotes, fixar versão e commitar lockfile.
- Configurar React Router e estrutura inicial `app`, `design-system`, `features`, `shared` quando houver primeira tela.
- Comandos reais para instalação, desenvolvimento, teste, lint/typecheck e build.
- **Aceite:** instalação limpa e build de produção passam usando apenas o lockfile e runtime documentados.

### 1.4 Ambiente local

- Docker Compose para PostgreSQL e serviços locais necessários; health check, volume local ignorado pelo Git, porta configurável, credenciais apenas de desenvolvimento.
- Documentar subir, esperar readiness, migrar, executar aplicações e encerrar.
- **Aceite:** pessoa nova sobe o ambiente a partir do README sem banco previamente instalado; nada aponta para produção.

### 1.5 CI inicial

- GitHub Actions com Java 21 e Node escolhidos, cache derivado de lockfiles, compilação/typecheck/lint/testes e migrations aplicáveis.
- A pipeline deve falhar em teste quebrado e não mascarar ausência de serviços necessários.
- **Aceite:** workflow valida o mesmo conjunto essencial da máquina local e falha deliberadamente se um comando obrigatório falhar.

## Portão 2 — definir contratos e persistência compartilhada

### 2.1 Erros, IDs e contratos da API

- Definir envelope de erro estável, validação de campos e correlation/request ID.
- Definir serialização monetária: recomendação a decidir é transmitir centavos como inteiro dentro do limite acordado ou string decimal exata; Java BIGINT pode exceder precisão segura do `number` JavaScript.
- Datas de vencimento como `YYYY-MM-DD`; instantes ISO-8601 UTC. Respostas de relatório incluem período/timezone/`as_of`.
- Paginação e ordenação limitadas; sem consultas não limitadas em coleções crescentes.
- **Aceite:** contrato OpenAPI descreve requests/responses/erros e exemplos pt-BR não confundem vencimento com instante.

### 2.2 Modelo e migrations iniciais

Criar entidades somente conforme fatias: `Tenant`, `Membership`, `User/Identity`, `Contact`, `FinancialAccount`, categorias, títulos, movimentos, baixas, idempotência, auditoria, importações e conciliação.

Regras de banco:

- UUID ou estratégia acordada para IDs; `tenant_id` em todos os registros empresariais.
- `amount_minor BIGINT` com valor positivo, moeda/direção explícitas, limites e checks.
- FK composta tenant/recurso e `UNIQUE(tenant_id, id)` quando requerida para impedir associação cruzada.
- `version` em dados concorridos; índices por tenant, estado e vencimento conforme consultas.
- Restrições únicas para memberships, chave idempotente, checksum de arquivo por conta e match ativo.
- Migrations Flyway imutáveis após aplicadas; rollback tratado por migration corretiva quando necessário.
- **Aceite:** banco vazio migra; constraints rejeitam valores/associações inválidas; dados de teste não dependem da ordem de execução.

### 2.3 Contexto de tenant e transações

- Derivar tenant ativo de membership autenticado, não confiar em header/body do cliente.
- Tornar acesso ao contexto explícito e falhar fechado se não houver contexto empresarial em caso de uso que o exige.
- Testar consulta, leitura, escrita, contagem, exportação e jobs com dois tenants.
- **Aceite:** ID válido de outra empresa não revela registros e não cria efeito; FK composta protege contra bug de filtro no serviço.

## Portão 3 — identidade e empresas

### 3.1 Autenticação local segura

- Definir cadastro inicial (convite ou proprietário primeiro), verificação de e-mail e recuperação de senha apenas se caberem ao produto demo; não inventar integração real sem necessidade.
- Hash de senha com algoritmo gerenciado recomendado pelo Spring Security. Cookies HttpOnly/Secure/SameSite conforme ambiente; CSRF em mutações SPA; logout invalida sessão.
- Respostas de login/recuperação evitam enumeração de usuários. Rate limit e bloqueio progressivo definidos sem permitir negação fácil de serviço.
- **Aceite:** usuário autentica, sessão persiste de forma segura, logout encerra acesso, ausência de CSRF em escrita falha.

### 3.2 Tenant e membership

- Proprietário cria empresa e vira membro inicial. Nome, documento empresarial e informações opcionais precisam política de minimização; não coletar CNPJ sem uso concreto.
- Definir papéis e matriz de permissão: documentos exemplificam proprietário/gestor/leitor, mas ainda não fecham ações por papel.
- Convite de membro futuro pode ser adiado; MVP de demonstração pode iniciar com proprietário e leitor seed/local.
- **Aceite:** troca de empresa atualiza contexto e dados; usuário sem membership não pode ler nem operar tenant.

### 3.3 Recuperação, sessões e segurança

- Expiração, renovação/rotação, invalidação após troca de senha e comportamento em múltiplas abas/dispositivos.
- Sessão/cookie com CSRF testado de ponta a ponta pela SPA.
- **Aceite:** fluxos de autenticação funcionam em navegador e API de teste sem exceção insegura de CSRF.

## Portão 4 — contatos e fundação visual

### 4.1 Design tokens e shell

- Implementar tokens semânticos do Design System, tipografia, fundo/superfície, foco, status e espaçamento; medir contraste no contexto real antes de congelar as cores.
- Criar `AppShell`, navegação desktop/tablet/mobile, cabeçalho da empresa, estado de sessão, página de erro e skeleton.
- Respeitar teclado, zoom, foco visível, `prefers-reduced-motion` e alvos móveis.
- **Aceite:** shell navegável em três larguras e foco visível sem depender apenas de cor.

### 4.2 Componentes base

- Button, Link, Badge, TextField, MoneyField, DateField, Select/Combobox, Alert, Toast, Skeleton, EmptyState, ConfirmDialog e Table/Pagination conforme necessidade imediata.
- Cada componente tem estados disabled/loading/error/focus e testes de interação.
- Storybook fica opcional até a biblioteca reutilizável justificar manutenção.
- **Aceite:** telas usam componentes/token compartilhados; nenhuma paleta hex repetida em features.

### 4.3 Clientes e fornecedores

- Começar por Contact com classificação cliente/fornecedor ou dois papéis combináveis, decisão explícita do domínio.
- CRUD necessário: criar, listar, pesquisar, ver detalhes e editar; exclusão/cancelamento e duplicidade precisam regra definida.
- Campos mínimos: nome, tipo, e-mail/telefone opcionais e observação restrita; CPF/CNPJ só se uso e proteção estiverem definidos.
- **Aceite:** cadastro de um tenant nunca aparece para outro; busca, paginação, validação e estados vazios funcionam.

## Portão 5 — contas, categorias e títulos

### 5.1 Contas financeiras

- CRUD conta de controle: nome, tipo/categoria, saldo inicial em centavos, data de referência e estado ativo.
- Definir se saldo inicial é fato imutável separado ou atributo editável até primeira movimentação. Preferir histórico explícito de ajuste após existir uso.
- **Aceite:** saldo inicial aparece como saldo controlado com data de referência; não é apresentado como consulta bancária.

### 5.2 Categorias

- Receitas/despesas, categorias padrão do tenant ou globais imutáveis, arquivamento e regra para categorias em uso.
- **Aceite:** listagens e relatórios filtram por categoria sem misturar empresas.

### 5.3 Recebíveis e pagáveis

- Campos: descrição, contato, valor original em centavos, vencimento, categoria, conta prevista se definida, observação, status calculado e datas de criação/alteração.
- Estados de negócio: em aberto, parcialmente baixado, quitado, vencido calculado por data e saldo remanescente; cancelamento/reversão separado de pagamento.
- Definir edição após baixa: preservar histórico, impedir alterações que invalidem somas ou exigir fluxo de ajuste.
- **Aceite:** filtro por status/pessoa/vencimento, detalhe e edição válida; valor zero/negativo e tenant alheio são rejeitados.

### 5.4 Páginas de títulos

- Listas de contas a receber e pagar com pendente, vencimento, status e ações.
- Formulário com validação pt-BR, MoneyField exato, prevenção de submissão dupla e recuperação após erro.
- **Aceite:** mesma API alimenta lista e detalhe; valores persistidos são exibidos exatamente em pt-BR.

## Portão 6 — caixa e baixas

### 6.1 CashTransaction

- Movimento é a fonte de verdade para saldo controlado: tenant, conta, direção, amount_minor, moeda, instante/data contábil acordada, descrição, origem e estado.
- Definir lançamentos manuais diretos vs. lançamentos que nascem de baixa, evitando criar duas transações para a mesma ocorrência.
- Saldo projetado por soma sobre movimentos confirmados e saldo inicial, ou read model simples; não cachear sem estratégia de invalidação.
- **Aceite:** receita aumenta e despesa reduz conta conforme direção, com histórico identificável.

### 6.2 Baixa parcial e total

- API registra recebimento/pagamento realizado fora do FlowPay e cria alocação + movimento atomicamente, ou aloca movimento existente segundo caso de uso.
- Bloquear título em disputa, validar soma de baixas ≤ título, soma de alocações ≤ movimento, tenant e conta autorizados.
- Repetição após timeout usa chave idempotente persistida; payload diferente com mesma chave tem resposta de conflito definida. Resposta e crash recuperáveis.
- Definir lock order para título, conta e idempotency key; transação curta, sem chamadas externas.
- Auditoria e outbox no mesmo commit quando houver evento que precise sobreviver ao crash. Não usar outbox como requisito para ação síncrona sem consumidor.
- **Aceite financeiro:** título de R$100 recebe duas solicitações concorrentes de R$70: uma confirma, outra falha integralmente; baixa=R$70, pendente=R$30, um movimento. Repetir chave vencedora não duplica efeitos.
- **Aceite reversão:** reversão exige motivo, mantém registro original e gera estado/efeito auditável sem apagar movimento silenciosamente.

### 6.3 Transferências e ajustes

- Antes de implementar, decidir se transferência entre contas entra no MVP. Caso afirmativo, representar duas pernas em operação atômica com valor pareado e sem alterar caixa total da empresa.
- Ajuste de saldo exige razão/motivo e trilha de auditoria.
- **Aceite:** relatório não interpreta transferência interna como receita ou despesa consolidada.

## Portão 7 — dashboard e relatórios de uso diário

### 7.1 Indicadores

- Saldo controlado, receber, pagar e vencidos; período, timezone e `as_of` explícitos.
- Previsto por vencimentos separado do realizado por movimentos confirmados.
- Consultas agregadas por tenant, com índices e limites verificados; considerar montantes negativos/estornos conforme regra definida.
- **Aceite:** KPIs coincidem com consultas de movimentos/títulos na massa de referência.

### 7.2 Dashboard responsivo

- Cabeçalho de período e “Novo lançamento”; KPIs; fluxo previsto × realizado; próximos vencimentos; movimentos recentes e pendências de conciliação.
- Se houver saldo importado, exibir separado com data e diferença a conciliar.
- **Aceite:** sem dado, exibir empty state; erro explica próximo passo; desktop e mobile preservam valor/status/vencimento.

### 7.3 Relatório mensal básico

- Entradas/saídas por período e categoria, contas pendentes, vencidas e movimentos; exportação fica decisão posterior.
- Definir timezone comercial e limites de período; não fazer consultas cross-tenant.
- **Aceite:** totais reconciliam com registros para dataset conhecido e filtros têm nomes claros.

## Portão 8 — importação CSV

### 8.1 Contrato e parser

- Definir dialeto aceito, separador, encoding, cabeçalho, formato de data, decimal/separador de milhar, sinal/direção, tamanho máximo, limite de linhas e erros por coluna.
- Parser não executa fórmulas/macros; tratar BOM, linhas vazias, aspas, vírgula/ponto e datas ambíguas de forma explícita.
- Aplicar limites de recursos e proteger exportações contra CSV formula injection.
- **Aceite:** fixtures válidos e inválidos têm prévia determinística; valor/direção não são inferidos silenciosamente.

### 8.2 Upload privado e prévia

- Selecionar conta autorizada, guardar arquivo em diretório local privado para dev ou storage privado; persistir metadados, checksum e status.
- Preview mostra aceitas, rejeitadas e duplicatas sem caixa; confirmar importação é ação explícita.
- Limpar temporários em falha/cancelamento e não registrar conteúdo bruto no log.
- **Aceite:** arquivo fica inacessível a usuário de outro tenant e importado não altera saldo.

### 8.3 Confirmação, deduplicação e reprocesso

- Unicidade `(tenant, conta, checksum)` sob concorrência; deduplicação dentro do arquivo e candidatos entre arquivos com decisão humana.
- Definir colisão do arquivo já importado: responder import existente ou indicar duplicata; não regravar linhas.
- Para processamento síncrono pequeno, transação e limites definidos; jobs grandes somente quando volume justificar.
- Se job assíncrono entrar, persistir estados e checkpoints com retry idempotente; não declarar exactly-once.
- **Aceite:** importar/reimportar o mesmo CSV não cria novos movimentos, baixas ou linhas duplicadas.

### 8.4 UI de importação

- Etapas: conta → arquivo → mapeamento → prévia/erros → confirmar → rever duplicatas; foco e progresso acessíveis.
- **Aceite:** cancelar não confirma importação; mensagem diz que importar salva linhas e não cria caixa.

## Portão 9 — conciliação

### 9.1 Sugestões

- Candidatos por valor/direção/moeda, janela de data e texto; regras de scoring explicáveis. Não usar score para aprovar automaticamente caso ambíguo.
- Revalidar saldo, tenant e estado no momento da confirmação, pois candidatos podem ter mudado.
- **Aceite:** casos únicos são fáceis de reconhecer; ambiguidades mostram evidências e exigem escolha humana.

### 9.2 Confirmar vínculo

- Caminho A: vincular `StatementLine` a movimento/baixa existente sem criar caixa.
- Caminho B: usuário escolhe criar movimento; registrar movimento e vínculo atomicamente.
- Match ativo único por linha e decisão tenant/conta validada por FK/serviço.
- **Aceite:** o mesmo reconhecimento nunca percorre A e B; reenvio não cria novo efeito.

### 9.3 Ignorar, desfazer e exceções

- Distinguir ignorar sugestão, marcar linha como revisada e desfazer conciliação. Preservar evidência e motivo da reversão.
- Sem match parcial ou múltiplas linhas para uma transação no MVP, até definir novo contrato.
- **Aceite:** desfazer não apaga linha original nem muda saldo indevidamente; estado pode ser auditado.

### 9.4 UI da conciliação

- Desktop com linha/extrato e candidatos; mobile sequencial; valor/data/descrição/origem e ações Vincular/Criar/Ignorar.
- **Aceite:** usuário vê saldo controlado, extrato e diferença com referência temporal; ação de alta consequência pede contexto.

## Portão 10 — MVP demonstrável

### 10.1 Jornada completa

1. Proprietário cria conta e empresa.
2. Cadastra cliente e fornecedor.
3. Cadastra conta financeira com saldo inicial.
4. Cria título a receber e despesa a pagar.
5. Registra recebimento/pagamento já realizado externamente.
6. Vê caixa controlado, previsto, pendente e vencido.
7. Importa CSV exportado pelo usuário.
8. Concilia linha com movimento existente sem duplicidade.
9. Consulta resumo mensal.

- **Aceite:** execução E2E reprodutível com tenant de demonstração e dados fictícios; snapshots de estado confirmados no banco; outra empresa não lê esses dados.

### 10.2 Dados seed e ambiente demo

- Seed determinístico, removível/recriado, senhas e dados sintéticos sem relação com pessoas reais.
- Demonstrar banner/ambiente demo; reset documentado sem risco a dados externos.
- **Aceite:** ambiente pode ser recriado em banco limpo com resultado conhecido; nenhum segredo seed é reutilizado fora do dev/demo isolado.

### 10.3 Usabilidade e acessibilidade

- Testar teclado, leitores de tela nos fluxos principais, foco/modal, erros, zoom 200%, contraste e viewport móvel.
- Conferir linguagem não técnica, status não comunicado só por cor, valores negativos e datas no fuso definido.
- **Aceite:** relatório de auditoria documenta critérios verificados e questões abertas; não reivindicar conformidade ampla sem avaliação.

## Portão 11 — qualidade, segurança e operação

### 11.1 Testes por camada

- Unitários: estados, Money, regras de títulos, limites, datas e arredondamento/formatação.
- Integração: migrations/constraints/locks no PostgreSQL por Testcontainers.
- API: autenticação, CSRF, autorização, erros e tenant.
- Concorrência: dupla baixa, chave repetida, checksum repetido e match disputado; sincronização determinística e consulta dos efeitos finais.
- UI: formulários, cache separado por empresa, erro/retry, tabelas e estados acessíveis.
- E2E: jornada MVP e isolamento de tenant.
- **Aceite:** CI executa a suíte necessária; resultados e limitações são reportados.

### 11.2 Modelagem de segurança e privacidade

- Mapear ativos, papéis, limites de confiança, ameaças IDOR/CSRF/XSS/SQLi/upload/abuso e controles com referência OWASP ASVS.
- Limitar dados pessoais coletados; política de acesso/download/retention/exclusão segura de extratos definida antes de aceitar dados sensíveis.
- Redação de logs; segredos só em configuração local/gerenciador; dependências verificadas e atualizadas.
- **Aceite:** testes negativos de autorização, upload e CSRF; plano de correção de achados, sem prometer segurança absoluta.

### 11.3 Observabilidade

- Logs estruturados com request ID e IDs opacos, sem PII, conteúdo CSV ou token.
- Métricas de latência/erro, duração de importação, falhas de job/outbox e lock contention sem labels de alta cardinalidade.
- Micrometer primeiro; OpenTelemetry/Prometheus/Grafana conforme necessidade de visualização e demonstração.
- **Aceite:** falha de API/job pode ser localizada e correlacionada sem expor valores sensíveis.

### 11.4 Backup e restauração

- Definir dados incluídos, retenção, RPO/RTO como objetivos a decidir antes de uso real; executar restauração em ambiente isolado.
- Incluir metadados e política de storage de arquivos de extrato.
- **Aceite:** restauração ensaiada produz dados consistentes e procedimento está documentado antes de qualquer promessa operacional.

### 11.5 Deploy de demonstração

- HTTPS, configuração separada, banco privado, storage privado, migrações controladas, limites de upload, logs sanitizados e plano simples de reversão.
- **Aceite:** deployment de demo validado com dados fictícios; nenhum recurso de pagamento real ou integração bancária.

## Portão 12 — recursos posteriores, cada um com seu próprio plano

Não fazem parte do MVP básico. Só iniciar um recurso quando jornadas existentes e dados forem confiáveis.

### 12.1 OFX e importação avançada

- Especificação parser/versionamento, encoding, limite/streaming, prevenção de duplicata entre arquivos e import job recuperável.
- **Aceite:** bateria de fixtures reais anonimizadas ou fabricadas, sem incluir extratos pessoais no repositório.

### 12.2 Serviços e contratos

- Catálogo de serviços, versão de contrato, cliente, período e valores; regras para alteração/cancelamento e vínculo financeiro.
- **Aceite:** contrato não cria cobrança sem regra explícita; mudanças preservam histórico.

### 12.3 Faturas e cobranças

- Documento de cobrança, itens, totais, vencimento, status, emissão/cancelamento; sem afirmar validade fiscal ou emitir nota fiscal.
- Definir PDF/email em tarefa própria e privacidade antes de anexos.
- **Aceite:** fatura representa cobrança e não altera caixa até baixa confirmada.

### 12.4 Assinaturas e recorrência

- Plano, cadência, próxima ocorrência, pausa/cancelamento, geração idempotente de títulos e timezone.
- **Aceite:** retry de job não duplica ocorrência e cancelamento respeita data/regra definida.

### 12.5 Notificações

- Preferências, horários, destinatários, template, consentimento/descadastro, retry e deduplicação. Provedor configurado sem credencial fixa.
- **Aceite:** falha ou retry não gera notificações duplicadas; não bloqueia transação financeira.

### 12.6 API pública e webhooks

- Scopes, rotação/revogação de credenciais, rate limit, versionamento, assinatura HMAC, timestamp, replay window, retries e redelivery.
- **Aceite:** segredo só é mostrado no provisionamento, payload não vaza PII, assinatura inválida/replay é rejeitado.

## Portão 13 — laboratório de pagamentos simulados

### 13.1 Fronteira de segurança e domínio

- Definir explicitamente se sandbox é apenas ambiente de desenvolvimento/teste ou feature visível no produto e qual usuário acessa.
- Persistência, tabelas, APIs, rotas e identificadores distintos de `finance` real; nenhuma FK ou caso de uso permite liquidar título real a partir de pagamento simulado.
- Badge/URL/namespace `SANDBOX` visível. Não aceitar credenciais ou dinheiro real.
- **Aceite:** teste de integração tenta cruzar sandbox→finance e é rejeitado na API e no banco/contrato.

### 13.2 Estado de pagamento e provedor fictício

- Modelo de autorização/captura/falha/estorno com máquina de estados, comando idempotente e provedor determinístico/fault injection.
- Definir timeout, resultado desconhecido, replay e concorrência com locks/versão.
- **Aceite:** mesma operação repetida tem um único efeito simulado; estados inválidos são recusados.

### 13.3 Ledger de dupla entrada

- Definir contas, débitos/créditos, moeda, transação balanceada, imutabilidade e estratégia de correção por lançamentos compensatórios.
- **Aceite:** cada lote debita e credita valores iguais; lote desequilibrado não comita; reversão preserva ledger.

### 13.4 Settlement e reconciliação simulada

- Settlement fictício separado; arquivo/ciclo de provedor mock; discrepâncias e reparação.
- **Aceite:** relatório liga operação simulada ao ledger e settlement sem afetar caixa real.

### 13.5 Outbox, workers e recuperação

- Outbox persistida no mesmo commit do pagamento simulado; worker com lease/claim, backoff limitado, dead-letter observável e dedupe por consumer/event ID.
- Testar crash antes/depois do envio e retomada; contrato declara entrega at least once.
- **Aceite:** reinício não perde evento confirmado nem repete efeito de consumidor; nenhuma afirmação de exactly-once distribuído.

## Portão 14 — evolução com evidência

- Medir p95, consultas, importação e locks no conjunto de dados compatível com hipóteses; otimizar query/índice antes de cache.
- Considerar extração de módulo só quando houver evidência de necessidade independente de deploy/ownership/escala e contrato estável.
- Considerar broker/Redis/Kubernetes apenas mediante problema medido, plano operacional e comparação de custo.
- Tema escuro só após tokens e estados com contraste validados; visual testing/catálogo quando componentes e regressões justificarem.
- **Aceite:** cada complexidade nova tem problema medido, alternativa simples avaliada e teste/monitoramento que confirma o benefício.

## Critérios transversais de conclusão

Uma etapa é “pronta” quando todos se aplicarem:

1. Critérios observáveis e dono do comportamento definidos.
2. Contrato backend/frontend persistido e versionável alinhado.
3. Segurança de tenant/autorização cobre endpoints e tarefas assíncronas afetadas.
4. Invariantes financeiras relevantes têm teste que falha se violadas.
5. Estados de UI, recuperação de erro e feedback no idioma correto foram tratados.
6. Migrations, comandos, documentação e configuração de exemplo atualizados.
7. Verificações foram executadas; resultado e limitações registrados.
8. Review trata achados importantes; nada é descrito como implementado se ainda é mock/proposta.
9. A demonstração não usa dinheiro real, integração bancária, dados pessoais ou segredos reais.

## Decisões que o plano não inventa

Os documentos ainda não fecham: matriz de papéis, onboarding/convites, campos pessoais/empresariais, formato financeiro da API, política de edição/deleção, transferência entre contas, dedupe de linhas entre arquivos diferentes, timezone do tenant, limites reais de upload/escala, política final de retenção, contrato de conflito de idempotência, escopo do outbox no núcleo, exportação, e escopo visível do sandbox. Tratar cada ponto quando se tornar bloqueio de uma etapa; não parar o bootstrap por decisões necessárias apenas a fases futuras.

## Uso de agentes durante a implementação

Ativar colaboração apenas quando a sessão permitir e a tarefa justificar. Sequência sugerida: `flowpay-architect` define contrato/aceite; `flowpay-backend` implementa persistência/API; `flowpay-frontend` integra UI após contrato; `flowpay-quality` revisa e valida; `flowpay-devops` cuida de ambiente/CI. Implementação paralela backend/frontend somente quando contrato e arquivos estiverem delimitados. O agente principal integra, executa a jornada e relata evidências.
