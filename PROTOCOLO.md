# Protocolo de mensagens do xadrez em rede

Os dois processos trocam **uma mensagem de texto por linha** pelo socket.
O código fica em `src/network/Message.java`.

## Lances

Formato: `<vez><peça><origem><destino>[=<promoção>]`

| Mensagem   | Significado                                         |
|------------|-----------------------------------------------------|
| `1Pe2e4`   | Jogador 1 (brancas) move o peão de e2 para e4       |
| `2Ng8f6`   | Jogador 2 (pretas) move o cavalo de g8 para f6      |
| `1Ke1g1`   | Jogador 1 faz o roque pequeno (o rei anda duas casas) |
| `1Pe7e8=N` | Jogador 1 promove o peão em e8 a cavalo             |

- **vez**: `1` para as brancas (servidor) e `2` para as pretas (cliente).
- **peça**: `K` rei, `Q` dama, `R` torre, `B` bispo, `N` cavalo, `P` peão. São as mesmas letras que o jogo já mostra no tabuleiro.
- **origem e destino**: casas de `a1` a `h8`. A origem vai junto porque o `ChessMatch` precisa dela para executar o lance.
- **promoção** (opcional): `Q`, `R`, `B` ou `N`. Sem promoção, o jogo promove a dama.

## Mensagens de controle

| Mensagem      | Quem envia | Significado                                 |
|---------------|------------|---------------------------------------------|
| `#BEMVINDO 2` | servidor   | Conexão aceita. Você é o jogador 2 (pretas) |
| `#XEQUE 2`    | quem jogou | O jogador 2 está em xeque                   |
| `#MATE 1`     | quem jogou | Xeque-mate: o jogador 1 venceu              |
| `#ERRO texto` | quem recebeu | Lance recusado, com o motivo              |
| `#SAIR 1`     | qualquer um | O jogador 1 abandonou a partida            |

## A variável `vez`

Cada processo tem a sua própria cópia de `vez` (classe `TurnControl`), e ela começa em 1.
Ela só muda quando um lance válido é **enviado** (por quem jogou) ou **recebido** (pelo adversário).
Por isso as duas cópias ficam sempre iguais, sem que nenhum processo leia a memória do outro.

Quando um processo recebe um lance, ele confere três coisas:

1. A mensagem segue o formato (`Message.parse`).
2. O número da vez na mensagem é igual ao seu `vez` (`TurnControl.validate`).
3. A peça informada é a que está na casa de origem no seu tabuleiro.

Se algo não confere, ele responde `#ERRO` e não altera a vez.

Os métodos de `TurnControl` são `synchronized`. A partir do sprint 2, a thread que lê o socket e a thread do jogo usam o mesmo objeto, e `esperarMinhaVez()` usa `wait()` e `notifyAll()` para bloquear o jogador até a vez dele chegar.

## Testes

```
javac -d bin $(find src -name "*.java")
java -cp bin network.ProtocolSelfTest
```

O teste simula dois processos jogando o mate do pastor trocando apenas texto, e confere que os dois tabuleiros terminam em xeque-mate.

## Como o jogo usa o protocolo

- `MatchProtocol` liga o protocolo ao `ChessMatch`. `localMove` executa o lance do jogador local e devolve as mensagens a enviar. `receive` valida e aplica o lance do adversário, e devolve `#ERRO` quando algo não confere.
- `MessageChannel` é o canal de mensagens. Por enquanto existe só o `LocalChannel`, um par de filas em memória que trafega o mesmo texto que vai pelo socket. No sprint 2 entra o canal por socket sem mudar o resto do jogo.

Para jogar pelo protocolo no mesmo terminal:

```
javac -d bin $(find src -name "*.java")
java -cp bin application.Program
# escolha a opção 2
```

Cada jogador tem o seu próprio tabuleiro. A tela mostra as últimas mensagens trocadas (por exemplo, `jogador 1 enviou: 1Qh5f7` e depois `#MATE 1`).

## Partida em rede (sprint 2)

Cada jogador roda o programa no seu terminal. Pode ser na mesma máquina, como pede o enunciado, ou em duas máquinas da mesma rede.

```
javac -d bin $(find src -name "*.java")

# terminal 1: servidor, joga de brancas
java -cp bin application.Program      # opção 3, porta 5000

# terminal 2: cliente, joga de pretas
java -cp bin application.Program      # opção 4, porta 5000, endereço localhost
```

Na sua vez, digite a casa de origem e a de destino (ex.: `e2` e `e4`). Para desistir, digite `sair`.

### Como funciona

1. O servidor abre um `ServerSocket` e espera. Quando o cliente conecta, o servidor envia `#BEMVINDO 2`.
2. Cada processo cria um `NetworkGame` com duas threads:
   - **thread do jogo:** lê o teclado na sua vez e envia o lance;
   - **thread receptora:** lê o socket o tempo todo, valida e aplica o lance do adversário no seu tabuleiro.
3. Quando não é a sua vez, a thread do jogo dorme em `TurnControl.esperarMinhaVez()` (`wait()`). Quando a receptora aplica o lance do adversário, ela chama `passarVez()` (`notifyAll()`) e acorda o jogador.
4. A partida termina com `#MATE`, `#SAIR` ou se a conexão cair. Nos três casos `TurnControl.encerrar()` acorda quem estiver esperando.

### Exclusão mútua até aqui

| Recurso compartilhado entre as threads | Como é protegido |
|---|---|
| variável `vez` (`TurnControl`) | métodos `synchronized`, com `wait`/`notifyAll` |
| tabuleiro (`MatchProtocol` / `ChessMatch`) | métodos `synchronized` de `MatchProtocol` |
| escrita no socket | `SocketMessageChannel.send` é `synchronized`, para as linhas não se misturarem |
| histórico de mensagens | `Collections.synchronizedList` |

No sprint 3 entra a memória compartilhada entre os dois **processos**: um arquivo mapeado em memória, protegido com `FileLock`.

### Testes

```
java -cp bin network.ProtocolSelfTest   # protocolo e regras (63 verificações)
java -cp bin network.NetworkSelfTest    # mate do pastor e desistência por localhost
```
