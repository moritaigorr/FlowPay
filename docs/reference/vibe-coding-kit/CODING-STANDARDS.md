# CODING STANDARDS — Frontend e backend

> Aplicar **somente as seções relevantes à stack existente**. O estilo do repositório e `ARCHITECTURE.md` têm precedência sobre exemplos genéricos aqui. Não adicionar dependências por impulso.

## 1. Princípios transversais
- Mudanças pequenas e revisáveis; nomes claros; funções com responsabilidade definida.
- Simplicidade primeiro: evitar abstrações sem casos reais.
- Respeitar limites entre módulos/camadas e contratos externos.
- Erros devem ser tratados explicitamente; não engolir exceções nem vazar informações sensíveis.
- Documentar **o porquê** de decisões não óbvias, não comentários repetindo o código.
- Manter configuração externa ao código e nunca versionar segredos.
- Anexar evidência verificável de testes; nunca afirmar que executou algo não executado.

## 2. Java / Spring Boot
- Respeitar versão do Java e Spring Boot definida no projeto.
- Preferir injeção por construtor quando compatível com padrões existentes.
- Evitar regras de negócio em controllers e objetos de persistência **quando a arquitetura adotada exigir separação**.
- Definir contratos HTTP claros: validação, respostas, status e erros estruturados.
- Colocar limites transacionais onde o caso de uso requer consistência; justificar.
- Tratar N+1, paginação e consultas eficientes; medir antes de otimizar.
- Usar migrations versionadas (ex.: Flyway, se já adotado); não alterar esquema manualmente em produção.
- Integrações externas: timeouts, tratamento de falha, idempotência/retry quando apropriado, observabilidade e cuidado com duplicações.
- Segurança: autenticação ≠ autorização; validar permissões no servidor; não expor stack traces, tokens ou dados pessoais.
- Testes: unitários para regras; integração para persistência, segurança e contratos; Testcontainers ou alternativas conforme stack já presente.

## 3. TypeScript / React / Frontend
- Preferir tipos explícitos em contratos; evitar `any` sem motivo documentado.
- Separar apresentação, estado e acesso a dados **quando o projeto já estabelece esses limites**.
- Componentes com propósitos claros; reutilização racional; evitar componentes gigantes.
- Estados explícitos: loading, erro, vazio, sucesso, permissões.
- Acessibilidade: HTML semântico, labels, foco visível, navegação por teclado.
- Forms com validação adequada e mensagens úteis.
- Evitar efeitos colaterais durante renderização; limpar assinaturas/requests quando aplicável.
- Usar tokens de `DESIGN-SYSTEM.md` e preservar consistência entre telas.
- Não colocar segredos ou regras de autorização apenas no cliente.

## 4. Bancos de dados e APIs
- Migrações reversíveis quando viável; planejar mudanças destrutivas.
- Índices justificados por consultas reais; prever volumes e cardinalidade.
- Proteger dados sensíveis, aplicar menor privilégio e parametrizar queries.
- Definir paginação, filtros, ordenação e limites.
- Versionar contratos ou adotar estratégia explícita de compatibilidade.
- Em integrações, documentar semântica de falhas e consistência.

## 5. Git, CI/CD e documentação
- Commits pequenos e descritivos; PR com contexto, testes e riscos.
- Lint, build, typecheck e testes automatizados no CI quando houver infraestrutura.
- Documentar variáveis de ambiente necessárias **sem valores secretos**.
- README deve conter setup, comandos de execução e validação.
- Mudanças arquiteturais aceitas devem ir para `ARCHITECTURE.md` ou ADR.

## 6. Critérios para uma revisão de código
1. Atende ao requisito e critérios de aceitação?
2. Respeita a arquitetura e convenções reais?
3. Tem testes proporcionais aos riscos?
4. É seguro para dados e permissões?
5. É compreensível e fácil de manter?
6. A resposta da IA explica o motivo e fornece evidências/fonte?

