---
name: flowpay-feature
description: Use when planning or scoping a FlowPay feature, an MVP increment, a module contract, or an architectural change.
---

# Recortar uma entrega FlowPay

Leia [AGENTS.md](../../../AGENTS.md), as [Master Project Instructions](../../../docs/FLOWPAY_MASTER_PROJECT_INSTRUCTIONS.md), a [visão](../../../docs/FLOWPAY_VISAO_DO_PROJETO.md) e as seções de arquitetura ou design afetadas. O resultado é uma entrega vertical verificável, com escopo proporcional ao pedido.

## Procedimento

1. Identifique o que já existe no checkout e o resultado que o usuário precisa. Se só houver documentos, trate bootstrap como trabalho novo; não presuma aplicação funcional.
   Java 21/Maven e a stack React/Vite já foram escolhidos pelo responsável. Defina apenas versões compatíveis e escolhas ainda abertas; não reabra essas decisões como se fossem propostas indefinidas.
2. Posicione o pedido na sequência fundação, financeiro, CSV/conciliação e primeira versão utilizável. Recursos posteriores entram quando solicitados; não os acrescente como dependência artificial.
3. Mapeie módulos, interfaces públicas e dono de cada dado. Para uma operação financeira, descreva quem altera caixa, limite transacional, tenant, autorização e efeito de repetição/falha.
4. Defina o menor contrato suficiente: campos, centavos/moeda, datas, estados, erros e efeitos persistidos. Identifique escolhas ainda não fixadas pelos documentos, como códigos HTTP, papéis exatos e resposta a chave idempotente reutilizada com outro payload.
5. Estabeleça critérios observáveis na interface, API e persistência conforme o recorte. Escolha validações capazes de detectar os riscos da mudança. Para entregas maiores, detalhe tarefas pequenas com implementação, testes, revisão, documentação e verificação final da jornada; use TDD quando viável.

## Formato de saída

- **Entrega:** problema, ator e comportamento visível.
- **Escopo:** o que será implementado e dependências existentes/necessárias.
- **Contrato:** módulos, entradas/saídas, transação e regras afetadas.
- **Aceite:** exemplos com estado inicial, ação e resultado.
- **Validação:** comandos existentes ou infraestrutura que ainda precisa ser criada.
- **Decisões:** requisitos com fonte, escolhas novas e dúvidas impeditivas.

Dimensione essa saída ao pedido: não crie um plano extenso para uma alteração pequena. Para mudança estrutural, compare alternativas, limites de consistência e custo de operação. Proponha ADR quando a decisão for duradoura. Se o contrato mudar após começar a implementação, atualize os consumidores e critérios de aceite antes de declarar a entrega integrada.

Exemplo: “Registrar recebimento parcial de R$ 70 em título de R$ 100” afeta `finance`, gera uma entrada de `7000` centavos, baixa de `7000` e pendência de `3000`. Receber de verdade fora do FlowPay é pré-condição; integração bancária não faz parte dessa entrega.

## Cuidados

O PRD v3.0 citado no System Design não estava no checkout inicial. Use as fontes disponíveis e sinalize a lacuna. Não converta uma decisão nova em requisito supostamente extraído desse PRD. Se dividir trabalho entre agentes estiver autorizado, atribua arquivos e contratos antes de iniciar tarefas paralelas.
