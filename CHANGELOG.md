# Changelog

> **⚠ Скоро проект полностью изменится (теперь он называется Clars Ford, ранее Lotus Blight). Документ описывает старую версию и скоро устареет. / The project (now Clars Ford, formerly Lotus Blight) is about to change completely; this document describes the old version.**


Retroactive version report for the full rewrite of Lotus Blight. `mod_version`
was left at `1.0.0` for the entire rewrite instead of being bumped per batch
of work — this file assigns version numbers to the existing commit history
after the fact, without rewriting any past commits. Going forward,
`gradle.properties`' `mod_version` is bumped alongside the commit that
finishes each batch of work below.

Versioning switched from `major.minor.patch` to a flat `1.NNNN` counter at
`1.1010` — every entry below `1.1010` still uses the old three-part numbers
as they were actually committed at the time.

*Note: commit messages between `1.7.0` and `1.67.x` didn't embed a version
number, so that stretch isn't individually reconstructable here — see
`git log` for the raw history of that range. Everything from `1.68.0` onward
is transcribed directly from its own commit message.*

## 2.0008 — Сложность «Хромо», хромо-карты, Скрип, «Психически не здоров.» / The Chromo difficulty, Chromo maps, Creak, "Mentally unwell."

### Русский
- **Сложность «Хромо».** Пятый пункт в обычном списке сложностей Minecraft (в настройках мира и при создании мира), с предупреждением. Переводит весь мир на неё навсегда, обратно нельзя, даже командами. Внутри мир держится на Hard и заблокирован, враги получают двойной урон и дальность обнаружения и на четверть быстрее. Серверу нужен оператор, в одиночной игре хватает любого.
- **Хромо-карты.** Песни 1 и 2 боя на рабочем столе на хромо-сложности играются на своих, более тяжёлых картах. У песни 1 запас здоровья 320 вместо 48. Сюжет на этом заканчивается, дальше пока нет. Новые события песни 1: экран закручивается (0:45), картинка раздваивается и появляется огромный глаз (1:00), три глаза и вращающийся Глитчер (1:20), нити-стены, которые не пускают курсор, пока их не перережешь зажатой левой кнопкой (1:43).
- **Скрип (рабочее название «Крик»).** Новая сущность лестничной клетки со своей гладкой моделью из камня, арматуры и дыр со светящимся глазом. Когда на него смотрят, он прикрывает лицо руками; умеет ходить крадучись. Поведение будет позже: `/summon lotusblight:creak ~ ~ ~`.
- **Эффект «Психически не здоров.»** Картинка становится полусерой, а по краям экрана медленно наползают стены мяса. Рисуется слоем интерфейса, поэтому работает с любым шейдерпаком. `/effect give @s lotusblight:mentally_unwell 120`.
- Для проверки: меню моментов `moments.bat` получило пункты хромо-песен; тесты на побег, хромо и появление Скрипа.

### English
- **The Chromo difficulty.** A fifth entry in Minecraft's ordinary difficulty list (in a world's options and when creating one), with a warning. It switches the whole world over for good: no way back, not even with commands. Underneath the world is held on Hard and locked; enemies get double damage and detection range and are a quarter faster. On a server an operator is needed; in single player anyone will do.
- **Chromo maps.** On the Chromo difficulty songs 1 and 2 of the desktop fight are played on their own, harder maps. Song 1 has 320 health instead of 48. The story ends there for now. New events in song 1: the screen starts to turn (0:45), the picture doubles and a huge eye appears (1:00), three eyes and a spinning Glitcher (1:20), threads that wall the cursor off until they are cut with the left button held (1:43).
- **Creak (working name "Крик").** A new stairwell creature with its own smooth model of stone, rebar and holes, with a glowing eye. When looked at, it covers its face with its hands; it can stalk. Behaviour comes later: `/summon lotusblight:creak ~ ~ ~`.
- **The "Mentally unwell." effect.** The picture goes half-grey and walls of meat slowly creep in from the edges of the screen. Drawn as a layer of the interface, so it works with any shader pack. `/effect give @s lotusblight:mentally_unwell 120`.
- For testing: the moments menu `moments.bat` got the Chromo songs; tests for the escape, Chromo and Creak's appearance.

## 2.0005 — Новый побег, рог, возвращение Хончо, пальцы в игре / The new escape, the horn, Honcho's return, fingers in the game

### Русский
- **Побег от лотоса переделан.** Залы теперь 7 на 7 внутри (раньше проход был в один блок). Перед дверью расчищенная площадка на твёрдом основании, деревья и рельеф вход не закрывают; дверь 3 на 3. Дальше длинный зал и перекрёсток: правильная ветка светит жёлтым и ведёт в большую безопасную комнату, ложная светит красным и кончается тупиком. Нормальный свет: потолочные панели и огни в полу вместо одной лампочки.
- **Препятствия в побеге:** с потолка падают книжные полки (пыль, звук, удар), пятна паутины, а во второй фазе ещё и стеклянные перегородки на всю ширину зала, которые надо пробить. Перед следующим забегом всё убирается. Дверь-печать больше нельзя сломать киркой. `/lotus chase tp` ставит на площадку, а не внутрь блока.
- **Рог** (даётся один раз после финала): зажми Shift с рогом в 1-й, 2-й или 3-й ячейке хотбара. 1-я зовёт Хончо (перезарядка 30 с), 2-я очищает блок под прицелом до 150 блоков (160 раз за игровые сутки), 3-я лечит и даёт 12 золотых сердец (перезарядка 2 игровых суток). Над хотбаром полоски перезарядки.
- **Возвращение после финала:** открывается тот же портал, из него выходит Хончо; короткий разговор, рукопожатие, и он идёт с игроком дальше (`/lotus normalbranch return` для проверки).
- **Пальцы в самой игре.** У Хончо отдельная кисть и пять пальцев с двумя костями, руки живые в ходьбе и покое, новые анимации «отряхнуться» и «рукопожатие». У игрока ладонь и пять пальцев, которые сжимаются по состоянию руки (предмет, замах, покой).
- Мод Ransom In Minecraft (Wentory, MIT) указан в ASSETS.md; в репозиторий не входит.

