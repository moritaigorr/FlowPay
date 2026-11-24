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
- Java 21/Maven já estavam definidos; o bootstrap decidiu Node 26 LTS com npm e lockfile, Spring Boot 4.2.0, Vite 8.3 e README em inglês. Ver versões e comandos atuais no README antes de atualizações.
- Idioma dos documentos novos: Master prefere inglês para documentação pública; comunicação e UI inicial são pt-BR. Definir idioma do OpenAPI quando esse contrato existir.

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

- Maven, Java 21 e Spring Boot 4.2.0, Spring MVC, Validation, Data JPA, Actuator e driver PostgreSQL/Flyway conforme a fatia habilitar cada camada. Segurança de login entra na etapa de identidade.
- Criar pacote raiz `com.flowpay` e módulos iniciais sem criar código vazio para fases futuras.
- Expor somente o health check do Actuator, sem detalhes de ambiente.
- **Aceite:** `mvn -f backend/pom.xml verify` funciona com Java 21 e Maven; nenhuma funcionalidade empresarial é declarada pronta.

### 1.3 Criar frontend mínimo

- Node 26 LTS, npm, Vite 8.3, React e TypeScript; runtime e lockfile ficam fixados no repositório.
- Adicionar React Router, query, forms e estrutura de features quando a primeira tela real precisar deles.
- Comandos reais para instalação, desenvolvimento, lint/typecheck e build constam no README.
- **Aceite:** instalação limpa e build de produção passam usando apenas o lockfile e runtime documentados.

### 1.4 Ambiente local

- Docker Compose usa PostgreSQL 18.7, health check, volume local, porta configurável e credenciais somente de desenvolvimento.
- README documenta subir/parar banco e executar aplicações; migrations entram quando a primeira mudança de persistência for criada.
- **Aceite:** pessoa nova sobe o ambiente a partir do README sem banco previamente instalado; nada aponta para produção.

### 1.5 CI inicial

- GitHub Actions com Java 21 e Node 26, cache de Maven/npm, compilação, typecheck e lint.
- A pipeline deve falhar quando um comando obrigatório falhar; PostgreSQL entra no CI quando testes de integração o exigirem.
- **Aceite:** workflow valida o mesmo conjunto essencial da máquina local e falha deliberadamente se um comando obrigatório falhar.

## Portão 2 — definir contratos e persistência compartilhada

### 2.1 Erros, IDs e contratos da API

**Implementado (24/11/2026):** erros de controller/domínio, validação, JSON malformado, erros HTTP do Spring MVC, falhas inesperadas, autenticação, autorização e CSRF usam `application/problem+json` com formato RFC 9457, `requestId` correlacionado ao header `X-Request-Id` e lista estável de campos inválidos sem valores rejeitados. Sete testes JUnit verificam payloads, status, redação e validação; uma execução da API com PostgreSQL descartável confirmou 401, 403 sem CSRF e 401 com token CSRF válido sem sessão. Detalhes internos de exceções inesperadas não são retornados. O [OpenAPI](openapi.json) documenta as 44 operações atuais, schemas de entrada/saída e erros; `node scripts/validate-openapi.mjs` compara rotas com os controllers e verifica referências e parâmetros de path. O spec é um snapshot versionado e precisa acompanhar alterações de controllers/DTOs.

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

**Andamento (24/11/2026):** the authenticated session lists memberships. The SPA can select and switch companies. Business requests send `X-FlowPay-Tenant` as the requested selection; the backend validates it together with the authenticated identity and membership on every request. The header never grants access. Requests without a selection remain accepted for a single membership and fail closed for multiple memberships; an unauthorized company returns 403 and an invalid identifier returns 400.

- Derivar tenant ativo de membership autenticado, não confiar em header/body do cliente.
- Tornar acesso ao contexto explícito e falhar fechado se não houver contexto empresarial em caso de uso que o exige.
- Testar consulta, leitura, escrita, contagem, exportação e jobs com dois tenants.
- **Aceite:** ID válido de outra empresa não revela registros e não cria efeito; FK composta protege contra bug de filtro no serviço.

## Portão 3 — identidade e empresas

**Andamento (24/11/2026):** a primeira fatia cria conta e empresa com membership `OWNER`, e oferece login por sessão com CSRF da SPA. A matriz operacional foi decidida e aplicada no backend: `OWNER`/`MANAGER` escrevem e `READER` consulta; a SPA sinaliza leitura e oculta controles de alteração para `READER`. administração de memberships, verificação de e-mail, recuperação de senha, configuração de proxy confiável e mitigação distribuída de credential stuffing continuam pendentes. O endpoint de cadastro agora é negado por padrão (`flowpay.identity.registration.enabled=false`); `application-local.yml` habilita o fluxo para desenvolvimento e a demo local. A SPA consulta `GET /api/auth/config` e só mostra cadastro quando habilitado; falha de configuração mantém apenas login. A jornada real de 24/11 também expôs e corrigiu a junção do filtro de tenant com ORDER BY na consulta de memberships; o E2E financeiro passou com empresa selecionada. Trusted-proxy handling and distributed abuse defenses remain open before public exposure.

