# FlowPay — especificação do produto e da arquitetura

Data: 2026-11-08. Estado: baseline técnica aprovada; implementação local em andamento. Consulte docs/implementation-status.md para distinguir fundações verificadas dos fluxos ainda planejados.

## Intenção e direção escolhida

Projeto de portfólio para vagas de Java Backend Developer / Software Engineer Júnior internacionais. Entregar uma API de simulação de pagamentos confiável, um painel web convincente e uma demonstração que permita entender o produto em poucos minutos. Nenhum dinheiro real será movimentado; não coletar dados de cartão ou credenciais bancárias.

O usuário escolheu API + painel web e monólito modular. As decisões adicionais abaixo são propostas do implementador e ficam explícitas para revisão.

## Diretriz central: empregabilidade internacional

Prioridade reafirmada pelo usuário em 2026-11-08: o FlowPay deve fortalecer sua candidatura a vagas internacionais de backend Java. Toda decisão de escopo, tecnologia, arquitetura e apresentação deve contribuir para esse objetivo.

- Priorizar fundamentos de Java, Spring, SQL, HTTP, segurança, transações e concorrência, com comportamentos demonstráveis e testes significativos.
- Manter um painel visualmente convincente e enxuto, conectado ao backend, com uma demonstração curta e reproduzível.
- Produzir documentação pública em inglês e preparar uma explicação técnica em inglês das decisões e limitações.
- Construir de modo que o usuário consiga compreender, explicar, depurar e modificar o código com autonomia; incluir explicações e pontos de aprendizado nas entregas.
- Avaliar novas dependências e funcionalidades pelo problema que resolvem, pelo esforço de manutenção e pela capacidade de o usuário defendê-las em entrevista.
- Apresentar resultados verificáveis e identificar o sistema como projeto pessoal de simulação; não atribuir experiência profissional, movimentação real ou escala não comprovada.
- Contexto informado pelo usuário em 2026-11-08: um ano de experiência profissional com Java e seus frameworks, inglês fluente e interesse tanto em trabalho remoto do Brasil quanto em mudança de país. Não presumir quais frameworks domina, formação, cidadania, autorização de trabalho ou países preferidos.
- Calibrar as explicações para alguém com experiência prática em Java: enfatizar decisões, alternativas, transações, concorrência, SQL, segurança, diagnóstico e verificação; explicar os conceitos necessários conforme surgirem. Preparar demonstração e discussão técnica em inglês, sem tratar fluência como uma lacuna informada.
- O projeto complementa a experiência profissional já existente. Apresentar experiência e projeto pessoal separadamente, sem inflar tempo de trabalho ou atribuir operação financeira real à simulação. Formação, países-alvo e elegibilidade podem ser investigados ao preparar candidaturas; não bloqueiam a implementação.

Critério para propor trabalho adicional: qual competência relevante para a vaga essa entrega demonstra, como será verificada e como o usuário poderá explicá-la? Se não houver resposta concreta, reavaliar sua prioridade.

Orientações de construção para agentes: `AGENTS.md` na raiz e `docs/guides/frontend-guidelines.md` detalham bibliotecas preferidas, limites de escopo, integração com a API, qualidade visual e critérios de verificação. Esses arquivos complementam esta especificação e não representam implementação ou instalação das bibliotecas.

Adição aprovada pelo usuário: React Best Practices durante construção/revisão, Lighthouse nas telas funcionais com build de produção e k6 na API real após os fluxos estarem implementados. `docs/guides/performance-guidelines.md` define cenários, evidências reproduzíveis e a regra medir -> identificar gargalo -> corrigir -> medir novamente. Incluir relatório técnico em inglês com resultados reais; ferramentas e medições ainda não foram configuradas.

## Abordagens consideradas

1. API com Swagger: menor esforço, excelente foco em backend, menor impacto visual para visitantes.
2. API + painel: escolhida; demonstra experiência de produto e profundidade técnica com custo controlável.
3. Microsserviços: maior esforço de implantação, comunicação e operação; reservado a estudos posteriores se houver uma necessidade concreta.

## Objetivos, limites e critérios de sucesso

