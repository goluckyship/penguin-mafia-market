scoreboard players set #world don_timer 2400
execute unless entity @e[tag=is_don] as @a[biome=penguin_mafia:mafia_peaks,limit=1,sort=random] at @s run function penguin_mafia:don/spawn