### 3.1 Autenticação local segura

**Implementation (2026-11-24):** shared PostgreSQL counters allow five login attempts per 15 minutes for each socket source and normalized identifier; successful login clears that bucket. Registration allows five attempts per hour per socket source. Throttled responses return 429 and `Retry-After`. The database stores an HMAC of the context, not a plain address or email, and expired rows are cleaned hourly. The backend uses `getRemoteAddr()` as the trust anchor and consumes `X-Forwarded-For` only when that peer matches `FLOWPAY_TRUSTED_PROXY_CIDRS`. It walks the chain right to left, handles repeated headers, caps size/hops, and falls back to the socket peer for malformed input. Actual narrow ranges and upstream append behavior must be configured and verified before deployment behind a proxy. Outside the `local` profile, `FLOWPAY_RATE_LIMIT_HMAC_KEY` is required (minimum 32 bytes).

- Definir cadastro inicial (convite ou proprietário primeiro), verificação de e-mail e recuperação de senha apenas se caberem ao produto demo; não inventar integração real sem necessidade.
- Hash de senha com algoritmo gerenciado recomendado pelo Spring Security. Cookies HttpOnly/Secure/SameSite conforme ambiente; CSRF em mutações SPA; logout invalida sessão.
- Login throttling is temporary per source/identifier; it avoids a global account lock. Password recovery is not implemented and must adopt equivalent controls when added.
- **Aceite:** usuário autentica, sessão persiste de forma segura, logout encerra acesso, ausência de CSRF em escrita falha.

### 3.2 Tenant e membership

- Proprietário cria empresa e vira membro inicial. Nome, documento empresarial e informações opcionais precisam política de minimização; não coletar CNPJ sem uso concreto.
- **Decisão de implementação (24/11/2026):** `OWNER` e `MANAGER` podem criar e alterar dados operacionais do tenant (contatos, contas, categorias, títulos, movimentos, importação e conciliação); `READER` consulta esses dados, mas não altera. O papel `OWNER` fica reservado para controles de empresa/membership futuros; endpoints de convite e administração de membros seguem fora do MVP atual. As escritas validam a membership no backend.
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

**Andamento (22/11/2026):** a primeira fatia implementa criar, listar/paginar, buscar, detalhar e editar contatos com papel combinável de cliente/fornecedor. Exclusão fica adiada até a regra de vínculo com fatos financeiros ser definida; mutations atuais exigem membership `OWNER`.

- Começar por Contact com classificação cliente/fornecedor ou dois papéis combináveis, decisão explícita do domínio.
- CRUD necessário: criar, listar, pesquisar, ver detalhes e editar; exclusão/cancelamento e duplicidade precisam regra definida.
- Campos mínimos: nome, tipo, e-mail/telefone opcionais e observação restrita; CPF/CNPJ só se uso e proteção estiverem definidos.
- **Aceite:** cadastro de um tenant nunca aparece para outro; busca, paginação, validação e estados vazios funcionam.

## Portão 5 — contas, categorias e títulos

### 5.1 Contas financeiras

**Andamento (22/11/2026):** fatia vertical implementada para cadastrar, listar, detalhar, renomear e arquivar/reativar contas por empresa. O saldo de abertura é gravado como movimento `OPENING_BALANCE` imutável; o saldo controlado é calculado pela soma dos movimentos, e a data de abertura usa `DATE`. A criação recebe chave de idempotência por tenant e hash do payload: repetição igual devolve a conta existente; conteúdo diferente retorna conflito. Valores trafegam em centavos como string decimal e a interface formata em pt-BR sem ponto flutuante. Tipos iniciais: bancária, caixa e outra; moeda fixa em BRL. A edição não altera tipo nem saldo de abertura; exclusão não é oferecida.

- Conta de controle: nome, tipo, saldo inicial em centavos, data de referência e estado ativo/arquivado.
- Decisão desta fatia: saldo de abertura é fato imutável no histórico; ajuste posterior exigirá movimento explícito com motivo e trilha auditável.
- **Aceite:** saldo inicial aparece como saldo controlado com data de referência; não é apresentado como consulta bancária.

### 5.2 Categorias

**Andamento (22/11/2026):** fatia vertical implementada para criar, listar, detalhar, renomear e arquivar categorias pertencentes ao tenant. Cada categoria classifica entrada ou saída; nomes não diferenciam maiúsculas/minúsculas dentro da mesma direção. Escritas concorrentes por tenant/direção são serializadas no PostgreSQL e o índice único preserva a regra no banco. Categoria arquivada permanece no histórico e pode ser reativada; não há exclusão. Categorias globais não entram nesta fatia.

- Categorias próprias do tenant, associadas à direção (entrada/saída), com arquivamento para preservar referências futuras.
- **Aceite:** listagens filtram por empresa; futuras listagens e relatórios de títulos usarão categoria do mesmo tenant.