- Um visitante entra na demonstração, cria um pagamento, repete a solicitação sem duplicar e acompanha uma transição final.
- Todas as informações funcionais do painel vêm da API; exemplos iniciais são dados sintéticos persistidos.
- Nenhum usuário consegue ler ou modificar dados de outra empresa.
- Idempotência e transições permanecem corretas com requisições concorrentes.
- Execução local documentada com Docker Compose e pipeline de verificações reproduzível.
- README, nomes no código, interface e documentação pública em inglês; esta especificação em português.
- Demonstrar domínio de transações, HTTP, autorização, testes e decisões arquiteturais.
- Fora desta versão: dinheiro real, cartões, integrações bancárias, estornos, parcelas, assinaturas, câmbio, Kafka, Redis, webhooks, ledger contábil, microsserviços e recuperação de senha.
- Evitar gráficos sem definição de métrica, telas com dados fixos, abstrações sem uso e alegações de escala sem medição.

## Stack e política de versões

Backend: Java 21, Spring Boot 4 estável, Maven Wrapper, Spring Web MVC, Bean Validation, Spring Security, Spring Data JPA, PostgreSQL 18 e Flyway. Spring Session JDBC persiste sessões no mesmo PostgreSQL. Spring Modulith verifica fronteiras dos módulos.

Frontend: React, TypeScript, Vite, React Router, TanStack Query, Tailwind CSS e componentes acessíveis baseados em Radix/shadcn. Gráficos apenas onde necessários; preferir métricas e tabelas legíveis.

Testes: JUnit Jupiter gerenciado pelo BOM do Spring Boot (6.0.3 no pin Boot 4.0.9 verificado em R00; corrige a sugestão inicial de JUnit 5 para manter compatibilidade), Mockito quando necessário, Testcontainers com PostgreSQL, testes de arquitetura com Spring Modulith e Playwright para jornadas críticas. Documentação HTTP com OpenAPI e integração compatível com a versão escolhida do Spring Boot. Pins e evidências em `docs/development/dependencies.md`.

Entrega: Docker Compose, imagens de build em múltiplos estágios e GitHub Actions. Logs estruturados e Spring Boot Actuator com exposição pública mínima.

O implementador fixa versões estáveis exatas, verifica compatibilidade entre Boot, Modulith, Session e OpenAPI, e registra Java/Maven/Node nas ferramentas do projeto. Usar lockfile no frontend e tags específicas de imagens; não usar versões preview ou tags latest. Caso alguma dependência impeça Boot 4, registrar a incompatibilidade antes de propor alteração da linha principal.

## Estrutura do repositório e arquitetura

Monorepo: backend/, frontend/, infra/, docs/. Uma aplicação Java, uma aplicação web e um PostgreSQL. Na entrega, um proxy de mesma origem serve arquivos estáticos e encaminha /api para Java. O frontend não implementa regras financeiras.

Módulos Java no pacote-base: company, identity, customer, payment. Cada módulo expõe uma API pequena no pacote raiz e mantém implementação em internal. Dentro de internal, separar web, application, domain e persistence conforme necessário, sem criar camadas vazias. Repositórios e entidades JPA não são contratos entre módulos.

Dependências: identity depende da API de company; customer depende da API de company; payment depende das APIs de customer e company. Web obtém a identidade por um adaptador de segurança comum de infraestrutura, sem criar dependência reversa entre módulos. A orquestração de provisionamento/demo fica em configuração de aplicação fora dos módulos de domínio e usa suas APIs públicas; nenhum módulo depende dela. Verificar ausência de ciclos com Spring Modulith.

Controllers traduzem HTTP; casos de uso coordenam transações; regras do domínio controlam transições; persistência garante integridade. DTOs de entrada e saída distintos das entidades. Nenhum campo de empresa enviado pelo cliente define autorização.

## Modelo de dados

Todos os IDs de negócio são UUID. Datas são Instant em UTC, serializadas em ISO 8601; interface exibe o fuso do navegador e informa esse comportamento.

- Company: id, name, created_at, demo, expires_at opcional.
- User: id, company_id, email normalizado, password_hash opcional para usuário demo, created_at. Email de login convencional globalmente único; usuários demo recebem identificador interno único e não podem usar login por senha.
- Customer: id, company_id, name, email, created_at, updated_at. Nome obrigatório, email válido; duplicidade de email permitida e documentada.
- Payment: id, company_id, customer_id, amount NUMERIC(19,2), currency, status, description opcional, simulation_scenario, created_at, updated_at.
- PaymentEvent: id, company_id, payment_id, sequence, previous_status opcional, new_status, reason_code opcional, actor_user_id, created_at. Primeiro evento registra criação. Histórico é somente leitura.
- IdempotencyRecord: id, company_id, operation, key, request_hash, payment_id, http_status, response_json, created_at.
- Tabelas de sessão geridas por migrações compatíveis com Spring Session JDBC.

