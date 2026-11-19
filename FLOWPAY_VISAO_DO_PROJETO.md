# FlowPay — Visão Geral do Projeto

**Documento de apresentação do produto**  
**Versão:** 1.0 · **Data:** 18 de novembro de 2026  
**Tipo:** projeto pessoal de portfólio, em desenvolvimento

## 1. O que é o FlowPay?

O **FlowPay** é uma plataforma web de **gestão financeira para pequenas empresas prestadoras de serviços**. Seu objetivo é ajudar negócios como agências de marketing, consultorias, freelancers e empresas de tecnologia a organizar suas finanças em um só lugar.

Por meio do FlowPay, uma empresa poderá registrar receitas e despesas, acompanhar contas a pagar e receber, cadastrar clientes e fornecedores, consultar o fluxo de caixa, importar extratos bancários e conferir movimentações financeiras.

Além das funcionalidades de gestão, o projeto terá um **ambiente separado de simulação de pagamentos**, usado para explorar problemas técnicos complexos como processamento de transações, idempotência, concorrência, registros contábeis e recuperação de falhas. **O FlowPay não movimentará dinheiro real nem se conectará diretamente a bancos na versão inicial.**

Em resumo, o FlowPay será um sistema que ajuda pequenas empresas a responder quatro perguntas:

1. **Quanto dinheiro entrou e saiu?**
2. **Quanto ainda tenho a receber dos clientes?**
3. **Quais despesas preciso pagar e quando vencem?**
4. **Como estará o fluxo de caixa nos próximos dias ou meses?**

## 2. Por que o projeto existe?

Pequenas empresas frequentemente precisam acompanhar receitas, despesas, clientes, fornecedores e vencimentos usando planilhas e ferramentas separadas. Isso pode dificultar a organização, provocar lançamentos duplicados e tornar confusa a diferença entre valores previstos e valores efetivamente recebidos.

O FlowPay pretende centralizar essas tarefas em uma interface clara, com histórico de alterações e mecanismos para conferir os registros financeiros.

O projeto também tem um segundo objetivo: ser um **portfólio de engenharia de software** que demonstre conhecimento de Java, Spring Boot, React, PostgreSQL, arquitetura modular, segurança, testes e sistemas financeiros confiáveis.

## 3. Para quem é o FlowPay?

O público inicial são **pequenas empresas prestadoras de serviços**, especialmente:

- Agências de marketing, design e publicidade.
- Consultorias e empresas de assessoria.
- Freelancers e profissionais autônomos.
- Pequenas software houses e empresas de tecnologia.
- Negócios que trabalham com contratos, projetos e serviços recorrentes.

Dentro dessas empresas, o sistema será usado por proprietários, gestores financeiros e funcionários autorizados.

O foco em prestadoras de serviços permite criar um produto completo sem precisar desenvolver, logo no início, recursos de estoque, logística ou gestão de produtos físicos.

## 4. O que a empresa poderá fazer?

### 4.1 Organizar clientes e fornecedores

Cadastrar pessoas e empresas com quem o negócio trabalha. Cada cadastro poderá ser relacionado a cobranças, despesas e histórico financeiro.

**Exemplo:** uma agência cadastra um cliente chamado Acme Tecnologia e consegue consultar todas as contas a receber desse cliente.

### 4.2 Controlar contas a receber

Registrar valores que clientes devem pagar, com descrição, vencimento, situação e histórico de recebimentos. O sistema mostrará se o valor está em aberto, parcialmente recebido, pago ou atrasado.

**Exemplo:** uma consultoria registra R$ 2.000 a receber em 15/12 e acompanha quando o cliente realiza o pagamento.

### 4.3 Controlar contas a pagar

Cadastrar despesas, fornecedores, categorias e datas de vencimento, além de registrar quando uma conta foi paga.

**Exemplo:** uma empresa acompanha aluguel, internet, softwares e prestadores terceirizados.

### 4.4 Registrar movimentações financeiras

Manter um histórico de entradas e saídas associadas a contas financeiras cadastradas para controle. O sistema calcula o **saldo registrado** com base no saldo inicial e nos lançamentos reconhecidos.