### 5.3 Recebíveis e pagáveis

**Andamento (22/11/2026):** fatia vertical implementada para criar, listar/paginar, buscar, detalhar, editar e cancelar títulos, além de registrar baixas parciais/totais e reversões auditáveis parciais/totais com motivo. Cada título exige contato com papel correspondente, categoria ativa da direção correta e pode apontar para conta ativa prevista. O vencimento é `LocalDate`; a API informa `asOf` e deriva `OVERDUE` no fuso `America/Sao_Paulo` até existir configuração de fuso por empresa. Valores trafegam como centavos em strings. Criação, baixa e reversão usam chaves idempotentes por tenant/operação, e payload diferente com a mesma chave conflita. Títulos com baixa não podem ser editados ou cancelados nesta fatia. Criar/editar previsão não cria `CashTransaction`; baixa confirmada grava movimento e liquidação atomicamente.

- Campos implementados: descrição, contato, valor original em centavos, vencimento, categoria, conta prevista opcional e observação.
- Estados desta fatia: em aberto, vencido derivado por data de referência, parcialmente baixado, quitado e cancelado com motivo.
- **Aceite desta fatia:** tenant alheio não lê o título; incompatibilidade entre tipo de título, papel do contato e direção da categoria é rejeitada; título previsto não altera caixa.
### 5.4 Páginas de títulos

**Andamento (23/11/2026):** as telas iniciais “A receber” e “A pagar” compartilham a API, listam valores previstos, contato, categoria, vencimento e estado, com busca, paginação, edição e cancelamento com motivo. O formulário valida dinheiro em pt-BR sem ponto flutuante e a criação preserva chave idempotente em caso de falha. O cancelamento agora usa formulário inline acessível com motivo obrigatório limitado a 500 caracteres, foco inicial/restaurado e mensagem de que não cria movimento de caixa. A jornada E2E financeira cobre o cancelamento e a exibição do motivo. Em 24/11/2026, filtros opcionais por estado, contato e vencimento foram adicionados à API tenant-scoped e às telas de títulos; o estado vencido usa a mesma referência de data `America/Sao_Paulo` da listagem. Lint e build do frontend passaram. A integração PostgreSQL de filtros cobre estados aberto, vencido, parcial, quitado e cancelado, contato e intervalo de datas, incluindo baixa e cancelamento reais no banco de teste; a execução Testcontainers local ainda aguarda Docker; o backend build passou no Actions para o commit `78836f7`. A interface não sugere cadastro quando filtros ativos retornam zero resultados; orienta revisar/limpar os filtros. A jornada E2E, que verifica parâmetros, limpeza e estado vazio, passou contra a API real e PostgreSQL 16.15 em 24/11/2026. O run remoto `#6` falhou no typecheck do frontend por uma string quebrada no botão Limpar; a correção foi feita localmente e aguarda sincronização/novo CI.

- Validado em 24/11/2026: a jornada Playwright com API real e PostgreSQL 16.15 descartável verificou filtros de estado, contato, intervalo, limpeza e estado vazio. A integração Testcontainers cobre estados e persistência, mas sua execução segue pendente em CI com Docker. Próximos itens: observar o workflow remoto atualizado e prosseguir com as etapas financeiras.
- **Aceite desta fatia:** API alimenta listas e detalhe; valores persistidos são exibidos exatamente em pt-BR; previsão é identificada como não realizada.
## Portão 6 — caixa e baixas

**Andamento (22/11/2026):** fatias de movimento manual, baixa e reversão codificadas. `MANUAL` representa entrada/saída avulsa já realizada, categorizada e idempotente; não quita título. `TITLE_SETTLEMENT` é um `CashTransaction` de entrada para recebível e saída para pagável; a baixa cria movimento e liquidação atomicamente. Reversão parcial/total exige motivo, cria movimento de direção oposta e preserva a baixa original. As telas listam movimentos e baixas. Backend compilado para Java 21 e cinco testes unitários de valores passaram; migrations V6–V8 foram aplicadas e validadas em PostgreSQL 16.15 local. Smoke autenticado com tenant sintético cobriu idempotência, baixa, excesso, movimento manual, reversão e saldos. Testes de integração PostgreSQL com Testcontainers agora cobrem concorrência de baixas e rollback planejado; compilam, mas foram ignorados na execução local porque o daemon Docker não está disponível. A verificação concorrente contra PostgreSQL real segue pendente.

### 6.1 CashTransaction

**Andamento:** saldo controlado continua calculado pela soma de `CashTransaction`, incluindo abertura, movimentos manuais, liquidações e reversões. API e tela paginada de histórico incluem origem, conta, categoria, direção, data e valor para movimentos existentes.

