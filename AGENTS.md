# FlowPay — instruções de desenvolvimento

## Contexto e fontes

FlowPay é um SaaS de gestão financeira para pequenas prestadoras de serviços brasileiras, com um laboratório futuro de pagamentos simulados. A versão inicial registra operações feitas fora do sistema; não movimenta dinheiro nem integra diretamente com bancos.

Leia as fontes conforme a tarefa:

| Fonte | Consultar para |
|---|---|
| [Master Project Instructions](docs/FLOWPAY_MASTER_PROJECT_INSTRUCTIONS.md) | Diretrizes do responsável, stack definida, qualidade, colaboração e prioridades |
| [Visão do produto](docs/FLOWPAY_VISAO_DO_PROJETO.md) | Público, jornada, escopo do MVP e fases futuras |
| [System Design](docs/FLOWPAY_SYSTEM_DESIGN.md) | Módulos, persistência, consistência, segurança e operação |
| [Design System](docs/FLOWPAY_DESIGN_SYSTEM.md) | Tokens, componentes, telas, linguagem e acessibilidade |
| [Plano de implementação](docs/FLOWPAY_IMPLEMENTATION_ROADMAP.md) | Etapas, dependências, decisões abertas e critérios de aceite |

Os documentos descrevem requisitos e propostas, não comprovam implementação. Inspecione o checkout atual antes de afirmar que uma funcionalidade existe ou falta. O repositório começou somente com documentação; o bootstrap acrescentou a base React/Vite, Spring Boot, PostgreSQL local e CI, mas ainda não implementa funcionalidades financeiras. A estrutura inicial de código mencionada na visão e o PRD v3.0 citado no System Design não estavam presentes; reavalie essa lacuna quando relevante e não invente conteúdo ou comandos executáveis.

Pedidos explícitos do usuário orientam a tarefa. As Master Project Instructions são a referência mais recente fornecida pelo responsável; suas escolhas explícitas substituem opções ainda abertas nas propostas anteriores. Havendo outras divergências documentais, identifique as fontes, preserve as invariantes abaixo e registre a decisão; esclareça apenas o que impedir o trabalho. Detalhes novos de contrato devem ser apresentados como decisões de implementação, não como requisitos já documentados.

## Arquitetura de referência

- Backend Java 21/Spring Boot em monólito modular, Maven, Spring Web/Security/Validation/Data JPA, Hibernate, PostgreSQL e Flyway.
- Frontend React/TypeScript SPA com Vite, React Router, TanStack Query, React Hook Form, Zod, Tailwind CSS, shadcn/ui, Radix UI e TanStack Table. A stack está escolhida; dependências entram conforme o recorte, sem presumir que já estão instaladas.
- Domínios iniciais: identity, contacts, finance, statements, reconciliation, reporting e audit. Estrutura Java sugerida em `com.flowpay`, com `api`, `application`, `domain` e `infrastructure` por módulo.
- Módulos se comunicam por interfaces/DTOs públicos; entidades JPA e repositories são internos. Evite ciclos e DTOs que exponham entidades.
- Priorize fundação → financeiro → CSV/conciliação → relatórios e jornada demonstrável. Billing, contratos, assinaturas, OFX e sandbox são fases posteriores, salvo pedido explícito.
- Workers internos e outbox PostgreSQL precedem brokers. Microsserviços, Kafka, Kubernetes, Redis, CQRS e event sourcing não são requisitos do MVP.
- Java 21 e Maven estão definidos. O bootstrap fixou Spring Boot 4.2.0, Node 26 LTS/npm, Vite 8.3 e PostgreSQL 18.7 no Compose. Os comandos reais estão no README; atualize-os ao mudar ferramentas ou versões. Recursos de produto entram conforme o recorte; a stack escolhida não comprova funcionalidades implementadas.
- Testes previstos: JUnit 5, Mockito, AssertJ, Testcontainers, WireMock, Vitest, React Testing Library e Playwright; use cada ferramenta quando pertinente. Infraestrutura: Docker/Compose, GitHub Actions e OpenAPI/Swagger. Observabilidade: logs estruturados, Micrometer, OpenTelemetry, Prometheus e Grafana, introduzidos quando a entrega justificar seu custo.

