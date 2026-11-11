# PRD — Template de requisitos de produto

> Copie este arquivo para `PRD.md` em cada novo projeto, preenchendo os campos. Marque **A DEFINIR** onde não houver decisão. A IA não deve transformar suposições em requisitos.

## 1. Visão geral
- **Produto:** `[NOME]`
- **Problema:** `[DOR REAL]`
- **Público-alvo:** `[QUEM USA]`
- **Proposta de valor:** `[O QUE MELHORA E POR QUÊ]`
- **Responsável / data / versão:** `[PREENCHER]`

## 2. Objetivos e não objetivos
**Objetivos:**
1. `[OBJETIVO MENSURÁVEL]`
2. `[OBJETIVO]`

**Fora de escopo agora:**
- `[O QUE NÃO VAMOS FAZER]`

## 3. Personas e jornadas
- Persona: `[QUEM, CONTEXTO, PROBLEMA]`
- Jornada principal: `[ENTRADA → AÇÃO → RESULTADO]`
- Pontos de falha: `[ERROS, REDE, PERMISSÃO, DADOS VAZIOS]`

## 4. Requisitos funcionais
| ID | História / comportamento esperado | Prioridade | Critério de aceitação |
|---|---|---|---|
| RF-01 | Como `[USUÁRIO]`, quero `[AÇÃO]` para `[BENEFÍCIO]` | Must | Dado `[CONDIÇÃO]`, quando `[AÇÃO]`, então `[RESULTADO]` |
| RF-02 | `[DESCREVER]` | Should | `[DESCREVER]` |

## 5. Requisitos não funcionais
| Categoria | Requisito mensurável | Como verificar |
|---|---|---|
| Segurança | `[AUTENTICAÇÃO, PRIVACIDADE, PERMISSÕES]` | `[TESTE]` |
| Desempenho | `[METAS E CONDIÇÕES]` | `[MÉTODO]` |
| Acessibilidade | `[PADRÃO E FLUXOS]` | `[AUDITORIA]` |
| Confiabilidade | `[ERROS, RETENTATIVAS, DISPONIBILIDADE]` | `[TESTE]` |
| Observabilidade | `[LOGS, MÉTRICAS, ALERTAS]` | `[VERIFICAÇÃO]` |

## 6. Dados e integrações
- Entidades e relações: `[PREENCHER]`
- Origem/destino de dados: `[PREENCHER]`
- APIs externas e restrições: `[PREENCHER]`
- Política de dados pessoais/retenção: `[PREENCHER]`
- Migrações e versionamento: `[PREENCHER]`

## 7. Arquitetura — **decisão explícita**
- Arquitetura adotada: `[A DEFINIR / REFERENCIAR ARCHITECTURE.md]`
- Motivo da escolha: `[PREENCHER]`
- Limites de módulo/camada: `[PREENCHER]`
- Tecnologias e versões: `[PREENCHER]`
- Decisões arquiteturais registradas (ADR): `[LINKS]`
- **Qualquer mudança exige justificativa e aprovação antes de implementação.**

## 8. Interface e experiência
- Fluxos / wireframes: `[LINKS]`
- Referências visuais: `[LINKS]`
- Tokens/componentes: `DESIGN-SYSTEM.md`
- Acessibilidade, mobile e estados de erro: `[REQUISITOS]`

## 9. Plano de entrega
| Etapa | Entrega demonstrável | Dependências | Testes |
|---|---|---|---|
| 1 | `[MVP MÍNIMO]` | `[DEP]` | `[TESTE]` |
| 2 | `[INCREMENTO]` | `[DEP]` | `[TESTE]` |

## 10. Métricas de sucesso
- Métrica principal: `[DEFINIÇÃO E META]`
- Métricas de qualidade: `[ERRO, TEMPO, SATISFAÇÃO ETC.]`
- Instrumentação: `[COMO MEDIR SEM COLETAR DADOS DESNECESSÁRIOS]`

