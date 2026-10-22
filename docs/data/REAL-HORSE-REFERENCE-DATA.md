# Real horse reference data (verified identity and pedigree only)

Registered on 2026-10-22 through the application UI (Club Manager → Register Horse → Pedigree) into the local
database `racehorse_integration_test`. Only the facts below come from public sources. Everything recorded later in the
application for these horses (ownership, stable box, baseline examination, training programs, sessions, medical
records) is **local operational data entered by club users for the course demo**. It is not a fact about the real
horse and must not be presented as one.

Fields left empty on purpose: registration number (no public registry number verified), photos, owner of record.
`horseCode` is an internal club identifier, not a registry code.

| Horse (record) | Sex (app value) | Foaled | Country of origin | Breed | Sire | Dam | Source |
|---|---|---|---|---|---|---|---|
| Frankel | Stallion | 2008-02-11 | Great Britain | Thoroughbred | Galileo (name) | Kind (name) | https://en.wikipedia.org/wiki/Frankel_(horse) |
| Nathaniel | Stallion | 2008-04-24 | Ireland | Thoroughbred | Galileo (name) | Magnificient Style (name) | https://en.wikipedia.org/wiki/Nathaniel_(horse) |
| Enable | Mare | 2014-02-12 | United Kingdom | Thoroughbred | **Nathaniel (linked record)** | Concentric (name) | https://en.wikipedia.org/wiki/Enable_(horse) |
| Sea The Stars | Stallion | 2006-04-06 | Ireland | Thoroughbred | Cape Cross (name) | Urban Sea (name) | https://en.wikipedia.org/wiki/Sea_The_Stars |
| Baaeed | Stallion | 2018-04-08 | United Kingdom | Thoroughbred | **Sea The Stars (linked record)** | Aghareed (name) | https://en.wikipedia.org/wiki/Baaeed |

Relationships preserved: Frankel and Nathaniel share the sire Galileo; Enable is by Nathaniel; Baaeed is by Sea The
Stars. Galileo (1998–2021) and the dams are recorded as pedigree names only, not as stable horses. Sources were read on
2026-10-22 from each article's infobox (Sire, Dam, Sex, Foaled, Country). Baaeed's infobox lists "Colt" (racing-age
term); he stands at stud, so the app value is Stallion.