### English
- **The lotus escape is redone.** The halls are 7 by 7 inside now (before, the way through was one block wide). In front of the door there is a cleared apron on a solid base, so trees and hillside no longer bury the entrance; the door is 3 by 3. Past it: a long hall and a T-junction; the real arm is lit yellow and leads to a big safe room, the decoy is lit red and ends in a dead end. Proper light: ceiling panels and lights in the floor instead of a single bulb.
- **Obstacles in the escape:** shelves fall from the ceiling (dust, sound, a hit), patches of cobweb, and in the second phase glass partitions across the whole hall that have to be broken through. Everything is cleared before the next run. The sealed door can no longer be broken with a pickaxe. `/lotus chase tp` puts you on the apron instead of inside a block.
- **The horn** (given once after the finale): hold Shift with the horn in hotbar slot 1, 2 or 3. Slot 1 calls Honcho (30 s cooldown), slot 2 cleanses the block at the crosshair up to 150 blocks away (160 uses per in-game day), slot 3 heals and gives 12 golden hearts (2 in-game days cooldown). Cooldown bars sit above the hotbar.
- **Coming back after the finale:** the same portal opens and Honcho steps out; a short talk, a handshake, and he goes on with the player (`/lotus normalbranch return` to test).
- **Fingers in the game itself.** Honcho has a separate hand and five two-bone fingers, hands alive when walking and idle, new "dust off" and "handshake" animations. The player has a palm and five fingers that close by what the hand is doing (item, swing, idle).
- The Ransom In Minecraft mod (Wentory, MIT) is credited in ASSETS.md; it is not part of this repository.

## 2.0001 — Финал: Глитчер берёт Хончо за шею / The finale: the Glitcher takes Honcho by the throat

### Русский
- **Финал.** Глитчер подходит, берёт Хончо за шею, поднимает над полом (тот бьётся и хватается за руку), замахивается и закидывает в портал. Реплики Глитчера теперь пишутся по буквам через весь экран, как в сцене выхода (с мигающим курсором), а не подписями.
- Пальцы: отдельные формы кисти (открытая, кулак, щепотка, указательный, коготь, хват), у каждого пальца своя пружина; Глитчер на рабочем столе и сцена выхода перед боем переведены на 15 суставов.

### English
- **The finale.** The Glitcher walks up, takes Honcho by the throat, lifts him off the floor (he struggles and claws at the arm), swings him back and throws him into the portal. The Glitcher's lines are now written across the screen letter by letter with a blinking cursor, as in the exit scene, instead of captions.
- Fingers: separate hand shapes (open, fist, pinch, pointing, claw, grip), each finger on its own spring; the Glitcher on the desktop and the scene before the fight now run on the 15-joint rig.

## 2.0000 — Анимации на 15 суставах, финал, круги по событиям / R15 animation, the finale, circles that follow the events

### Русский
- **Анимация на 15 суставах.** Все фигуры боя, катсцен и финала теперь на скелете из 15 суставов (плечи, локти, запястья, бёдра, колени, лодыжки, пояс, шея) с решателем IK для рук и ног. Драка в падении, душение и финальный бросок поставлены на нём напрямую, остальные сцены идут через переходник с пружинным сглаживанием и сгибами по движению.
- **Пальцы.** У каждой руки по пять пальцев с двумя косточками; рука расслаблена, сжимается в кулак при ударе и раскрывается.
- **Катсцена перед битвой переделана.** Глитчер говорит с игроком прямо на рабочем столе; открывается портал, Хончо вылетает из него, Глитчер берёт его за горло и спрашивает, кто он; дальше — спасение кликами. Портал стал детальнее (воронки, рваный край, молнии, искры); рабочий стол бьётся как стекло.
- **Финал после 3 песни.** Результаты ждут пробела; затем портал, бросок Глитчера, две реплики, «SIGNAL LOST». Игроку выдаётся рог (пока предмет-заглушка), окно Minecraft возвращается на передний план, а если игры нет — приложение закрывается.
- **Круги по событиям.** Пузыри в Aero-части 1 песни, глаза из поля глаз, окна «Изображение не найдено» с кнопкой OK во 2 песне, куски рабочего стола в 3 песне. События двигают и поле, и интерфейс: поворот мира, экран в руках твари (полосы HP и комбо внутри него), тряска от страха, глитч, сжатие с полем.
- **Обучение** про пробел и глаз теперь отдельная сцена между 1 и 2 песней; во 2 песне на 1:19 музыка больше не прерывается.
- **Быстрее:** туннель, небо, глитч и бойцы отрисовываются заметно дешевле.
- Инструменты для разработки: набор для анимации с проверками и контактными листами (в игру не входит).

### English
- **15-joint animation.** Every figure of the fight, the cutscenes and the finale is now on a 15-joint skeleton (shoulders, elbows, wrists, hips, knees, ankles, waist, neck) with IK for hands and feet. The falling fight, the choke and the finale's throw are posed on it directly; the other scenes go through an adapter with spring smoothing and bends that follow the motion.
- **Fingers.** Every hand has five fingers of two bones; the hand is loose, closes into a fist on a blow and opens again.
- **The cutscene before the fight is redone.** The Glitcher speaks to the player on the desktop itself; a portal opens, Honcho is thrown out of it, the Glitcher takes him by the throat and asks who he is; then the rescue by clicking. The portal is more detailed (vortices, torn rim, lightning, sparks); the desktop breaks like glass.
- **The finale after the third song.** The results wait for the space bar; then the portal, the Glitcher's throw, two lines, "SIGNAL LOST". The player is given a horn (a placeholder item for now), the Minecraft window is brought back to the front, and if the game is gone the program just closes.
- **Circles that follow the events.** Bubbles in the Aero part of the first song, the eyes of the field of eyes, "Image not found" boxes with an OK button in the second, pieces of the broken desktop in the third. The events move both the playfield and the interface: the world's turn, the screen held in the thing's hands (the HP and combo bars inside it), shaking with fear, glitching, shrinking with the field.
- **The lesson** on the space bar and the eye is now a scene of its own between the first and second songs; the second song's music is no longer interrupted at 1:19.
- **Faster:** the tunnel, sky, glitch and fighters are much cheaper to draw.
- Development tools: an animation toolkit with checks and contact sheets (not part of the game).

