scoreboard players set #world rival_timer 600
scoreboard players set #world rival_count 0
execute as @e[tag=is_rival] run scoreboard players add #world rival_count 1
execute if score #world rival_count matches ..3 as @a[limit=1,sort=random] at @s if biome ~ ~ ~ penguin_mafia:mafia_tundra run function penguin_mafia:rival/do_spawn
execute if score #world rival_count matches ..3 as @a[limit=1,sort=random] at @s if biome ~ ~ ~ penguin_mafia:mafia_peaks run function penguin_mafia:rival/do_spawn
