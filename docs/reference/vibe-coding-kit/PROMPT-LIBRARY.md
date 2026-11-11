# PROMPT LIBRARY — Prompts reutilizáveis

> Copie e adapte os trechos. Primeiro forneça o repositório e os documentos relevantes. **Não presumir acesso a arquivos ou à internet.**

## 1. Início de qualquer projeto
> Leia `AGENTS.md`, `PRD.md` (ou `PRD-TEMPLATE.md` preenchido), `ARCHITECTURE.md` se existir, e os padrões aplicáveis. Inspecione a stack e a estrutura real. Antes de implementar, resuma arquitetura, convenções, lacunas e um plano incremental com critérios de aceitação. Atue também como meu mentor: explique por quê e cite evidências do repositório e documentação que efetivamente consultar.

## 2. Implementar uma funcionalidade respeitando a arquitetura
> Implemente `[FUNCIONALIDADE]` com critérios `[CRITÉRIOS]`. Identifique os componentes correspondentes na arquitetura existente e faça a menor mudança coerente. Não invente camadas nem troque bibliotecas sem aprovação. Apresente fluxo de execução, alternativas consideradas, testes feitos e fontes técnicas verificáveis.

## 3. Aprender um conceito com código real
> Quero aprender `[CONCEITO]` a partir do meu projeto. Localize um exemplo real, explique desde o fundamento, mostre o caminho de execução e onde o princípio aparece no código. Explique quando usar, quando evitar e me dê um exercício de 10–20 minutos. Não modifique o projeto nesta tarefa.

## 4. Revisão arquitetural sem refatorar
> Audite a implementação de `[MÓDULO]` em relação ao `ARCHITECTURE.md` e padrões existentes. Liste violações com caminho/símbolo, gravidade, impacto e opções de correção. Não altere código até minha aprovação. Diferencie evidência observada de hipótese.

## 5. Criar uma interface a partir de referências
> Use `DESIGN-GUIDELINES.md` e `DESIGN-SYSTEM.md`. Analise as referências `[URLS/IMAGENS]` como inspiração, não como código para copiar. Proponha uma direção visual própria com tipografia, cores, layout, componentes, motion e acessibilidade. Implemente primeiro `[SEÇÃO]`, responsiva e coerente com os componentes existentes. Explique cada escolha visual.

## 6. Diagnosticar bug
> Investigue `[SINTOMA + COMO REPRODUZIR]`. Primeiro forme hipóteses baseadas em código e logs. Mostre causa-raiz sustentada por evidência antes da correção. Faça a menor alteração compatível com arquitetura e inclua um teste de regressão. Não diga que testou algo que não executou.

## 7. Criar testes que ensinem
> Analise `[FUNCIONALIDADE]` e sugira casos felizes, casos de falha e bordas mais importantes. Explique o motivo de cada teste e a diferença entre unitário, integração e ponta a ponta aqui. Reutilize o framework de testes do projeto; execute somente os comandos disponíveis e reporte os resultados reais.

## 8. Revisar segurança
> Faça uma revisão de segurança de `[ESCOPO]` usando a arquitetura e o contexto real. Priorize autorização, validação, segredos, logs, dados pessoais e dependências relevantes. Apresente problemas com evidências e correções proporcionais; cite OWASP e docs oficiais **apenas se consultadas**. Não faça ações destrutivas.

## 9. Explicar uma decisão técnica
> Compare `[OPÇÃO A]` e `[OPÇÃO B]` para `[CONTEXTO]`. Avalie complexidade, manutenção, desempenho, testes e aderência à arquitetura existente. Recomende com justificativa, sinalize premissas e dê fontes confiáveis (se não conseguir consultar agora, marque como referência sugerida).

## 10. Refatorar com segurança
> Quero melhorar `[TRECHO]` sem mudar o comportamento público. Mostre o problema concreto, o plano de pequenas etapas, os riscos e os testes de proteção. Não mude a arquitetura ou contratos sem aprovação. Ao final, explique o que foi simplificado e por quê.

## 11. Documentar uma decisão arquitetural (ADR)
> Produza uma proposta de ADR para `[DECISÃO]` com contexto, forças, opções, trade-offs, consequências e status `PROPOSTA`. **Não marque como ACEITA** sem minha aprovação e não reorganize o projeto antes dessa decisão.

## 12. Revisão final antes do deploy
> Execute `QUALITY-CHECKLIST.md` para `[ESCOPO]`. Use status PASSOU/FALHOU/NÃO EXECUTADO/NÃO APLICÁVEL e associe evidências a cada item. Liste riscos bloqueadores e não bloqueadores, sem afirmar sucesso onde faltam testes.