- Movimento é a fonte de verdade para saldo controlado: tenant, conta, direção, amount_minor, moeda, instante/data contábil acordada, descrição, origem e estado.
- Movimento manual avulso usa origem `MANUAL`, categoria ativa da direção correta e chave idempotente. Baixas de título usam origem própria; nenhum movimento é criado duas vezes pela mesma operação.
- Saldo projetado por soma sobre movimentos confirmados e saldo inicial, ou read model simples; não cachear sem estratégia de invalidação.
- **Aceite:** receita aumenta e despesa reduz conta conforme direção, com histórico identificável.

### 6.2 Baixa parcial e total

**Andamento:** baixa parcial/total, liquidação idempotente e reversão parcial/total com motivo foram implementadas. Reversões também são idempotentes e preservam os fatos originais. Smoke local cobriu parcial, replay, excesso e saldo. Foram adicionados testes Testcontainers para disputa concorrente e rollback; sua execução aguarda Docker ativo. O teste de concorrência exige duas solicitações de R$70 para título de R$100 e confere estado persistido após commit.

- API registra recebimento/pagamento realizado fora do FlowPay e cria alocação + movimento atomicamente, ou aloca movimento existente segundo caso de uso.
- Bloquear título em disputa, validar soma de baixas ≤ título, soma de alocações ≤ movimento, tenant e conta autorizados.
- Repetição após timeout usa chave idempotente persistida; payload diferente com mesma chave tem resposta de conflito definida. Resposta e crash recuperáveis.
- Definir lock order para título, conta e idempotency key; transação curta, sem chamadas externas.
- Auditoria e outbox no mesmo commit quando houver evento que precise sobreviver ao crash. Não usar outbox como requisito para ação síncrona sem consumidor.
- **Aceite financeiro:** título de R$100 recebe duas solicitações concorrentes de R$70: uma confirma, outra falha integralmente; baixa=R$70, pendente=R$30, um movimento. Repetir chave vencedora não duplica efeitos.
- **Aceite reversão:** reversão exige motivo, mantém registro original e gera estado/efeito auditável sem apagar movimento silenciosamente.

### 6.3 Transferências e ajustes

**Validação incremental (24/11/2026):** a execução Testcontainers do backend encontrou que V17 havia removido `STATEMENT_RECONCILIATION` da constraint de origem dos movimentos. V18 restaura essa origem sem alterar migrations aplicadas. `mvn -o -f backend/pom.xml verify` executou 73 testes PostgreSQL e unitários, todos aprovados; isso valida também os fluxos de conciliação com criação e vínculo de movimento.

**Decisão de implementação (24/11/2026):** transferências internas entre contas fazem parte do MVP de acompanhamento. O FlowPay apenas registra a operação já realizada fora do sistema. Cada transferência cria uma saída e uma entrada do mesmo valor, data e descrição em contas diferentes, na mesma transação local; isso preserva o saldo consolidado sem classificar a operação como receita ou despesa.

- Implementado: endpoint idempotente exige duas contas ativas do mesmo tenant, bloqueadas em ordem estável. A migration V15 cria o fato de transferência, vincula as duas pernas e impõe por FK composta que tenant, valor, data e descrição coincidam. Índice único impede pernas repetidas por direção. O histórico identifica conta de origem/destino; dashboard e totais por categoria excluem transferências das somas operacionais, enquanto saldos de cada conta refletem as duas pernas.
- Cobertura adicionada para criar/repetir/conflitar a chave, rejeitar a mesma conta, comparar saldo por conta e consolidado e verificar que dashboard não infla entradas/saídas. A suíte Testcontainers foi executada em PostgreSQL 16.16; `mvn verify` passou com 73 testes.

- Implementado e validado parcialmente (24/11/2026): `OWNER`/`MANAGER` podem registrar `ADJUSTMENT` por conta ativa, em centavos, direção explícita, data realizada e motivo obrigatório de até 500 caracteres. O movimento guarda o ator autenticado e o motivo na mesma linha de `CashTransaction`; usa idempotência por tenant/operação/chave. Ajustes alteram saldos por conta e consolidado, mas ficam fora das entradas/saídas operacionais e totais por categoria. `READER` não vê o controle de escrita; jornadas browser mockada e real passaram nessa verificação/UI. A migration V17 foi aplicada pelo Flyway em banco vazio PostgreSQL 16.15; V18 corrige a lista de origens permitidas sem alterar histórico aplicado. Testcontainers em PostgreSQL 16.16 passou no `mvn verify` (73 testes).
- **Aceite desta fatia:** Transferências criam duas pernas atômicas e pareadas, preservam o saldo consolidado e não inflam receita/despesa. Ajustes exigem motivo, papel de escrita, ator autenticado e idempotência; alteram o caixa sem inflar os totais operacionais. A execução PostgreSQL com Testcontainers foi validada em ambiente Docker local.

## Portão 7 — dashboard e relatórios de uso diário

### 7.1 Indicadores

