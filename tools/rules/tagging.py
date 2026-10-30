"""
Automatic situation tags, derived from a rule's wording. These are a first pass: anything they get
wrong is fixed in tag-overrides.json rather than here, unless the fix is a general pattern.
"""

import re

# id -> label shown in the app, in display order.
TAGS = {
    "strategy": "Strategy phase",
    "activate": "Activating",
    "move": "Moving",
    "charge": "Charging",
    "shoot": "Shooting",
    "shot": "Being shot",
    "fight": "Fighting",
    "retaliate": "Retaliating",
    "action": "Other actions",
    "objective": "Objectives",
    "counteract": "Counteracting",
    "incapacitated": "Incapacitated",
    "end": "End of turning point",
    "general": "General",  # given to anything the patterns don't place; see tag-overrides.json
}

# Each tag matches if any of its patterns is found in the lower-cased rule text.
PATTERNS = {
    "strategy": [r"strategy phase", r"start of (each|the|a) turning point", r"\binitiative\b", r"strategic gambit"],
    "activate": [r"\bis activated\b", r"\bwhen (it|this operative|a friendly [^.]*operative) is activated",
                 r"(start|end) of (its|that operative's|this operative's|their) activation", r"\bapl stat\b",
                 r"\bexpended\b"],
    "move": [r"\breposition\b", r"\bdash\b", r"\bfall back\b", r"\bmove stat\b", r"\bmoving\b",
             r"\bmove (up to|an additional|through|over)\b", r"\bclimb", r"\bdrop\b", r"\bjump"],
    "charge": [r"\bcharge\b", r"\bcharging\b"],
    "shoot": [r"\bshoot actions?\b", r"\bfriendly [^.]{0,60}operative is shooting\b", r"\bthis operative is shooting\b",
              r"\branged weapons?\b", r"\bwhenever (it|this operative) is shooting\b", r"\bshooting (an|a) enemy\b",
              r"\bperform (a|the) (free )?shoot\b", r"\bprimary target\b", r"\bsecondary target"],
    "shot": [r"\benemy operative is shooting\b", r"\bshooting (against |at )?(a|this|that) friendly\b",
             r"\bdefence dice\b", r"\bcover save\b", r"\bnot a valid target\b", r"\bcannot be selected as a valid target\b",
             r"\bobscured\b", r"\bsave stat\b", r"\bin cover\b",
             r"\binflicts? [^.]{0,30}dmg on (a|this|that) friendly", r"\bdamage inflicted on (it|this operative)\b"],
    "fight": [r"\bfight actions?\b", r"\bis fighting\b", r"\bmelee weapons?\b", r"\bfighting\b", r"\bstrike\b",
              r"\bperform (a|the) (free )?fight\b"],
    "retaliate": [r"\bretaliat", r"\benemy operative is fighting\b", r"\bfighting (against )?(a|this|that) friendly\b",
                  r"\bblock\b", r"\bmelee weapons?\b",
                  r"\binflicts? [^.]{0,30}dmg on (a|this|that) friendly", r"\bdamage inflicted on (it|this operative)\b"],
    "objective": [r"\bobjective marker", r"\bobjective\b", r"\bmission action", r"\bpick up marker\b",
                  r"\bplace marker\b", r"\bcontrol (an|the|that) objective", r"\bvictory points?\b", r"\bvps?\b",
                  r"\bop\b", r"\bkill op\b", r"\btac op"],
    "action": [r"\bcan perform (the|a|that) (?!(free )?(shoot|fight|charge|dash|reposition|fall back)\b)[a-z' -]{3,30} actions?\b"],
    "counteract": [r"\bcounteract"],
    "incapacitated": [r"\bincapacitated\b"],
    "end": [r"\bend of the (firefight phase|turning point|battle)\b", r"\bend of each turning point\b"],
}

COMPILED = {tag: [re.compile(p) for p in pats] for tag, pats in PATTERNS.items()}


def tag_text(kind: str, text: str) -> list[str]:
    t = text.lower().replace("’", "'")
    tags = {tag for tag, pats in COMPILED.items() if any(p.search(t) for p in pats)}
    if kind == "STRATEGY_PLOY":
        tags.add("strategy")
    # Unique actions ("NAME  1AP") are named actions; Other actions covers anything an operative does.
    if kind == "OPERATIVE" and re.search(r"\bthis operative can perform this action\b|\bcannot perform this action\b", t):
        tags.add("action")
    return sorted(tags)
