# Proposta Inicial de Pesquisa para Discussão

> **Finalidade:** validar e delimitar um problema de pesquisa candidato relacionado aos objetivos documentados do ECO-CHAINS antes de estabelecer sua contribuição científica.

## 1. Limite de Evidência

Este rascunho utiliza a proposta ECO-CHAINS e as fontes de segurança citadas para formular e delimitar o problema de pesquisa. Essas fontes não demonstram, por si sós, que a lacuna proposta exista ou seja original.

## 2. Ponto de Partida Documental

A proposta ECO-CHAINS inclui segurança entre os aspectos de software eco-responsável. Ela descreve uma Software Supply Chain como um conjunto de processos e fluxos que envolve fornecedores, componentes e canais de entrega, e afirma que decisões tomadas nessa cadeia podem afetar a segurança do software. Também registra que as informações relevantes podem estar dispersas, em formatos heterogêneos ou inacessíveis (ECO-CHAINS, Section 1.1, PDF pp. 1–2).

A proposta também afirma que artefatos externos incorporam decisões de seus fornecedores e que sua inclusão pode afetar a segurança do software. Entre seus objetivos estão a modelagem e a detecção de ameaças de segurança e a análise de decisões de desenvolvimento (ECO-CHAINS, Section 1.1, PDF p. 2; Section 1.4, PDF p. 5).

## 3. Tema Provisório

**Atribuição de caminhos relevantes para segurança entre código próprio e componentes de terceiros em aplicações Java distribuídas como bytecode JVM.**

## 4. Problema de Pesquisa Candidato

Não se sabe se a análise separada do bytecode próprio da aplicação e do bytecode de componentes de terceiros perde informações necessárias para caracterizar caminhos relevantes para segurança que atravessam a fronteira entre ambos.

Sem esclarecer essa incerteza, não é possível determinar se uma análise composta acrescenta informações relevantes sobre participação e atribuição entre componentes ou se apenas reúne resultados que as análises separadas já produziriam sob o mesmo escopo e as mesmas premissas.

## 5. Conceitos e Critérios Operacionais Provisórios

Os conceitos e critérios abaixo delimitam a discussão inicial e deverão ser validados antes do estudo.

### 5.1 Estrutura da Comparação

- **código próprio:** bytecode atribuído à aplicação segundo uma regra documentada de origem e responsabilidade;
- **componente de terceiro:** bytecode proveniente de um artefato externo, direto ou transitivo, segundo a mesma regra de atribuição;
- **fronteira entre componentes:** transição entre elementos atribuídos a origens ou responsabilidades diferentes;
- **análises separadas:** aplicação das mesmas regras de análise ao bytecode próprio e ao bytecode de terceiros em condições distintas;
- **análise composta:** aplicação dessas regras aos dois conjuntos de bytecode em uma única condição de análise;
- **informação acrescentada pela composição:** caminho entre componentes, atribuição ou lacuna de resolução identificada na análise composta e ausente nas análises separadas realizadas com entradas e regras equivalentes;
- **resultado sem diferença observada:** ausência de caminhos, atribuições ou lacunas adicionais na análise composta sob entradas, regras e condições de comparação equivalentes.

Esse primeiro conjunto define o que será comparado e os critérios para classificar tanto a informação acrescentada pela composição quanto a ausência de diferença observada.

### 5.2 Interpretação das Evidências

- **presença:** inclusão de um componente ou elemento de bytecode na distribuição analisada ou no `classpath` resolvido;
- **alcançabilidade estática:** caminho possível produzido por uma análise sob premissas e limitações declaradas;
- **relevância para segurança:** relação documentada entre uma origem influenciável, um caminho estaticamente alcançável e uma operação sensível.

A presença de um elemento não significa, por si só, que ele seja alcançável. Um caminho produzido pela análise estática também não basta, isoladamente, para estabelecer sua explorabilidade. Neste estágio, essas definições estabelecem apenas como os resultados da comparação serão classificados.

## 6. Questões de Pesquisa Provisórias

### RQ1 — Participação dos Componentes