## 1.9000 — Третья песня / The third song

### Русский
- **Третья песня боя.** После второй песни идёт катсцена (Глитчер, Хончо, душение — жмите по Глитчеру,
  чтобы оттолкнуть его за 12 секунд, иначе бой проигран), рабочий стол разбивается, и все падают вниз.
  Дальше — третья песня: Хончо и Глитчер дерутся, вы помогаете кругами. Карта третьей песни сделана
  командой в osu!lazer.
- **Драка.** У бойцов разные приёмы (удар, пинок, апперкот, рывок, захват, луч), свои обмены раз в несколько
  секунд и спецприём Хончо за каждые 25 попаданий подряд.
- **Ход песни:** мир кружится (0:41), по краям открываются глаза, наблюдающие за Глитчером (1:08), экран
  сжимается и его держат руки (1:34), Хончо смотрит в туннель из глаз (1:47), а с 2:25 Глитчер рвёт картинку.
- **Начало второй песни** — гнилые обои и глаза переходят из первой, Глитчер собирается заново и бросает окна.
- **Быстрее:** туннель, небо, глитч и бойцы отрисовываются заметно дешевле; кадр в тестах вырос по скорости
  в 1,5–3 раза. Исправлена ошибка, из-за которой туннель мог упасть.
- Треки третьей песни и катсцены лежат в jar (см. `ASSETS.md`).

### English
- **The fight's third song.** After the second song a cutscene plays (the Glitcher, Honcho, the choke - click
  the Glitcher to push it away within 12 seconds, or the fight is lost); the desktop shatters and everyone
  falls. Then the third song: Honcho and the Glitcher fight and you help with the circles. The third song's
  beatmap was made by the team in osu!lazer.
- **The fight.** The fighters have different moves (punch, kick, uppercut, dash, grab, beam), trade blows
  on their own every few seconds, and Honcho gets a special for every 25 hits in a row.
- **The song:** the world spins (0:41), eyes open at the sides watching the Glitcher (1:08), the screen
  shrinks and is held by hands (1:34), Honcho looks into a tunnel of eyes (1:47), and from 2:25 the Glitcher
  tears the picture up.
- **The second song's opening** carries the rotting wallpaper and eyes over from the first; the Glitcher
  re-forms and throws windows.
- **Faster:** the tunnel, sky, glitch and fighters are much cheaper to draw; frames in tests got 1.5-3 times
  faster. A crash in the tunnel is fixed.
- The tracks of the third song and the cutscene are in the jar (see `ASSETS.md`).

## 1.8000 — Release: «Проклятая» / the Cursed branch
Открываем скрытую ветку. / The hidden branch is out in the open.

### Русский
- **Четвёртая ветка — «Проклятая».** Скрытая ветка, включается особой цепочкой смертей (как именно —
  оставляем игрокам). Уводит в отдельное измерение, а заканчивается тем, что Minecraft «зависает»,
  и поверх рабочего стола открывается босс. Ветка новая: могут быть шероховатости, пишите о них.
- **Босс на рабочем столе — Глитчер.** Ритм-бой из двух песен прямо поверх рабочего стола (для игры —
  кликайте по кругам; Esc выходит из боя в любой момент).
  - **Первая песня.** Глитчер ходит по экрану, стучит по «стеклу», собирает и бросает иконки вашего
    рабочего стола (только их картинки — сами иконки не двигаются), злится, топает — и экран гаснет,
    открывается поле глаз, обои-Aero рвутся.
  - **Вторая песня.** Экран рвётся и спрашивает «ты умрёшь?»; песня встаёт на паузу, Хончо в диалоговом
    окне учит, как жать; потом контакт рвёт глаз-ромб: он раскалывается и смыкается на бите — жмите
    ПРОБЕЛ, когда метка на ленте дойдёт до линии. Кругов в это время нет. В конце — анимация финала.
- **Что это делает на вашем компьютере (без сюрпризов).** Мод ничего не удаляет и не перемещает,
  не сканирует диски, не открывает ваши документы и ничего никуда не отправляет. Ошибки, «файлы»
  и «зависание» — картинка. Для сцены он: делает снимок экрана и читает картинку текущих обоев Windows
  (чтобы найти иконки), запускает второй процесс Java из jar, сворачивает окна через Проводник
  (`Shell.Application`, при выходе они возвращаются) и кладёт распакованную музыку во временную папку
  `%TEMP%\lotusblight` (убирается вместе с процессом). Рассчитано на Windows.
- **Размер.** jar стал около 144 МБ: внутри финальное видео и музыка боя.
- Новые ассеты и их авторы — в `ASSETS.md` (там же отмечено, что ещё нужно уточнить).

### English
- **The fourth branch — the Cursed one.** A hidden branch, started by a particular chain of deaths (we
  leave the "how" for players to find). It takes you to a separate dimension and ends with Minecraft
  "freezing" and a boss opening on top of your desktop. The branch is new — expect rough edges, and
  report them.
