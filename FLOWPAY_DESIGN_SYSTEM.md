# FlowPay — Design System (v1.0)

**Status:** proposta visual e de experiência · **Data:** 18/11/2026  
**Produto:** painel SaaS financeiro para empresas prestadoras de serviços  
**Objetivo:** uma interface profissional, confiável e simples para tarefas financeiras diárias

## 1. Direção visual recomendada: *Calm Finance*

O FlowPay deve parecer uma ferramenta na qual o gestor confia para conferir dinheiro: **limpa, legível, objetiva e com alta densidade de informação bem organizada**. A marca usa azul-petróleo como cor principal, superfícies neutras, detalhes discretos e estados semânticos claros. Evitar visual de aplicativo de criptomoedas (neons, gradientes intensos e números em destaque exagerado).

| Direção considerada | Aparência | Trade-off | Decisão |
|---|---|---|---|
| Minimalista monocromática | Cinza/preto, tipografia forte | Pode perder pistas de hierarquia e status | Alternativa |
| **Calm Finance** | Azul-petróleo, neutros quentes, bordas finas | Exige atenção a contrastes semânticos | **Recomendada** |
| Dashboard vibrante | Muitas cores e cartões fortes | Distrai das tarefas e cria ruído financeiro | Rejeitada para MVP |

**Princípios de UX:** clareza antes de decoração; números auditáveis; reconhecimento antes de memorização; erros recuperáveis; ações perigosas com confirmação e contexto; menos cliques para operações rotineiras; acessibilidade desde o início.

## 2. Referências de design pesquisadas

O sistema usa **tokens semânticos** (cores por função, não por tela), seguindo os conceitos de papéis de cor do Material Design 3. Os componentes podem usar **shadcn/ui** e primitivas Radix para manter controle visual, navegação por teclado e acessibilidade. Tabelas de dados podem usar TanStack Table, que separa lógica de tabela da aparência. São referências e ferramentas, **não um template copiado**.