Restrições: amount > 0; currency = BRL; status e cenário limitados aos valores conhecidos; UNIQUE(company_id, operation, key); UNIQUE(payment_id, sequence). Customer e Payment possuem chaves compostas/únicas auxiliares para uma FK (customer_id, company_id), impedindo associação entre empresas no banco. Eventos também referenciam pagamento e empresa de forma consistente.

Índices para consultas de pagamentos por empresa, criação, status e cliente. Otimizações adicionais precisam de consulta real e plano de execução. Não usar exclusão lógica no MVP; clientes com pagamentos não podem ser excluídos e exclusão de clientes não faz parte da API inicial.

## Valores e máquina de estados

Moeda única BRL. Entrada e saída monetária são strings decimais, por exemplo "150.00". Backend usa BigDecimal; rejeitar mais de duas casas decimais, zero, valores negativos e valores acima da capacidade do banco. Não arredondar silenciosamente. Frontend formata BRL e não faz aritmética de dinheiro com ponto flutuante.

Estados: PENDING -> APPROVED, REJECTED ou CANCELLED. Todos os destinos são finais. Criar pagamento e evento inicial juntos. Alterar status e adicionar evento juntos. Repetir process/cancel após estado final retorna 409, inclusive quando o destino seria o mesmo; a idempotência descrita adiante vale para criação.

Cenários: APPROVE e DECLINE, escolhidos na criação e imutáveis. Processamento síncrono e determinístico; APPROVE leva a APPROVED, DECLINE a REJECTED com código SIMULATED_DECLINE. A interface sempre identifica simulação. Não usar aleatoriedade, espera artificial ou fila nesta versão.

## Transações, concorrência e idempotência

POST /payments exige Idempotency-Key entre 1 e 128 caracteres ASCII imprimíveis sem espaços. Escopo: empresa + operação create-payment + chave. Canonicalizar o DTO validado: valor com escala 2, moeda BRL, UUID normalizado, cenário e descrição (ausente e null equivalentes; descrição aparada). Calcular SHA-256 sobre representação canônica de campos explicitamente ordenados. Campos desconhecidos são rejeitados.

Na mesma transação READ COMMITTED: tentar reservar a chave com INSERT ... ON CONFLICT DO NOTHING; quem insere cria pagamento e evento e grava resposta de criação. Quem encontra conflito lê o registro confirmado em uma instrução posterior, compara o hash e retorna o JSON e status originais ou 409 IDEMPOTENCY_KEY_REUSED. A restrição única arbitra chamadas simultâneas. Em falha a transação inteira reverte, permitindo nova tentativa. Não capturar violação de unicidade de JPA e continuar numa transação marcada para rollback.

Ordem explícita: autenticar, validar formato/canonicalizar e verificar validade da empresa; para empresa demo, adquirir o lock da empresa antes de qualquer reserva de chave ou escrita. Então reservar/consultar a chave. Replay ou conflito de hash são resolvidos antes de verificar limite de quantidade e existência/vínculo do cliente. Só uma criação nova valida essas referências e o limite sob o lock, cria o pagamento e grava o resultado. Assim repetir a criação do 500º pagamento continua retornando a resposta original. A autorização e expiração da empresa continuam obrigatórias em todo replay. O lock de empresa serializa intencionalmente criações dentro de uma mesma demo; testes da arbitragem concorrente pela restrição única também usam empresa convencional, sem esse lock de quota.

Resposta original de criação usa 201 e Location. Repetição devolve a mesma resposta 201 com Idempotency-Replayed: true, mesmo se o estado atual já mudou; GET retorna estado atual. Não persistir headers de sessão no registro. Chaves não expiram enquanto a empresa existir no MVP. Timeout de contenção retorna 503 com orientação de repetir usando a mesma chave; não afirmar sucesso sem commit.

Process/cancel: buscar pagamento por id E empresa e adquirir lock de linha, revalidar PENDING sob o lock, atualizar e registrar evento numa única transação. A segunda operação concorrente observa estado final e recebe 409. Transações curtas, sem chamadas externas. Testar com conexões independentes.

## Autenticação e autorização

Uma empresa por usuário e uma permissão de operador no MVP. Sem cadastro público convencional nem gestão de equipes. Provisionar empresa e usuário de desenvolvimento por comando/bootstrap explícito; senha por variável de ambiente e hash seguro pelo PasswordEncoder. Credenciais de desenvolvimento não são habilitadas na implantação pública. Essa simplificação do cadastro de empresas original é proposta do implementador.

Sessão via Spring Security + Spring Session JDBC; cookie HttpOnly, SameSite=Lax e Secure em produção, expiração por inatividade de 30 minutos. Renovar identificador no login e invalidar sessão no logout. CSRF habilitado em operações de escrita, inclusive entrada demo/login, com endpoint para obter token e renovação quando necessário. Usar cookie de sessão e token CSRF em header; não guardar credenciais em localStorage.

