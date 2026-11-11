# QUALITY CHECKLIST — Testes, segurança e revisão

> Marque cada item como `PASSOU`, `FALHOU`, `NÃO EXECUTADO` ou `NÃO APLICÁVEL`, com evidência. Nunca marcar PASSOU sem verificar.

## A. Requisitos e arquitetura
- [ ] Critérios de aceitação do PRD conferidos
- [ ] Arquitetura existente identificada a partir de código/documentação
- [ ] Limites de camadas/módulos respeitados
- [ ] Nenhuma dependência ou reorganização inesperada
- [ ] Alterações de contrato, API ou esquema documentadas

## B. Qualidade do código
- [ ] Build/compilação sem erros
- [ ] Lint e formatação executados (se existentes)
- [ ] Typecheck executado (se aplicável)
- [ ] Sem código morto/duplicação evitável
- [ ] Tratamento de erros consistente e sem exposição de dados internos

## C. Testes
- [ ] Testes unitários para regras críticas
- [ ] Testes de integração para banco, APIs e segurança quando aplicável
- [ ] Casos de borda e falha cobrindo comportamentos relevantes
- [ ] Regressão das funcionalidades relacionadas verificada
- [ ] Comandos e resultados registrados corretamente

## D. Segurança e privacidade
- [ ] Segredos não commitados e não enviados ao frontend
- [ ] Autenticação/autorização revisadas do lado servidor
- [ ] Validação de entradas e tratamento seguro de saídas
- [ ] Dependências e vulnerabilidades avaliadas com ferramenta disponível
- [ ] Logs sem credenciais, tokens ou dados pessoais desnecessários
- [ ] Dados, backup e migrações destrutivas tratados com cautela

## E. UI/UX e acessibilidade (se aplicável)
- [ ] Visual segue `DESIGN-SYSTEM.md` e `DESIGN-GUIDELINES.md`
- [ ] Mobile e desktop revisados
- [ ] Navegação por teclado e foco visível
- [ ] Contraste, labels e mensagens compreensíveis
- [ ] Loading, erro e estados vazios previstos
- [ ] Reduced motion respeitado
- [ ] Imagens e fontes com carregamento apropriado

## F. Confiabilidade e operação
- [ ] Timeouts e falhas de integrações tratados
- [ ] Métricas/logs úteis para diagnosticar problemas
- [ ] Configuração por ambiente revisada
- [ ] Instruções de execução e rollback, quando necessário
- [ ] Comportamento com concorrência e repetição avaliado quando relevante

## G. Aprendizado e transparência
- [ ] IA explicou **o que, por que e como**
- [ ] Decisões conectadas à arquitetura definida
- [ ] Alternativas/trade-offs explicados quando relevantes
- [ ] Fontes técnicas com URL, seção/versão quando verificadas
- [ ] Fontes não consultadas sinalizadas como referências para estudar
- [ ] Evidências internas citam caminhos/símbolos reais
- [ ] Limitações e itens não executados declarados

## Modelo de relatório de validação
| Verificação | Estado | Evidência (comando, arquivo, captura, resultado) | Ação pendente |
|---|---|---|---|
| Build | NÃO EXECUTADO | `[MOTIVO]` | `[PRÓXIMO PASSO]` |
| Testes | NÃO EXECUTADO | `[MOTIVO]` | `[PRÓXIMO PASSO]` |
| Arquitetura | NÃO EXECUTADO | `[ARQUIVOS A CONFERIR]` | `[PRÓXIMO PASSO]` |
| Segurança | NÃO EXECUTADO | `[ESCOPO]` | `[PRÓXIMO PASSO]` |
| Acessibilidade | NÃO APLICÁVEL | `[JUSTIFICATIVA]` | — |

## Regra de conclusão
**Não dizer “100% pronto”** se verificações críticas falharam ou não foram executadas. Separar claramente implementação entregue, validação realizada e riscos restantes.

Referências para estudo: https://owasp.org/www-project-top-ten/ · https://www.w3.org/TR/WCAG22/ · https://docs.junit.org/current/user-guide/

## H. Política personalizada: rigor adaptativo, segurança não negociável
A fase de desenvolvimento ajuda a escolher **quais** verificações executar, mas o risco real determina o rigor final. Analisar: exposição pública, dados reais/sensíveis, autenticação, pagamentos, efeitos irreversíveis, dependências externas e alterações de schema.

| Cenário | Mínimo a verificar | Recomendável antes de divulgar |
|---|---|---|
| Exploração local + dados fictícios | Objetivo principal funciona; erros evidentes revisados; sem segredos | Checagem manual básica e anotação de limitações |
| Protótipo visual de demonstração | Navegação principal, mobile/desktop, estados de erro, reduced motion, sem segredos | Build e smoke test, checagem de teclado/contraste |
| MVP acessível ao público | Testes dos fluxos críticos e permissões, validação de entradas, build, logs seguros | CI, revisão de dependências e casos de falha |
| Produção **ou qualquer mudança de alto risco** | Testes proporcionais, integração/regressão onde relevante, segurança, observabilidade e recuperação | Monitoramento pós-release e plano de rollback |

**Bloqueadores independentemente da fase:** credenciais vazadas, acesso indevido a dados, autorização ausente, perda de dados previsível, ou afirmar validação inexistente. Se um item crítico não puder ser testado, sinalizar como **NÃO EXECUTADO** e descrever o risco; não autorizar publicação automaticamente.

## I. Revisão de design criativo
- [ ] Existe um conceito visual próprio, registrado em `DESIGN-GUIDELINES.md`?
- [ ] A referência inspirou um princípio, sem cópia literal de identidade, assets ou interface?
- [ ] Uma interação memorável tem propósito e não impede a tarefa do usuário?
- [ ] Animações têm alternativa reduzida; navegação segue acessível sem efeitos?
- [ ] Layout foi revisado nos principais tamanhos de tela aplicáveis?
- [ ] Elementos criativos têm custo de performance aceitável no contexto?

## J. Relatório rápido que ensina
Para toda entrega significativa, registrar:
- **Nível escolhido:** `[EXPLORAÇÃO/PROTÓTIPO/MVP/PRODUÇÃO]` + `[RISCO]` e justificativa.
- **O que foi feito:** `[CAMINHOS DE ARQUIVOS]`.
- **Por que assim:** `[DECISÃO E ALTERNATIVA RELEVANTE]`.
- **Arquitetura preservada:** `[EVIDÊNCIA REAL]`.
- **Conceito para aprender:** `[EXPLICAÇÃO CURTA LIGADA AO CÓDIGO]`.
- **Fonte consultada de fato:** `[LINK/SEÇÃO/VERSÃO]` ou `não consultada`.
- **Referência apenas sugerida:** `[LINK]`, explicitamente marcada.
- **Validação:** `[PASSOU/FALHOU/NÃO EXECUTADO/NÃO APLICÁVEL]`, comando e saída resumida.
- **Risco residual:** `[O QUE AINDA FALTA]`.

Nunca confundir checklist marcado manualmente com teste automatizado que não foi executado.