Os módulos mais detalhados sugeridos no master podem ser subdomínios dos módulos iniciais; não crie todos como pacotes independentes de uma vez. Justifique fronteiras e abstrações com regras e casos de uso concretos. A fase avançada aprofunda idempotência, concorrência e outbox no laboratório; essas garantias continuam necessárias no núcleo financeiro desde as operações que delas dependem.

## Invariantes do produto

1. **Tenancy:** contexto de empresa derivado da identidade autenticada e membership autorizado. Filtre toda operação empresarial por tenant, incluindo jobs, downloads e relatórios. Relacionamentos financeiros usam FKs compostas `(tenant_id, resource_id)` com UNIQUE correspondente.
2. **Dinheiro:** valores positivos em centavos (`amount_minor`, BIGINT), direção `INFLOW|OUTFLOW` separada e moeda BRL. Defina limites; não use ponto flutuante para cálculos monetários. Datas de vencimento são `LocalDate`; instantes de lançamento, UTC. Consultas financeiras informam período, timezone e `as_of`.
3. **Caixa:** somente `CashTransaction` altera saldo controlado. Baixas referenciam movimentos; importar `StatementLine` não altera caixa. Conciliar cria um movimento OU vincula um existente, sem duplicar o efeito.
4. **Baixas:** bloquear o título antes de validar/gravar operações concorrentes; soma das baixas válidas não excede seu valor e soma das alocações ativas não excede o movimento. Idempotência é escopada por `(tenant_id, operation, key)`.
5. **Histórico:** mudanças financeiras, auditoria e outbox são atômicas quando aplicáveis. Reversões são explícitas, com motivo e histórico preservado; não apague fatos confirmados. Entrega assíncrona é at least once com efeitos deduplicados.
6. **Conciliação:** mesmo arquivo deduplicado por tenant, conta e checksum; match ativo único por linha; ambiguidades passam por revisão humana. MVP concilia uma linha com um movimento completo; matches parciais são evolução posterior.
7. **Segurança:** sessão/cookie com Spring Security e CSRF integrado à SPA; autorização por papel. Armazene senhas com hash adequado, valide entradas e use consultas parametrizadas. Uploads privados e tratados como entrada não confiável. Não exponha extratos, PII, credenciais ou tokens em logs/dados demo, nem fixe segredos no código. Use OWASP ASVS como referência nas tarefas de segurança e explique decisões sensíveis. Credenciais de API e webhooks entram quando o recurso for implementado.
8. **Experiência:** diferencie previsto/realizado e saldo controlado/saldo do extrato. Use “Registrar pagamento realizado fora do FlowPay”. Sandbox visível e isolado: nunca quita títulos nem altera caixa do acompanhamento real.

## Agentes e skills locais

Perfis em `.codex/agents/*.toml`: `flowpay-architect`, `flowpay-backend`, `flowpay-frontend`, `flowpay-quality` e `flowpay-devops`. Consulte [o guia de uso](docs/DEVELOPMENT_AGENTS.md) para responsabilidades e exemplos.

Skills em `.agents/skills/`:

| Tarefa | Skill |
|---|---|
| Recortar uma entrega e seus critérios de aceite | `flowpay-feature` |
| Implementar domínio, persistência e API financeira | `flowpay-finance` |
| Importar CSV, deduplicar e conciliar | `flowpay-reconciliation` |
| Implementar telas e componentes | `flowpay-ui` |
| Verificar comportamento e revisar uma entrega | `flowpay-verify` |
| Alterar schema, migrations ou consultas PostgreSQL | `flowpay-postgres` |
| Criar ou diagnosticar testes Java/Spring | `flowpay-backend-testing` |
| Avaliar controles de segurança da aplicação | `flowpay-security` |
| Criar ou executar jornadas browser E2E | `flowpay-e2e` |

Leia o `SKILL.md` relevante antes de aplicar seu fluxo. Em tarefas pequenas, trabalhe diretamente. Quando houver delegação solicitada ou prevista pelas instruções aplicáveis, prefira implementação e revisão independentes, delimite arquivos e contratos por agente; tarefas que alteram os mesmos arquivos devem ser sequenciais. O agente principal integra os resultados, resolve achados importantes e relata evidências. Uma revisão sem defeitos encontrados deve apresentar a cobertura verificada, sem inventar achados.