- **A boss on your desktop — the Glitcher.** A two-song rhythm fight right over your desktop (click the
  circles; Esc leaves the fight at any moment).
  - **First song.** The Glitcher walks about the screen, knocks on the "glass", picks up and throws
    your desktop icons (only pictures of them — the icons themselves never move), gets angry, stomps —
    and the screen goes dark, a field of eyes opens, the Aero wallpaper tears.
  - **Second song.** The screen tears and asks "will you die?"; the song pauses, Honcho teaches you how
    to press in a dialogue window; then a diamond eye cuts the contact off: it splits and closes on the
    beat — press SPACE when the mark on the strip reaches the line. No circles during that. The finale
    animation ends it.
- **What it does on your computer (no surprises).** The mod deletes and moves nothing, scans no disks,
  opens none of your documents and sends nothing anywhere. The errors, the "files" and the "freeze"
  are pictures. For the scene it takes a screenshot and reads the picture of your current Windows
  wallpaper (to find the icons), starts a second Java process from the jar, minimises windows through
  Explorer (`Shell.Application`; they come back on exit) and puts the unpacked music in a temp folder,
  `%TEMP%\lotusblight` (cleaned up with the process). Built for Windows.
- **Size.** The jar is now about 144 MB: it carries the finale video and the fight's music.
- New assets and their authors are listed in `ASSETS.md` (with what still needs confirming).

## 1.1050.2
Мелкие правки — в счёт 50 версий не идут, войдут в полный лог 1.1100.
Small changes — not counted toward the 50 versions, they go into the 1.1100 full log.

### Русский
- У сцены StarFall со Звёздным Светом появилась своя музыка — «DOORS: The Stairwell UST» (PAWDER); она
  затихает, когда Звёздный Свет договорит.
- Лекция Мирового Лотоса звучит под «Mischievous Light» (Hotel++ Soundtrack, chronocopes) вместо
  «Crimson Red Light», на который у нас не было разрешения автора.
- `ASSETS.md`: авторы и ссылки на все треки.

### English
- The StarFall scene with Star Light has music of its own — "DOORS: The Stairwell UST" (PAWDER); it
  fades once Star Light is done speaking.
- The World Lotus lecture now plays "Mischievous Light" (Hotel++ Soundtrack, chronocopes) instead of
  "Crimson Red Light", which we had no permission from its author for.
- `ASSETS.md`: authors and links for every track.

## 1.1050.1
Мелкие правки после релиза — в счёт 50 версий не идут, войдут в полный лог 1.1100.
Small post-release changes — not counted toward the 50 versions, they go into the 1.1100 full log.

### Русский
- Новая лицензия: мод можно включать в бесплатные сборки без изменений и со ссылкой; мы не
  претендуем на персонажей, историю и сюжет DOORS — это AU и хедканоны; проект некоммерческий
  (`LICENSE`).
- README переписан под текущий мод, на русском и английском.
- `ASSETS.md` — список ассетов и их авторов: DOORS (LSPLASH), скин Хончо (Gdux), музыка, текстуры.
- Рекомендуемая сборка с установщиком: `install.bat` ставит моды, шейдеры и настройки в `.minecraft`
  или выбранную папку, а моды, которые нельзя раздавать, скачивает с их официальных страниц.

### English
- A new license: the mod may be included unmodified in free modpacks with a link; we claim no
  authorship of DOORS' characters, story or plot — this is AU and headcanons; the project is
  non-commercial (`LICENSE`).
- The README is rewritten for the current mod, in Russian and English.
- `ASSETS.md` — the assets and their authors: DOORS (LSPLASH), Honcho's skin (Gdux), music, textures.
- A recommended pack with an installer: `install.bat` puts the mods, shader pack and settings into
  `.minecraft` or a chosen folder, and downloads the mods that can't be redistributed from their
  official pages.

## 1.1050 — Release: everything from 1.100 to 1.1050
The first full release of the 1.1000s: 1.100.0 and 1.101.0, the switch to the flat 1.NNNN counter at
1.1010, and every version after it up to this one — grouped by what changed rather than by the order
it landed in. Every 50 versions gets a log like this; the per-version detail for 1.1017–1.1042 stays
in its own section below.

### Русский

**Хончо**
- У Хончо своя модель вместо позаимствованной у зомби, с локтями, коленями и поясницей; стойка,
  поза встречи и молитва на коленях взяты прямо из проекта Blockbench. Позы сперва проигрывались
  зеркально — руки уходили за спину, — теперь стоят ровно так, как задуманы.
- Он больше не зомби даже внутри: отдельное существо, без солнечных ожогов, превращения в
  утопленника, подкреплений, маленьких копий и курочек-наездников. Големы его не трогают.
- 25 сердец, анимация ходьбы, один Хончо на весь мир.
- После третьего флакона держится к игроку ближе, а тому, кто его кормит, иногда говорит, что свет
  больше не ведёт его — теперь его ведёт игрок.
- Встреча на ветке войны стала полноценной катсценой: игрок спотыкается и лежит на земле, камера
  внизу, Хончо протягивает руку. «Да» — он поднимает, радуется, гладит по голове и тут же, в той
  же сцене, рассказывает свою историю и на коленях просит взять его в помощники. «Нет» — молится и
  переспрашивает; второй отказ — уходит из мира навсегда. Во время сцены игрока нельзя ранить, она
  не начинается посреди Побега или боя с боссом и не может «потерять» окно ответа.
- С модом Cinematic встреча получает настоящий кадр с камерой и затемнением.
- Команды `/lotus honcho` для проверки всего перечисленного.

> **От Reranyti:** Окееееей, теперь Хончо… У нас из фиксоооов:
> 25 сердечек — он больше не сдохнет от первого удара. Порадуйтесь, ваш виртуальный муж не сдохнет.
> И у нас появилась поддержка нового мода! Cinematic для катсцен — с ним камера работает намного лучше.
> Теперь исправлен «КАТЯЩИЙСЯ ХОНЧОООО».
> Также добавлены команды для тестов, а я откланиваюсь.