**Andamento (22/11/2026):** criada `GET /api/dashboard` autenticada e tenant-scoped. A resposta inclui período inclusivo, fuso IANA e `asOf`; o período aceita até 366 dias e, sem datas, assume o mês atual no fuso informado. Decisão de implementação: saldo controlado soma todos os movimentos com data efetiva até `to`, incluindo saldo inicial; entradas/saídas do período excluem o movimento de abertura. Previsões e vencidos usam saldo restante de títulos abertos, considerando baixas e reversões com data efetiva até `asOf`. A resposta inclui os cinco próximos vencimentos após `asOf` (até 30 dias) e cinco movimentos recentes dentro do período. O smoke autenticado no PostgreSQL local confirmou saldo R$ 70,00, entrada realizada R$ 70,00 e previsão restante R$ 30,00 após baixa parcial de um título de R$ 100,00. Consultas de leitura financeira foram movidas para contrato público do módulo `finance`; `reporting` não consulta mais tabelas do financeiro diretamente.

- Saldo controlado, receber, pagar e vencidos; período, timezone e `as_of` explícitos.
- Previsto por vencimentos separado do realizado por movimentos confirmados.
- Consultas agregadas por tenant, com índices e limites verificados; considerar montantes negativos/estornos conforme regra definida.
- **Aceite:** KPIs coincidem com consultas de movimentos/títulos na massa de referência.

### 7.2 Dashboard responsivo

**Andamento (22/11/2026):** a área autenticada abre em “Visão geral”, com filtro por datas, timezone do navegador, KPIs de saldo controlado, previsto, realizado e vencido, além de listas de próximos vencimentos e movimentos recentes. Estados de carregamento, erro, período inválido e listas vazias foram implementados; o layout adapta de quatro colunas a uma coluna em telas menores. Typecheck, lint e build frontend passaram; a tela ainda precisa de inspeção visual em navegador desktop/mobile.

- Cabeçalho de período e “Novo lançamento”; KPIs; fluxo previsto × realizado; próximos vencimentos; movimentos recentes e pendências de conciliação.
- Se houver saldo importado, exibir separado com data e diferença a conciliar.
- **Aceite:** sem dado, exibir empty state; erro explica próximo passo; desktop e mobile preservam valor/status/vencimento.

### 7.3 Relatório mensal básico

**Andamento (22/11/2026):** criada a API autenticada `GET /api/reports/monthly?month=AAAA-MM&timezone=...`, com resumo de entradas/saídas realizadas por categoria, títulos ainda pendentes e vencidos na posição do fim do mês, e movimentos paginados (máximo 100 por página). Saldo inicial aparece no histórico, mas não infla os totais realizados. O intervalo é o mês civil no fuso IANA solicitado; a faixa de ano aceita é 1900–9999. Uma tela mensal foi adicionada à navegação, com loading/erro/vazio, totais, categorias e movimentos. O relatório compartilha um contrato de leitura público de Finance, e baixas/reversões passam a manter a categoria do título no movimento para permitir classificação consistente. As leituras agregadas rodam em uma transação PostgreSQL `REPEATABLE_READ` para totais e listas compartilharem o mesmo snapshot. Typecheck, lint, build e testes unitários passaram; três testes Testcontainers foram ignorados por falta do daemon Docker. Smoke autenticado no PostgreSQL local verificou baixa categorizada de R$70 em título de R$100, saldo pendente de R$30, exclusão do saldo inicial dos totais realizados e paginação dos movimentos.

- Entradas/saídas por período e categoria, contas pendentes, vencidas e movimentos; exportação fica decisão posterior.
- Definir timezone comercial e limites de período; não fazer consultas cross-tenant.
- **Aceite:** totais reconciliam com registros para dataset conhecido e filtros têm nomes claros.

## Portão 8 — importação CSV

### 8.1 Contrato e parser

**Decisão de implementação desta etapa:** a primeira prévia aceita UTF-8 (com BOM opcional), separador `;`, cabeçalho exato `data;descricao;valor;direcao`, data estrita `dd/MM/aaaa`, valor positivo BRL em formato `1.234,56` e direção explícita `ENTRADA` ou `SAIDA`. O arquivo é limitado a 10 MB e 50.000 registros. Esses limites são guardrails iniciais de implementação, não capacidade medida. A prévia autentica o usuário, deriva a empresa da sessão/membership e só aceita conta ativa autorizada; não persiste o arquivo/linhas e não altera caixa.

**Proteção de recursos (24/11/2026):** o parser interrompe a leitura no momento em que aceita o registro além do limite de 50.000 linhas, antes de armazenar as linhas excedentes. Um teste com CSV malformado após o excesso comprova que o limite é reportado sem continuar a análise do restante do conteúdo.