**Como o código próprio e os componentes de terceiros participam, separada e conjuntamente, de caminhos relevantes para segurança em um corpus delimitado de aplicações Java distribuídas como bytecode JVM?**

### RQ2 — Valor da Análise Composta

**Quais informações relevantes para segurança, se houver, são acrescentadas por uma análise composta quando comparada às análises separadas do bytecode próprio e do bytecode de terceiros sob o mesmo escopo e as mesmas premissas?**

A conexão principal está no objetivo de modelagem e detecção de ameaças de segurança do ECO-CHAINS (Section 1.4, PDF p. 5). A discussão sobre gerenciamento de dependências também oferece a motivação documental para investigar como decisões incorporadas por artefatos externos podem afetar o software consumidor (Section 1.1, PDF p. 2).

## 7. Escopo do Estudo Inicial

Incluído no estudo inicial:

- aplicações escritas em Java e distribuídas como bytecode JVM;
- bytecode próprio e de terceiros, inclusive dependências diretas e transitivas quando seus artefatos e sua proveniência estiverem disponíveis;
- aplicações próprias, casos sintéticos ou alvos cuja análise tenha sido autorizada;
- um corpus pequeno e controlado, com fronteira de responsabilidade e inventário de dependências explícitos;
- comparação estática entre análise do código próprio, análise das dependências e análise composta;
- classes de chamadas ou fluxos de dados relevantes para segurança selecionadas por um protocolo explícito.

Fora do estudo inicial:

- generalizações para todas as linguagens JVM, versões de compilador, frameworks ou domínios de aplicação;
- prova de ausência de vulnerabilidades ou cobertura completa dos comportamentos possíveis;
- análise de alvo não autorizado.

As versões exatas de Java, do compilador e do formato de arquivo de classe serão controles experimentais definidos após a seleção do corpus e a avaliação de viabilidade; elas não constituem uma fronteira científica automática da pesquisa.

## 8. Plano do Estudo Inicial

### Etapa 1 — Delimitação

- revisar a literatura de segurança necessária para determinar se a lacuna proposta é relevante e original;
- validar as definições de código próprio, componente de terceiro, fronteira entre componentes, caminho relevante para segurança e unidade de análise;
- selecionar classes de caminhos, corpus delimitado, condições de comparação e resultados observáveis;
- definir as evidências que sustentariam, enfraqueceriam ou refutariam o valor proposto da análise composta.

### Etapa 2 — Comparação Estática Controlada

Esta etapa produzirá respostas delimitadas a evidências estáticas para RQ1 e RQ2; ela não sustentará, isoladamente, conclusões de explorabilidade.

- inventariar os artefatos e atribuir sua proveniência segundo uma regra documentada;
- analisar separadamente o bytecode próprio e o bytecode de terceiros;
- analisar de forma composta as mesmas entradas, sem alterar as regras da análise;
- comparar caminhos internos e entre componentes, fronteiras não resolvidas e lacunas de atribuição;
- relatar os resultados com premissas e limites residuais de cobertura explícitos.

## 9. Pontos para Discussão

A proposta apresentada para discussão é realizar um estudo inicial e delimitado que compare a análise estática do bytecode próprio, do bytecode de terceiros e do conjunto composto, com o objetivo de verificar se a composição acrescenta informações relevantes sobre caminhos entre componentes ou sua atribuição. Esta proposta é submetida à sua avaliação. As questões a seguir buscam orientar a delimitação do estudo inicial.

1. Esse recorte é adequado e suficientemente específico para o ECO-CHAINS?
2. A comparação estática proposta constitui um primeiro estudo discriminante?
3. Quais classes de caminhos e qual corpus devem delimitar a avaliação inicial?
4. Qual literatura de segurança deve ser examinada antes de avaliar a originalidade da proposta?

## References

- Eler, Marcelo Medeiros, and Gerson Sunyé. *Supply Chains for Eco-Responsible Software (ECO-CHAINS)*. IRP proposal, August 28, 2026, especialmente Sections 1.1 e 1.4, PDF pp. 1–5.