**Заражение**
- Заражение наконец видно: оно растёт по поверхности, а не прячется в камне. Под землю начинает
  уходить с третьей фазы, а на пятой пробивается глубоко — там же появляется лотосовая руда.
- Темп спокойнее — примерно в 3–4 раза медленнее, чем в бете; полмира за пару игровых дней больше
  не заражается.
- Туман в заражённой зоне действительно сгущается, и пятая фаза теперь самая тяжёлая, а не слабее
  второй.
- Созревший очаг (фаза 4) правда становится болотом лотоса — биом меняется на всю высоту, заодно
  догоняют и старые очаги. Для этого пришлось исправить ошибку в самом WorldEdit.
- В родном болоте и на своей территории лотос растёт быстрее — это настоящий бонус, а не
  перекраска.
- Корни больше не превращают воду в твёрдый пол, на суше они редкие; над водой заражается дно.
- Ростки лотоса стоят на воде вместе со своей кувшинкой и не ломаются; очищающий порошок их не
  убирает, но замедляет очаг.
- Лотосовые деревья всегда в цветах лотоса.
- Заражённая вода больше не отдельная жидкость — вода остаётся водой, а заражение живёт в биоме и
  тумане. Вместе с ней ушли устаревшие блоки: лотосовая земля, спутанные корни, сухая древесина
  Блеска.
- У болота лотоса и тундры Блеска появились свои цвета тумана — раньше они брали чужие.
- Мшистые железы больше не пропадают на всю сессию из-за одной неудачной попытки найти им место.
- Корни растут через очередь и не застревают, кувшинки дочерних очагов не ставятся на сушу, а
  Сердце лотоса в мире снова одно.
- Убраны повторяющиеся залпы деревьев, лианы, стиравшие постройки, подвисания из-за загрузки чанков
  и заплатки Блеска прямо во время генерации.

> **От Reranyti:** Ой, бля, моё нелюбимое.
> Ну, теперь короче вся поверхность хорошая! Заражается до… эм… Раньше оно жрало как не в себя, если что, но там был коммит исправлений, и мы понизили его скорость.
> Там наконец-то есть вкусный, сладкий туман — теперь вы будете как ёбек в тумани. Ну и, конечно, логично было исправить то, что лотос такой: «ну билин, это моя территория, а я не расту быстрее» — теперь исправлено.
> Заражённая вода сосёт. Откланиваюсь.

**Сюжет и события**
- Побег и StarFall приходят раньше — либо по заражению мира, либо просто спустя время после выбора
  ветки.
- Страницы дневника Объекта Ноль находятся не только у стражей: и возле созревающих очагов, и за
  пройденный Побег, и без дублей.
- Метеоритный камень опасен Альянсу, а не Войне: ветка войны на стороне Звёздного Света. Добыть его
  теперь можно — раньше не получалось ни у кого, как и нормально копать остальные блоки мода нужными
  инструментами.
- Место падения метеорита стало огромным пятном, сразу считается территорией Блеска (стражи там не
  появляются, лотос растёт медленнее) и отмечается на карте.
- Лаборатория Побега строится без дыр; выйти из игры посреди забега — значит быть пойманным, и
  наказание теперь действительно доходит.
- Чёрные сердца — только на ветке Альянса.
- StarFall больше не обрушивает FPS и не даёт повторять сцену ради выгоды.
- Стражи не превращаются в мирных волков после перезагрузки, арена босса-предателя и ветка его
  достижений работают, споры и холодная порча реально наносят урон.

**Системы**
- Репутация с фракциями — Лотос, Учёные, Звёздный Свет.
- Собственный игровой календарь с виджетом.
- Всё, что игрок прошёл, переживает смерть и возвращение из Края; стартовый набор не выдаётся
  повторно.
- Ответы на сцены проверяются сервером: подделанный клиент больше не выдаст себе концовки и не
  повторит сцены.

**Диалоги**
- С Chat Overhaul реплики Звёздного Света, Мирового Лотоса, Хончо и внутреннего голоса звучат в
  его чате — у каждого свой цвет и своя иконка (раньше Звёздный Свет был с лицом Стива). Наши окна
  диалогов тогда не мешают, а окна выбора остаются нашими.

**Совместимость и производительность**
- Все связи с другими модами собраны в один реестр — `/lotus compat` показывает, что включено.
  Правки чужих модов проверяют, что нужный код есть в установленной версии, и поэтому не ломаются
  ни на старых, ни на новых версиях.
- Исправлены ошибки в зависимостях: WorldEdit (смена биома), Ex Meteor Shower (три его достижения
  не загружались).
- Streams Reflowing: реки вокруг точки появления строятся заранее, при первом открытии мира, и мод
  один раз предлагает ему более лёгкое качество — это главная причина подвисаний на слабых ПК при
  прогрузке новых мест. Своё значение игрока не трогаем, всё выключается в конфиге.
- JourneyMap: метки и зона заражения не пересобираются впустую.
- Мод запускается на выделенном сервере и без TerraBlender; все нужные зависимости объявлены.
- 15 автоматических проверок (GameTest) прогоняют Хончо, распространение, порошок, метеоритный камень,
  лоровые пороги и смену биома; в релизный jar они не входят.

**Мелочи**
- Экранные оверлеи больше не рисуются по двадцать раз за кадр, а таймер Побега идёт по настоящему
  времени.
- Английский перевод (`en_us.json`) — у игроков не на русском больше нет сырых ключей.
- Модель «Витаминки», достижение за очищающий порошок, текстура Хончо в jar, клавиша N при ответе
  Хончо, сброс клиентских состояний при выходе из мира, страница дневника засчитывается при подборе,
  а не при выпадении, пасхалка мира с одним биомом, рассинхрон ягод и жезла, частицы, которые не
  появлялись.