**Incremento entregue em 8.2–8.3:** confirmar salva o original em `BYTEA` no PostgreSQL privado da aplicação e persiste cada linha com estado aceito/rejeitado/repetido. A chave `(tenant, conta, SHA-256 dos bytes originais)` torna repetições atômicas; confirmação idêntica responde à importação existente. O usuário pode consultar histórico e linhas com paginação, sob o tenant autenticado. Como decisão inicial de autorização, `OWNER` e `MANAGER` podem pré-visualizar/confirmar, e membros autenticados podem consultar o histórico do tenant. Isso não evita possíveis linhas iguais em arquivos diferentes. Não existe endpoint de download, match ou movimento de caixa nesta etapa. A retenção de arquivo e linhas ainda precisa ser definida antes de usar extratos reais; o armazenamento em banco é uma decisão inicial para desenvolvimento, adequada ao limite de 10 MB, ainda sem medição de volume operacional.

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

**Decisão de implementação:** a primeira versão só sugere movimentos da mesma conta, com valor e direção exatos, numa janela de até três dias da data do extrato. A pontuação soma valor (50), direção (25), proximidade da data (5–20) e tokens de descrição semelhantes (0–5). A API retorna no máximo dez candidatos, suas evidências e a explicação da ordenação; avalia até cem e sinaliza se houver mais correspondências. Não escolhe nem confirma automaticamente. Movimentos de saldo inicial não são candidatos. As consultas de movimentos ficam atrás do contrato público de Finance.

**Implementado:** `GET /api/statement-lines/{id}/suggestions` e ação de consulta na UI para linhas aceitas. A tela apresenta compatibilidade e evidências sem efetuar vínculo; linhas já conciliadas e movimentos já usados são excluídos da sugestão.

### 9.2 Confirmar vínculo

**Decisão e implementação:** `POST /api/statement-lines/{id}/match` exige `Idempotency-Key`, trava a linha antes de revalidar e salva a decisão com ator e histórico. A chave é escopada por tenant/operação e reutilização com outro payload conflita. Índices parciais impedem duas conciliações ativas por linha ou por movimento; FKs compostas preservam tenant e conta. `LINK_EXISTING` só registra o vínculo. `CREATE_MOVEMENT` requer categoria ativa na mesma direção e grava movimento + match na transação local. A UI explica o efeito de cada ação e pede confirmação explícita. Na etapa 9.3 foram entregues revisão auditada de linhas e remoção explícita do vínculo.

- Candidatos por valor/direção/moeda, janela de data e texto; regras de scoring explicáveis. Não usar score para aprovar automaticamente caso ambíguo.
- Revalidar valor, direção, moeda, tenant, conta e elegibilidade no momento da confirmação, pois candidatos podem ter mudado.
- **Aceite:** casos únicos são fáceis de reconhecer; ambiguidades mostram evidências e exigem escolha humana.


### 9.3 Ignorar, desfazer e exceções

- Implementado: estados de revisão `PENDING`, `REVIEWED` e `IGNORED`, com motivo, autor, evento append-only e idempotência por tenant. Linhas ignoradas não recebem sugestões nem podem ser conciliadas até serem reabertas; a interface permite marcar, ignorar e reabrir.
- Desfazer vínculo registra motivo, autor e idempotência no match histórico, liberando a unicidade ativa da linha/movimento. Remove somente a relação; movimento criado na conciliação permanece no caixa controlado e exige estorno separado.
- Candidatos individuais também podem ser ignorados e restaurados por linha; ambos os comandos exigem motivo, autoria e chave idempotente. A supressão afeta somente as sugestões daquela linha e não oculta nem modifica o movimento no caixa.
- Sem match parcial ou múltiplas linhas por transação no MVP até definir novo contrato de valores.
- Aceite: linha e motivos permanecem auditáveis; desfazer vínculo não altera caixa e ignorar um candidato não altera a operação nem impede seu uso em outra linha.

### 9.4 UI da conciliação

