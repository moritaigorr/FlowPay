# Agentes e skills de desenvolvimento do FlowPay

Este conjunto traduz as [Master Project Instructions](FLOWPAY_MASTER_PROJECT_INSTRUCTIONS.md), a visão do produto, o System Design e o Design System em instruções reutilizáveis para o Codex dentro deste repositório. O master foi fornecido posteriormente pelo responsável e define Java 21, Maven e a stack React/TypeScript/Vite, além das práticas de desenvolvimento.

## Organização

```text
AGENTS.md                           # Caminhos relativos à raiz do repositório
README.md                           # Pré-requisitos e comandos reais do bootstrap
compose.yaml                        # PostgreSQL local de desenvolvimento
.github/workflows/ci.yml            # Verificações iniciais de backend e frontend
backend/                            # API Java/Spring Boot
frontend/                           # SPA React/TypeScript/Vite
docs/
  DEVELOPMENT_AGENTS.md
  FLOWPAY_MASTER_PROJECT_INSTRUCTIONS.md
  FLOWPAY_VISAO_DO_PROJETO.md
  FLOWPAY_SYSTEM_DESIGN.md
  FLOWPAY_DESIGN_SYSTEM.md
  FLOWPAY_IMPLEMENTATION_ROADMAP.md # Etapas, dependências e critérios de aceite
scripts/
  validate-agent-assets.mjs          # Validação local sem dependências
.codex/agents/
  flowpay-architect.toml             # Escopo e contratos
  flowpay-backend.toml               # API, domínio e persistência
  flowpay-frontend.toml              # Interface e integração
  flowpay-quality.toml               # Revisão e verificação
  flowpay-devops.toml                # Ambiente local e CI
.agents/skills/
  flowpay-feature/SKILL.md           # Recortar uma entrega
  flowpay-finance/SKILL.md            # Implementar regras financeiras
  flowpay-reconciliation/SKILL.md     # Importar e conciliar
  flowpay-ui/SKILL.md                 # Construir telas
  flowpay-verify/SKILL.md             # Validar comportamento
  flowpay-postgres/SKILL.md           # Schema, migrations e consultas PostgreSQL
  flowpay-backend-testing/SKILL.md    # Testes Java/Spring por camada
  flowpay-security/SKILL.md           # Revisão de controles da aplicação
  flowpay-e2e/SKILL.md                # Jornadas browser com Playwright
```

`AGENTS.md` fornece o contexto comum. Cada agente tem uma responsabilidade e critérios de entrega. Skills são procedimentos que podem ser usados tanto pelo agente principal quanto pelos especialistas; não é necessário abrir vários agentes para uma tarefa pequena.

## Padrão de desenvolvimento

O agente avalia pedidos com independência, explica discordâncias com evidências e diferencia requisitos documentados de decisões novas. A entrega padrão é uma fatia pequena e demonstrável, com validação proporcional ao risco. ADRs, planejamento extenso, delegação paralela, auditorias completas e E2E entram quando o custo ou risco da mudança justificar; não são burocracia obrigatória para toda tarefa. No fim, informe mudanças, verificações realizadas e limitações.

## Usar no Codex

