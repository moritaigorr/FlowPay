# FlowPay - checkpoint de implementacao

Atualizado: 23/11/2026

Este arquivo registra onde retomar a jornada ativa do MVP.

## Objetivo

Implementar os estagios restantes do roadmap em incrementos pequenos, mantendo as invariantes de financas, tenancy, seguranca e UX. O MVP ainda nao esta concluido.

## Estado do roadmap

- Portao 9.3 implementado: revisao de linha (PENDING, REVIEWED, IGNORED), motivo, autor, evento append-only e idempotencia por tenant. Linhas ignoradas nao recebem sugestoes nem podem ser conciliadas ate reabrir.
- Desfazer vinculo mantem o movimento de caixa. Estornar o movimento e uma operacao separada. Ignorar/restaurar candidato nao altera caixa nem afeta outras linhas.
- Portao 9.4 parcial: resumo da importacao mostra periodo, fuso, `asOf`, saldo controlado da conta, totais de atividade do extrato/caixa e diferencas de movimentacao. O CSV atual nao informa saldos inicial/final, portanto a tela nao calcula diferenca entre saldos bancarios.
- Portao 10.2 parcial: perfil Spring `demo` adiciona seed sintetico, transacional e idempotente com identificadores estaveis. Exige `FLOWPAY_DEMO_PASSWORD`, sem senha fixa. O cabecalho mostra `Ambiente de demonstracao` para o tenant seed.
- Portao 10.3 parcial: salvar ou cancelar os formularios de contatos, contas, categorias, titulos, movimentos avulsos, baixas/reversoes e decisao de conciliacao retorna o foco a um titulo da secao. Baixa e reversao focam o campo de valor ao abrir. Typecheck, lint sem avisos e build passaram; comportamento ainda nao foi conferido no browser/leitor de tela.
- Seed implementa empresa, login `demo@flowpay.local`, dois contatos, conta com saldo inicial ficticio, categorias e titulos em aberto. Nao apaga registros nem cria baixas/importacoes. Protecao adicional: so executa com perfil `local` e URL PostgreSQL loopback para o banco `flowpay`.
- Pendencias do seed: executar em PostgreSQL limpo e confirmar reexecucao/estado resultante; validar isolamento por tenant. O reset descrito no README apaga o volume inteiro do Compose e so serve para dados descartaveis deste checkout.
- Portoes 10.1, 10.3 e 11 ainda nao foram aceitos. Ainda falta uma jornada E2E demonstrada, auditoria de acessibilidade, revisao de seguranca/operacao e validacao visual completa.

## Commits recentes

- `f015023` Document current FlowPay feature coverage
- `9b663ac` Add isolated deterministic demo data seed
- `b27abb1` Label the isolated demo workspace
- `26c7a90` Restrict demo seed to local database
- `a6fd931` Restore focus after contact form closes
- `5193171` Return focus after financial title form closes
- `95931d8` Return focus from primary workspace forms
- `a75b325` Restore focus across settlement and reconciliation
- Trabalho anterior: `1a0cc50`, `81c3f8f`, `5c6c9a5` e `6a2c90a`.

## Verificacoes e limites

- Backend: Maven 3.9.16 offline + JDK 25 compilou com `--release 21` apos adicionar o seed. O build de compilacao passou; os testes nao foram repetidos nesta etapa.
- Frontend: `npm.cmd run typecheck`, `npm.cmd run lint -- --deny-warnings` e `npm.cmd run build` passaram apos a gestao de foco dos fluxos principais.
- Comando `docker` nao encontrado no ambiente; os testes Testcontainers e migrations ainda precisam ser executados contra PostgreSQL. O PostgreSQL 16 local exige senha nao fornecida; o Compose fixa PostgreSQL 18.7.
- Nenhuma execucao real do perfil `demo` foi feita. A compilacao completa do backend passou apos forcar a recompilacao da classe alterada. Build nao comprova SQL, migrations, reexecucao idempotente ou login.
- A tela ainda nao foi revisada no browser em mobile/acessibilidade.

## Proximos passos

1. Quando Docker estiver disponivel, subir o banco Compose, ativar `local,demo` com senha fornecida pelo ambiente, confirmar login, dados conhecidos e segunda inicializacao idempotente.
2. Validar isolamento do tenant demo e executar migrations/testes de integracao.
3. Revisar no browser o rótulo demo e jornadas de dashboard/contatos/contas/titulos; executar verificacoes de teclado e mobile.
4. Retomar Portao 9.4 decidindo como representar saldo inicial/final do extrato; nao inferir esses valores das linhas CSV atuais.
5. Continuar Portoes 10 e 11 com uma jornada E2E completa, acessibilidade e seguranca/operacao proporcionais ao uso demo.

## Arquivos de referencia

- [Roadmap](FLOWPAY_IMPLEMENTATION_ROADMAP.md)
- [README e comandos locais](../README.md)
- [Instrucoes de desenvolvimento](../AGENTS.md)
