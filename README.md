# Xadrez em rede (Java)

Xadrez de console para dois jogadores. A comunicação é por **sockets TCP**, cada processo guarda a sua variável **vez**, e os processos dividem uma **memória compartilhada** protegida por **exclusão mútua**.
O protocolo e a memória estão explicados em [PROTOCOLO.md](PROTOCOLO.md).

## Requisitos

- **JDK 17 ou mais novo.** Confira com `javac -version`.
- No Windows, use o **Windows Terminal** ou o terminal do IntelliJ. O `cmd` antigo pode mostrar códigos estranhos no lugar das cores.

## 1. Compilar

| Windows | Linux / macOS |
|---|---|
| dois cliques em `compilar.bat` | `./compilar.sh` |

Gera o `xadrez.jar`. Os scripts `jogar` e `testar` compilam sozinhos se o jar ainda não existir.

## 2. Testes automáticos

| Windows | Linux / macOS |
|---|---|
| `testar.bat` | `./testar.sh` |

Saída esperada:

```
OK: 36 verificações das regras do xadrez passaram.
OK: 133 verificações passaram.
OK: 16 verificações de rede passaram.
OK: 8 verificações de memória compartilhada passaram.
Todos os testes passaram.
```

## 3. Jogar na mesma máquina (o que o enunciado pede)

Abra **duas** janelas e rode `jogar.bat` (ou `./jogar.sh`) em cada uma.

| Janela 1: brancas | Janela 2: pretas |
|---|---|
| opção `3` | opção `4` |
| porta: Enter (5000) | porta: Enter (5000) |
| espera o adversário | endereço: Enter (`localhost`) |

Na sua vez, digite a casa de origem e depois a de destino, por exemplo `e2` e `e4`. As casas possíveis aparecem destacadas. Para desistir, digite `sair`.
Cada tela mostra as últimas mensagens do protocolo (`enviado: 1Pe2e4`) e a linha `memória compartilhada: lance N confere`.

**Espectador (opcional):** abra uma terceira janela e escolha a opção `5`. Ela acompanha a partida lendo só a memória compartilhada, sem socket.

### Partida rápida para testar (mate do pastor, 4 lances)

| Brancas | Pretas |
|---|---|
| e2 → e4 | e7 → e5 |
| f1 → c4 | b8 → c6 |
| d1 → h5 | g8 → f6 |
| h5 → f7 (xeque-mate) | |

As brancas veem "Você venceu!" e as pretas veem "Você perdeu.".

## Regras implementadas

Movimento de todas as peças, xeque, xeque-mate, roque pequeno e grande, en passant, promoção (dama, torre, bispo ou cavalo) e empate por afogamento.
As regras são conferidas por um teste que conta os lances legais de sete posições e compara com os números conhecidos (perft).
Não há empate por repetição de posição, pela regra dos 50 lances, por falta de material nem por acordo. Nesses casos a partida só termina se alguém digitar `sair`.

## 4. Jogar em duas máquinas

1. As duas máquinas precisam estar na **mesma rede** (mesmo Wi-Fi ou cabo).
2. No servidor (opção 3), o jogo mostra o IP da máquina: `Em outra máquina da rede, o adversário usa: 192.168.x.x`.
3. No cliente (opção 4), digite esse IP no lugar de `localhost`.
4. Na primeira vez, o Windows pergunta se o Java pode usar a rede. Marque **Redes privadas** e permita.

Em máquinas diferentes a memória compartilhada não existe, porque cada computador tem a sua memória. O jogo avisa (`memória compartilhada desligada`) e continua normalmente pelo socket.

## Pelo IntelliJ

Rode `application.Program` normalmente. Para abrir a segunda instância, vá em **Run → Edit Configurations → Modify options → Allow multiple instances** e rode de novo.

## Problemas comuns

| Mensagem | O que fazer |
|---|---|
| `Address already in use` | Já existe um servidor nessa porta. Feche-o ou use outra porta (ex.: 5001) nos dois lados. |
| `O servidor não respondeu em 5 segundos` | O IP está errado ou o firewall bloqueou. Confira o IP mostrado na tela do servidor. |
| O firewall do Windows abre uma janela durante `testar.bat` | É o teste de rede abrindo uma porta local. Pode permitir ou cancelar: o teste usa só `localhost`. |
| `Connection refused` | O servidor ainda não está esperando, ou o IP/porta está errado. Abra o servidor primeiro. |
| O cliente não conecta de outra máquina | Libere o Java no firewall do Windows (redes privadas). |
| `javac` não é reconhecido | Instale o JDK 17+ e coloque a pasta `bin` dele no PATH. |