Esse saldo não será apresentado como se fosse uma consulta bancária em tempo real: ele depende dos dados inseridos ou conferidos pelo usuário.

### 4.5 Importar extratos bancários

Importar arquivos **CSV** e, em uma fase posterior, **OFX**, exportados pelo próprio usuário a partir do banco.

O FlowPay verificará o formato, apresentará uma prévia e ajudará a identificar registros repetidos ou inválidos. **Importar um arquivo não criará entradas e saídas automaticamente no caixa.**

### 4.6 Conciliar as movimentações

Comparar linhas do extrato importado com os lançamentos existentes no FlowPay. Quando uma entrada já tiver sido registrada manualmente, o sistema deverá permitir vinculá-la ao extrato sem contar o dinheiro duas vezes.

**Exemplo:** o gestor registrou um recebimento de R$ 500 na segunda-feira; ao importar o extrato, vincula a linha correspondente ao registro existente.

### 4.7 Visualizar o dashboard e relatórios

Consultar indicadores como saldo controlado, valores a receber, despesas a pagar, contas vencidas, próximos vencimentos e fluxo de caixa.

O FlowPay distinguirá:

- **Realizado:** dinheiro registrado como efetivamente recebido ou pago.
- **Previsto:** valores esperados conforme vencimentos de contas e cobranças.

### 4.8 Gerenciar usuários e permissões

Permitir que uma empresa tenha vários usuários, com diferentes níveis de acesso. Um funcionário poderá apenas consultar informações, enquanto outro poderá cadastrar despesas e registrar pagamentos.

Os dados de uma empresa não poderão ser visualizados por outra.

## 5. Funcionalidades que virão depois

Após o lançamento da primeira versão utilizável, o FlowPay poderá ganhar:

| Funcionalidade | O que acrescenta ao produto |
|---|---|
| Faturas | Criar cobranças formais com itens, valores e vencimentos. |
| Catálogo de serviços | Cadastrar os serviços oferecidos pela empresa. |
| Contratos | Relacionar clientes, serviços, valores e períodos de contratação. |
| Assinaturas | Gerar cobranças recorrentes, como mensalidades. |
| Notificações | Lembrar vencimentos e avisar sobre eventos importantes. |
| Importação OFX | Aceitar outro formato comum de extrato. |
| Relatórios avançados | Analisar resultados por período, cliente e categoria. |
| API de integração | Permitir que outras aplicações interajam com o FlowPay. |

O objetivo é adicionar recursos de forma progressiva, mantendo cada versão funcional.

## 6. O laboratório de pagamentos simulados

Uma área separada do FlowPay será dedicada à **infraestrutura financeira simulada**.

Ela permitirá que desenvolvedores testem pagamentos fictícios e investiguem situações que ocorrem em sistemas reais, como:

- Uma requisição de pagamento enviada duas vezes.
- Duas operações tentando alterar o mesmo pagamento ao mesmo tempo.
- Um provedor de pagamentos que demora ou deixa de responder.
- Um estorno realizado parcialmente.
- Um registro contábil que precisa manter débitos e créditos equilibrados.
- Divergências entre transações internas e extratos de um provedor simulado.

Essa área existirá para demonstrar engenharia de software avançada. **Pagamentos fictícios não poderão quitar contas a receber reais nem alterar o saldo financeiro controlado de uma empresa.**

## 7. Exemplo completo de uso

Imagine uma pequena agência de design que atende dez clientes.

1. O proprietário cria uma conta da empresa no FlowPay.
2. Cadastra seus clientes e fornecedores.
3. Registra R$ 3.000 a receber por um projeto entregue.
4. Cadastra R$ 800 de despesas de ferramentas e serviços.
5. Consulta o dashboard para visualizar o que precisa receber e pagar.
6. Quando o cliente paga, registra o recebimento de R$ 3.000.
7. Exporta um CSV do banco e importa no FlowPay.
8. Localiza a entrada de R$ 3.000 no extrato e a vincula ao recebimento já registrado.
9. Confere as despesas e movimentações do mês.
10. Gera um relatório para entender o fluxo de caixa.

