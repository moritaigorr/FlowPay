---
name: flowpay-reconciliation
description: Use when implementing or debugging FlowPay CSV statement imports, duplicate detection, reconciliation suggestions, confirmed matches, or match reversals.
---

# Importar e conciliar sem duplicar caixa

Leia [AGENTS.md](../../../AGENTS.md), [System Design, seções 6–9](../../../docs/FLOWPAY_SYSTEM_DESIGN.md) e [Design System, seções 6.3–7](../../../docs/FLOWPAY_DESIGN_SYSTEM.md).

## Importação

Siga conta → arquivo → mapeamento CSV → prévia/erros → confirmação → revisão de duplicatas. Autorize acesso à conta pelo tenant. Valide tamanho, linhas, encoding, datas e valores; reporte erros com número da linha e correção possível. Não adivinhe silenciosamente datas ambíguas. Os limites de 10 MB/50 mil linhas são hipóteses iniciais do desenho, não capacidade medida.

Armazene o original em área privada, com acesso e download autorizados/auditados. Trate o conteúdo como entrada não confiável; proteja também exportações contra fórmula CSV. Retenção de 90 dias é proposta, com política final ainda a definir.

Deduplicate o mesmo arquivo por `(tenant_id, financial_account_id, file_checksum)`, inclusive sob confirmações simultâneas. Arquivos distintos podem conter operações iguais: checksum não substitui deduplicação de linhas. Defina critérios de candidatos e preserve revisão de operações legítimas com mesma data/valor; não descarte apenas por semelhança. Salvar `StatementImport`/`StatementLine` não cria baixa nem altera caixa.

Se houver processamento assíncrono, use estado persistido e retries idempotentes; uma execução repetida não pode duplicar linhas ou efeitos. Jobs grandes e OFX pertencem à evolução, salvo pedido.

## Conciliação

- Sugira movimentos com evidências de valor, data e descrição; confiança é indicação. Correspondências ambíguas exigem decisão humana.
- Confirme atomicamente tenant, conta, elegibilidade e unicidade do match ativo da linha. Em um caminho, vincule a `CashTransaction` existente. No outro, crie movimento e vínculo conforme contrato; nunca execute os dois caminhos para o mesmo reconhecimento de caixa.
- Revalide no servidor valor, direção, moeda e estado dos candidatos no momento da confirmação. Sugestão exibida pode estar desatualizada; conflito preserva a linha e permite atualizar os candidatos. O contrato deve definir a elegibilidade de movimentos já vinculados, sem inferi-la apenas pelo índice único da linha.
- MVP: uma linha representa um movimento completo. Não acrescente match parcial ou múltiplas linhas por movimento sem definir um novo contrato de valores.
- Preserve evidência original e histórico de reversões. Desfazer vínculo e estornar movimento têm efeitos distintos; explicite o efeito escolhido, sem apagar automaticamente uma baixa manual prévia.

## Cenário de aceite

Recebimento manual de `50000` centavos já confirmado → importar CSV → vincular linha → reimportar arquivo. O saldo controlado permanece igual ao saldo após a baixa manual, sem segunda movimentação/baixa, e existe apenas um match ativo para a linha. Confira também confirmações concorrentes, arquivo diferente com operação semelhante, erro de parsing, rollback e tenant alheio.

Na UI, diferencie saldo controlado, saldo do extrato e diferença com data de referência. Antes de confirmar, explique que importar salva evidências e não altera o caixa. Relate testes executados e lacunas do algoritmo/contrato.