company_id e user_id são derivados da sessão autenticada. Consultas de objetos estrangeiros retornam 404, sem revelar sua existência. Sem sessão: 401; falha de CSRF: 403; entrada inválida: 400; conflito de estado/idempotência: 409. Usar HTTPS na entrega pública. OpenAPI não inclui segredos e endpoints do Actuator não expõem configuração, ambiente ou banco.

Login e criação de demo recebem limite de tentativas no proxy. Limites são configuráveis e respondem 429. Escopo de implantação: uma instância; a escolha exata do provedor e configuração de TLS ficam para o implementador, registradas no guia de implantação.

## Contrato HTTP inicial

Prefixo /api/v1. JSON camelCase. Erros application/problem+json com type, title, status, detail, instance, code, requestId e fieldErrors quando aplicável. Stack traces e detalhes SQL nunca aparecem na resposta.

| Método | Rota | Contrato |
|---|---|---|
| GET | /auth/csrf | Token CSRF para iniciar sessão |
| POST | /auth/login | Email/senha; 200 com usuário/empresa, cookie de sessão |
| GET | /auth/me | Usuário e empresa autenticados |
| POST | /auth/logout | Invalida sessão; 204 |
| POST | /demo/sessions | Cria ambiente demo e autentica visitante; 201 |
| GET | /company | Empresa atual |
| POST | /customers | name, email; 201 |
| GET | /customers | Lista paginada com busca por nome/email |
| POST | /payments | customerId, amount, currency, description?, simulationScenario; 201 |
| GET | /payments | Lista paginada e filtrada |
| GET | /payments/{id} | Detalhes atuais |
| GET | /payments/{id}/events | Histórico ordenado por sequência |
| POST | /payments/{id}/process | Sem corpo; 200 com pagamento final |
| POST | /payments/{id}/cancel | Sem corpo; 200 com pagamento final |
| GET | /dashboard/summary | Métricas do período |

Paginação page zero-based, size padrão 20 e máximo 100. Pagamentos ordenados por createdAt desc e id desc; resposta com items, page, size, totalElements, totalPages. Filtros: status, customerId, createdFrom inclusivo, createdTo exclusivo e busca q por UUID exato ou nome/email do cliente. Ordenação fixa no MVP. Limitar tamanho da busca a 200 caracteres e parâmetros inválidos retornam 400.

Endpoints de eventos e detalhes sempre verificam empresa. Mutação bem-sucedida invalida dados relacionados no cache do frontend; sem polling, SSE ou WebSocket no MVP. DTOs, códigos de erro e exemplos são documentados no OpenAPI.

## Métricas

Todos os indicadores filtram pagamentos pela data de criação no intervalo [from, to), usando seu status atual. Painel padrão: últimos 30 dias, com limites enviados como instantes UTC. A interface explica que métricas desse período podem mudar quando pagamentos pendentes forem processados.

- Approved volume: soma de amount dos APPROVED.
- Pending payments: contagem e soma dos PENDING.
- Approval rate: APPROVED / (APPROVED + REJECTED); sem denominador, exibir sem dados em vez de 0%.
- Total payments: contagem de todos os estados.
- Gráfico opcional de volume aprovado por dia UTC, com legenda de fuso explícita; agregação feita pelo backend.

Não chamar volume aprovado de receita ou saldo. Cancelados e pendentes não entram no denominador da taxa de aprovação.

## Experiência visual e demonstração

Três telas principais: Overview, Payments e Payment details. Cadastro simples de cliente acessível no fluxo de criação e lista básica de clientes. Configurações de conta, times e faturamento ficam fora do escopo.

Direção visual aprovada pelo usuário em 2026-11-08: Precision Glass refined, referência em `docs/design/concepts/precision-glass-refined.png` e baseline em `docs/design/design-system.md`. Tema claro com azul como destaque, tipografia legível, hierarquia forte, profundidade/material restritos à identidade e navegação, tabela opaca e valores alinhados. Status têm texto além de cor; detalhes usam linha do tempo. Adaptar para desktop/celular e verificar teclado, foco, contraste e feedback de erros na implementação. A aprovação do mockup não comprova essas verificações.

Todos os fluxos têm estados de carregamento, vazio, sucesso e falha. Bloquear ação repetida enquanto a chamada está em andamento, mantendo proteção independente no backend. Aviso persistente de ambiente simulado. Link para documentação da API e código-fonte quando os destinos existirem.