### English

**Honcho**
- Honcho has a model of his own instead of a borrowed zombie body, with elbows, knees and a waist;
  his idle, meeting and kneeling prayer poses come straight from the Blockbench project. They first
  played mirrored — arms swinging toward his back — and now sit exactly as designed.
- He's no longer a zombie underneath either: a creature of his own with no sunburn, no Drowned
  conversion, no reinforcements, no baby copies or chicken jockeys. Golems leave him alone.
- 25 hearts, a walking animation, one Honcho per world.
- After the third vial he stays closer to the player, and now and then tells whoever feeds him that
  the light doesn't guide him anymore — the player does.
- The war-branch meeting is a real cutscene now: the player trips and lies on the ground, the camera
  low, and Honcho offers a hand. Yes — he pulls them up, is glad, pats their head and, still in the
  same scene, tells his story and kneels to ask to be their assistant. No — he prays and asks again;
  a second no and he leaves the world for good. The player can't be hurt during it, it never starts
  mid-Chase or mid-boss-fight, and it can't lose its answer window.
- With the Cinematic mod the meeting gets a proper camera shot and fade.
- `/lotus honcho` commands to test all of the above.

> **From Reranyti:** Okaaaay, now Honcho… Our fixeeees:
> 25 hearts — he won't drop dead from the first hit anymore. Rejoice, your virtual husband won't die.
> And we now support a new mod! Cinematic for cutscenes — the camera works much better with it.
> The "ROLLING HONCHOOOO" is fixed now.
> Test commands were added too, and I'm taking my leave.

**Infection**
- The infection is finally visible: it grows across the surface instead of hiding in the rock. It
  starts reaching underground from phase 3 and at phase 5 pushes deep — that's where lotus ore forms.
- A calmer pace — roughly 3-4 times slower than in the beta; half the world no longer falls in a
  couple of in-game days.
- The fog in infected land really thickens, and phase 5 is now the heaviest instead of weaker than
  phase 2.
- A mature outbreak (phase 4) truly becomes lotus marsh — the biome changes over the full height, and
  older outbreaks catch up. That took fixing a bug in WorldEdit itself.
- In its home marsh and its own territory the lotus grows faster — a real bonus, not a recolor.
- Roots no longer turn water into a solid floor and are rare on land; over water the bottom gets
  infected.
- Lotus shoots sit on the water with their own lily pad and can't be broken; cleansing powder leaves
  them but slows their outbreak.
- Lotus trees always wear the lotus palette.
- Infected water is no longer a separate fluid — water stays water, the infection lives in the biome
  and the fog. Outdated blocks went with it: lotus soil, tangled roots, dry Blessing wood.
- Lotus marsh and Blessing tundra have fog colors of their own — they used to borrow others'.
- Mossy glands no longer vanish for the whole session after one failed attempt to place them.
- Roots grow through a queue and don't get stuck, child outbreaks don't put lily pads on dry land,
  and there's only ever one Lotus Heart in the world again.
- Gone: repeating tree bursts, vines wiping out builds, hitches from chunk loading, and Blessing
  patches placed mid-generation.

> **From Reranyti:** Oh damn, my least favorite.
> So now, basically, the whole surface is good! It infects up to… uh… It used to eat everything like crazy, just so you know, but there was a fix commit and we slowed it down.
> There's finally a tasty, sweet fog — now you'll be like a hedgehog in the fog. And of course it made sense to fix the lotus going "ugh, this is my territory and I don't grow any faster" — fixed now.
> Infected water sucks. I'm taking my leave.

**Story and events**
- The Chase and StarFall arrive sooner — by world infection, or simply some time after choosing a
  branch.
- Object Zero diary pages don't only come from guardians anymore: also near maturing outbreaks and
  for surviving the Chase, never as duplicates.
- Meteorite stone is deadly to the Alliance, not the war branch: the war branch is on Star Light's
  side. It can actually be mined now — nobody could before, and the mod's other blocks didn't take
  the right tools either.
- A meteor impact leaves a huge patch, counts as Blessing territory right away (no guardians, slower
  lotus) and shows on the map.
- The Chase lab is built without holes; logging out mid-run means being caught, and the penalty
  actually lands now.
- Black hearts belong to the Alliance branch only.
- StarFall no longer tanks the frame rate or lets its scene be replayed for gain.
- Guardians don't turn into passive wolves after a restart, the traitor boss arena and its
  advancement branch work, spores and the cold blight really deal damage.

**Systems**
- Reputation with factions — the Lotus, the Scientists, Star Light.
- A game calendar of its own with a HUD widget.
- Everything a player has been through survives death and leaving the End; the starter kit isn't
  handed out twice.
- Scene answers are checked by the server: a modified client can't hand itself endings or replay
  scenes anymore.

**Dialogue**
- With Chat Overhaul the lines of Star Light, the World Lotus, Honcho and the inner voice are spoken
  in its chat — each with a name color and an icon of their own (Star Light used to wear Steve's
  face). Our dialogue boxes step aside then; the choice windows stay ours.

**Compatibility and performance**
- Every link to another mod is gathered in one registry — `/lotus compat` shows what's active.
  Patches to other mods check that the code they need exists in the installed version, so they hold
  up on older and newer versions alike.
- Bugs fixed inside dependencies: WorldEdit (biome changes), Ex Meteor Shower (three of its
  advancements never loaded).
- Streams Reflowing: the rivers around spawn are built up front the first time a world opens, and the
  mod suggests a lighter river quality once — the main cause of hitches on weaker PCs while new land
  loaded. A value the player chose is left alone, and it can all be turned off in the config.