## 7. Referências recomendadas (confirmar versões antes de aplicar)
- Java — https://dev.java/learn/
- Spring Boot — https://docs.spring.io/spring-boot/reference/
- Spring Security — https://docs.spring.io/spring-security/reference/
- React — https://react.dev/learn
- TypeScript — https://www.typescriptlang.org/docs/
- OWASP Cheat Sheet Series — https://cheatsheetseries.owasp.org/
- MDN — https://developer.mozilla.org/

Ao citar qualquer documento, distinguir **consultado agora** de **recomendado para leitura**.

## 8. Modo de trabalho personalizado — desenvolvimento com aprendizagem equilibrada
**Objetivo:** aprender a raciocinar como engenheiro enquanto entrega software. Não transformar cada alteração trivial em uma aula. Para decisões importantes (padrão, dependência, algoritmo, transação, segurança, modelo de dados ou contrato), apresentar:
1. **Decisão:** qual caminho foi usado e onde aparece no código.
2. **Motivo:** qual requisito atende e como se encaixa na arquitetura atual.
3. **Trade-off:** alternativa considerada e por que ficou de fora.
4. **Conceito:** explicação enxuta com um exemplo real do projeto.
5. **Fontes:** arquivos e símbolos reais; documentação oficial *consultada agora* separada de links *sugeridos para estudo*.

### Regras para preservar a arquitetura em qualquer stack
- Ler `ARCHITECTURE.md`, ADRs e código antes de editar; se não existirem, identificar padrões observáveis e apontar lacunas.
- Nenhuma migração silenciosa entre arquitetura em camadas, modular monolith, hexagonal, DDD ou Clean Architecture.
- Não impor `repository`, `service`, `use case`, `DTO`, `mapper`, `hook`, `context` ou outra abstração só porque apareceu em um exemplo.
- Se a tarefa exigir mudança estrutural, registrar *problema, opções, impacto, plano de transição* e solicitar aprovação, como exige `AGENTS.md`.

## 9. Qualidade adaptativa por fase **e risco**
| Contexto | Expectativa mínima | Maior atenção |
|---|---|---|
| Exploração local de baixo risco, com dados fictícios | Rodar caminho principal, revisar erros visíveis e manter código legível | Velocidade para validar hipótese |
| Protótipo demonstrável | Build e smoke tests pertinentes, estados básicos de UI, segredos protegidos | Não confundir demo com produto pronto |
| MVP com usuários ou dados reais | Testes de fluxo crítico, validação, autorização, proteção de dados, tratamento de falhas | Segurança não é opcional |
| Produção ou mudança de alto risco | Cobertura proporcional, testes de regressão/integração, análise de impactos, migração segura e plano de retorno quando aplicável | Evitar indisponibilidade e perda de dados |

**Salvaguardas em qualquer fase:** nunca expor segredos; não burlar autenticação/autorização; não executar migrações destrutivas sem aprovação; não alegar testes que não rodaram; não introduzir vulnerabilidades por conveniência.

## 10. Criatividade no frontend sem comprometer engenharia
- Usar `DESIGN-GUIDELINES.md` e `DESIGN-SYSTEM.md` para soluções visuais autorais e consistentes.
- Separar escolhas estéticas de comportamentos obrigatórios (formulário, navegação, estados de erro e acessibilidade).
- Animação e renderização 3D devem ter custo justificado; evitar dependências pesadas quando CSS ou elementos existentes resolvem.
- Não modificar contratos do backend apenas para facilitar uma animação ou componente sem avaliação e aprovação.

## 11. Padrão curto de conclusão
**Mudanças** (arquivos), **Por quê** (decisão e arquitetura), **Como funciona** (conceito principal), **Fontes** (consultadas x sugeridas), **Validações** (comandos e resultados) e **Pendências** (riscos e próximos passos). Detalhar mais apenas para decisões complexas ou quando o usuário pedir.