## 13. Prompt de aprendizagem após cada mudança
> Agora seja meu mentor: explique em português simples, mas tecnicamente correto, o que acabamos de fazer, por que essa solução combina com nossa arquitetura, como o fluxo funciona, um trade-off, links de documentação oficial efetivamente consultada (ou referências sugeridas marcadas) e um pequeno desafio para eu praticar.

## Modelo universal de encerramento
> Responda sempre com: (1) o que mudou; (2) por que foi feito assim; (3) como funciona; (4) como respeita a arquitetura; (5) arquivos e evidências; (6) fontes consultadas vs. sugeridas; (7) testes realmente executados; (8) próximo conceito para estudar.

## 14. Gerar três propostas visuais criativas
> Quero um design autoral para `[PRODUTO]`. Leia `DESIGN-GUIDELINES.md`, `DESIGN-SYSTEM.md` e o PRD, além de observar a UI atual. Proponha três direções realmente distintas: editorial expressivo, tech interativo e imersiva (somente se fizer sentido). Para cada uma, informe tipografia, cores, grids, gesto memorável, inspiração (com links/prints disponíveis), adaptação original, riscos e custo. Recomende uma com justificativa. Não implemente mudanças amplas antes da minha escolha.

## 15. Melhorar uma interface sem deixá-la genérica
> Analise `[PÁGINA OU COMPONENTE]` e identifique cinco melhorias prioritárias de hierarquia, composição, personalidade visual e microinterações. Diferencie gosto subjetivo de problemas observáveis. Não copiar a identidade de terceiros. Faça primeiro uma pequena melhoria compatível com o design system; explique por que a escolha ajuda e como validou desktop/mobile.

## 16. Aprender sem interromper demais
> Implemente `[TAREFA]` normalmente, seguindo `AGENTS.md`. Depois, explique somente as duas ou três decisões técnicas mais importantes em linguagem acessível: o que mudou, por que, como funciona, uma alternativa e uma fonte verificável. Se precisar de mudança arquitetural, pare e peça aprovação antes.

## 17. Escolher testes pelo risco real
> Antes de implementar `[TAREFA]`, classifique fase `[EXPLORAÇÃO/PROTÓTIPO/MVP/PRODUÇÃO]` e risco `[BAIXO/MÉDIO/ALTO]`. Identifique dados, usuários, autenticação e efeitos irreversíveis envolvidos. Proponha testes proporcionais segundo `QUALITY-CHECKLIST.md`, mantendo segurança essencial em todas as fases. Depois execute os possíveis e registre os não executados.

## 18. Revisar código como um mentor sênior
> Revise `[ARQUIVOS/MÓDULO]` procurando bugs, riscos, violações comprováveis de arquitetura e complexidade desnecessária. Priorize achados por impacto, aponte caminhos/símbolos, mostre solução mínima e ensine o conceito envolvido. Separe fatos, inferências e referências sugeridas. Não reescreva tudo nem mude arquitetura sem aprovação.

## 19. Investigar fonte e versão de uma solução
> Para a decisão `[DECISÃO]`, identifique a versão efetivamente usada de `[BIBLIOTECA/FRAMEWORK]`. Se houver acesso à documentação, consulte a seção oficial relevante e cite URL, versão e regra aplicada. Se não houver, assuma explicitamente que não verificou agora e ofereça links como leitura sugerida. Explique por que a decisão serve a este repositório.

## 20. Construir portfólio técnico e criativo
> Quero transformar `[PROJETO JAVA/SPRING]` em um case visual atraente. Respeite a arquitetura real. Proponha uma apresentação com problema, contexto, decisão arquitetural, fluxo da API, trade-offs, testes e resultado demonstrável. Use motion criativo, porém acessível e leve. Não invente métricas, usuários nem resultados. Cite os arquivos do projeto que comprovam cada informação.

## 21. Preparar release com rigor adaptativo
> Aplique `QUALITY-CHECKLIST.md` a `[FEATURE/RELEASE]`. Classifique risco pela exposição, dados e impacto, não apenas pelo nome da fase. Execute as verificações disponíveis, reporte resultados e pendências, e recomende liberar ou bloquear com justificativa. Não declarar 'pronto para produção' sem evidência suficiente.

## 22. Abrir uma sessão de vibe coding com contexto mínimo
> Leia `AGENTS.md` primeiro, depois `PRD.md`, `ARCHITECTURE.md` (se houver) e os documentos relevantes. Resuma em até oito linhas o objetivo, os limites arquiteturais, a direção visual, os riscos e o próximo passo pequeno. Implemente quando o escopo estiver claro. Ao finalizar, explique as decisões importantes de forma equilibrada, cite fontes reais e mostre testes executados.
