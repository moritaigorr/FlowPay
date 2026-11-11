# DESIGN SYSTEM — Tokens e componentes

> Template editável por projeto. Valores abaixo são **ponto de partida**, não padrões obrigatórios; adaptar somente com justificativa e manter consistência.

## 1. Identidade
- Nome do produto: `[NOME]`
- Personalidade: `[3 ADJETIVOS]`
- Princípios: clareza, consistência, responsividade, acessibilidade.
- Tema inicial: **a definir pelo projeto**, com preferência por direção visual criativa e autoral, sem prejudicar uso e legibilidade.

## 2. Tokens de cor — exemplo inicial
| Token semântico | Claro | Escuro | Uso |
|---|---|---|---|
| `--background` | `#FFFFFF` | `#0B0F14` | Fundo geral |
| `--surface` | `#F5F7FA` | `#151B23` | Cards e seções |
| `--foreground` | `#121820` | `#F3F5F7` | Texto principal |
| `--muted-foreground` | `#4B5563` | `#ABB5C2` | Texto secundário |
| `--accent` | `#225AC7` | `#82ABFF` | Ações/destaques |
| `--border` | `#D8DEE7` | `#354151` | Divisórias |

**Atenção:** cores são sugestões e precisam de **teste de contraste real** nas combinações usadas. Estados de sucesso/erro/alerta devem ter texto/ícone além de cor.

## 3. Tipografia
- Família de interface: `[EX.: INTER OU SYSTEM UI; VALIDAR DISPONIBILIDADE E LICENÇA]`
- Família de código: `[EX.: MONO SYSTEM]`
- Escala sugerida: `12, 14, 16, 20, 24, 32, 40, 56px`.
- Texto de corpo inicial: `16px`, line-height `1.5–1.7`.
- Headings com tamanhos responsivos (`clamp`) e quebra de linha planejada.

## 4. Espaçamento e dimensões
- Unidade base: `4px`; escala `4, 8, 12, 16, 24, 32, 48, 64, 96px`.
- Container: `max-width: 1200px` (ajustar ao conteúdo).
- Padding lateral responsivo: `16px` mobile, `24–48px` desktop.
- Raios: `8px` pequeno, `12px` médio, `20px` grande.
- Sombras: sutis, não substituir bordas/contraste.

## 5. Componentes essenciais
| Componente | Variantes | Estados obrigatórios |
|---|---|---|
| Button | primary, secondary, text | hover, focus, disabled, loading |
| Input | texto, busca, senha | normal, focus, error, disabled |
| Card | conteúdo, projeto, destaque | hover apenas se clicável, focus quando interativo |
| Navigation | desktop, mobile | ativo, expandido, foco |
| Dialog | confirmação, formulário | abertura, fechamento, foco, escape |
| Alert | informação, sucesso, erro | descrição acessível |
| Skeleton/Empty state | carregando, sem dados | mensagem útil |

## 6. Layout e breakpoints
- Mobile-first; definir breakpoints com base na quebra do conteúdo, não apenas em dispositivos populares.
- Não depender de hover para acessar funções.
- Navegação completa por teclado; rótulos acessíveis; foco visível.
- Respeitar área segura e evitar overflow horizontal.

## 7. Motion
- Microinterações: `120–220ms` como referência, não regra absoluta.
- Transições maiores: `220–400ms` quando justificadas.
- Respeitar `prefers-reduced-motion` e evitar bloquear conteúdo atrás de animação.

## 8. Implementação
- Centralizar tokens em CSS variables ou tema equivalente.
- Usar tokens semânticos (`foreground`, `surface`) em vez de cores hardcoded em vários componentes.
- Manter catálogo de componentes e exemplos; reutilizar antes de criar novo.
- Se o projeto já possui design system, **ele prevalece**; alterações devem ser propostas, não impostas.

## 9. Checklist de revisão
- [ ] Cores e tipografia consistentes
- [ ] Contraste testado nas combinações reais
- [ ] Componentes com estados completos
- [ ] Responsividade verificada
- [ ] Acessibilidade e navegação por teclado verificadas
- [ ] Motion com alternativa reduzida

## Referências para estudar (não implica consulta em tempo real)
- https://www.w3.org/TR/WCAG22/
- https://developer.mozilla.org/en-US/docs/Web/CSS
- https://www.w3.org/WAI/ARIA/apg/

## 10. Extensão personalizada: sistema criativo, não um template rígido
**Modo padrão: CRIATIVO.** Conservar os tokens semânticos e a consistência, mas permitir composições, cores e tipografia distintas por produto. **Não aplicar a mesma paleta em todos os projetos.** O quadro de cores da seção 2 é *exemplo*, não branding definitivo.

### Definir uma identidade a cada novo projeto
Preencher antes de implementar novas telas:
- Conceito e sensação desejada: `[EX.: LABORATÓRIO DIGITAL / EDITORIAL EXPERIMENTAL]`
- Adjetivos de personalidade: `[3]`
- Cor base / acento / apoio: `[VALORES E FUNÇÕES]`
- Fontes e licenças: `[NOME / ORIGEM / USO]`
- Estilo do grid e das superfícies: `[DESCRIÇÃO]`
- Assinatura visual: `[UM ELEMENTO DIFERENCIADOR]`
- Intensidade de movimento: `[BAIXA / MODERADA / ALTA COM JUSTIFICATIVA]`
- Referências aprovadas: `[LINKS OU ARQUIVOS]`
- Tokens e componentes já existentes a preservar: `[LISTA]`

### Tokens adicionais opcionais
| Token | Significado | Observação |
|---|---|---|
| `--accent-secondary` | Segundo destaque | Só quando fortalecer hierarquia |
| `--hero-gradient` | Gradiente principal | Não usar para codificar estados |
| `--display-font` | Fonte expressiva de títulos | Garantir fallback e legibilidade |
| `--motion-fast` | Microinteração | Valor inicial 160ms; ajustar após testar |
| `--motion-medium` | Transição de seções | Valor inicial 320ms; não bloquear interação |
| `--focus-ring` | Indicador visível de foco | Contraste suficiente em fundos reais |

### Design criativo com variantes conscientes
- **Editorial:** componentes mais tipográficos e menos decorativos; contraste de tamanhos e ritmo de seções.
- **Experimental:** cards assimétricos e camadas leves; preservar ordem lógica no DOM.
- **Tech:** tipografia mono como detalhe, visualizações técnicas funcionais e não só decoração.
- Componentes de alta expressividade exigem também estados `hover`, `focus`, `active`, `disabled`, `loading` quando aplicáveis.
- Garantir que o conteúdo crítico continue acessível sem JavaScript de animação.

### Decisão + aprendizado
Para cada novo componente-base ou token importante: explicar **o motivo**, o problema resolvido, o efeito na experiência e um trade-off em poucas frases. Citar arquivos/elementos do design existente, se houver. Não inventar testes de contraste ou de responsividade; registrar os que realmente ocorreram.
