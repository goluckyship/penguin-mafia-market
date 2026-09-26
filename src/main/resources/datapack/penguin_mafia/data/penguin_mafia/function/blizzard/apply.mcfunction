# Crude shelter check: if there's a block 2 above the player's head (e.g. a cabin
# roof), they're sheltered and skip the effect. Not a perfect roof detector, but
# it means standing inside a Hideout Cabin genuinely protects you.
execute as @a[biome=penguin_mafia:mafia_peaks] at @s if block ~ ~2 ~ air run function penguin_mafia:blizzard/apply_single