- JourneyMap: markers and the infection area aren't rebuilt for nothing.
- The mod runs on dedicated servers and without TerraBlender; every required dependency is declared.
- 15 automated checks (GameTest) run through Honcho, spread, the powder, meteorite stone, the lore
  thresholds and the biome change; they're left out of the release jar.

**Small things**
- HUD overlays are no longer drawn twenty times a frame, and the Chase timer runs on real time.
- An English translation (`en_us.json`) — players not on Russian no longer see raw keys.
- A model for the vitamin item, the cleansing powder advancement, Honcho's texture in the jar, the N
  key while answering Honcho, client state reset when leaving a world, diary pages counted on pickup
  rather than on drop, the single-biome world easter egg, berry and rod desync, particles that never
  showed.

## 1.1042 — Beta: full audit release (1.1017 – 1.1042)
A full read-through of the whole mod (175 classes, resources, design docs) with every confirmed
bug fixed, then checked by building the jar and starting a dedicated server until `Done`. One
commit per fix, kept on the `fix/audit-2026-09` branch for reference.

Critical:
- Player state (branch, traitor flag, Honcho progress, StarFall, reputation, one-time gifts) was
  wiped on every death and End exit - Forge only carries over `PlayerPersisted`. Now copied on clone.
- Meteorite stone could never be obtained: no `mineable` tags existed for any block of the mod,
  and mining progress read an always-empty client state. The contact kill for the war branch never
  fired either (`entityInside` on a full cube) - now `stepOn` and punching it.
- Honcho, a Zombie underneath, burned at dawn, turned into a Drowned underwater, was attacked by
  golems and could call reinforcements. All disabled; he now spawns once per world.
- StarFall: the grafting rod was never removed (`removeItem` compares by reference), and the
  client-sent line packet could be replayed to drop the radius-24 meteor patch at will.
- Black hearts for golden items now apply to the Alliance branch (the one Star Light warns).

