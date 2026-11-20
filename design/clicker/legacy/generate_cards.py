"""
СТАРЫЙ ГЕНЕРАТОР — больше не используется: набор карточек теперь правится вручную
в app/src/main/assets/clicker/cards.json. Скрипт оставлен как справка по формулам первого
баланса и пишет только в design/clicker/legacy/generated.json — набор приложения не трогает.

Был: генератор набора карточек кликера: по параметрам баланса выписывал
app/src/main/assets/clicker/cards.json. Запуск: python design/clicker/generate_cards.py [key=value ...]
Параметры подбираются симулятором (ClickerBalanceSimulation в юнит-тестах).

Устройство:
- 5 категорий (Планета, Орбита, Звезда, Галактика, Вселенная) открыты сразу — это темы,
  а не ступени: в каждой есть и дешёвые карточки, и дорогие.
- Карточки дохода выстроены в одну лестницу цен (tiers) от c0 до top_cost и разложены по
  категориям по кругу. Окупаемость тоже растёт по лестнице — от first_payback_h до last_payback_h.
- Около 30% карточек открываются уровнями 1–2 карточек подешевле из любых категорий
  (уровень подобран так, чтобы его прокачка стоила примерно как сама карточка); остальные
  открыты сразу — их сдерживает только цена.
- У карточки не больше max_level уровней. Уровень n даёт baseValue·value_growthⁿ — каждый
  следующий чуть больше; цена растёт быстрее — ×costGrowth за уровень.
- «Хаос» — временные карточки с сервера (здесь их нет, симулятор моделирует события сам).
- Улучшения за звёздную пыль (100 за прохождение) — на все не хватит.
"""
import json, math, os, random, sys

P = dict(
    c0=10.0,                # цена самой дешёвой карточки
    top_cost=5e9,           # цена самой дорогой
    tiers=55,               # карточек дохода (по 11 в категории)
    first_payback_h=2.0,    # окупаемость первого уровня самой дешёвой, часов пассивного дохода
    last_payback_h=200.0,   # и самой дорогой
    income_cost_growth=1.22,
    value_growth=1.10,      # во сколько раз каждый следующий уровень даёт больше
    max_level=30,
    tap_cost=200.0,         # прокачка тапа: цена первого уровня
    tap_cost_growth=1.6,
    tap_value=1.0,          # +коинов за тап на первом уровне
    tap_value_growth=1.30,  # во сколько раз каждый следующий уровень даёт больше
    tap_levels=40,
    base_income=0.0,        # пассивный доход без карточек: игрок начинает с нуля (тапами)
    goal=4e12,              # цель прохождения — заработать столько
    dependent_share=0.3,    # какая доля карточек дохода требует уровней других карточек
    seed=7,
)

CATEGORIES = [("planet", "Планета"), ("orbit", "Орбита"), ("star", "Звезда"), ("galaxy", "Галактика"), ("universe", "Вселенная")]

PERKS = [
    # id, имя, эффект, значение за уровень, цена первого уровня (n-й стоит цена·n), уровней
    ("stars", "Сила звёзд: +15% ко всему доходу", "income", 0.15, 25, 4),
    ("discount", "Скидка: −5% к ценам карточек", "discount", 0.05, 20, 4),
    ("dream", "Дремлющий двигатель: +10% к доходу вне игры", "offline_rate", 0.10, 30, 2),
    ("reserve", "Запас: +3 ч дохода вне игры", "offline_cap", 3.0, 20, 3),
    ("capital", "Стартовый капитал: +50 тыс. коинов в начале", "start_coins", 5e4, 15, 3),
    ("impulse", "Импульс тапа: +0.5 с дохода за тап", "tap_seconds", 0.5, 15, 3),
    ("battery", "Аккумулятор: +500 энергии", "energy_max", 500.0, 10, 3),
    ("regen", "Регенератор: +0.5 энергии в секунду", "energy_regen", 0.5, 15, 2),
]