Abra este repositório como projeto. Agentes personalizados ficam em arquivos TOML locais, com `name`, `description` e `developer_instructions`, conforme a [documentação de agentes do Codex](https://learn.chatgpt.com/docs/agent-configuration/subagents). Estes perfis herdam modelo, ferramentas e permissões da sessão; os arquivos não mudam essas configurações.

As skills estão na pasta de descoberta por repositório, `.agents/skills`, conforme a [documentação de skills](https://learn.chatgpt.com/docs/build-skills). Podem ser selecionadas pela descrição ou solicitadas por `$nome-da-skill`. Se não aparecerem, reinicie a sessão do Codex no projeto. A disponibilidade dos agentes também depende do cliente e das políticas da sessão. Se o cliente não oferecer delegação, peça ao agente principal para ler o perfil e executar o mesmo escopo diretamente.

### Pedidos prontos

Planejar a primeira entrega:

```text
Use $flowpay-feature para recortar a fundação do FlowPay com login,
empresa e memberships. Inspecione o que existe e defina contratos,
critérios de aceite e uma sequência curta de implementação.
```

Consultar o plano detalhado:

```text
Leia docs/FLOWPAY_IMPLEMENTATION_ROADMAP.md, compare o Portão 0 e a
primeira etapa com o estado real do repositório e me diga qual é a
próxima entrega executável, com seus critérios de aceite.
```

Pedir um especialista:

```text
Use o agente flowpay-backend e a skill $flowpay-finance para implementar
baixa parcial de contas a receber. Considere idempotência, tenant,
concorrência e testes no PostgreSQL. Inspecione antes a estrutura existente.
```

Implementar interface:

```text
Use o agente flowpay-frontend e $flowpay-ui para implementar a tela de
contas a receber conforme o Design System. Inclua os estados da tela
e informe quais comportamentos estão integrados ou usam mocks.
```

Trabalhar com extratos:

```text
Use $flowpay-reconciliation para implementar a importação CSV com prévia
e confirmação. Preserve o saldo controlado e verifique a reimportação.
```

Preparar ambiente:

```text
Use o agente flowpay-devops para preparar o ambiente local e o CI
compatíveis com a stack documentada e registrar os comandos reais.
```

Revisar:

```text
Peça ao agente flowpay-quality para usar $flowpay-verify e revisar
as alterações atuais. Relate achados com evidência e limitações,
sem modificar os arquivos durante a revisão.
```

Quando o trabalho justificar agentes em paralelo, peça explicitamente a divisão e aguarde a integração. Por exemplo: `flowpay-architect` define primeiro o contrato; depois backend e frontend trabalham em arquivos separados; `flowpay-quality` revisa o resultado integrado. Tarefas dependentes ou que editam os mesmos arquivos seguem em sequência.

## Responsabilidades

| Agente | Entrega esperada |
|---|---|
| `flowpay-architect` | Recorte vertical, contratos, decisões com fontes e critérios de aceite |
| `flowpay-backend` | Casos de uso, API, migrations e evidência de integridade |
| `flowpay-frontend` | Telas, estados, integração e evidência de uso por teclado/mobile |
| `flowpay-quality` | Achados priorizados e resultados de validação, com limites explícitos |
| `flowpay-devops` | Ambiente reproduzível, CI e comandos operacionais verificados |

## Manutenção

Mantenha o master como diretriz do responsável e os três documentos originais em `docs/` como fontes detalhadas de produto, arquitetura e visual. As escolhas explícitas mais recentes do master substituem opções antes abertas; não indicam implementação concluída. Atualize `AGENTS.md` ao mudar uma invariante ou disponibilizar código/comandos; atualize uma skill quando seu procedimento mudar. Preserve a diferença entre requisito documentado e decisão nova. Não replique paletas, schemas inteiros ou manuais de ferramentas em cada perfil. Prefira inglês para documentação pública nova e identificadores; a comunicação com o responsável e a interface inicial usam pt-BR.

Ao adicionar um agente, mantenha um único perfil TOML com nome único e responsabilidade delimitada. Ao adicionar uma skill, use pasta com o mesmo nome do frontmatter, `SKILL.md` com `name` e `description` e links relativos válidos. Valide estrutura e faça um cenário de uso antes de confiar no fluxo.

Execute `node scripts/validate-agent-assets.mjs` a partir da raiz para verificar o formato simples usado pelos perfis TOML e frontmatter YAML, nomes únicos, links Markdown locais e caminhos de arquivos citados pelos agentes. O script não substitui um parser geral TOML/YAML nem comprova ativação no cliente. Novos campos ou formatos exigem atualizar o validador ou adotar parsers completos. O ambiente atual não oferece Python no PATH, portanto o `quick_validate.py` da skill-creator não foi executado.

Na criação deste conjunto havia três documentos, sem aplicação ou infraestrutura de testes. A visão menciona uma estrutura inicial e o System Design referencia um PRD v3.0 ausentes neste checkout. As instruções registram essa diferença; não implementam nem comprovam as funcionalidades planejadas.
