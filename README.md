# 🎹 Chromatix

App de celular (modo **horizontal**) para criar **Chromatics de FNF** e exportar em **.sf2**.
Importe ou grave um sample, ajuste, teste no piano e exporte.

## Recursos
- Importar áudio (wav, mp3, ogg…) ou **gravar** pelo microfone
- **Piano multitoque** para testar o Chromatic na hora
- Nota raiz, afinação fina, corte (início/fim), loop, ataque / decaimento / sustain / release, volume
- Lista de projetos (abrir, duplicar, excluir, exportar) salva no aparelho
- Configurações: oitavas do piano, nome das notas (FL C5 = 60 ou padrão C4 = 60), volume, release padrão
- Funciona offline depois de instalado (PWA)

## O que é um Chromatic (pesquisa)
Chromatic é um sample (voz de personagem, “oh”, risada, som de cano etc.) espalhado pelo teclado, para tocar melodias com ele nota por nota. Nos mods de FNF costuma ser feito no FL Studio e distribuído como **.sf2** (SoundFont).

Como funciona um SF2:
- Guarda o sample (WAV 16 bits mono) dentro do arquivo, ligado a um *Instrument* com **faixa de teclas** e **nota raiz**.
- A **nota raiz** é a nota em que o sample soa “como gravado”. Teclas acima/abaixo são resultado de acelerar/desacelerar o sample.
- Por causa disso, o som fica natural só por algumas notas ao redor da raiz; muito longe disso ele soa fino/grave demais. É normal.

Dicas:
1. Use um sample **curto, limpo e sem silêncio** no começo.
2. Descubra o tom do sample (afinador) e coloque essa nota como raiz, senão o Chromatic sai desafinado.
3. Se o sample tem cauda/reverb, evite loop. Para sons sustentados (“aaaa”), ative o loop numa parte estável.
4. Teste no piano do app e ajuste a afinação fina.

Fontes consultadas: tutoriais de SoundFont do MuseScore, Swami (Ubuntu Studio) e fórum do Polyphone (raiz, faixa de teclas, transposição por reamostragem); repositório Kreagato/FNF-soundfonts (fluxo de FNF no FL Studio).

## Usar no celular
Abra o link do GitHub Pages no Chrome → menu ⋮ → **Instalar app / Adicionar à tela inicial**.
Para gravar pelo microfone é preciso HTTPS (o GitHub Pages já é).

## Publicar pelo Termux
```bash
pkg install unzip git gh -y
termux-setup-storage
cd ~ && unzip ~/storage/downloads/chromatix.zip && cd chromatix
git init -b main && git add . && git commit -m "Chromatix 1.0"
gh repo create chromatix --public --source=. --push
gh api -X POST repos/:owner/chromatix/pages -f 'source[branch]=main' -f 'source[path]=/'
```
O site fica em `https://SEU_USUARIO.github.io/chromatix/` (pode levar ~1 min).

## Estrutura
`index.html` (app inteiro) · `manifest.json` · `sw.js` (offline) · `icon-192.png` / `icon-512.png`

## Limitações
- Um sample por projeto, mapeado em todas as 128 teclas (preset “Chromatic”).
- Os projetos ficam no navegador do aparelho; exporte o .sf2 para guardar.