Todo esse fluxo deverá ser possível pela interface web, sem que a pessoa precise conhecer programação ou executar requisições de API manualmente.

## 8. Como o FlowPay será construído?

O sistema terá duas partes principais:

- **Frontend:** a interface web, desenvolvida em React e TypeScript, por onde o usuário realiza suas atividades.
- **Backend:** uma API Java com Spring Boot, responsável pelas regras de negócio, segurança e persistência das informações.

O banco de dados será o **PostgreSQL**. A organização inicial seguirá uma arquitetura de **monólito modular**, na qual cada área do negócio tem responsabilidades bem definidas, mas funciona dentro de uma única aplicação backend.

Outras ferramentas planejadas incluem Flyway para evolução do banco, Spring Security para autenticação e autorização, JUnit/Testcontainers para testes, Docker para o ambiente local e GitHub Actions para automação.

## 9. O que diferencia o projeto?

O FlowPay pretende combinar duas características:

**1. Utilidade prática.** Uma empresa consegue executar tarefas financeiras reais de acompanhamento e organização em uma interface acessível.

**2. Profundidade técnica.** Por trás das telas, o sistema lida com autenticação, isolamento entre empresas, consistência monetária, concorrência, importações repetidas, auditoria, processamento assíncrono e testes automatizados.

O projeto não será considerado avançado apenas por utilizar muitas tecnologias. O diferencial será demonstrar decisões arquiteturais justificadas e comportamento correto diante de situações difíceis.

## 10. O que não é o FlowPay?

- Não é um banco digital.
- Não transfere ou guarda dinheiro de clientes.
- Não executa pagamentos bancários reais.
- Não substitui um contador ou um sistema fiscal.
- Não emite notas fiscais na versão inicial.
- Não é um ERP completo desde o primeiro lançamento.

A intenção é evoluir para uma plataforma empresarial mais ampla **somente depois** de o núcleo de gestão financeira estar sólido.

## 11. Etapas de desenvolvimento

| Fase | Resultado esperado |
|---|---|
| Fundação | Login, empresas, usuários e cadastros básicos. |
| Gestão financeira | Contas a pagar/receber, movimentações e dashboard. |
| Extratos e conciliação | Importar CSV e conferir dados sem duplicar caixa. |
| Primeira versão utilizável | Relatórios, testes, documentação e jornada demonstrável. |
| Expansão de gestão | Faturas, contratos, assinaturas e automações. |
| Infraestrutura avançada | Pagamentos simulados, ledger e reconciliação de PSP. |

**Estado atual:** a visão de produto, o PRD, o planejamento técnico, o System Design e o Design System estão documentados. A estrutura inicial do código foi criada, mas a aplicação completa **ainda não está pronta para uso**. As funcionalidades descritas aqui são o escopo planejado, não capacidades já entregues.

## 12. Resultado esperado para o portfólio

Ao final, o FlowPay deverá ter um repositório público com:

- Uma aplicação web funcional e demonstrável.
- Documentação clara de uso e instalação.
- API documentada com OpenAPI.
- Diagramas arquiteturais e decisões técnicas.
- Testes automatizados e integração contínua.
- Cenários de falha e evidências de confiabilidade.
- Dados fictícios para demonstração sem exposição de dados sensíveis.

### Descrição curta do projeto

> **FlowPay é uma plataforma SaaS de gestão financeira para pequenas empresas prestadoras de serviços, criada para centralizar contas a pagar e receber, movimentações, fluxo de caixa, importação de extratos e conciliação bancária. O projeto também inclui uma infraestrutura de pagamentos simulados para explorar desafios avançados de engenharia de software.**

### Descrição para GitHub (em inglês)

> FlowPay is a multi-tenant financial management SaaS for small service businesses. It helps teams track receivables, payables, cash flow, and bank statement reconciliation, while featuring a separate simulated payment infrastructure to demonstrate reliable backend engineering with Java, Spring Boot, PostgreSQL, React, and TypeScript.
