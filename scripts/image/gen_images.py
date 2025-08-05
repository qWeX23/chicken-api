#!/usr/bin/env python3
import os, json, time, hashlib, base64, pathlib, sys
from openai import OpenAI

with open("openai_api_key.txt") as f:
    client = OpenAI(api_key=f.read().strip())

OUT = pathlib.Path("output/breeds").resolve()
OUT.mkdir(parents=True, exist_ok=True)

with open("traits.json") as f:
    traits = json.load(f)

'''
traits.json format:
```json
{
  "breed_name": "description of key physical traits",
  ...
}
```
Example:
```json
{
  "silkie": "fluffy silkie plumage, walnut comb, feathered feet",
  "plymouth_rock": "black-and-white barred pattern, single comb, yellow legs",
  ...
}
'''
images_done = 0
for breed in traits:
    png_path = OUT / f"{breed}.png"
    if png_path.exists():                      # resume-safe
        continue

    prompt = (
        f"A flat, minimalist digital illustration of a {breed.replace('_', ' ')} "
        f"chicken in profile view, facing right … key physical traits: "
        f"{traits[breed]}. "
        "Render in a clean cartoon style with no background (transparent). "
        "Modern, friendly, app-ready."
    )

    resp = client.images.generate(
        model="gpt-image-1",
        quality="medium",
        size="1024x1024",
        prompt=prompt,
        n=1,
    )
    print(f"Generated {breed} image: {resp.data[0]}")
    png_path.write_bytes(base64.b64decode(resp.data[0].b64_json))
    images_done += 1
    time.sleep(1.2)             # polite pacing

print(f"✅ Generated {images_done} images")