Skills complementares, somente quando relevantes e disponíveis: `superpowers:brainstorming` e `superpowers:writing-plans` para desenho/planejamento; `superpowers:subagent-driven-development` para implementação delegada autorizada; `superpowers:test-driven-development` para testes; `superpowers:systematic-debugging` para diagnóstico; `superpowers:verification-before-completion` para comprovar conclusão; `build-web-apps:react-best-practices` para React; `product-design:audit` para auditoria de experiência solicitada; `codex-security:threat-model` e `codex-security:security-scan` para modelagem e auditoria de segurança pertinentes. Não declare uso de skill indisponível nem transforme toda alteração em auditoria completa.

## Fluxo de entregas

### Julgamento independente e ritmo de desenvolvimento

- Analise cada pedido à luz do produto, arquitetura, evidência e riscos; não concorde automaticamente. Quando houver conflito ou risco concreto, explique por quê e recomende um caminho viável.
- Separe fatos documentados de inferências e decisões de implementação. Faça perguntas somente quando uma resposta mudar materialmente o resultado ou evitar risco relevante; em escolhas reversíveis, declare a suposição e siga.
- Prefira uma entrega vertical pequena e demonstrável. Para mudanças simples, implemente diretamente e faça a verificação mais rápida pertinente. Para regras financeiras, tenancy, segurança ou concorrência, use a evidência e os testes específicos ao risco afetado.
- Planejamento, ADR, agentes paralelos, auditoria ampla e E2E não são etapas obrigatórias para toda alteração. Use-os quando a complexidade, durabilidade da decisão ou independência do trabalho justificar o custo.
- Termine com resumo do que mudou, verificações executadas e limitações. Atualize documentação quando uma decisão, dependência ou comando real mudar.

Ao delegar uma tarefa autorizada, forneça objetivo, arquivos sob responsabilidade, contrato compartilhado e critérios de aceite. O especialista devolve: resultado, arquivos alterados, decisões de contrato, verificações executadas e pendências. Informe se cada verificação passou, falhou ou não pôde ser executada. Um bloqueio não autoriza ampliar escopo, trocar stack ou sobrescrever trabalho de outro agente. O responsável integra mudanças dependentes e verifica a jornada após a integração.

Em revisão, achados devem indicar gravidade, arquivo/linha quando disponível, gatilho, impacto e evidência. Aceite revisões sem achados quando explicarem o que foi examinado e os limites da verificação. Consultar um perfil manualmente aplica suas instruções, mas não comprova que um agente personalizado foi iniciado.

Para funcionalidades maiores: entender problema → desenhar regras/contratos → planejar tarefas pequenas verificáveis → implementar incrementalmente → testar → revisar → documentar → verificar a jornada completa. Pratique TDD quando viável: teste significativo, falha pelo motivo esperado, implementação mínima e refatoração. Toda invariante financeira crítica precisa de teste; documentação isolada não exige código ou testes artificiais.

Mantenha README, ADRs, documentação de API/banco, estratégia de testes e instruções de deploy/operação conforme esses artefatos se tornarem necessários. Não crie documentos vazios apenas para preencher uma lista. Cada fase deve entregar comportamento demonstrável antes de expandir para a seguinte. Usabilidade, qualidade e confiabilidade são os critérios de decisão; complexidade técnica exige justificativa.

## Validação e comunicação

Responda ao responsável em português brasileiro, com explicações simples e exemplos para decisões importantes. Identificadores de código em inglês; interface inicial em português brasileiro; documentação pública nova preferencialmente em inglês. Preserve documentos existentes salvo tarefa de tradução explícita.

Implemente o menor recorte que resolva o pedido, preservando alterações do usuário. Teste os riscos afetados nos níveis adequados: domínio, integração PostgreSQL, API/autorização, concorrência, contratos, E2E e recuperação de falhas. Use comandos existentes e informe o que executou, resultado e limitações. Não declare build, testes, acessibilidade, integração ou confiabilidade de produção aprovados sem evidência. A profundidade do portfólio vem de software demonstrável, decisões justificadas e resultados medidos.

Mudanças somente documentais pedem validação de estrutura, referências e coerência; não exigem criar uma aplicação para testá-las. Atualize documentação quando alterar uma decisão ou disponibilizar um comando real.