- Implementado parcialmente: resumo por importação separa atividade, caixa controlado, vínculos e saldos informados. A jornada Playwright local (2/2) verifica uma linha válida duplicada, outra válida, uma data rejeitada, saldos de abertura/fechamento, roll-forward zerado, vinculação sem duplicar caixa e replay. Em 23/11, o E2E também verificou uma importação somente rejeitada: nenhum movimento do extrato, diferença de saldo controlado de R$ 150 e roll-forward de R$ 300. O Actions foi observado no SHA remoto `def2abf`: frontend passou e backend falhou porque o teste usava `webEnvironment=NONE`; o checkout local contém a correção `MOCK` em `eb508aa`, validada localmente. É necessário observar nova execução após sincronizar os commits locais.
- Verificação visual (23/11/2026): resumo, linhas salvas, prévia e saldos informados foram conferidos em Chrome a 1440×1000 e 390×844. A seção de saldos agora usa grade de cartões responsiva; notas têm espaçamento e pluralização corretos; tabelas roláveis são focáveis por teclado com foco visível. Axe WCAG 2.1 A/AA passou no estado conciliado em desktop e mobile; documento sem overflow horizontal a 390 px.
- Decisão de implementação: os saldos representam o fechamento do dia indicado; a data de fechamento precisa ser posterior à de abertura; linhas válidas precisam estar após a data de abertura e até a data de fechamento. Valores negativos são permitidos para contas com saldo devedor. O checksum continua identificando o arquivo: uma repetição devolve a importação existente e preserva os metadados gravados originalmente.
- A tela distingue diferença líquida de movimentação, diferença do saldo final do extrato contra o saldo controlado até a mesma data e diferença de roll-forward (saldo final menos saldo inicial e movimentos aceitos). Importar ou vincular não altera o caixa; somente a decisão explícita de criar movimento o faz.
- Verificação local: backend compilado e empacotado pelo `mvn --batch-mode --no-transfer-progress -f backend/pom.xml verify` com JDK 25/Maven 3.9.16. A suíte executou 39 testes sem falhas nem skips, incluindo 12 integrações em PostgreSQL 16.16 descartável. A migration V14 foi aplicada desde schema vazio; testes conferem cálculo para saldos positivo e negativo, replay preservando metadados, import sem efeito no caixa e isolamento de linhas por tenant.
- Testes unitários rejeitam conjunto de campos incompleto, data de fechamento não posterior e linha aceita fora do período. O teste de integração precisou passar a usar contexto web simulado e agora remove membership/usuário antes do tenant; com o teste ativo também foi corrigido o acesso a linhas de importação alheia (404 tenant-scoped) e o total paginado de linhas.
- Pendências: observar a jornada no CI e revisar manualmente como a tela comunica diferenças de saldo incompatíveis. O aceite global também depende dos portões 10 e 11.
- Critério parcial: contrato e experiência de preenchimento implementados; comportamento persistido e cálculos dependem da validação de integração antes do aceite.
## Portão 10 — MVP demonstrável

### 10.1 Jornada completa

- Verificado localmente em Chrome: Playwright valida login do owner demo, dashboard com saldo seed, contatos, conta, títulos e telas de extrato; a jornada financeira cria tenant sintético, contato cliente/fornecedor, conta, categorias, títulos, baixa parcial, pagamento realizado fora do FlowPay, importa CSV, vincula linha ao movimento existente sem duplicar caixa, repete a importação, consulta o relatório mensal e importa um arquivo somente com linha rejeitada para comparar saldos inconsistentes. Executado com API e Vite em portas exclusivas e PostgreSQL 16 descartável: saldo final R$ 1.150,00, diferença líquida do extrato zerada na importação principal, segunda importação rejeitada apresenta diferença de saldo R$ 150 e roll-forward R$ 300. `npm run test:e2e`: 2 testes passaram em 23/11/2026. O cenário de escrita segue opt-in local (`FLOWPAY_E2E_ALLOW_WRITES=true`); em 24/11/2026 a jornada de escrita também passou contra API real e PostgreSQL 16.15 descartável, incluindo transferência e ajuste auditado; o workflow remoto atualizado ainda precisa ser observado.

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

- Parcial: o perfil Spring `demo` cria registros sintéticos com IDs estáveis, em transação e de forma idempotente. Exige `FLOWPAY_DEMO_PASSWORD`; não há senha padrão no código. O seed exige também o perfil `local` e URL PostgreSQL loopback para o banco `flowpay`.
- README documenta o login e o reset somente para o volume descartável deste checkout. O seed não apaga dados e aborta diante de colisões detectadas com outro usuário/empresa.
- A UI identifica o tenant do seed com o rótulo `Ambiente de demonstração`.
- Verificado: PostgreSQL 16 descartável desde schema vazio; as 17 migrations foram aplicadas, os nove grupos de registros seed foram conferidos e uma segunda execução não duplicou dados. A senha sintética corresponde ao hash armazenado.
- Verificado via MockMvc e filtros reais da API: o login `local,demo` exige CSRF; a sessão demo lê os dois contatos e o saldo inicial. Uma sessão autenticada de outro tenant, com CSRF válido, não lê nem altera contato demo e vê saldo controlado zero.
- Verificado em 24/11/2026: a jornada Playwright de login demo passou em Chrome contra API real e PostgreSQL 16.15 descartável. O teste alcança os controles de login com Tab/Enter, aplica axe nas telas cobertas e valida a tela de extratos em viewport móvel de 390 px. A auditoria visual/manual de zoom, contraste e leitor de tela permanece em 10.3.
- **Aceite:** ambiente pode ser recriado em banco limpo com resultado conhecido; nenhum segredo seed é reutilizado fora do dev/demo isolado.

### 10.3 Usabilidade e acessibilidade

- Parcial: a jornada seed foi exercitada em Chrome desktop e mobile; no viewport de 390 px não há rolagem horizontal no dashboard e na entrada de extratos. Isso não substitui auditoria de teclado, contraste, zoom ou leitor de tela.
- Parcial: scans automatizados do axe-core com tags WCAG 2.1 A/AA passaram em login, dashboard, contatos, entrada de extratos, pagamento, linhas/resumo/saldos de conciliação e relatório mensal. O estado financeiro conciliado foi escaneado em desktop e a 390 px; o teste também confirma alternador e envio do login via Tab/Enter. Não comprova conformidade WCAG. Em 24/11/2026, a nova jornada Playwright também escaneou por axe a seleção da empresa e o workspace de leitura.
- Cobertura automatizada complementar (24/11/2026): a jornada demo valida o dashboard a 720 px de largura CSS, sem rolagem horizontal e com o saldo seed visível. É uma verificação de layout responsivo que aproxima uma tela desktop com zoom, mas não altera a escala do navegador nem substitui teste manual a 200%.