- [Material Design 3 — Color roles](https://m3.material.io/styles/color/the-color-system)
- [shadcn/ui — Theming](https://ui.shadcn.com/docs/theming)
- [Radix Primitives — Accessibility](https://www.radix-ui.com/primitives/docs/overview/accessibility)
- [TanStack Table — Overview](https://tanstack.com/table/latest/docs/overview)
- [W3C — WCAG 2.2](https://www.w3.org/TR/WCAG22/)

## 3. Tokens de design

### 3.1 Cores (tema claro inicial)

| Token | Hex | Uso |
|---|---|---|
| `--background` | `#F7F9FA` | Fundo da aplicação |
| `--surface` | `#FFFFFF` | Cards, tabelas e modais |
| `--surface-subtle` | `#F0F4F5` | Áreas secundárias |
| `--foreground` | `#17252C` | Texto principal |
| `--muted-foreground` | `#51636C` | Texto secundário |
| `--border` | `#DCE4E7` | Bordas e divisores |
| `--primary` | `#075A63` | Botão principal, foco e links |
| `--primary-hover` | `#064C54` | Hover principal |
| `--primary-foreground` | `#FFFFFF` | Texto sobre primary |
| `--info` | `#1D4E8F` | Informação e ajuda |
| `--success` | `#17643A` | Sucesso/conciliado |
| `--warning` | `#8A5600` | Atenção/pendente |
| `--danger` | `#B42318` | Erro/ação destrutiva |
| `--focus-ring` | `#075A63` | Contorno de foco |

**Pares de estados semânticos (propostos, validar contraste):**

| Estado | Fundo | Texto | Borda |
|---|---|---|---|
| Success | `#EAF7EF` | `#17643A` | `#ACD8BD` |
| Warning | `#FFF6E6` | `#7A4C00` | `#EACB8E` |
| Danger | `#FFF0EE` | `#A1261D` | `#E6B2AD` |
| Info | `#EDF4FF` | `#1D4E8F` | `#B8CBEA` |

Os componentes usam aliases `--success-background`, `--success-foreground`, `--success-border` (e equivalentes de warning/danger/info). Os pares são **alvos de design**, não resultados certificados; se falharem na medição de contraste, ajustar os tokens antes de publicar.

**Aviso:** cores propostas precisam de teste automatizado de contraste em todos os pares de texto/fundo antes de serem congeladas. As cores semânticas não são substitutas de labels e ícones; não comunicar estado apenas por cor.

### 3.2 Tipografia

- Fonte principal: **Inter** (fallback `system-ui`, `Arial`, sans-serif); números financeiros usam `font-variant-numeric: tabular-nums`.
- Escala recomendada: `12 / 14 / 16 / 20 / 24 / 32 px`.
- Texto de tabela e formulários: 14 px; texto de corpo: 16 px; títulos de página: 24 px / 600; KPIs: 24–32 px / 650.
- Valores monetários à direita em tabelas e com formatação pt-BR (`R$ 1.234,56`); negativos com sinal claro, sem depender só da cor.
- Data: `dd/MM/yyyy`; horários com fuso explícito quando relevantes.

### 3.3 Espaçamento, raio e elevação

| Token | Valor | Uso |
|---|---:|---|
| `space-1` | 4 px | Microespaço |
| `space-2` | 8 px | Ícones e labels |
| `space-3` | 12 px | Grupos pequenos |
| `space-4` | 16 px | Espaçamento padrão |
| `space-6` | 24 px | Cards e seções |
| `space-8` | 32 px | Separação principal |
| `radius-sm` | 6 px | Campos e badges |
| `radius-md` | 10 px | Cards e menus |
| `radius-lg` | 14 px | Modais e painéis |
| `shadow-card` | `0 1px 3px rgba(23,37,44,.06)` | Elevação mínima |

Altura de campo/botão: **40–44 px** no desktop; alvos interativos prioritários ≥ 44 × 44 px no mobile quando possível. Estados de foco devem ser perceptíveis por teclado. Respeitar `prefers-reduced-motion`.

## 4. Layout da aplicação

### Desktop (≥ 1024 px)

- Sidebar fixa de 248 px, recolhível para 72 px, com logo e seções por tarefa.
- Topbar de 64 px com nome da empresa, busca, ajuda, notificações e menu do usuário.
- Conteúdo com largura fluida, `max-width` aproximada de 1440 px e padding de 24–32 px.
- Grade de KPIs com 4 colunas quando houver espaço; tabelas com filtros acima e paginação abaixo.

### Tablet (768–1023 px)

- Sidebar em drawer; KPIs em 2 colunas; tabelas com scroll horizontal controlado e colunas prioritárias fixas.

### Mobile (< 768 px)

- Navegação em menu/drawer; KPIs em uma coluna; ações mais comuns acessíveis; tabelas densas viram lista de registros com detalhes expansíveis onde fizer sentido.
- Não esconder informações críticas como valor, vencimento e status para caber na tela; permitir ver detalhes.

### Navegação sugerida

```text
Visão geral
Financeiro
  Contas a receber
  Contas a pagar
  Movimentações
  Contas financeiras
Conciliação
  Importar extratos
  Pendências
Gestão
  Clientes
  Fornecedores
  Serviços (fase posterior)
Cobranças (fase posterior)
  Faturas
  Assinaturas
Relatórios
Equipe e configurações
Laboratório de pagamentos (sandbox separado)
```

## 5. Biblioteca de componentes

| Grupo | Componentes | Regras de comportamento |
|---|---|---|
| Base | `Button`, `IconButton`, `Link`, `Badge`, `Divider`, `Tooltip` | Hover, focus-visible, disabled, loading e variantes semânticas |
| Entrada | `TextField`, `MoneyField`, `DateField`, `Select`, `Combobox`, `Checkbox`, `Textarea` | Label visível, hint, validação inline, erros acessíveis |
| Feedback | `Alert`, `Toast`, `Skeleton`, `EmptyState`, `Progress`, `ConfirmDialog` | Não depender de toast para falhas críticas; erro persistente ao lado do campo |
| Dados | `DataTable`, `FilterBar`, `Pagination`, `StatusBadge`, `MetricCard`, `TrendChart` | Ordenação/filtros acessíveis, valores tabulares, export claro |
| Navegação | `AppSidebar`, `Topbar`, `Breadcrumb`, `Tabs`, `PageHeader` | Item ativo identificável além de cor; teclado e aria-label |
| Financeiro | `AmountDisplay`, `DueDateLabel`, `TransactionRow`, `ReconciliationMatchCard` | Distinguir previsto/realizado e saldo controlado/extrato |

**Prioridade de implementação:** Button → Field → Card → Table → Dialog → Toast → MoneyField → StatusBadge → MetricCard → ImportPreview → ReconciliationMatchCard. Manter um catálogo com variações e states, idealmente Storybook quando houver pelo menos 8 componentes reutilizáveis.

### Estados de dados

| Situação | Representação visual |
|---|---|
| Aberto | Badge neutro `Em aberto` |
| Vencido | Badge de atenção `Vencido` + data |
| Parcialmente pago | Badge informativo `Parcial` + `R$ X de R$ Y` |
| Pago | Badge sucesso `Pago` + data |
| A conciliar | Badge atenção `Pendente de conciliação` |
| Conciliado | Badge sucesso `Conciliado` + link do vínculo |
| Possível duplicata | Badge aviso `Revisar duplicata` + contexto |
| Simulado | Badge explícito `SANDBOX` sempre visível |

## 6. Padrões de tela (templates)

### 6.1 Visão geral

**Cabeçalho:** “Visão geral” + seletor de período + ação primária “Novo lançamento”.

1. Primeira linha: Saldo controlado; A receber; A pagar; Atrasados.
2. Segunda linha: gráfico de fluxo de caixa **previsto × realizado** e lista de vencimentos próximos.
3. Terceira linha: movimentações recentes e pendências de conciliação.
4. Se existir saldo de extrato importado, exibir painel `Saldo do extrato` e `Diferença a conciliar`, com aviso de data de referência.

**Não mostrar** “dinheiro disponível” ou “saldo bancário atual” se o dado é apenas controlado manualmente.

### 6.2 Contas a receber / pagar

- Cabeçalho com título, total pendente e botão “Novo recebível” ou “Nova despesa”.
- Filtros: status, cliente/fornecedor, vencimento, categoria e busca.
- Colunas: descrição, pessoa, valor total, valor pendente, vencimento, status, ações.
- Ações contextuais: ver detalhes, registrar baixa, editar quando permitido, cancelar/reverter com motivo.
- Estados vazios com exemplo claro (“Você ainda não registrou contas a receber”).

### 6.3 Importar extrato

**Fluxo em etapas:** selecionar conta → escolher arquivo → mapear colunas (CSV) → prévia/erros → confirmar importação → revisar duplicatas. Mostrar progresso e número de linhas aceitas/rejeitadas. **Importar não altera caixa automaticamente.**

### 6.4 Conciliação

Lista de linhas importadas à esquerda e propostas de movimentos/baixas à direita (no desktop); no mobile, etapas sequenciais. Para cada candidato: valor, data, descrição, confiança como **indicação**, botão `Vincular`/`Criar lançamento`/`Ignorar`. Correspondências ambíguas nunca são confirmadas silenciosamente.

## 7. Padrões de interação e microcopy

- Usar linguagem comum: “Registrar recebimento”, não “Executar settlement”; “Conferir extrato”, não “Executar reconciliation”.
- Toda ação relevante distingue **registrar** de **pagar de verdade**. Exemplo: “Registrar pagamento realizado fora do FlowPay”.
- Antes de confirmar importação: “Vamos salvar as linhas do extrato. Nenhuma movimentação será criada até você conciliá-las.”
- Antes de estornar baixa: mostrar valor, conta afetada e motivo obrigatório; oferecer trilha de auditoria.
- Ao encontrar possível duplicata: mostrar referência, data, valor e origem; permitir decisão humana.
- Erros: dizer o que falhou, efeito no dado e próximo passo (“A linha 24 tem data inválida; revise o formato DD/MM/AAAA”).
- Conclusão: feedback com ID/link para o registro, não só toast.

## 8. Acessibilidade e qualidade

**Meta:** WCAG 2.2 nível AA para os fluxos críticos (não afirmar conformidade sem auditoria). Texto normal com contraste mínimo de 4,5:1; ícones/controles informativos ≥ 3:1 conforme critério aplicável; foco visível; navegação por teclado; labels programáticos; mensagens de erro ligadas aos campos; suporte a zoom 200%; animações reduzidas. [W3C WCAG 2.2](https://www.w3.org/TR/WCAG22/)

**Testes:** Storybook/axe para componentes, testes Vitest de estado, Playwright com teclado e viewport desktop/mobile, conferência manual dos formulários de valores e conciliação. Validar contraste de cada token em background real, inclusive status badges.

## 9. Estrutura de código sugerida

```text
frontend/src/
  app/                # router, providers, auth, layout
  design-system/
    tokens.css
    primitives/       # componentes base (shadcn/Radix customizados)
    patterns/         # MoneyField, MetricCard, DataTable
  features/
    dashboard/
    receivables/
    payables/
    statements/
    reconciliation/
    contacts/
  shared/
    api/
    format/           # currency, date, statuses
```

**Token CSS ilustrativo:**

```css
:root {
  --background: #f7f9fa;
  --surface: #ffffff;
  --foreground: #17252c;
  --muted-foreground: #51636c;
  --border: #dce4e7;
  --primary: #075a63;
  --primary-foreground: #ffffff;
  --radius: 10px;
}
```

Adaptar tokens aos nomes semânticos da implementação escolhida; componentes devem consumir `var(--primary)`/aliases, e **não** cores hex espalhadas nas telas.

## 10. Roadmap do Design System

1. **Fundação:** tokens, AppShell, botões, inputs, cards, tabela e navegação.
2. **MVP financeiro:** MoneyField, status, dashboard e telas de contas.
3. **Conciliação:** upload, preview, estados de linha, confirmação de match, exceções.
4. **Produto expandido:** faturas, assinaturas, notificações e relatórios completos.
5. **Maturidade:** tema escuro após validar contrastes; testes visuais, catálogo Storybook e guideline de contribuição.

**Decisões para revisão:** paleta azul-petróleo, fonte Inter, estilo leve e denso, sidebar desktop, prioridade ao tema claro, shadcn/ui + Radix como base, gráficos só quando esclarecem informações. O documento especifica o sistema visual, mas **não é um protótipo renderizado**.