## 11. Riscos, dúvidas e decisões abertas
| Questão | Impacto | Responsável | Estado |
|---|---|---|---|
| `[DÚVIDA]` | `[BAIXO/MÉDIO/ALTO]` | `[PESSOA]` | Aberto |

## 12. Definition of Done
- [ ] Critérios de aceitação atendidos
- [ ] Arquitetura respeitada
- [ ] Testes e validações executados e documentados
- [ ] Segurança e acessibilidade revisadas
- [ ] Documentação e instruções de execução atualizadas
- [ ] Decisões explicadas com evidências e referências verificáveis

## Prompt de início
> Leia este PRD, AGENTS.md e a arquitetura existente. Liste lacunas e premissas, proponha etapas pequenas com critérios de aceite e explique o porquê de cada etapa. Não escreva código até estabelecer um plano compatível com as decisões já tomadas.

## 13. Perfil do processo (personalizado)
- **Estilo de aprendizado:** equilibrado. A IA deve implementar e explicar decisões relevantes, sem interromper cada pequena alteração.
- **Estilo visual:** criativo. Propor caminhos visuais distintos para páginas novas, e justificar a escolha.
- **Qualidade:** adaptativa. Classificar o projeto e o risco por entrega, sem abandonar salvaguardas essenciais.

### Classificação inicial obrigatória
- Fase do projeto: `[EXPLORAÇÃO / PROTÓTIPO / MVP / PRODUÇÃO]`
- Exposição: `[LOCAL / DEMONSTRAÇÃO / PÚBLICO / USUÁRIOS REAIS]`
- Dados tratados: `[SINTÉTICOS / PÚBLICOS / PESSOAIS / FINANCEIROS / OUTROS SENSÍVEIS]`
- Risco desta entrega: `[BAIXO / MÉDIO / ALTO]` e justificativa `[MOTIVO]`
- Verificações exigidas: `[REFERENCIAR QUALITY-CHECKLIST.md]`
- **Atenção:** protótipo com dados reais, pagamento, autenticação ou exposição pública pode ser de **alto risco**; a etiqueta “protótipo” não reduz requisitos de segurança.

## 14. Descoberta orientada e design
**Antes do código**, a IA deve consolidar um briefing curto com o usuário e registrar decisões:
- Problema específico a resolver e principal fluxo do usuário.
- Sucesso observável e o que está fora de escopo.
- Arquitetura já existente ou proposta para aprovação; não assumir que `Clean Architecture` é obrigatória.
- Três direções visuais quando o design for uma decisão central, incluindo uma recomendação fundamentada.
- Hipóteses, incertezas e dependências explicitamente marcadas.
- Plano por incrementos demonstráveis, com aceite e nível de validação proporcional ao risco.

## 15. Aprendizado integrado às entregas
Para cada incremento relevante, entregar junto do resultado:
1. **Decisão-chave** — que problema solucionou e por que esta abordagem.
2. **Arquitetura** — quais limites existentes foram respeitados.
3. **Conceito aprendido** — uma explicação breve ligada ao código real; exercício apenas se útil.
4. **Fontes** — caminhos do projeto e documentação efetivamente consultada, separando referências sugeridas.
5. **Validação** — comandos, resultados reais e pendências, conforme fase/risco.

### Exemplo de planejamento por fase
| Fase | Objetivo | Mínimo de qualidade | Não confundir com |
|---|---|---|---|
| Exploração local | Validar ideia/fluxo com dados fictícios | Requisitos críticos, funcionamento principal e transparência sobre lacunas | Pronto para publicar |
| Protótipo demonstrável | Mostrar experiência navegável | Smoke test, responsividade essencial, sem segredos expostos | Produto seguro para dados reais |
| MVP público | Validar uso real | Testes dos fluxos críticos, revisão de segurança, observabilidade básica | Dispensa de controles por ser MVP |
| Produção | Operar com confiança | Regressão, testes adequados ao risco, acessibilidade, segurança, monitoramento e rollback quando aplicável | Garantia absoluta de ausência de bugs |