def build(p):
    rnd = random.Random(p["seed"])
    n = p["tiers"]
    ratio = (p["top_cost"] / p["c0"]) ** (1 / (n - 1))
    pb_ratio = (p["last_payback_h"] / p["first_payback_h"]) ** (1 / (n - 1))
    per_level = math.log(ratio) / math.log(p["income_cost_growth"])  # столько уровней «стоят» одну ступень
    cats = [{"id": cid, "name": name} for cid, name in CATEGORIES]
    cards, tier_card = [], []
    counters = {cid: 0 for cid, _ in CATEGORIES}

    def cost_at(t):
        return p["c0"] * ratio ** t

    def payback_at(t):
        return p["first_payback_h"] * pb_ratio ** t

    def requirement(t, gap):
        """Карточка на gap ступеней дешевле и уровень, прокачка до которого ≈ цене ступени t."""
        src = tier_card[t - gap]
        return {"card": src, "level": min(p["max_level"] - 5, max(2, round(gap * per_level * 0.85)))}

    for t in range(n):
        cid, name = CATEGORIES[t % len(CATEGORIES)]
        counters[cid] += 1
        cost = cost_at(t)
        # Условия — только у части карточек (dependent_share), остальные открыты, пока по карману
        requires = []
        if t >= 2 and rnd.random() < p["dependent_share"]:
            requires.append(requirement(t, rnd.randint(1, min(4, t))))
            if t >= 8 and rnd.random() < 0.5:
                r2 = requirement(t, rnd.randint(5, min(12, t)))
                if r2["card"] != requires[0]["card"]:
                    requires.append(r2)
        card_id = f"{cid}_{counters[cid]}"
        tier_card.append(card_id)
        cards.append({
            "id": card_id, "category": cid, "name": f"{name} · {counters[cid]}", "effect": "income",
            "baseCost": round(cost), "costGrowth": p["income_cost_growth"],
            "baseValue": round(cost / payback_at(t), 3), "valueGrowth": p["value_growth"], "maxLevel": p["max_level"],
            "requires": requires,
        })

    def utility(cid, name, kind, label, t, extra):
        """Вспомогательная карточка категории на ступени t: открывается её дешёвой карточкой."""
        t = min(t, n - 1)
        own = [c for c in tier_card[: t + 1] if c.startswith(cid + "_")]
        req = [{"card": own[-1] if own else tier_card[0], "level": 3}]
        if t >= 4:
            req.append(requirement(t, rnd.randint(2, min(6, t))))
        card = {"id": f"{cid}_{kind}", "category": cid, "name": f"{name} · {label}", "requires": req}
        card.update(extra(cost_at(t), payback_at(t)))
        cards.append(card)

    # В категориях — только пассивный доход: карточки дохода и усилитель категории
    for k, (cid, name) in enumerate(CATEGORIES):
        utility(cid, name, "boost", "усилитель", 18 + 7 * k, lambda c, pb, cid=cid: {
            "effect": "boost", "target": cid, "baseCost": round(c * 8), "costGrowth": 2.0, "baseValue": 0.10, "maxLevel": 10})

    # Прокачка тапа — отдельно, кнопкой на вкладке «Тап»: сила тапа, объём и восстановление энергии.
    # Сила тапа растёт по той же лестнице, что и доход: цена — ×tap_cost_growth за уровень,
    # прибавка — ×tap_value_growth; окупаемость (у того, кто тапает) растёт вместе с игрой
    cards.append({"id": "tap_power", "category": "tap", "name": "Сила тапа", "effect": "tap",
                  "baseCost": p["tap_cost"], "costGrowth": p["tap_cost_growth"],
                  "baseValue": p["tap_value"], "valueGrowth": p["tap_value_growth"], "maxLevel": p["tap_levels"]})
    cards.append({"id": "tap_energy", "category": "tap", "name": "Объём энергии", "effect": "energy_max",
                  "baseCost": 500, "costGrowth": 3.2, "baseValue": 250.0, "maxLevel": 10})
    cards.append({"id": "tap_regen", "category": "tap", "name": "Восстановление энергии", "effect": "energy_regen",
                  "baseCost": 2000, "costGrowth": 3.6, "baseValue": 0.25, "maxLevel": 8})
    cats.append({"id": "tap", "name": "Тап", "hidden": True})

    cats.append({"id": "chaos", "name": "Хаос", "temporary": True})
    perks = [{"id": i, "name": nm, "effect": e, "value": v, "cost": c, "maxLevel": m} for i, nm, e, v, c, m in PERKS]
    # Уровни игрока — вид планеты: лава, камень, океаны, трава, города, спутник, много спутников, кольцо
    rules = {"goal": p["goal"], "stardustPerRun": 100, "maxRuns": 3, "baseIncomePerHour": p["base_income"], "tapIncomeSeconds": 0.0,
             "levels": [0, 1e4, 1e5, 5e5, 1e6, 1e7, 1e9, 1e12]}
    return {"version": 6, "rules": rules, "categories": cats, "cards": cards, "perks": perks}


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        k, v = arg.split("=")
        P[k] = type(P[k])(v)
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    # Набор приложения правится вручную — сюда только для сравнения
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "generated.json")
    data = build(P)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    sys.stdout.reconfigure(encoding="utf-8")
    perk_total = sum(c * m * (m + 1) // 2 for _, _, _, _, c, m in PERKS)
    print(f"{len(data['cards'])} карточек, улучшений {len(PERKS)} на {perk_total} пыли, цель {data['rules']['goal']:.3g}")