- Parcial: ao salvar ou cancelar os formulários de contatos, contas, categorias, títulos, movimentos avulsos, baixas/reversões e confirmação de conciliação, o foco retorna a um título da seção; campos de valor recebem foco ao abrir baixa/reversão.
- Pendente: auditar manualmente teclado, zoom, contraste, leitor de tela e fluxos além do demo em toda a jornada.
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
- Parcial: a suíte Playwright completa passou localmente em Chrome com API e PostgreSQL descartável. Em 24/11/2026, uma nova jornada E2E com fixture `READER` opt-in confirmou troca real de empresa, header por tenant, saldo isolado na API e UI somente leitura; a jornada principal também passou com transferências e ajuste auditado. CI habilita esse fixture apenas no banco descartável do job. Testes PostgreSQL de concorrência de baixas e constraints executaram localmente com Testcontainers. O workflow remoto atualizado e o resultado do commit local ainda precisam ser observados. O teste que inicia duas conciliações de linhas distintas contra o mesmo movimento, sincronizadas por barreira, passou e verificou um único vínculo persistido.
- **Aceite:** CI executa a suíte necessária; resultados e limitações são reportados.

### 11.2 Modelagem de segurança e privacidade

- Mapear ativos, papéis, limites de confiança, ameaças IDOR/CSRF/XSS/SQLi/upload/abuso e controles com referência OWASP ASVS.
- Parcial (23/11/2026): a [revisão de segurança](FLOWPAY_SECURITY_REVIEW.md) registra ativos, atores, fronteiras e controles observados com referência à versão estável ASVS 5.0.0. É revisão de código parcial, não declaração de conformidade nem de prontidão para produção.
- Verificado no `mvn --batch-mode --no-transfer-progress -f backend/pom.xml verify`: testes MockMvc com sessão válida rejeitam CSRF ausente no upload multipart, impedem prévia de conta de outro tenant com CSRF válido e rejeitam arquivo acima de 10 MB sem persistência.
- Limitar dados pessoais coletados; política de acesso/download/retention/exclusão segura de extratos definida antes de aceitar dados sensíveis.
- Redação de logs; segredos só em configuração local/gerenciador; dependências verificadas e atualizadas.
- Open before public exposure: validate trusted-proxy/client-address handling; run Testcontainers for V16 and concurrent limits; define statement retention/deletion and verify the deployed environment. Current throttles are a first layer and do not stop distributed credential stuffing.
- **Aceite:** testes negativos de autorização, upload e CSRF; plano de correção de achados, sem prometer segurança absoluta.

### 11.3 Observabilidade

- Parcial: `RequestCorrelationFilter` cria um UUID por requisição, devolve `X-Request-Id`, mantém `request_id` no MDC durante o processamento e registra método, status, resultado e duração. O cliente não escolhe o ID; rota, query string, tenant, identidade, corpo, CSV e tokens não são incluídos nesse log. O MDC está no padrão de console do Spring Boot. A importação de extratos registra duração em `flowpay.statement.operation.duration`, com `operation` (`preview`/`confirm`) e `outcome` (`success`/`error`/`created`/`replay`), sem labels empresariais. O endpoint de métricas fica exposto somente no perfil `local`, protegido pela sessão. O `mvn verify` passou com testes unitários e integração para sucesso, erro, criação e replay; métricas de jobs/outbox e lock contention seguem abertas.
- Métricas de latência/erro, duração de importação, falhas de job/outbox e lock contention sem labels de alta cardinalidade.
- Micrometer primeiro; OpenTelemetry/Prometheus/Grafana conforme necessidade de visualização e demonstração.
- **Aceite:** falha de API/job pode ser localizada e correlacionada sem expor valores sensíveis.

### 11.4 Backup e restauração

- Parcial (24/11/2026): scripts PowerShell criam dump PostgreSQL custom-format e restauram em banco novo `flowpay_restore_*`, sem sobrescrever a origem ou destino existente. O Compose continua sendo o modo padrão; foi acrescentado modo local restrito a loopback. Ensaio local com PostgreSQL 16.15 confirmou dump, restauração de 17 migrations e preservação de registro sintético; a recusa de destino existente e host fora de loopback também foi verificada. O dump fica em diretório ignorado pelo Git. A migration V18 foi validada pelo `mvn verify` com PostgreSQL 16.16/Testcontainers; a restauração via scripts e o modo Compose ainda precisam de ensaio neste ambiente.
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
