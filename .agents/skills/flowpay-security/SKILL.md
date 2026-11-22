---
name: flowpay-security
description: Use when designing or reviewing FlowPay authentication, tenant authorization, sessions, CSRF, uploads, privacy, secrets, or application security controls.
---

# Revisar segurança no FlowPay

Leia [AGENTS.md](../../../AGENTS.md) e as seções relevantes de identidade, autorização, uploads e operação. Use [OWASP ASVS](https://github.com/OWASP/ASVS) como catálogo de verificação, selecionando requisitos aplicáveis ao escopo; uma revisão parcial não representa conformidade completa.

## Limites de confiança

- Identidade autenticada e membership autorizam o tenant. Derive contexto empresarial no servidor; não use header, URL ou body como prova de acesso.
- Proteja leitura, escrita, contagem, exportação, downloads e jobs pelo mesmo limite de tenant. Revise também relações por FK composta.
- O modelo FlowPay usa sessão/cookie com Spring Security e CSRF integrado à SPA. Não substitua por JWT ou bearer token sem decisão explícita do responsável.
- Autorização por papel deve ser aplicada no servidor. A matriz exata de papéis e ações é decisão a definir na entrega pertinente.

## Revisar por superfície

- **Sessões:** hash de senha gerenciado, cookies HttpOnly/Secure/SameSite conforme ambiente, logout/invalidação e respostas que evitem enumeração de contas.
- **CSRF/CORS:** mutações da SPA exigem proteção CSRF; valide origins e métodos conforme arquitetura real. CORS não substitui autorização.
- **IDOR/tenancy:** tente acessar e alterar recursos de outro tenant com sessão e CSRF válidos; rejeição por falta de autenticação não prova isolamento.
- **Entradas e SQL:** validação no limite, consultas parametrizadas e limites contra abuso. Não confie em nomes de arquivo, MIME type declarado ou campos ocultos.
- **Uploads:** armazenamento privado, tamanho/formato limitados, parsing resiliente, nome gerado pelo servidor, autorização no download e política de retenção definida antes de dados reais.
- **Privacidade e segredos:** não grave extratos, PII, senhas, tokens ou credenciais em logs/dados demo. Segredos ficam fora do código e dos fixtures.
- **Financeiro:** autorização, tenant, idempotência, concorrência e auditoria são parte do controle de segurança; preserve reversões e fatos confirmados.

## Evidência e comunicação

Para cada achado, informe severidade, arquivo/linha, pré-condição, gatilho, impacto e evidência. Diferencie reprodução de hipótese. Corrija apenas se o pedido autorizar implementação; depois valide o controle e regressões relacionadas.

Registre controles fora de escopo e decisões abertas sem ampliar a tarefa. Não declare “seguro”, “conforme ASVS” ou “auditado” com base em leitura superficial ou somente testes unitários.
