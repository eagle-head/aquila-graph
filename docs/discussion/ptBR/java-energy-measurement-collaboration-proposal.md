# Proposta Breve de Colaboração Experimental em Java

## Contexto

O estudo *How Green Is Your Upgrade? Carbon Emission Trends in Python Web Frameworks* analisa como o consumo de energia e as emissões de carbono variam entre versões sucessivas de frameworks web em Python. A colaboração proposta busca acrescentar ao trabalho existente uma extensão experimental delimitada ao ecossistema Java, sem constituir um projeto independente nem alterar previamente o objetivo do estudo original (Amorim et al., 2026, Sections 1, 3, 5 e 6).

## Objetivo da Colaboração

Avaliar, em conjunto com os responsáveis pelo estudo, a viabilidade e a utilidade de aplicar um protocolo comparável a frameworks web em Java. A extensão observaria a evolução das medições entre versões sob uma carga controlada, sem pressupor aumento, redução ou estabilidade do consumo de energia e das emissões.

## Atividades Propostas

1. Delimitar com a equipe os frameworks, as versões, a carga e os critérios de comparação.
2. Preparar imagens Docker, uma configuração Docker Compose e scripts reproduzíveis, com versões e dependências fixadas.
3. Executar um estudo-piloto local para verificar o protocolo e a coleta com CodeCarbon.
4. Realizar a coleta destinada à análise na infraestrutura definida pela equipe, mantendo separados os dados do piloto.
5. Registrar o hardware, o sistema operacional, o runtime Java, a versão do Docker, a configuração do CodeCarbon e as condições de cada execução.
6. Entregar os scripts, os dados brutos, os registros de execução e uma síntese dos resultados para avaliação pelos responsáveis pelo artigo.

## Delimitação

A contribuição complementaria o estudo em andamento e somente seria incorporada após avaliação da equipe. O uso de Docker e Docker Compose estabilizaria o ambiente de software, mas o relatório identificaria separadamente o hardware efetivamente utilizado na coleta. As conclusões seriam limitadas às condições observadas, inclusive se não fosse encontrada diferença relevante entre as versões avaliadas.

## Pergunta sobre o Reconhecimento da Colaboração

Caso a extensão em Java seja aceita, executada e efetivamente utilizada no trabalho, seria possível registrar meu nome na versão final do artigo, por exemplo, como co-autor responsável por essa contribuição experimental? Esta pergunta não constitui uma solicitação de coautoria, mas de reconhecimento nominal pela colaboração que vier a ser incorporada ao estudo.

## Referências

- Amorim, Fernando Karchiloff Gouveia de; Carvalho, Vinicius Renan de; Cordeiro, Daniel; Sunyé, Gerson; Eler, Marcelo Medeiros. *How Green Is Your Upgrade? Carbon Emission Trends in Python Web Frameworks*. Manuscrito do SBES 2026, especialmente Sections 1, 3, 5 e 6.
- CodeCarbon. [Tracking with the CLI](https://docs.codecarbon.io/latest/tutorials/cli/). Acesso em 31 ago. 2026.
