"""
Checks the story data files without starting the game: every character file is valid and its skin exists and is 64x64 with an opaque face,
every dialogue refers to known speakers/intros, its labels exist, every line can end, the dimension files parse.
Exit code 0 = fine.
"""
import json
import os
import sys
from PIL import Image

RES = 'D:/The LOTOS/src/main/resources'
A = RES + '/assets/lotusblight'
D = RES + '/data/lotusblight'
errors = []


def err(msg):
    errors.append(msg)
    print('ERROR:', msg)


chars = {}
for f in sorted(os.listdir(A + '/characters')):
    try:
        o = json.load(open(f'{A}/characters/{f}', encoding='utf-8'))
    except Exception as e:
        err(f'{f}: not valid JSON ({e})')
        continue
    for k in ('id', 'name', 'skin'):
        if k not in o:
            err(f'{f}: missing "{k}"')
    if o.get('id') and o['id'] + '.json' != f:
        err(f'{f}: id "{o["id"]}" does not match the file name')
    chars[o.get('id')] = o
    ns, _, path = o.get('skin', '').partition(':')
    skin = f'{A}/{path}'
    if ns != 'lotusblight' or not os.path.isfile(skin):
        err(f'{f}: skin {o.get("skin")} not found')
        continue
    im = Image.open(skin).convert('RGBA')
    if im.size != (64, 64):
        err(f'{f}: skin is {im.size}, expected 64x64')
    face = [im.getpixel((8 + x, 8 + y)) for x in range(8) for y in range(8)]
    if any(p[3] < 255 for p in face):
        err(f'{f}: the face area of the skin is not fully opaque')
    if 'color' in o:
        try:
            int(o['color'], 16)
        except ValueError:
            err(f'{f}: colour "{o["color"]}" is not hex')

EXPR = {'neutral', 'blink', 'talk', 'happy', 'angry', 'sad', 'scared', 'shock'}
for f in sorted(os.listdir(A + '/dialogues')):
    try:
        o = json.load(open(f'{A}/dialogues/{f}', encoding='utf-8'))
    except Exception as e:
        err(f'{f}: not valid JSON ({e})')
        continue
    lines = o.get('lines', [])
    if not lines:
        err(f'{f}: no lines')
    labels = {}
    for i, l in enumerate(lines):
        if l.get('label'):
            if l['label'] in labels:
                err(f'{f}: label "{l["label"]}" used twice')
            labels[l['label']] = i
    for i, l in enumerate(lines):
        where = f'{f} line {i}'
        if l.get('intro') is not None:
            if l['intro'] not in chars:
                err(f'{where}: intro of unknown character "{l["intro"]}"')
            continue
        if l.get('speaker') and l['speaker'] not in chars:
            err(f'{where}: unknown speaker "{l["speaker"]}"')
        if l.get('expr') and l['expr'].lower() not in EXPR:
            err(f'{where}: unknown expression "{l["expr"]}"')
        if not l.get('text'):
            err(f'{where}: no text')
        targets = [c.get('next') for c in l.get('choices', [])] + [l.get('next')]
        for t in targets:
            if t and t not in labels:
                err(f'{where}: goes to the missing label "{t}"')
        for c in l.get('choices', []):
            if not c.get('text'):
                err(f'{where}: a choice without text')
            fl = c.get('flag')
            if fl and not all(ch.islower() or ch.isdigit() or ch == '_' for ch in fl):
                err(f'{where}: the flag "{fl}" would be refused by the server (only a-z 0-9 _)')
        fl = l.get('flag')
        if fl and not all(ch.islower() or ch.isdigit() or ch == '_' for ch in fl):
            err(f'{where}: the flag "{fl}" would be refused by the server')
    # the dialogue must be able to end: the last line, or a line marked "end", or a choice chain that ends
    if lines and not any(l.get('end') for l in lines) and not (lines[-1].get('choices') == [] or 'choices' not in lines[-1]):
        err(f'{f}: nothing ends the dialogue (the last line has choices and no line is marked "end")')

for rel in ('dimension/dream.json', 'dimension_type/dream.json', 'dimension/hollow.json', 'dimension_type/hollow.json'):
    try:
        json.load(open(f'{D}/{rel}', encoding='utf-8'))
    except Exception as e:
        err(f'{rel}: not valid JSON ({e})')

dt = json.load(open(f'{D}/dimension_type/dream.json', encoding='utf-8'))
for k in ('min_y', 'height', 'logical_height', 'has_skylight', 'has_ceiling', 'ambient_light', 'effects', 'infiniburn',
          'monster_spawn_light_level', 'monster_spawn_block_light_limit', 'coordinate_scale', 'natural', 'ultrawarm', 'piglin_safe',
          'bed_works', 'respawn_anchor_works', 'has_raids'):
    if k not in dt:
        err(f'dimension_type/dream.json: missing "{k}"')
if dt.get('height', 0) % 16 or dt.get('min_y', 0) % 16:
    err('dimension_type/dream.json: min_y and height must be multiples of 16')

print('characters:', len(chars), 'dialogues:', len(os.listdir(A + '/dialogues')), 'errors:', len(errors))
sys.exit(1 if errors else 0)
