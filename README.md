# 🎹 Chromatix 0.2 (Kotlin + Jetpack Compose)

App Android (modo **horizontal**) para criar **Chromatics de FNF** e exportar em **.sf2**.

## Novo na 0.2: dublar vogais (A, E, I, O — e U opcional)
1. Crie um projeto (já vem no modo **Vogais**).
2. Toque em **A**, **E**, **I** ou **O** e **grave** (ou importe) aquela vogal. Cante/fale todas na **mesma nota** e coloque essa nota como *Nota raiz*.
3. Quando as 4 estiverem dubladas (✓), elas **se repetem no teclado inteiro**: a tecla usa a vogal `nota MIDI % 4`.

| Nota | C | C# | D | D# | E | F | F# | G | G# | A | A# | B |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Vogal | A | E | I | O | A | E | I | O | A | E | I | O |

Se você dublar o **U** (opcional), ele entra no ciclo e o teclado passa a repetir **A E I O U** (nota % 5). Sem U, o padrão é o da tabela, igual em todas as oitavas. As teclas mostram a letra e a cor de cada vogal; teclas de vogais ainda não gravadas ficam cinza e mudas.
No .sf2 isso vira 4 samples e 128 zonas (uma por tecla), então funciona em qualquer player/DAW de SoundFont.
O modo **Único** (um sample em todas as teclas) continua disponível.

## Recursos
- Importar áudio (mp3, wav, ogg, m4a…) ou gravar pelo microfone, por vogal
- Piano multitoque de baixa latência (AudioTrack) para testar
- Corte por vogal, nota raiz, afinação, loop, ataque/decaimento/sustain/release, volume
- Projetos (abrir, duplicar, renomear, excluir, exportar .sf2) e configurações

## Compilar e instalar (pelo Termux + GitHub Actions)
O APK é compilado na nuvem pelo GitHub, não no celular.
```bash
pkg install unzip git gh -y
termux-setup-storage
cd ~ && unzip ~/storage/downloads/chromatix-0.2.zip && cd cx
git init -b main && git add . && git commit -m "Chromatix 0.2"
gh repo create chromatix-app --public --source=. --push
gh run watch                       # acompanha a compilação (~5 min)
gh release download --pattern '*.apk' --dir ~/storage/downloads
```
Depois abra o APK pelo gerenciador de arquivos e permita "instalar de fontes desconhecidas". Se a compilação falhar: `gh run view --log-failed`.

## Estrutura
`Model.kt` (projeto, armazenamento, config) · `Audio.kt` (mixer, decodificador, gravador) · `Sf2.kt` (exportador) · `MainActivity.kt` (telas Compose)

## Limitações
- O SF2 muda a nota acelerando/desacelerando o sample, então o som fica natural só perto da raiz.
- Todas as vogais compartilham raiz, afinação, envelope e loop (o corte é por vogal).