Major:
- Phase 4 biome rewrite only changed the Y=0 biome cell (WorldEdit's 2D `setBiome`); now every
  4x4x4 cell over the full height.
- Guardians turned into passive wolves after a restart or chunk reload (goals aren't saved).
- All six HUD overlays were drawn once per HUD layer (20+ times a frame); the chase clock counted
  frames instead of ticks and ran out in seconds.
- The traitor advancement branch never loaded (missing parent `follow_the_current`).
- Lotus spores and cold blight damage almost never landed (two unrelated counters).
- Starter kit handed out again when logging in from another dimension.
- The phase 4 tree burst, biome rewrite and announcement replayed whenever the block count dipped
  under a threshold and climbed back - outbreaks now remember their peak phase.
- Synchronous chunk loads in the spread engine; vine barriers and the boss arena overwrote chests.
- Traitor boss arena was built inside the player; leftover wave mobs stayed in the world forever.
- Roots and leaves never applied spores on contact.

Found by starting a dedicated server:
- The mod failed to construct without TerraBlender (class verification needed `terrablender.api.Region`
  before the `isLoaded` check). TerraBlender is now a required dependency.
- Client-only rendering, color handlers and the wiki/journal screens were referenced from common
  classes, so the mod never loaded on a dedicated server.

Dependencies:
- Ex Meteor Shower needs OctoLib (and OctoLib needs Architectury) without declaring it - both are
  now listed as required, so a missing one is reported by the loader instead of crashing.
- Chat Overhaul declared as an optional dependency.

Smaller fixes: packet directions and server-side validation, Honcho scenes re-shown if left
unanswered, client state and music reset when leaving a world, the Honcho "N" answer no longer
opens the atlas, the chase lab waits for its whole footprint to load and leaving mid-run counts as
being caught, roots queue, lily pads on dry land, a second heart from the epicenter, scientist
pages counted on pickup, `en_us.json` fallback, Honcho idle pose (the animation never played
because of a name mismatch), boss wave count corrected to 5 in the docs.

## 1.1011 – 1.1016
- Faction reputation; Honcho moves closer after the 3rd vial (1.1011).
- Honcho gets his own GeckoLib model instead of the zombie one, plus the trip-meeting scene (1.1012).
- Infected water tint per biome fixed; Mossy Glands could fail to appear at all (1.1013).
- Lotus territory as a real spread bonus, `honcho.geo.json` fix, own calendar (1.1014).
- Real phase 4 biome transition through WorldEdit; infected water removed as a separate fluid (1.1016).

## 1.1010 — Versioning switch
- Switched `mod_version` from `major.minor.patch` to a flat `1.NNNN` counter.

## 1.101.0 — Honcho ambient line
- Rare ambient line for whoever is currently feeding Honcho, echoing "the
  light used to guide him, now the player does" without quoting anything.

## 1.100.0 — Meteorite patches, Blessing territory, map waypoints
- StarFall's meteorite impact patch radius widened substantially (9 → 24).
- Impact sites now carry their own "territory" flag (not a real Minecraft
  biome) recognized by the same checks that already treat the real Blessing
  biome specially (guardian spawn skip, infection spread penalty) — set the
  instant the site is seeded, ahead of the slower block-by-block spread.
- Meteorite impact positions now show as waypoints on the minimap.

## 1.96.0 – 1.99.0 — Honcho
- New original quest NPC appearing once after a player survives StarFall:
  brings him a Star Light Vial, gets a permanent health blessing and lore in
  return. A short-lived duplicate NPC ("Horichoniy", a WanderingTrader-based
  vendor) was added by mistake and removed again the same day.
- War-branch-only "let me be your assistant" scene on first meeting, with a
  Yes/No choice each granting its own advancement.
- The vial quest became repeatable instead of one-time: every vial deepens a
  real dependency — Honcho follows whoever fed him and visibly weakens
  (Weakness + Slowness) if 20 minutes pass without another one.

## 1.93.0 – 1.94.1 — Quarantine world border
- World border capped at 1,000,000 blocks, centered on spawn, for story
  reasons ("the site was sealed under quarantine").
- Fully custom (not vanilla) purple force-field wall rendered at the nearest
  edge, plus a bounce-back-and-can't-die-from-it mechanic replacing vanilla's
  own border damage, plus a blood-red sky tint on approach.

## 1.91.0 – 1.92.0 — Per-biome infection color overhaul
- Every biome with a dictated fog gradient now derives its own ground/wood
  tint from that gradient instead of one of 8 shared group colors.
- Stone/sand/gravel intentionally kept one shared tone across every biome
  (they're minerals, not biome-tied organics) — fixed from an accidental
  identical color bug, not turned into 50 more variants.
- Infected water's underwater fog now follows the same per-biome gradient
  instead of one fixed dark green everywhere.
- Infection spread speed now fades smoothly with distance from the nearest
  player instead of freezing outbreaks outside a fixed radius entirely.

## 1.87.0 – 1.90.0 — Post-audit bug-fix wave
- A second full-mod workflow audit found and fixed: an unguarded
  synchronous-chunk-load deadlock (also present in three other spots besides
  the one already fixed in the Escape lab), a starter-water-search fairness
  bug starving later-joining players, grass blocks almost never actually
  converting during infection spread, and tree canopies starving ground
  conversion of frontier attempts.
- Several one-off crashes/bugs on specific Forge builds and StarFall's own
  branch mix-up were fixed the same week.

## 1.85.0 – 1.86.0 — Meteorite gear, real StarFall trigger
- Crafting recipes for the whole `lotus_alloy` tool/armor set, the grafting
  rod, seer lens and vitamin — previously creative-only.
- StarFall now fires on its own once world infection crosses 30%, instead of
  only via the admin test command.
- Full meteorite tool/armor tier (between diamond and netherite).

## 1.68.0 – 1.84.0 — Traitor branch, Escape, StarFall
- Full traitor storyline: guardian-kill/cleanse-powder betrayal triggers, a
  5-wave boss fight, and its own incineration cutscene.
- "Побег от лотоса" — a persistent, sealed escape-the-lab event with a
  real/decoy junction, unlocked only once world infection crosses 15%.
- StarFall — a one-time Star Light encounter with a full alliance/war branch
  script, ending in either a permanent resource or a real consequence
  depending on the player's choices, backed by the Ex Meteor Shower mod for
  its comet visuals.

## 1.6.0 — Inner voice system, glowing berries fixed
- `glowing_berry` was registered but unreachable (bush dropped itself, not the
  food item) — right-click the bush to harvest instead of breaking it.
- New True Light effect (opposite of Lotoniriya) and a second, small
  "dialogue system": eating a glowing_berry shows a short subtitle-style
  scene (player's own skin/name as the portrait, not the Lotus), advanced
  with Enter, with a gold vignette and its own music cue.

## 1.5.0 — Full art pass (sprite sheet)
- Replaced 12 item textures and ~19 block textures with hand-drawn sprite
  sheet art, replacing the earlier procedural Pillow textures.
- Split `infected_lotus.png` from the GeckoLib crown's box-UV atlas (they
  used to share one file, which would have scrambled the crown's geometry).
- `blessing_nodule` intentionally left on the old texture (new art has
  transparent gaps unsuitable for a cube_all block).

## 1.4.0 — Live bug-fix pass (config, cleansing, armor, ore, biome, clustering)
- `ACTIVE_CHUNK_RADIUS`, `SPREAD_RADIUS`, `MAX_PENDING_WORLDGEN_TASKS` config
  values wired up (previously read nowhere).
- Blessing biome's wiki-claimed spread slowdown actually implemented.
- Fixed JourneyMap version-range crash (`1.20.1-6.0.5` compared as < `6.0`).
- Cleansing powder now actually decreases outbreak progress.
- Armor texture lookup fixed (missing namespace) and a real texture added.
- `lotus_ore` given worldgen placement (previously could never spawn).
- Blessing biome moved out of TerraBlender's snow-generating climate zone.
- Mini-lotus shoot spam clustering fixed with a spacing check.

## 1.3.0 — Spread engine deep fixes
- Fixed the "pillars instead of lotuses" bug (ordinary spread was placing a
  full 4-tall anchor instead of a small shoot).
- Root-grown child mini-outbreaks now place a visible flower.
- Open-ocean spread sped up but capped below the mini-biome phase.
- Lotus Heart now only appears at real phase-4 maturity, not instantly from
  a planted seed.
- Guaranteed distant mini-biome epicenter (5000-9000 blocks from spawn).
- Admin `/lotus` command tree for testing outbreaks/branches/map state.

## 1.2.0 — Dialogue system rewrite
- Branch choice (Alliance/Resistance) now actually persists, locks
  permanently server-side, and influences real spread speed near the player
  — previously it only gated one tool and reset every time the dialogue
  screen reopened.
- Rewrote all dialogue lines for a more natural tone; wired previously dead
  lore content (scientist's journal, village water crisis) into the flow.

## 1.1.0 — Integrations and item audit
- GeckoLib bloom animation for the infected lotus crown.
- Soft TerraBlender integration for biome placement.
- JourneyMap waypoint integration (replacing the mod's own HUD map when
  JourneyMap is installed).
- YACL-based in-game config screen.
- Curios API investigated for the seer lens, reverted before shipping —
  would have hard-crashed the mod for anyone without Curios installed.
- Fixed a held-item render bug and an Embeddium chunk-mesh crash on
  infected water.
- Item/lang audit: missing translation keys, dead duplicate advancement key.

## 1.0.0 — Full rewrite baseline
- Complete rewrite from the broken `story-v11` prototype: persisted
  SavedData-backed outbreak/spread engine (replacing a non-persistent static
  map), full item/armor/tool set, worldgen (biomes, mini-biome, guaranteed
  spawn), lore/dialogue foundation, procedural art pass.
