# DESIGN GUIDELINES — Referências e identidade visual

> Finalidade: orientar a IA a criar interfaces próprias, bonitas, utilizáveis e consistentes. **Não copiar interfaces de terceiros literalmente.**

## 1. Briefing visual do projeto (preencher)
- Nome do produto: `[NOME]`
- Público-alvo: `[PESSOAS E NECESSIDADES]`
- Personalidade da marca (3 adjetivos): `[EX.: PRECISO, ELEGANTE, ACESSÍVEL]`
- Objetivo principal da página: `[AÇÃO MAIS IMPORTANTE]`
- Referências aprovadas (URLs ou screenshots): `[LINKS]`
- O que aproveitar de cada referência: `[EX.: HIERARQUIA, ESPAÇAMENTO, MOTION]`
- O que evitar: `[EX.: GRADIENTES EXCESSIVOS, CARDS GENÉRICOS]`

## 2. Onde buscar repertório
- Awwwards — https://www.awwwards.com/ — experiências web e criatividade.
- Recent (antigo Godly) — https://godly.website/ — inspiração de páginas contemporâneas (verificar eventual redirecionamento).
- Land-book — https://land-book.com/ — landing pages e composição.
- Mobbin — https://mobbin.com/ — padrões de interface de produtos reais.
- Dribbble — https://dribbble.com/ — exploração de componentes e identidades.

As referências **não** são instruções para copiar: extraia princípios, discuta os trade-offs e desenvolva identidade própria. Não alegue ter aberto um link se a ferramenta não permitiu acesso; solicite screenshot ou descreva a limitação.

## 3. Direção visual inicial sugerida (alterável pelo projeto)
Para um portfólio de Java/backend, a direção preferida é **criatividade editorial + clareza técnica**: tipografia expressiva, grid intencional, projetos com contexto arquitetural e uma interação memorável. Evitar templates genéricos e efeitos gratuitos; adaptar intensidade visual ao público e ao objetivo.

## 4. Hierarquia e layout
- Priorizar legibilidade, alinhamento, ritmo vertical e contraste.
- Definir largura máxima de conteúdo, espaçamento consistente e comportamento mobile-first.
- Cada seção deve responder a uma pergunta do visitante; evitar seções decorativas sem propósito.
- Hero: proposta clara + prova/credibilidade + CTA relevante.
- Cards de projetos: problema, solução, stack, decisões, resultado e link.

## 5. Tipografia, cores e imagens
- No máximo 2 famílias tipográficas salvo justificativa.
- Usar tokens e escalas definidos em `DESIGN-SYSTEM.md`; não espalhar valores arbitrários.
- Testar contraste e tamanhos em telas pequenas; comunicar sem depender só de cor.
- Preferir screenshots reais, ícones coerentes e ilustrações com uso autorizado.
- Não inserir conteúdo fictício como se fosse fato (clientes, números, depoimentos).

## 6. Animações e interações
- Motion deve sinalizar estado, orientar atenção ou reforçar a navegação.
- Preferir animações expressivas **quando tiverem propósito e desempenho adequado**; evitar parallax pesado e scroll-jacking.
- Respeitar `prefers-reduced-motion`; manter navegação por teclado.
- Carregamento, hover, foco, erro e estado vazio devem ter tratamento.

## 7. Processo obrigatório com IA
1. Ler `PRD-TEMPLATE.md` preenchido e `DESIGN-SYSTEM.md`.
2. Inspecionar o design existente antes de propor mudanças.
3. Explicar **qual princípio** foi extraído das referências e **por quê**.
4. Propor direção visual (tipografia, cor, layout, comportamento) para aprovação em mudanças amplas.
5. Implementar uma seção por vez e validar desktop/mobile.
6. Apresentar resumo de decisões e os arquivos alterados.

## 8. Critérios de aceitação
- [ ] Identidade coerente, não cópia de outro site
- [ ] Hierarquia clara e navegação compreensível
- [ ] Layout funcional em mobile e desktop
- [ ] Foco visível, contraste e textos acessíveis
- [ ] Sem animações que prejudiquem a usabilidade
- [ ] Sem componentes improvisados fora do design system
- [ ] Evidências de revisão visual e correções

## 9. Fontes de referência técnica
- WCAG — https://www.w3.org/TR/WCAG22/
- MDN CSS — https://developer.mozilla.org/en-US/docs/Web/CSS
- web.dev — https://web.dev/

**Regra:** citar a fonte exata usada em uma decisão técnica quando houver consulta; não confundir inspiração visual com validação técnica.

## 10. Preferência personalizada: design criativo, com intenção
**Preferência padrão:** criar experiências visualmente memoráveis, originais e expressivas — sem sacrificar clareza, velocidade ou acessibilidade. Esta preferência **não** obriga a usar 3D, neon ou muitas animações em todo projeto. O contexto do PRD e o design system existente têm prioridade.

### Exploração visual em três direções
Antes de uma implementação visual nova ou uma reformulação importante, apresentar três caminhos curtos e contrastantes:
1. **Editorial expressivo:** títulos marcantes, ritmo tipográfico, composições assimétricas controladas.
2. **Tech interativo:** bento grids com personalidade, motion guiado por interação e detalhes técnicos.
3. **Experiência imersiva:** profundidade e transições narrativas; 3D/WebGL apenas se o benefício justificar peso e manutenção.
Para cada direção, descrever: referência + princípio extraído, como a proposta será original, público, riscos de acessibilidade/performance e custo de implementação. Recomendar uma delas e pedir aprovação **antes de mudanças amplas**, conforme `AGENTS.md`. Pequenas alterações dentro da direção já aprovada podem seguir sem nova aprovação.

### Banco de referências — registro por projeto
| Referência | Fonte/URL ou screenshot | Elemento admirado | Adaptação original proposta | O que NÃO copiar |
|---|---|---|---|---|
| 01 | `[URL OU ARQUIVO]` | `[TIPOGRAFIA/MOTION/GRID]` | `[COMO ADAPTAR]` | `[IDENTIDADE/ASSETS/LAYOUT INTEGRAL]` |
| 02 | `[URL OU ARQUIVO]` | `[DETALHE]` | `[ADAPTAÇÃO]` | `[ELEMENTOS PROTEGIDOS]` |

### Experimentos criativos seguros
- Escolher **um gesto memorável** por página (ex.: hero tipográfico animado, transição de cards ou visualização interativa), não dez efeitos competindo.
- Criar estado funcional sem animação, para teclado e `prefers-reduced-motion`.
- Priorizar animação de `transform` e `opacity` quando apropriado; evitar efeitos caros em scroll em dispositivos modestos.
- Não adicionar bibliotecas de motion ou 3D sem demonstrar necessidade e compatibilidade com a stack.
- Validar o design com screenshots reais em mobile e desktop; não apresentar mockups gerados como evidência de implementação.

### Comunicação equilibrada — aprender sem interromper
Ao entregar uma decisão visual importante, explicar em **3 a 6 frases**: o que foi escolhido, por que ajuda o usuário, qual princípio de design foi aplicado e qual alternativa foi descartada. Links de inspiração são **inspiração**, não comprovação técnica. Citar documentação técnica consultada de fato ou marcar como leitura sugerida.

### Exemplos de direção para projetos Java/backend
Um portfólio pode usar narrativa visual criativa para **dar destaque à engenharia**, com diagramas leves de arquitetura, fluxos de API, estudos de caso e pequenos demos — sem esconder conteúdo essencial atrás de animação. Para dashboards e áreas administrativas, privilegiar eficiência e leitura; criatividade pode aparecer em tipografia e identidade, sem dificultar operações.
