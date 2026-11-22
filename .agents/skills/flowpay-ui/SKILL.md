---
name: flowpay-ui
description: Use when implementing or reviewing FlowPay React screens, financial forms, dashboard components, responsive layouts, or interface accessibility.
---

# Construir interfaces FlowPay

Leia [AGENTS.md](../../../AGENTS.md) e as seções pertinentes do [Design System](../../../docs/FLOWPAY_DESIGN_SYSTEM.md). Use os tokens desse documento como referência única; não copie a paleta inteira para esta skill.

## Preparar a tela

Inspecione componentes, cliente HTTP e estrutura existentes. A organização sugerida separa `app`, `design-system`, `features` e `shared`; adapte ao código real. As [Master Project Instructions](../../../docs/FLOWPAY_MASTER_PROJECT_INSTRUCTIONS.md) definem React/TypeScript SPA com Vite, React Router, TanStack Query, React Hook Form, Zod, Tailwind CSS, shadcn/ui, Radix e TanStack Table. Use as ferramentas conforme a necessidade da entrega; stack escolhida não comprova dependências instaladas nem justifica reinstalação. Consulte `build-web-apps:react-best-practices` quando disponível e relevante.

Mapeie tarefa, dados necessários, permissões, contrato da API e estados de carregamento, vazio, erro, sucesso e submissão. Separe mocks identificados de integração verificada.

## Implementar

- Preserve Calm Finance: tema claro, azul-petróleo e superfícies neutras com tokens semânticos, Inter/fallback e números tabulares. Tokens ficam centralizados; componentes consomem aliases.
- Valores em pt-BR, alinhados à direita em tabelas, com sinal explícito. Datas de vencimento não devem deslocar por conversão indevida de timezone. Mantenha centavos exatos; entrada `1.234,56` representa `123456`, sem cálculo monetário em ponto flutuante. Respeite limites definidos no contrato da API.
- Rotule “Saldo controlado”, “Saldo do extrato” e sua data de referência separadamente. Distinga previsto/realizado. Use “Registrar pagamento realizado fora do FlowPay”, sem prometer transferência bancária.
- Labels visíveis, erros persistentes associados aos campos, foco visível e ordem de teclado previsível. Após falha, preserve valores e permita recuperação. Evite submissão repetida e use o contrato idempotente do servidor quando aplicável.
- Integre cookie/sessão e CSRF no cliente HTTP; tratar autorização só ocultando botões é insuficiente.
- TanStack Query deve separar dados por empresa em suas chaves; descarte cache empresarial em logout/troca de empresa. Essa separação complementa a autorização do servidor. Atualize consultas afetadas após confirmação da mutação.
- Timeout em mutação financeira deixa resultado possivelmente desconhecido: consulte o resultado ou repita com a mesma chave conforme contrato. Não habilite retry automático com chave nova nem apresente saldo otimista como confirmado. Bloquear o botão, sozinho, não garante idempotência.
- Em reversão, mostre valor, conta afetada e motivo obrigatório. Em conciliação, mostre evidências e decisão explícita. Sandbox exige identificação persistente.
- Desktop tem sidebar e tabelas; tablet usa drawer; mobile preserva valor, vencimento e status. Use os breakpoints documentados, sem esconder informação crítica.

## Exemplo de aceite

Tela de contas a receber mostra total/pendência/vencimento/status. Registrar `7000` de `10000` centavos apresenta “Parcial”, recebido R$ 70,00 e pendente R$ 30,00. Falha na API mantém o formulário; nova submissão não duplica o efeito financeiro.

## Validar

Verifique viewport desktop/mobile, teclado, foco de dialogs, mensagens de erro, zoom 200% e movimento reduzido conforme a mudança. Meça contraste nos pares reais de tokens: as cores propostas não são certificadas. Use Vitest/React Testing Library para comportamento de componentes e Playwright/axe quando disponíveis. Não declare WCAG AA apenas por utilizar componentes acessíveis. Relate o que foi renderizado/testado e o que ainda depende da API.
