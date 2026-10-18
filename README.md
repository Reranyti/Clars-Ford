# Clars Ford

*Ранее — Lotus Blight / Порча лотоса. Formerly Lotus Blight.*

> **⚠ Скоро проект полностью изменится / The project is about to change completely**
>
> Текущая версия мода («Clars Ford», теперь Clars Ford) замораживается. Вместо неё готовится новая история: сюжет,
> концовки, диалоги и меню будут переработаны с нуля, а игра станет сериями-эпизодами. Многое из
> описанного ниже исчезнет или станет другим. Подробности автор раскроет позже.
> Сохранения и миры текущей версии с новой могут быть несовместимы: делайте копии.
>
> The current version ("Lotus Blight", now Clars Ford) is being frozen. A new story is in the works: plot, endings,
> dialogues and menus are being reworked from scratch, and the game becomes a series of episodes.
> Much of what is described below will disappear or change. The author will share details later.
> Worlds from the current version may not be compatible with the new one: back them up.

**Русский** · [English](#english)

Мод для Minecraft Forge 1.20.1 о красивом и опасном заражении лотосом. Оно рождается в воде, идёт
по рекам, выходит на берег и превращает землю в болото лотоса — а вокруг него разворачивается
история со своими ветками, событиями и персонажами.

Clars Ford (бывший Lotus Blight) — бесплатный некоммерческий фанатский проект. Часть персонажей и событий — это
альтернативная вселенная (AU) и хедканоны по мотивам игры DOORS от LSPLASH; мы не претендуем на
авторство её персонажей и сюжета (подробнее — в [LICENSE](LICENSE) и [ASSETS.md](ASSETS.md)).

## Что в моде

- **Заражение.** Очаги растут по поверхности через пять фаз — от цветения до «Под контролем
  Мирового Лотоса». Созревший очаг превращает местность в болото лотоса, с густым туманом, своими
  деревьями, корнями и ростками, а на пятой фазе уходит глубоко под землю, где появляется лотосовая
  руда.
- **Выбор ветки.** Альянс с Лотосом или война с ним — от этого меняется то, как мир и его персонажи
  относятся к игроку.
- **События.** Побег от лотоса, StarFall со Звёздным Светом, бой с боссом-предателем, внутренний
  голос, страницы дневника Объекта Ноль.
- **Хончо.** Помощник на ветке войны, со своей моделью, катсценой встречи и историей.
- **«Проклятая».** Четвёртая, скрытая ветка: измерение, «зависание» игры и босс Глитчер прямо на
  вашем рабочем столе (ритм-бой из двух песен). Ничего не удаляет и не читает ваши файлы — подробности в
  [CHANGELOG.md](CHANGELOG.md) (1.8000).
- **Живая карта**, очищающий порошок, метеоритное снаряжение, репутация с фракциями, свой календарь.

## Предупреждения

- **Жестокое содержание.** Поздние сцены (лестничная клетка, концовки) показывают кровь, расчленёнку
  и насилие, пугающие образы. Не для детей и не для впечатлительных.
- **Мерцание, помехи и громкие звуки.** Глитч-эффекты, резкая смена света и пугающие внезапные
  появления (в том числе в стороннем моде Ransom). Осторожно при светочувствительной эпилепсии.
- **Окно поверх рабочего стола.** В скрытой ветке мод рисует окно поверх вашего рабочего стола,
  сворачивает остальные окна и один раз делает снимок экрана **только в памяти**, чтобы «сломать»
  рабочий стол на картинке. Чтобы знать, где ваши значки, мод читает в память картинку обоев
  рабочего стола и три значения реестра Windows об их положении. Снимок не сохраняется и никуда не
  отправляется, мод не пользуется сетью и не удаляет и не переносит ваши файлы.
- **Сложность «Хромо» необратима.** Она выбирается в обычном списке сложностей Minecraft, включается
  для всего мира и не отключается обратно, даже командами.
- **Готовится: концовки, которые удаляют мир.** Любая концовка (кроме «проклятой») убивает игрока.
  Мир, созданный заново вместе с модом, концовка может удалить. Миры, созданные без мода, мод не
  трогает, а в мире, куда мод добавили позже, пропадает только его содержимое. Делайте копии миров.
- **Сторонний мод Ransom In Minecraft** (Wentory, MIT) в сборке отдельный и необязательный: у него
  свои пугающие появления и штраф за провал (по умолчанию он отнимает хотбар). Настраивается в его
  конфиге.

## Установка

**Проще всего — рекомендуемая сборка.** Архив `LotusBlight-Pack-<версия>.zip` из
[релизов](https://github.com/Reranyti/Clars-Ford/releases): распакуйте и запустите `install.bat`.
Установщик спросит папку (стандартная `.minecraft` или любая другая), разложит моды, шейдеры и
настройки, а моды, которые нельзя класть в архив, скачает с их официальных страниц. Forge 1.20.1
(47.x) нужно поставить заранее.

**Вручную.** Поставьте Forge 1.20.1 (47.x) и положите в папку `mods` jar-файлы мода
(`lotusblight-<версия>.jar` и `lotuslib-<версия>.jar`) и обязательные зависимости.

Обязательные моды:
- [GeckoLib](https://modrinth.com/mod/geckolib) 4.8+
- [WorldEdit](https://modrinth.com/mod/worldedit) 7.2+
- [Ex Meteor Shower](https://www.curseforge.com/minecraft/mc-mods/ex-meteor-shower) 0.0.4+, ему нужны
  [OctoLib](https://modrinth.com/mod/shatterbyte-lib) и [Architectury](https://modrinth.com/mod/architectury-api)
- [TerraBlender](https://modrinth.com/mod/terrablender) 3.0.1+

Необязательные — мод использует их, если они установлены:
- [Streams Reflowing](https://modrinth.com/mod/streams-reflowing) — заражение идёт по течению рек;
- Chat Overhaul — реплики персонажей в его
  чате, со своими иконками;
- Cinematic — кадры с камерой в катсценах;
- [JourneyMap](https://modrinth.com/mod/journeymap) — очаги и зона заражения на его карте;
- [Nature's Compass](https://modrinth.com/mod/natures-compass), [YetAnotherConfigLib](https://modrinth.com/mod/yacl).

Игре со всей сборкой и шейдерами лучше выделить 6–8 ГБ памяти.

## Команды

Все команды — `/lotus ...`, нужны права оператора. `/lotus book` открывает книгу со всеми командами,
`/lotus compat` показывает, что мод правит и использует в установленных модах.

## Совместимость

Всё, что мод делает с другими модами, собрано в одном реестре. Исправления чужих модов (WorldEdit,
Ex Meteor Shower, Chat Overhaul) включаются, только если нужный код есть в установленной версии, —
старые и новые версии этих модов не ломают игру. Рекомендуемые настройки других модов (например,
качество рек Streams Reflowing) выставляются один раз и не трогают значения, которые игрок выбрал
сам; выключается в `config/lotusblight-common.toml` (`applyRecommendedSettings`).

## Сборка из исходников

Нужна Java 17.

```text
./gradlew build
```

jar-файлы появятся в `build/libs/`. Моды, с которыми мод компилируется, но которых нет в Maven,
кладутся вручную в папку `libs/` (список — в [libs/README.md](libs/README.md)).

Автопроверки (GameTest) — `./gradlew runGameTestServer`. Архив рекомендуемой сборки собирает
`python tools/pack/build_pack.py`.

## Ссылки

- [CHANGELOG.md](CHANGELOG.md) — история версий
- [ASSETS.md](ASSETS.md) — ассеты и их авторы
- [LICENSE](LICENSE) — лицензия

Авторы: Reranyti и neiber573.


##Скриноты ветки(Reranity)
<img width="1255" height="753" alt="image" src="https://github.com/user-attachments/assets/e6e38819-b07e-433e-bebd-908644a2e1d9" />
<img width="1263" height="725" alt="image" src="https://github.com/user-attachments/assets/7082c2d8-40da-4759-b266-7054fa570802" />
<img width="1291" height="739" alt="image" src="https://github.com/user-attachments/assets/571e0cb0-0eb5-445f-a7ba-e0d743b240f7" />



---

<a name="english"></a>
# Clars Ford

*Formerly Clars Ford.*

[Русский](#lotus-blight--порча-лотоса) · **English**

A Minecraft Forge 1.20.1 mod about a beautiful and dangerous lotus infection. It starts in water,
follows the rivers, climbs onto the shore and turns the land into lotus marsh — and around it runs a
story with its own branches, events and characters.

Clars Ford (formerly Lotus Blight) is a free, non-commercial fan project. Some of its characters and events are an
alternate universe (AU) and headcanons inspired by DOORS by LSPLASH; we claim no authorship of its
characters or story (see [LICENSE](LICENSE) and [ASSETS.md](ASSETS.md)).

## What's in it

- **The infection.** Outbreaks spread across the surface through five phases — from blooming to
  "Under the World Lotus' control". A mature outbreak turns the land into lotus marsh, with thick fog,
  its own trees, roots and shoots, and at phase 5 it reaches deep underground, where lotus ore forms.
- **A branch to choose.** Alliance with the Lotus or war against it — it changes how the world and
  its characters treat the player.
- **Events.** The Lotus Chase, StarFall with Star Light, the traitor boss fight, the inner voice, the
  Object Zero diary pages.
- **Honcho.** An assistant on the war branch, with his own model, meeting cutscene and story.
- **The Cursed branch.** A fourth, hidden branch: a dimension, the game "freezing", and the Glitcher boss
  right on your desktop (a two-song rhythm fight). It deletes nothing and reads none of your files —
  see [CHANGELOG.md](CHANGELOG.md) (1.8000).
- **A living map**, cleansing powder, meteorite gear, faction reputation, a calendar of its own.

## Warnings

- **Graphic content.** The late scenes (the stairwell, the endings) show blood, dismemberment and
  violence, and frightening imagery. Not for children or the easily disturbed.
- **Flashing, interference and loud sounds.** Glitch effects, sudden changes of light and sudden
  frightening appearances (including in the third-party Ransom mod). Take care with photosensitive
  epilepsy.
- **A window over your desktop.** In the hidden branch the mod draws a window over your desktop,
  minimises your other windows and takes one screenshot **in memory only**, to "break" the desktop
  on the picture. To know where your icons are it reads the picture of your desktop wallpaper into
  memory, and three Windows registry values about it. The picture is not saved or sent anywhere; the mod uses no network and does
  not delete or move your files.
- **The Chromo difficulty cannot be undone.** It is picked in Minecraft's ordinary difficulty list,
  applies to the whole world and cannot be switched back, not even with commands.
- **Coming: endings that delete the world.** Every ending (except the cursed one) kills the player.
  A world created anew together with the mod can be deleted by the ending. Worlds made without the
  mod are never touched, and in a world the mod was added to later only its own content disappears.
  Keep copies of your worlds.
- **The third-party Ransom In Minecraft mod** (Wentory, MIT) is separate and optional in the pack: it
  has its own frightening appearances and a penalty for failing (by default it takes your hotbar).
  It can be configured in its own config.

## Installing

**The easy way is the recommended pack.** Get `LotusBlight-Pack-<version>.zip` from the
[releases](https://github.com/Reranyti/Clars-Ford/releases), unpack it and run `install.bat`. The
installer asks for a folder (the default `.minecraft` or any other), puts the mods, shader pack and
settings in place, and downloads the mods that can't be bundled from their official pages. Install
Forge 1.20.1 (47.x) first.

**By hand.** Install Forge 1.20.1 (47.x) and put the mod's jars (`lotusblight-<version>.jar` and
`lotuslib-<version>.jar`) and the required dependencies into `mods`.

Required mods:
- [GeckoLib](https://modrinth.com/mod/geckolib) 4.8+
- [WorldEdit](https://modrinth.com/mod/worldedit) 7.2+
- [Ex Meteor Shower](https://www.curseforge.com/minecraft/mc-mods/ex-meteor-shower) 0.0.4+, which needs
  [OctoLib](https://modrinth.com/mod/shatterbyte-lib) and [Architectury](https://modrinth.com/mod/architectury-api)
- [TerraBlender](https://modrinth.com/mod/terrablender) 3.0.1+

Optional — used when installed:
- [Streams Reflowing](https://modrinth.com/mod/streams-reflowing) — the infection follows the rivers'
  current;
- Chat Overhaul — characters speak in its
  chat, with their own icons;
- Cinematic — camera shots in cutscenes;
- [JourneyMap](https://modrinth.com/mod/journeymap) — outbreaks and the infected area on its map;
- [Nature's Compass](https://modrinth.com/mod/natures-compass), [YetAnotherConfigLib](https://modrinth.com/mod/yacl).

Give the game 6–8 GB of memory with the whole pack and shaders.

## Commands

All commands are `/lotus ...` and need operator rights. `/lotus book` opens a book with every
command, `/lotus compat` shows what the mod changes in and uses from the installed mods.

## Compatibility

Everything the mod does with other mods is gathered in one registry. Fixes to other mods (WorldEdit,
Ex Meteor Shower, Chat Overhaul) only go in when the code they need exists in the installed version —
older and newer versions of those mods don't break the game. Recommended settings for other mods
(Streams Reflowing's river quality, for one) are set once and never override a value the player chose;
turn it off in `config/lotusblight-common.toml` (`applyRecommendedSettings`).

## Building from source

Needs Java 17.

```text
./gradlew build
```

The jars end up in `build/libs/`. Mods the build compiles against that aren't on Maven go into `libs/`
by hand (listed in [libs/README.md](libs/README.md)).

Automated checks (GameTest): `./gradlew runGameTestServer`. The recommended pack archive is built by
`python tools/pack/build_pack.py`.

## Links

- [CHANGELOG.md](CHANGELOG.md) — version history


- [ASSETS.md](ASSETS.md) — assets and their authors
- [LICENSE](LICENSE) — license

Authors: Reranyti and neiber573.