Demonstração guiada: entrar em ambiente demo isolado -> selecionar/criar cliente -> criar pagamento -> repetir exatamente a requisição original com a mesma chave -> mostrar mesmo ID e marcador de replay -> processar -> ver estado e evento final. Guardar o snapshot da requisição original em memória enquanto o guia estiver ativo. O guia oferece abrir detalhes de request/response como recurso opcional para quem quer avaliar a API.

Cada POST /demo/sessions cria uma empresa e operador exclusivos e dados sintéticos em transação; a sessão só é autenticada após sucesso. Entrar novamente numa sessão demo existente e válida a reutiliza. Empresas demo expiram após 24 horas; requisições autenticadas também verificam expires_at e devolvem 401 DEMO_EXPIRED quando necessário. Job periódico remove ambientes expirados, seus registros e sessões. A limpeza usa a mesma ordem de locks das operações de domínio e não remove empresas convencionais.

Proteção de recursos proposta: no máximo 100 clientes e 500 pagamentos por empresa demo; ao atingir o limite, retornar 409 DEMO_LIMIT_REACHED. Contagem e criação protegidas por lock da empresa para impedir ultrapassagem concorrente; toda operação que precisa dos dois locks adquire empresa antes de pagamento. Limites de entrada demo por IP no proxy e capacidade total configurável de ambientes ativos; ao esgotar capacidade, responder 503 sem criar dados parciais. Não publicar senhas compartilhadas para a demo.

## Verificação e entrega

Testes de domínio: transições, valores, cenários e canonicalização. Integração com PostgreSQL real: migrações, FKs entre empresas, filtros, histórico atômico, rollback, idempotência concorrente e disputa process/cancel. Segurança: sessão, logout, CSRF, isolamento de leitura/escrita, expiração demo e acesso direto por IDs estrangeiros. Verificar reserva de limites demo em concorrência.

Playwright: entrada demo, criação de cliente/pagamento, replay, aprovação, recusa, cancelamento, filtros e detalhes. Verificação visual manual em desktop/celular e navegação por teclado. Testes de arquitetura impedem ciclos e acesso aos internos dos módulos.

CI: build/test backend, testes de integração com Docker disponível, lint/typecheck/build frontend e jornadas críticas em ambiente de integração. Nunca reportar métricas de carga sem registrar comando, ambiente e resultado. Não fixar uma porcentagem artificial de cobertura como objetivo principal.

Logs com requestId retornado em header e erros, operação e resultado, sem senhas, cookies ou corpo completo com dados pessoais. Health/readiness mínimos para operação. Compose com healthchecks, volume PostgreSQL, migrações automáticas e exemplo de variáveis sem segredos. Bootstrap local explícito e instruções separadas para demonstração pública.

README: proposta, link da demonstração quando disponível, capturas reais, início rápido, arquitetura, fluxos, testes, limitações e decisões. ADRs sobre monólito modular, sessão, idempotência e locks. Repositório público e deploy só serão realizados quando solicitados; são entregáveis planejados, não ações implícitas nesta especificação.

## Ordem de entrega proposta

1. Fundação: fixar dependências, contratos OpenAPI, migrações, módulos, Docker e CI.
2. Backend: provisionamento, autenticação, clientes, pagamentos, histórico, idempotência e concorrência, com testes críticos; depois estabelecer baseline de carga com k6.
3. Produto: painel integrado, estados da interface, métricas e acessibilidade; aplicar React Best Practices desde os primeiros componentes e medir telas reais com Lighthouse em build de produção.
4. Demonstração: ambientes isolados, limites/limpeza e guia reproduzível.
5. Acabamento: verificação completa, documentação em inglês, capturas, relatório reproduzível de performance e preparação de implantação. Otimizar gargalos identificados e comparar medições equivalentes, preservando correção e segurança.

Cada etapa entrega um fluxo verificável. O roadmap detalhado está em `docs/plans/2026-11-08-flowpay-implementation-roadmap.md`, com tarefas R00–R23, responsáveis, dependências e critérios de aceite. O andamento real fica em `docs/implementation-status.md`. A criação do plano não iniciou implementação nem representa conclusão de suas tarefas.

## Revisão de viabilidade

Revisão independente concluída em 2026-11-08. O ponto levantado foi a precedência do replay de idempotência sobre limites da demonstração e validações exclusivas de novas criações. A ordem transacional foi explicitada acima, preservando autenticação/expiração e o resultado original mesmo quando a quota já foi atingida. Nenhuma questão de produto ficou pendente dessa revisão.
