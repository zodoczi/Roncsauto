# Roncs Autó – Android segédapp

Minimális Android alkalmazás, amely:
- Kamerával beolvassa a magyar rendszámot (CameraX + ML Kit)
- Megnyitja a JSZP oldalt a böngészőben
- Megnyitja a Járókelő bejelentő oldalát előtöltött leírással

## Használat

1. Nyisd meg a projektet Android Studio-ban
2. Engedélyezd a kamera engedélyt
3. Irányítsd a kamerát a rendszámra
4. Ha felismerte → használd a gombokat

## Fontos

- **Nincs automatikus JSZP lekérdezés** – nincs nyilvános API
- A felhasználónak DÁP / Ügyfélkapu+ belépéssel kell lekérdeznie
- Járókelőn is a felhasználó tölti ki a formot (fotó, pontos helyszín stb.)

Ez egy **segédapp**, nem teljes automatizálás.
